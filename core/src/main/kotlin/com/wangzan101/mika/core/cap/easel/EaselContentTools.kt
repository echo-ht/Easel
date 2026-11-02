package com.wangzan101.mika.core.cap.easel

import com.wangzan101.mika.core.easel.ContentPlan
import com.wangzan101.mika.core.easel.ContentPlanPatch
import com.wangzan101.mika.core.easel.EaselContentPort
import com.wangzan101.mika.core.easel.NewContentPlan
import com.wangzan101.mika.core.easel.NewPublishRecord
import com.wangzan101.mika.core.easel.PlanStatus
import com.wangzan101.mika.core.easel.PublishRecord
import com.wangzan101.mika.core.easel.PublishStatus
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ArgError
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Concurrency
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.tool.fail
import com.wangzan101.mika.core.tool.int
import com.wangzan101.mika.core.tool.ok
import com.wangzan101.mika.core.tool.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime

private val DATE_OR_TIME = Regex("""\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2})?)?""")

private fun dateArg(a: Args, name: String): String? {
    val raw = a.str(name, min = 0, max = 32)?.trim().orEmpty()
    if (raw.isEmpty()) return null
    if (!DATE_OR_TIME.matches(raw)) throw ArgError(name, "must be YYYY-MM-DD or YYYY-MM-DDTHH:mm")
    val normalized = raw.replace(' ', 'T')
    runCatching {
        if (normalized.length == 10) LocalDate.parse(normalized) else LocalDateTime.parse(normalized)
    }.getOrElse { throw ArgError(name, "must be a real date or date-time") }
    return normalized
}

private fun planStatus(a: Args, name: String = "status"): PlanStatus? {
    val raw = a.str(name, min = 0, max = 16)?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return PlanStatus.fromWire(raw) ?: throw ArgError(name, "must be one of ${PlanStatus.names}")
}

private fun publishStatus(a: Args, name: String = "status"): PublishStatus? {
    val raw = a.str(name, min = 0, max = 16)?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return PublishStatus.fromWire(raw) ?: throw ArgError(name, "must be one of ${PublishStatus.names}")
}

