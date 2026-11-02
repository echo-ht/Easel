package com.wangzan101.mika.core.easel

import kotlinx.serialization.Serializable

/** 内容日历里一条计划所处的阶段。 */
@Serializable
enum class PlanStatus {
    IDEA, DRAFTING, READY, SCHEDULED, PUBLISHED, ARCHIVED;

    companion object {
        fun fromWire(value: String): PlanStatus? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        val names: String get() = entries.joinToString { it.name.lowercase() }
    }
}

/** 一次发布的结果。先在手机上发布并核实，再记进发布日志。 */
@Serializable
enum class PublishStatus {
    PUBLISHED, FAILED;

    companion object {
        fun fromWire(value: String): PublishStatus? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        val names: String get() = entries.joinToString { it.name.lowercase() }
    }
}

/**
 * 内容日历的一条计划。[scheduledAt] 是 ISO 日期或日期时间（YYYY-MM-DD 或 YYYY-MM-DDTHH:mm），
 * 没排期为空。时间由 App 本地时区解释，不引入服务器。
 */
@Serializable
data class ContentPlan(
    val id: String,
    val title: String,
    val platform: String = "",
    val scheduledAt: String? = null,
    val status: PlanStatus = PlanStatus.IDEA,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

data class NewContentPlan(
    val title: String,
    val platform: String = "",
    val scheduledAt: String? = null,
    val status: PlanStatus = PlanStatus.IDEA,
    val notes: String = "",
)

/** 只改给出的字段；空字符串用于清空可清空的文字字段。 */
data class ContentPlanPatch(
    val title: String? = null,
    val platform: String? = null,
    val scheduledAt: String? = null,
    val status: PlanStatus? = null,
    val notes: String? = null,
    /** `scheduledAt` 为 null 时是「不改」；要取消排期时置 true。 */
    val clearScheduledAt: Boolean = false,
)

/** 发布日志：发布或失败后留下的事实，可指回内容日历里的计划。 */
@Serializable
data class PublishRecord(
    val id: String,
    val title: String,
    val platform: String,
    val status: PublishStatus = PublishStatus.PUBLISHED,
    val publishedAt: String,
    val postUrl: String? = null,
    val planId: String? = null,
    val note: String = "",
    val createdAt: Long,
)

data class NewPublishRecord(
    val title: String,
    val platform: String,
    val status: PublishStatus = PublishStatus.PUBLISHED,
    val publishedAt: String? = null,
    val postUrl: String? = null,
    val planId: String? = null,
    val note: String = "",
)

/**
 * 内容日历与发布日志的本机存储。核心只依赖这个端口，Android 侧用 App 私有目录实现；
 * 数据不经过服务器，也不依赖沙箱是否安装。
 */
interface EaselContentPort {
    suspend fun plans(): List<ContentPlan>
    suspend fun addPlan(input: NewContentPlan): ContentPlan
    suspend fun updatePlan(id: String, patch: ContentPlanPatch): ContentPlan?
    suspend fun deletePlan(id: String): Boolean

    suspend fun publishLog(limit: Int = 50): List<PublishRecord>
    suspend fun addPublish(input: NewPublishRecord): PublishRecord
    suspend fun deletePublish(id: String): Boolean
}
