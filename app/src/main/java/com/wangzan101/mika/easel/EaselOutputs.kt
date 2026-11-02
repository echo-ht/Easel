package com.wangzan101.mika.easel

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

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
}
