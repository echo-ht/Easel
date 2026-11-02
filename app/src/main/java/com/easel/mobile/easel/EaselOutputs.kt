package com.easel.mobile.easel

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.easel.mobile.core.cap.sandbox.ShareRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** 内容库里的一件成品。目前取 share_file 导出的「下载/Easel」,后续可扩展到沙箱工作区。 */
data class EaselOutput(
    val name: String,
    val uri: Uri,
    val size: Long,
    val addedAt: Long,
    val mime: String?,
)

object EaselOutputs {
    fun list(context: Context): List<EaselOutput> {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_ADDED,
            MediaStore.Downloads.MIME_TYPE,
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.OWNER_PACKAGE_NAME} = ?"
        val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/Easel/%", context.packageName)
        val rows = runCatching {
            resolver.query(collection, projection, selection, args, "${MediaStore.Downloads.DATE_ADDED} DESC")?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        add(
                            EaselOutput(
                                name = c.getString(1) ?: "未命名",
                                uri = ContentUris.withAppendedId(collection, id),
                                size = c.getLong(2),
                                addedAt = c.getLong(3) * 1000L,
                                mime = c.getString(4),
                            )
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
        return rows
    }

    /** 把用户从系统文件选择器挑中的素材复制进「下载/Easel」，之后和 Agent 导出的成品放在一起。 */
    suspend fun import(context: Context, source: Uri): EaselOutput = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(source) }.getOrNull()
        val name = ShareRules.fitName(
            runCatching { displayName(context, source) }.getOrNull()
                ?.substringAfterLast('/')
                ?.takeIf { ShareRules.plainName(it) }
                ?: "素材-${System.currentTimeMillis()}",
        )
        if (ShareRules.refusedType(name) || mime == "application/vnd.android.package-archive") {
            throw IllegalArgumentException("安装包不能放进素材库")
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Easel/")
            mime?.let { put(MediaStore.Downloads.MIME_TYPE, it) }
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        var size = 0L
        try {
            val input = resolver.openInputStream(source) ?: throw FileNotFoundException(source.toString())
            val output = resolver.openOutputStream(target) ?: throw FileNotFoundException(target.toString())
            input.use { from ->
                output.use { to ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = from.read(buffer)
                        if (read < 0) break
                        size += read
                        if (size > ShareRules.MAX_BYTES) throw IllegalArgumentException("素材不能超过 100 MB")
                        to.write(buffer, 0, read)
                    }
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            throw e
        }
        EaselOutput(
            name = displayName(context, target) ?: name,
            uri = target,
            size = size,
            addedAt = System.currentTimeMillis(),
            mime = resolver.getType(target),
        )
    }

    suspend fun delete(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
