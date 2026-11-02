package com.wangzan101.mika.core.knowledge

import org.junit.Assert.assertTrue
import org.junit.Test

/** P1 的技能包门槛：30-40 个高频技能，且发布、制作、复盘三条链都有人在。 */
class EaselSkillPackTest {
    @Test fun p1SkillPackIsComplete() {
        val skills = GuideCatalog.loadBundled().items.filter { it.kind == KnowledgeIndex.SKILL }
        assertTrue("expected 30-40 bundled skills, found ${skills.size}", skills.size in 30..40)
        val names = skills.map { it.name }.toSet()
        val required = setOf(
            "content-workflow", "xhs-note", "xhs-publish", "xhs-analysis",
            "copywriting", "text-polisher", "hook-title", "card-design",
            "publish-checklist", "publish-scheduler", "cross-platform-publish",
            "douyin-publish", "kuaishou-publish", "bilibili-publish", "zhihu-publish", "wechat-publish",
            "data-tracker", "postmortem", "video-script", "auto-subtitle", "video-reframe",
            "audio-editing", "image-editing", "asset-manager",
        )
        val missing = required - names
        assertTrue("missing P1 skills: $missing", missing.isEmpty())
    }
}
