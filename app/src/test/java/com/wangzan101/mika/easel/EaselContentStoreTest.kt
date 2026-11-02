package com.wangzan101.mika.easel

import com.wangzan101.mika.core.easel.ContentPlanPatch
import com.wangzan101.mika.core.easel.NewContentPlan
import com.wangzan101.mika.core.easel.NewPublishRecord
import com.wangzan101.mika.core.easel.PlanStatus
import com.wangzan101.mika.core.easel.PublishStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EaselContentStoreTest {
    private fun tempDir(): File = Files.createTempDirectory("easel-content").toFile()

    @Test fun plansSurviveReopenAndUpdateKeepsCreatedAt() = runTest {
        val dir = tempDir()
        var clock = 1_000L
        var n = 0
        val store = EaselContentStore(dir, now = { clock++ }, newId = { "p${++n}" })
        val plan = store.addPlan(NewContentPlan("选题", "xiaohongshu", "2026-11-05", PlanStatus.DRAFTING, "提纲"))
        assertEquals("p1", plan.id)

        val reopened = EaselContentStore(dir, now = { clock++ }, newId = { "p${++n}" })
        val loaded = reopened.plans().single()
        assertEquals(plan, loaded)

        val updated = reopened.updatePlan(plan.id, ContentPlanPatch(status = PlanStatus.READY, notes = ""))!!
        assertEquals(plan.createdAt, updated.createdAt)
        assertEquals(PlanStatus.READY, updated.status)
        assertEquals("", updated.notes)
        assertEquals(updated, EaselContentStore(dir).plans().single())
    }

    @Test fun publishLogIsNewestFirstAndDeleteWorks() = runTest {
        val dir = tempDir()
        val store = EaselContentStore(dir, now = { 2_000L }, newId = sequenceOf("r1", "r2").iterator().let { seq -> { seq.next() } })
        store.addPublish(NewPublishRecord("旧", "xhs", publishedAt = "2026-11-01T10:00"))
        store.addPublish(NewPublishRecord("新", "xhs", PublishStatus.FAILED, "2026-11-02T10:00", note = "风控"))
        val records = store.publishLog(10)
        assertEquals(listOf("新", "旧"), records.map { it.title })
        assertTrue(store.deletePublish(records.first().id))
        assertFalse(store.deletePublish("missing"))
        assertEquals(listOf("旧"), store.publishLog(10).map { it.title })
    }

    @Test fun corruptFileIsSetAsideInsteadOfBeingOverwritten() = runTest {
        val dir = tempDir().also { it.mkdirs() }
        File(dir, "calendar.json").writeText("not json")
        val store = EaselContentStore(dir, now = { 9_999L })
        assertTrue(store.plans().isEmpty())
        assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("calendar.json.corrupt-") })
        assertNull(store.updatePlan("missing", ContentPlanPatch(status = PlanStatus.READY)))
    }
}
