package com.wangzan101.mika.easel

import com.wangzan101.mika.core.easel.ContentPlanPatch
import com.wangzan101.mika.core.easel.NewContentPlan
import com.wangzan101.mika.core.easel.NewPublishRecord
import com.wangzan101.mika.core.easel.PlanStatus
import com.wangzan101.mika.core.easel.PublishStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

        val cleared = reopened.updatePlan(plan.id, ContentPlanPatch(clearScheduledAt = true))!!
        assertNull(cleared.scheduledAt)
        assertNull(EaselContentStore(dir).plans().single().scheduledAt)
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

    /** UI、主任务、子任务会各建一个 Store；两个实例同时读改写时不能互相覆盖。 */
    @Test fun separateStoresShareOneWriteLock() = runTest {
        val dir = tempDir()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = EaselContentStore(dir, now = { entered.countDown(); release.await(); 1_000L }, newId = { "p1" })
        val second = EaselContentStore(dir, now = { 2_000L }, newId = { "p2" })

        val a = async(Dispatchers.IO) { first.addPlan(NewContentPlan("第一条")) }
        assertTrue("first store entered its write", entered.await(2, TimeUnit.SECONDS))
        val b = async(Dispatchers.IO) { second.addPlan(NewContentPlan("第二条")) }
        try {
            val finishedEarly = withContext(Dispatchers.Default) { withTimeoutOrNull(300) { b.await() } }
            assertNull("second store must wait for the shared write lock", finishedEarly)
        } finally {
            release.countDown()
        }
        a.await()
        b.await()
        assertEquals(listOf("第一条", "第二条"), EaselContentStore(dir).plans().map { it.title }.sorted())
    }
}
