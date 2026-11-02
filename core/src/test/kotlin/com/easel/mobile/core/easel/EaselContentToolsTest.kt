package com.easel.mobile.core.easel

import com.easel.mobile.core.assembly.toolDispatcher
import com.easel.mobile.core.model.Part
import com.easel.mobile.core.tool.FakeScreen
import com.easel.mobile.core.tool.execute
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EaselContentToolsTest {
    private class MemoryPort : EaselContentPort {
        val plans = mutableListOf<ContentPlan>()
        val records = mutableListOf<PublishRecord>()
        val metrics = mutableListOf<ContentMetric>()
        private var at = 1_000L

        override suspend fun plans() = plans.toList()

        override suspend fun addPlan(input: NewContentPlan): ContentPlan =
            ContentPlan("p${plans.size + 1}", input.title, input.platform, input.scheduledAt, input.status, input.notes, at, at++)
                .also { plans += it }

        override suspend fun updatePlan(id: String, patch: ContentPlanPatch): ContentPlan? {
            val i = plans.indexOfFirst { it.id == id }
            if (i < 0) return null
            val old = plans[i]
            return old.copy(
                title = patch.title ?: old.title, platform = patch.platform ?: old.platform,
                scheduledAt = if (patch.clearScheduledAt) null else patch.scheduledAt ?: old.scheduledAt, status = patch.status ?: old.status,
                notes = patch.notes ?: old.notes, updatedAt = at++,
            ).also { plans[i] = it }
        }

        override suspend fun deletePlan(id: String) = plans.removeAll { it.id == id }

        override suspend fun publishLog(limit: Int) = records.sortedByDescending { it.publishedAt }.take(limit)

        override suspend fun addPublish(input: NewPublishRecord): PublishRecord =
            PublishRecord("r${records.size + 1}", input.title, input.platform, input.status,
                input.publishedAt ?: "2026-11-02T12:00", input.postUrl, input.planId, input.note, at++)
                .also { records += it }

        override suspend fun deletePublish(id: String) = records.removeAll { it.id == id }

        override suspend fun metrics(limit: Int) = metrics.sortedByDescending { it.capturedAt }.take(limit)

        override suspend fun addMetric(input: NewContentMetric): ContentMetric =
            ContentMetric("m${metrics.size + 1}", input.publishId, input.title, input.platform,
                input.capturedAt ?: "2026-11-02T12:00", input.views, input.likes, input.collects,
                input.comments, input.shares, input.followers, input.note, at++)
                .also { metrics += it }

        override suspend fun deleteMetric(id: String) = metrics.removeAll { it.id == id }
    }

    private suspend fun call(name: String, body: String, port: EaselContentPort? = MemoryPort()) =
        toolDispatcher(FakeScreen(), easelContent = port).let { d ->
            val call = Part.ToolCall("c", name, body)
            d to if (name in WRITE_TOOLS) d.execute(call) else d.executeRead(call)
        }

    @Test fun toolsOnlyExistWhenTheLocalStoreIsConnected() {
        val names = toolDispatcher(FakeScreen()).definitions().map { it.name }
        assertFalse(names.any { it.startsWith("content_plan_") || it.startsWith("content_publish_log_") })
        val withStore = toolDispatcher(FakeScreen(), easelContent = MemoryPort()).definitions().map { it.name }
        assertEquals(setOf("content_plan_add", "content_plan_list", "content_plan_update", "content_publish_log_add", "content_publish_log_list",
            "content_metrics_add", "content_metrics_list", "content_metrics_delete", "content_attribution_summary", "content_risk_scan"),
            withStore.filter { it.startsWith("content_") }.toSet())
    }

    @Test fun localWritesAreNotAdvertisedAsReads() = runTest {
        val d = toolDispatcher(FakeScreen(), easelContent = MemoryPort())
        for (name in WRITE_TOOLS) {
            val result = d.executeRead(Part.ToolCall("c", name, "{}"))
            assertEquals("not_a_read", result.content["error"]?.jsonPrimitive?.content)
        }
    }

    @Test fun capabilityPromptAndDefinitionsFollowThePort() = runTest {
        val d = toolDispatcher(FakeScreen(), easelContent = MemoryPort())
        val names = d.definitions().map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("content_plan_add", "content_plan_list", "content_plan_update", "content_publish_log_add", "content_publish_log_list",
            "content_metrics_add", "content_metrics_list", "content_metrics_delete", "content_attribution_summary", "content_risk_scan")))
        val prompt = d.promptSections(d.definitions()).joinToString("\n")
        assertTrue(prompt.contains("Content calendar and publishing log"))
        assertTrue(prompt.contains("content_plan_add"))
    }

    @Test fun addListAndUpdateAPlan() = runTest {
        val port = MemoryPort()
        val (_, add) = call("content_plan_add",
            """{"title":"周末选题","platform":"xiaohongshu","scheduledAt":"2026-11-05T20:00","status":"drafting","notes":"先写提纲"}""", port)
        assertEquals(true, add.content["ok"]?.jsonPrimitive?.content?.toBoolean())
        val id = add.content["plan"]!!.jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(PlanStatus.DRAFTING, port.plans.single().status)

        val (_, list) = call("content_plan_list", """{"from":"2026-11-01","to":"2026-11-30","status":"drafting"}""", port)
        assertEquals(1, list.content["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(id, list.content["plans"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)

        val (_, update) = call("content_plan_update", """{"id":"$id","status":"ready","notes":""}""", port)
        assertEquals("ready", update.content["plan"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals(PlanStatus.READY, port.plans.single().status)

        val (_, cleared) = call("content_plan_update", """{"id":"$id","clearScheduledAt":true}""", port)
        assertTrue(cleared.content["ok"]!!.jsonPrimitive.content.toBoolean())
        assertNull(port.plans.single().scheduledAt)
    }

    @Test fun publishLogMarksItsPlanPublished() = runTest {
        val port = MemoryPort()
        port.addPlan(NewContentPlan("一条笔记", "xiaohongshu", "2026-11-05"))
        val id = port.plans.single().id
        val (_, result) = call("content_publish_log_add",
            """{"title":"一条笔记","platform":"xiaohongshu","planId":"$id","postUrl":"https://example.com/p/1","note":"已在 App 看到"}""", port)
        assertTrue(result.content["ok"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(PlanStatus.PUBLISHED, port.plans.single().status)
        assertEquals(1, port.records.size)

        val (_, list) = call("content_publish_log_list", "{}", port)
        assertEquals(1, list.content["records"]!!.jsonArray.size)
        assertEquals("https://example.com/p/1", list.content["records"]!!.jsonArray.single().jsonObject["postUrl"]!!.jsonPrimitive.content)
    }

    @Test fun rejectsBadDatesAndMissingPlanIds() = runTest {
        val (_, badDate) = call("content_plan_add", """{"title":"x","scheduledAt":"tomorrow"}""")
        assertEquals("invalid_arguments", badDate.content["error"]?.jsonPrimitive?.content)
        val (_, impossibleDate) = call("content_plan_add", """{"title":"x","scheduledAt":"2026-02-31"}""")
        assertEquals("invalid_arguments", impossibleDate.content["error"]?.jsonPrimitive?.content)
        val (_, missing) = call("content_plan_update", """{"id":"nope","status":"ready"}""")
        assertEquals("plan_not_found", missing.content["error"]?.jsonPrimitive?.content)
        assertNotNull(missing.content["message"])
    }

    @Test fun metricSnapshotsFillFromThePublishRecordAndSummarize() = runTest {
        val port = MemoryPort()
        port.addPublish(NewPublishRecord("一条爆款", "xiaohongshu", publishedAt = "2026-11-01T10:00"))
        val publishId = port.records.single().id

        val (_, added) = call("content_metrics_add",
            """{"publishId":"$publishId","views":1000,"likes":80,"collects":30,"comments":10,"shares":5,"note":"后台显示"}""", port)
        assertTrue(added.content["ok"]!!.jsonPrimitive.content.toBoolean())
        val metric = added.content["metric"]!!.jsonObject
        assertEquals("一条爆款", metric["title"]!!.jsonPrimitive.content)
        assertEquals("xiaohongshu", metric["platform"]!!.jsonPrimitive.content)
        assertEquals(80L, metric["likes"]!!.jsonPrimitive.content.toLong())

        val (_, second) = call("content_metrics_add",
            """{"publishId":"$publishId","views":2000,"likes":120,"collects":50,"comments":20,"shares":10}""", port)
        val secondId = second.content["metric"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val (_, list) = call("content_metrics_list", """{"publishId":"$publishId"}""", port)
        assertEquals(2, list.content["count"]!!.jsonPrimitive.content.toInt())

        val (_, summary) = call("content_attribution_summary", """{"top":1}""", port)
        assertEquals(2, summary.content["snapshotCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, summary.content["postCount"]!!.jsonPrimitive.content.toInt())
        val post = summary.content["topPosts"]!!.jsonArray.single().jsonObject
        assertEquals(120L + 50L * 2 + 20L * 2 + 10L * 3, post["engagement"]!!.jsonPrimitive.content.toLong())
        assertEquals(115L, post["engagementChange"]!!.jsonPrimitive.content.toLong())

        val (_, deleted) = call("content_metrics_delete", """{"id":"$secondId"}""", port)
        assertTrue(deleted.content["ok"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1, port.metrics.size)
    }

    @Test fun metricSnapshotNeedsANumberAndAKnownPublishRecord() = runTest {
        val (_, empty) = call("content_metrics_add", """{"title":"只有标题","platform":"xhs"}""")
        assertEquals("no_metric", empty.content["error"]?.jsonPrimitive?.content)
        val (_, missing) = call("content_metrics_add", """{"publishId":"nope","views":1}""")
        assertEquals("publish_not_found", missing.content["error"]?.jsonPrimitive?.content)
        val (_, negative) = call("content_metrics_add", """{"title":"x","platform":"xhs","views":-1}""")
        assertEquals("invalid_arguments", negative.content["error"]?.jsonPrimitive?.content)
        val port = MemoryPort()
        port.addPublish(NewPublishRecord("失败", "xhs", PublishStatus.FAILED))
        val (_, failed) = call("content_metrics_add", """{"publishId":"${port.records.single().id}","views":1}""", port)
        assertEquals("publish_not_published", failed.content["error"]?.jsonPrimitive?.content)
    }

    @Test fun riskScanReportsHighAndMediumFindingsWithMaskedEvidence() = runTest {
        val (_, result) = call("content_risk_scan",
            """{"platform":"xiaohongshu","text":"加我 13800138000，链接 http://192.168.1.8:8080，效果百分百有效。"}""")
        assertEquals("high", result.content["level"]!!.jsonPrimitive.content)
        assertEquals(1, result.content["blockCount"]!!.jsonPrimitive.content.toInt())
        assertTrue(result.content["warnCount"]!!.jsonPrimitive.content.toInt() >= 2)
        val encoded = result.content.toString()
        assertFalse(encoded.contains("13800138000"))
        assertFalse(encoded.contains("192.168.1.8"))
    }

    @Test fun dateNormalizationAcceptsOnlyRealLocalDates() {
        assertNull(EaselContentDate.normalize("  "))
        assertEquals("2026-11-05", EaselContentDate.normalize("2026-11-05"))
        assertEquals("2026-11-05T20:00", EaselContentDate.normalize("2026-11-05 20:00"))
        assertEquals("2026-11-05T20:00:30", EaselContentDate.normalize("2026-11-05T20:00:30"))
        for (bad in listOf("tomorrow", "2026-02-31", "2026-13-01", "2026-11-05T25:00")) {
            assertTrue(bad, runCatching { EaselContentDate.normalize(bad) }.isFailure)
        }
    }

    private companion object {
        val WRITE_TOOLS = setOf("content_plan_add", "content_plan_update", "content_publish_log_add", "content_metrics_add", "content_metrics_delete")
    }
}
