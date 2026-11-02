package com.wangzan101.mika.core.easel

import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.tool.execute
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
        assertEquals(setOf("content_plan_add", "content_plan_list", "content_plan_update", "content_publish_log_add", "content_publish_log_list"),
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
        assertTrue(names.containsAll(setOf("content_plan_add", "content_plan_list", "content_plan_update", "content_publish_log_add", "content_publish_log_list")))
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
        val (_, missing) = call("content_plan_update", """{"id":"nope","status":"ready"}""")
        assertEquals("plan_not_found", missing.content["error"]?.jsonPrimitive?.content)
        assertNotNull(missing.content["message"])
    }

    private companion object {
        val WRITE_TOOLS = setOf("content_plan_add", "content_plan_update", "content_publish_log_add")
    }
}
