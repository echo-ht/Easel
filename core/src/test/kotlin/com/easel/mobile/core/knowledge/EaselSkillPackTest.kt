package com.easel.mobile.core.knowledge

import org.junit.Assert.assertTrue
import org.junit.Test

/** P2.1 的技能包门槛：首批补齐到 60 个技能，发布、数据、风控和制作链都有人在。 */
class EaselSkillPackTest {
    @Test fun p2FirstBatchIsComplete() {
        val skills = GuideCatalog.loadBundled().items.filter { it.kind == KnowledgeIndex.SKILL }
        assertTrue("expected 60 bundled skills, found ${skills.size}", skills.size == 60)
        val names = skills.map { it.name }.toSet()
        val required = setOf(
            "content-workflow", "xhs-note", "xhs-publish", "xhs-analysis",
            "copywriting", "text-polisher", "hook-title", "card-design",
            "publish-checklist", "publish-scheduler", "cross-platform-publish",
            "douyin-publish", "kuaishou-publish", "bilibili-publish", "zhihu-publish", "wechat-publish",
            "data-tracker", "postmortem", "video-script", "auto-subtitle", "video-reframe",
            "audio-editing", "image-editing", "asset-manager",
            "account-diagnosis", "article-outline", "batch-process", "comment-insights",
            "content-matrix", "cross-platform-diff", "event-calendar", "profile-builder",
            "profile-manager", "publish-analytics", "quality-gate", "risk-scanner",
            "social-performance-review", "template-library", "topic-evaluator",
            "video-chapters", "video-highlights", "video-to-article", "subtitle-translate",
            "audio-mix", "slideshow-video",
        )
        val missing = required - names
        assertTrue("missing P1 skills: $missing", missing.isEmpty())
    }
}
