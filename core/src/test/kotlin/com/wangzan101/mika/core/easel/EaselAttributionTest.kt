package com.wangzan101.mika.core.easel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EaselAttributionTest {
    private fun metric(
        id: String,
        at: String,
        views: Long? = null,
        likes: Long = 0,
        collects: Long = 0,
        comments: Long = 0,
        shares: Long = 0,
        publishId: String? = id,
        title: String = "同一条",
        platform: String = "xhs",
    ) = ContentMetric(id, publishId, title, platform, at, views, likes, collects, comments, shares, null, "", at.hashCode().toLong())

    @Test fun latestSnapshotWinsAndChangeUsesTheEarliest() {
        val summary = EaselAttribution.summarize(listOf(
            metric("a", "2026-11-01T10:00", views = 1000, likes = 10, publishId = "p"),
            metric("b", "2026-11-02T10:00", views = 2000, likes = 30, collects = 5, publishId = "p"),
        ), top = 5)
        assertEquals(2, summary.snapshotCount)
        assertEquals(1, summary.postCount)
        val post = summary.topPosts.single()
        assertEquals("b", post.latest.id)
        assertEquals(2000L, post.latest.views)
        assertEquals(40L, post.engagement)
        assertEquals(30L, post.engagementChange)
        assertEquals(0.02, post.engagementRate!!, 0.0001)
    }

    @Test fun noViewsMeansNoEngagementRateAndMissingPublishIdGroupsByTitle() {
        val summary = EaselAttribution.summarize(listOf(
            metric("a", "2026-11-01", publishId = null, title = "甲", likes = 1),
            metric("b", "2026-11-02", publishId = null, title = "乙", likes = 2),
        ))
        assertEquals(2, summary.postCount)
        assertNull(summary.topPosts.first().engagementRate)
        assertEquals("xhs", summary.platforms.single().platform)
        assertEquals(3L, summary.platforms.single().engagement)
    }
}
