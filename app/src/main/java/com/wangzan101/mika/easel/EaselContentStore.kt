package com.wangzan101.mika.easel

import android.content.Context
import com.wangzan101.mika.core.easel.ContentPlan
import com.wangzan101.mika.core.easel.ContentPlanPatch
import com.wangzan101.mika.core.easel.EaselContentPort
import com.wangzan101.mika.core.easel.NewContentPlan
import com.wangzan101.mika.core.easel.NewPublishRecord
import com.wangzan101.mika.core.easel.PlanStatus
import com.wangzan101.mika.core.easel.PublishRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * 内容日历与发布日志，存在 App 私有目录 `files/easel/` 下的两个 JSON 文件里。
 * 和账号画像一样，只在手机上；不依赖沙箱、浏览器或网络。
 */
class EaselContentStore(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : EaselContentPort {
    constructor(context: Context) : this(File(context.filesDir, "easel"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val calendarFile = File(dir, "calendar.json")
    private val publishFile = File(dir, "publish-log.json")

    override suspend fun plans(): List<ContentPlan> = withContext(Dispatchers.IO) {
        synchronized(LOCK) { readPlans() }
    }

    override suspend fun addPlan(input: NewContentPlan): ContentPlan = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val at = now()
            val plan = ContentPlan(
                id = newId(), title = input.title.trim(), platform = input.platform.trim(),
                scheduledAt = input.scheduledAt, status = input.status, notes = input.notes.trim(),
                createdAt = at, updatedAt = at,
            )
            writePlans(readPlans() + plan)
            plan
        }
    }

    override suspend fun updatePlan(id: String, patch: ContentPlanPatch): ContentPlan? = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val plans = readPlans().toMutableList()
            val index = plans.indexOfFirst { it.id == id }
            if (index < 0) return@synchronized null
            val old = plans[index]
            val updated = old.copy(
                title = patch.title?.trim() ?: old.title,
                platform = patch.platform?.trim() ?: old.platform,
                scheduledAt = if (patch.clearScheduledAt) null else patch.scheduledAt ?: old.scheduledAt,
                status = patch.status ?: old.status,
                notes = patch.notes?.trim() ?: old.notes,
                updatedAt = now(),
            )
            plans[index] = updated
            writePlans(plans)
            updated
        }
    }

    override suspend fun deletePlan(id: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val plans = readPlans()
            val kept = plans.filterNot { it.id == id }
            if (kept.size == plans.size) return@synchronized false
            writePlans(kept)
            true
        }
    }

    override suspend fun publishLog(limit: Int): List<PublishRecord> = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            readPublishes()
                .sortedWith(compareByDescending<PublishRecord> { it.publishedAt }.thenByDescending { it.createdAt })
                .take(limit)
        }
    }

    override suspend fun addPublish(input: NewPublishRecord): PublishRecord = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val at = now()
            val record = PublishRecord(
                id = newId(), title = input.title.trim(), platform = input.platform.trim(), status = input.status,
                publishedAt = input.publishedAt ?: localTime(at), postUrl = input.postUrl?.trim()?.ifBlank { null },
                planId = input.planId?.trim()?.ifBlank { null }, note = input.note.trim(), createdAt = at,
            )
            writePublishes(readPublishes() + record)
            record
        }
    }

    override suspend fun deletePublish(id: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val records = readPublishes()
            val kept = records.filterNot { it.id == id }
            if (kept.size == records.size) return@synchronized false
            writePublishes(kept)
            true
        }
    }

    private fun readPlans(): List<ContentPlan> = read(calendarFile, ContentPlan.serializer())
    private fun readPublishes(): List<PublishRecord> = read(publishFile, PublishRecord.serializer())
    private fun writePlans(plans: List<ContentPlan>) = write(calendarFile, ListSerializer(ContentPlan.serializer()), plans)

    private fun writePublishes(records: List<PublishRecord>) =
        write(publishFile, ListSerializer(PublishRecord.serializer()), records)

    private fun <T> read(file: File, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        if (!file.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(serializer), file.readText(Charsets.UTF_8))
        }.getOrElse {
            runCatching { file.renameTo(File(dir, "${file.name}.corrupt-${now()}")) }
            emptyList()
        }
    }

    private fun <T> write(file: File, serializer: kotlinx.serialization.KSerializer<List<T>>, values: List<T>) {
        dir.mkdirs()
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(serializer, values), Charsets.UTF_8)
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun localTime(at: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).withNano(0).toString()

    companion object {
        /** UI、主任务和子任务各会 new 一个 Store；按进程串行化读改写，避免互相覆盖。 */
        private val LOCK = Any()

        val STATUS_LABELS = mapOf(
            PlanStatus.IDEA to "想法",
            PlanStatus.DRAFTING to "制作中",
            PlanStatus.READY to "待发布",
            PlanStatus.SCHEDULED to "已排期",
            PlanStatus.PUBLISHED to "已发布",
            PlanStatus.ARCHIVED to "已归档",
        )
    }
}