private fun ContentPlan.json() = buildJsonObject {
    put("id", id)
    put("title", title)
    put("platform", platform)
    scheduledAt?.let { put("scheduledAt", it) }
    put("status", status.name.lowercase())
    put("notes", notes)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun PublishRecord.json() = buildJsonObject {
    put("id", id)
    put("title", title)
    put("platform", platform)
    put("status", status.name.lowercase())
    put("publishedAt", publishedAt)
    postUrl?.let { put("postUrl", it) }
    planId?.let { put("planId", it) }
    put("note", note)
    put("createdAt", createdAt)
}

internal interface EaselTool : AgentTool {
    override val capability: String get() = EASEL
    override fun available(ctx: ToolContext) = ctx.easelContent != null
}

internal object ContentPlanAdd : EaselTool {
    override val verb = Verb.ContentPlanAdd
    override val risk = Risk.Write
    override val offScreen = true
    override val def = ToolDef(
        "content_plan_add",
        Prompts.tool("content_plan_add"),
        mapOf(
            "title" to str("What the piece is about, up to 200 characters."),
            "platform" to str("Target platform or channel, e.g. xiaohongshu, douyin, wechat. Up to 40 characters."),
            "scheduledAt" to str("Optional publish slot: YYYY-MM-DD or YYYY-MM-DDTHH:mm in local time."),
            "status" to str("Optional starting stage: ${PlanStatus.names}. Default idea."),
            "notes" to str("Optional angle, outline, references or constraints, up to 2000 characters."),
        ),
        listOf("title"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = ctx.easelContent!!
        val plan = port.addPlan(
            NewContentPlan(
                title = a.str("title", max = 200)!!.trim(),
                platform = a.str("platform", max = 40)?.trim().orEmpty(),
                scheduledAt = dateArg(a, "scheduledAt"),
                status = planStatus(a) ?: PlanStatus.IDEA,
                notes = a.str("notes", min = 0, max = 2000)?.trim().orEmpty(),
            ),
        )
        return ok { put("saved", true); put("plan", plan.json()) }
    }
}

internal object ContentPlanList : EaselTool {
    override val verb = Verb.ContentPlanList
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef(
        "content_plan_list",
        Prompts.tool("content_plan_list"),
        mapOf(
            "from" to str("Optional first date, YYYY-MM-DD, inclusive."),
            "to" to str("Optional last date, YYYY-MM-DD, inclusive."),
            "status" to str("Optional stage filter: ${PlanStatus.names}."),
            "limit" to int("Maximum entries to return, 1-200. Default 50.", 1, 200),
        ),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val from = dateArg(a, "from")?.take(10)
        val to = dateArg(a, "to")?.take(10)
        val status = planStatus(a)
        val limit = a.int("limit", 1, 200) ?: 50
        val plans = ctx.easelContent!!.plans()
            .filter { status == null || it.status == status }
            .filter { p ->
                val day = p.scheduledAt?.take(10)
                when {
                    from == null && to == null -> true
                    day == null -> false
                    from != null && day < from -> false
                    to != null && day > to -> false
                    else -> true
                }
            }
            .sortedWith(compareBy<ContentPlan>({ it.scheduledAt == null }, { it.scheduledAt }, { it.createdAt }))
            .take(limit)
        return ok { put("count", plans.size); put("plans", JsonArray(plans.map { it.json() })) }
    }
}

internal object ContentPlanUpdate : EaselTool {
    override val verb = Verb.ContentPlanUpdate
    override val risk = Risk.Write
    override val offScreen = true
    override val def = ToolDef(
        "content_plan_update",
        Prompts.tool("content_plan_update"),
        mapOf(
            "id" to str("Plan id returned by content_plan_add or content_plan_list."),
            "title" to str("New title, up to 200 characters. Omit to keep."),
            "platform" to str("New platform, up to 40 characters. Omit to keep."),
            "scheduledAt" to str("New slot, YYYY-MM-DD or YYYY-MM-DDTHH:mm. Omit to keep."),
            "status" to str("New stage: ${PlanStatus.names}. Omit to keep."),
            "notes" to str("New notes, up to 2000 characters; empty clears. Omit to keep."),
        ),
        listOf("id"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val id = a.str("id", max = 80)!!.trim()
        val patch = ContentPlanPatch(
            title = a.str("title", max = 200)?.trim(),
            platform = a.str("platform", max = 40)?.trim(),
            scheduledAt = dateArg(a, "scheduledAt"),
            status = planStatus(a),
            notes = a.str("notes", min = 0, max = 2000)?.trim(),
        )
        if (listOf(patch.title, patch.platform, patch.scheduledAt, patch.status, patch.notes).all { it == null })
            return fail("nothing_to_update", "Give at least one field to change.", recoverable = false)
        val plan = ctx.easelContent!!.updatePlan(id, patch)
            ?: return fail("plan_not_found", "No content plan has id $id.", recoverable = false)
        return ok { put("saved", true); put("plan", plan.json()) }
    }
}

internal object ContentPublishLogAdd : EaselTool {
    override val verb = Verb.ContentPublishLogAdd
    override val risk = Risk.Write
    override val offScreen = true
    override val def = ToolDef(
        "content_publish_log_add",
        Prompts.tool("content_publish_log_add"),
        mapOf(
            "title" to str("Published title or a short description, up to 200 characters."),
            "platform" to str("Platform or channel, up to 40 characters."),
            "status" to str("published = confirmed published, failed = attempted but not published. Default published."),
            "publishedAt" to str("Optional publish time, YYYY-MM-DD or YYYY-MM-DDTHH:mm. Default now."),
            "postUrl" to str("Optional link to the published post, up to 1000 characters."),
            "planId" to str("Optional content plan id. A confirmed publish marks that plan published."),
            "note" to str("Optional result, performance note or failure reason, up to 2000 characters."),
        ),
        listOf("title", "platform"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = ctx.easelContent!!
        val status = publishStatus(a) ?: PublishStatus.PUBLISHED
        val planId = a.str("planId", min = 0, max = 80)?.trim()?.ifBlank { null }
        if (planId != null && port.plans().none { it.id == planId })
            return fail("plan_not_found", "No content plan has id $planId.", recoverable = false)
        val record = port.addPublish(
            NewPublishRecord(
                title = a.str("title", max = 200)!!.trim(),
                platform = a.str("platform", max = 40)!!.trim(),
                status = status,
                publishedAt = dateArg(a, "publishedAt"),
                postUrl = a.str("postUrl", min = 0, max = 1000)?.trim()?.ifBlank { null },
                planId = planId,
                note = a.str("note", min = 0, max = 2000)?.trim().orEmpty(),
            ),
        )
        val plan = if (planId != null && status == PublishStatus.PUBLISHED) {
            port.updatePlan(planId, ContentPlanPatch(status = PlanStatus.PUBLISHED))
        } else null
        return ok {
            put("saved", true)
            put("record", record.json())
            plan?.let { put("plan", it.json()) }
        }
    }
}

internal object ContentPublishLogList : EaselTool {
    override val verb = Verb.ContentPublishLogList
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef(
        "content_publish_log_list",
        Prompts.tool("content_publish_log_list"),
        mapOf("limit" to int("Maximum records to return, 1-200. Default 50.", 1, 200)),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val limit = a.int("limit", 1, 200) ?: 50
        val records = ctx.easelContent!!.publishLog(limit)
        return ok { put("count", records.size); put("records", JsonArray(records.map { it.json() })) }
    }
}

internal object EaselContentGroup {
    val tools: List<AgentTool> = listOf(ContentPlanAdd, ContentPlanList, ContentPlanUpdate, ContentPublishLogAdd, ContentPublishLogList)
}

internal val ToolContext.easelContent: EaselContentPort?
    get() = this[EaselCapability.KEY]
