package com.wangzan101.mika.core.easel

import kotlin.math.max

/** 一条内容按最新快照算出的表现。 */
data class AttributedPost(
    val key: String,
    val publishId: String?,
    val title: String,
    val platform: String,
    val latest: ContentMetric,
    val snapshotCount: Int,
    val engagement: Long,
    val engagementRate: Double?,
    val engagementChange: Long,
)

/** 一个平台的汇总；触达和互动都按每条内容的最新快照计算。 */
data class AttributedPlatform(
    val platform: String,
    val posts: Int,
    val views: Long,
    val engagement: Long,
    val engagementRate: Double?,
)

data class AttributionSummary(
    val snapshotCount: Int,
    val postCount: Int,
    val platforms: List<AttributedPlatform>,
    val topPosts: List<AttributedPost>,
)

/**
 * 归因只做确定性聚合：同一发布事件以最新快照为准，变化量用最早快照对比；
 * 没有触达数据时不编互动率。LLM 负责解释，不在这里猜因果。
 */
object EaselAttribution {
    fun engagement(metric: ContentMetric): Long =
        (metric.likes ?: 0) + (metric.collects ?: 0) * 2 + (metric.comments ?: 0) * 2 + (metric.shares ?: 0) * 3

    fun summarize(metrics: List<ContentMetric>, top: Int = 5): AttributionSummary {
        val posts = metrics
            .groupBy { it.publishId ?: "${it.platform}\u0000${it.title}" }
            .map { (key, snapshots) ->
                val ordered = snapshots.sortedWith(compareBy<ContentMetric> { it.capturedAt }.thenBy { it.createdAt })
                val first = ordered.first()
                val latest = ordered.last()
                val engagement = engagement(latest)
                AttributedPost(
                    key = key,
                    publishId = latest.publishId,
                    title = latest.title,
                    platform = latest.platform,
                    latest = latest,
                    snapshotCount = ordered.size,
                    engagement = engagement,
                    engagementRate = latest.views?.takeIf { it > 0 }?.let { engagement.toDouble() / it },
                    engagementChange = engagement - engagement(first),
                )
            }
            .sortedWith(compareByDescending<AttributedPost> { it.engagementRate ?: -1.0 }.thenByDescending { it.engagement })

        val platforms = posts.groupBy { it.platform.ifBlank { "未指定平台" } }
            .map { (platform, items) ->
                val views = items.sumOf { it.latest.views ?: 0 }
                val engagement = items.sumOf { it.engagement }
                AttributedPlatform(
                    platform = platform,
                    posts = items.size,
                    views = views,
                    engagement = engagement,
                    engagementRate = if (views > 0) engagement.toDouble() / views else null,
                )
            }
            .sortedWith(compareByDescending<AttributedPlatform> { it.engagement }.thenByDescending { it.posts })

        return AttributionSummary(
            snapshotCount = metrics.size,
            postCount = posts.size,
            platforms = platforms,
            topPosts = posts.take(max(0, top)),
        )
    }
}
