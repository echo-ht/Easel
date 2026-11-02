package com.easel.mobile.core.cap.easel

import com.easel.mobile.core.easel.ContentMetric
import com.easel.mobile.core.easel.ContentPlan
import com.easel.mobile.core.easel.ContentPlanPatch
import com.easel.mobile.core.easel.EaselAttribution
import com.easel.mobile.core.easel.EaselContentDate
import com.easel.mobile.core.easel.EaselContentPort
import com.easel.mobile.core.easel.EaselRiskScanner
import com.easel.mobile.core.easel.NewContentMetric
import com.easel.mobile.core.easel.NewContentPlan
import com.easel.mobile.core.easel.NewPublishRecord
import com.easel.mobile.core.easel.PlanStatus
import com.easel.mobile.core.easel.PublishRecord
import com.easel.mobile.core.easel.PublishStatus
import com.easel.mobile.core.prompt.Prompts
import com.easel.mobile.core.tool.AgentTool
import com.easel.mobile.core.tool.ArgError
import com.easel.mobile.core.tool.Args
import com.easel.mobile.core.tool.Concurrency
import com.easel.mobile.core.tool.Risk
import com.easel.mobile.core.tool.ToolContext
import com.easel.mobile.core.tool.ToolDef
import com.easel.mobile.core.tool.ToolExecution
import com.easel.mobile.core.tool.Verb
import com.easel.mobile.core.tool.bool
import com.easel.mobile.core.tool.fail
import com.easel.mobile.core.tool.int
import com.easel.mobile.core.tool.num
import com.easel.mobile.core.tool.ok
import com.easel.mobile.core.tool.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun dateArg(a: Args, name: String): String? {
    val raw = a.str(name, min = 0, max = 32)?.trim().orEmpty()
    return try {
        EaselContentDate.normalize(raw)
    } catch (e: IllegalArgumentException) {
        throw ArgError(name, e.message ?: EaselContentDate.FORMAT_HINT)
    }
}

private fun metricValue(a: Args, name: String, nonNegative: Boolean = true): Long? {
    return a.long(name, min = if (nonNegative) 0 else Long.MIN_VALUE)
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

private fun ContentMetric.json() = buildJsonObject {
    put("id", id)
    publishId?.let { put("publishId", it) }
    put("title", title)
    put("platform", platform)
    put("capturedAt", capturedAt)
    views?.let { put("views", it) }
    likes?.let { put("likes", it) }
    collects?.let { put("collects", it) }
    comments?.let { put("comments", it) }
    shares?.let { put("shares", it) }
    followers?.let { put("followers", it) }
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
            "clearScheduledAt" to bool("Set true to remove the current slot. Omit or false to keep it."),
            "status" to str("New stage: ${PlanStatus.names}. Omit to keep."),
            "notes" to str("New notes, up to 2000 characters; empty clears. Omit to keep."),
        ),
        listOf("id"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val id = a.str("id", max = 80)!!.trim()
        val scheduledAt = dateArg(a, "scheduledAt")
        val clearScheduledAt = a.bool("clearScheduledAt", false)
        if (clearScheduledAt && scheduledAt != null)
            throw ArgError("clearScheduledAt", "omit scheduledAt when clearing the slot")
        val patch = ContentPlanPatch(
            title = a.str("title", max = 200)?.trim(),
            platform = a.str("platform", max = 40)?.trim(),
            scheduledAt = scheduledAt,
            status = planStatus(a),
            notes = a.str("notes", min = 0, max = 2000)?.trim(),
            clearScheduledAt = clearScheduledAt,
        )
        if (listOf(patch.title, patch.platform, patch.scheduledAt, patch.status, patch.notes).all { it == null } && !clearScheduledAt)
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

internal object ContentMetricsAdd : EaselTool {
    override val verb = Verb.ContentMetricsAdd
    override val risk = Risk.Write
    override val offScreen = true
    override val def = ToolDef(
        "content_metrics_add",
        Prompts.tool("content_metrics_add"),
        mapOf(
            "publishId" to str("Optional id from content_publish_log_list. When given, title and platform are filled from that record."),
            "title" to str("Content title, up to 200 characters. Required when publishId is omitted."),
            "platform" to str("Platform or channel, up to 40 characters. Required when publishId is omitted."),
            "capturedAt" to str("Optional capture time, YYYY-MM-DD or YYYY-MM-DDTHH:mm in local time. Default now."),
            "views" to num("Optional view/read count reported by the platform. Non-negative integer.", 0),
            "likes" to num("Optional like count. Non-negative integer.", 0),
            "collects" to num("Optional collect/favorite count. Non-negative integer.", 0),
            "comments" to num("Optional comment count. Non-negative integer.", 0),
            "shares" to num("Optional share/repost count. Non-negative integer.", 0),
            "followers" to num("Optional follower count or net change reported for this post. Integer."),
            "note" to str("Optional context, up to 2000 characters. Keep ranges and platform wording as observed."),
        ),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = ctx.easelContent!!
        val publishId = a.str("publishId", min = 0, max = 80)?.trim()?.ifBlank { null }
        val record = publishId?.let { id -> port.publishLog(Int.MAX_VALUE).firstOrNull { it.id == id } }
        if (publishId != null && record == null)
            return fail("publish_not_found", "No publish record has id $publishId.", recoverable = false)
        if (record != null && record.status != PublishStatus.PUBLISHED)
            return fail("publish_not_published", "Publish record $publishId is marked failed; metrics need a confirmed publish.", recoverable = false)
        val title = a.str("title", min = 0, max = 200)?.trim().orEmpty().ifBlank { record?.title.orEmpty() }
        val platform = a.str("platform", min = 0, max = 40)?.trim().orEmpty().ifBlank { record?.platform.orEmpty() }
        if (title.isBlank()) throw ArgError("title", "required when publishId is omitted")
        if (platform.isBlank()) throw ArgError("platform", "required when publishId is omitted")
        val views = metricValue(a, "views")
        val likes = metricValue(a, "likes")
        val collects = metricValue(a, "collects")
        val comments = metricValue(a, "comments")
        val shares = metricValue(a, "shares")
        val followers = metricValue(a, "followers", nonNegative = false)
        if (listOf(views, likes, collects, comments, shares, followers).all { it == null })
            return fail("no_metric", "Give at least one of views, likes, collects, comments, shares or followers.", recoverable = false)
        val metric = port.addMetric(
            NewContentMetric(
                publishId = publishId,
                title = title,
                platform = platform,
                capturedAt = dateArg(a, "capturedAt"),
                views = views,
                likes = likes,
                collects = collects,
                comments = comments,
                shares = shares,
                followers = followers,
                note = a.str("note", min = 0, max = 2000)?.trim().orEmpty(),
            ),
        )
        return ok { put("saved", true); put("metric", metric.json()) }
    }
}

internal object ContentMetricsList : EaselTool {
    override val verb = Verb.ContentMetricsList
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef(
        "content_metrics_list",
        Prompts.tool("content_metrics_list"),
        mapOf(
            "publishId" to str("Optional publish record id. Return only snapshots attributed to that post."),
            "platform" to str("Optional platform filter, case-insensitive."),
            "limit" to int("Maximum snapshots to return, 1-500. Default 100.", 1, 500),
        ),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val publishId = a.str("publishId", min = 0, max = 80)?.trim()?.ifBlank { null }
        val platform = a.str("platform", min = 0, max = 40)?.trim()?.ifBlank { null }
        val limit = a.int("limit", 1, 500) ?: 100
        val metrics = ctx.easelContent!!.metrics(limit = 500)
            .filter { publishId == null || it.publishId == publishId }
            .filter { platform == null || it.platform.equals(platform, ignoreCase = true) }
            .take(limit)
        return ok { put("count", metrics.size); put("metrics", JsonArray(metrics.map { it.json() })) }
    }
}

internal object ContentMetricsDelete : EaselTool {
    override val verb = Verb.ContentMetricsDelete
    override val risk = Risk.Write
    override val offScreen = true
    override val def = ToolDef(
        "content_metrics_delete",
        Prompts.tool("content_metrics_delete"),
        mapOf("id" to str("Snapshot id from content_metrics_add or content_metrics_list.")),
        listOf("id"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val id = a.str("id", max = 80)!!.trim()
        if (!ctx.easelContent!!.deleteMetric(id))
            return fail("metric_not_found", "No metric snapshot has id $id.", recoverable = false)
        return ok { put("deleted", true); put("id", id) }
    }
}

internal object ContentAttributionSummary : EaselTool {
    override val verb = Verb.ContentAttributionSummary
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef(
        "content_attribution_summary",
        Prompts.tool("content_attribution_summary"),
        mapOf(
            "limit" to int("Maximum metric snapshots to aggregate, 1-500. Default 200.", 1, 500),
            "top" to int("Number of top posts to include, 1-50. Default 5.", 1, 50),
        ),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val limit = a.int("limit", 1, 500) ?: 200
        val top = a.int("top", 1, 50) ?: 5
        val summary = EaselAttribution.summarize(ctx.easelContent!!.metrics(limit), top)
        return ok {
            put("snapshotCount", summary.snapshotCount)
            put("postCount", summary.postCount)
            put("platforms", JsonArray(summary.platforms.map { platform ->
                buildJsonObject {
                    put("platform", platform.platform)
                    put("posts", platform.posts)
                    put("views", platform.views)
                    put("engagement", platform.engagement)
                    platform.engagementRate?.let { put("engagementRate", it) }
                }
            }))
            put("topPosts", JsonArray(summary.topPosts.map { post ->
                buildJsonObject {
                    post.publishId?.let { put("publishId", it) }
                    put("title", post.title)
                    put("platform", post.platform)
                    put("snapshotCount", post.snapshotCount)
                    put("engagement", post.engagement)
                    post.engagementRate?.let { put("engagementRate", it) }
                    put("engagementChange", post.engagementChange)
                    put("latest", post.latest.json())
                }
            }))
        }
    }
}

internal object ContentRiskScan : EaselTool {
    override val verb = Verb.ContentRiskScan
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef(
        "content_risk_scan",
        Prompts.tool("content_risk_scan"),
        mapOf(
            "text" to str("Text to check before publishing: title, body, caption or script, up to 20000 characters."),
            "platform" to str("Optional target platform, up to 40 characters. Used only as context in the report."),
        ),
        listOf("text"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val report = EaselRiskScanner.scan(
            a.str("text", max = 20_000)!!,
            a.str("platform", min = 0, max = 40)?.trim().orEmpty(),
        )
        return ok {
            put("level", report.level.name.lowercase())
            put("platform", report.platform)
            put("blockCount", report.blockCount)
            put("warnCount", report.warnCount)
            put("findings", JsonArray(report.findings.map { finding ->
                buildJsonObject {
                    put("level", finding.level.name.lowercase())
                    put("code", finding.code)
                    put("evidence", finding.evidence)
                    put("suggestion", finding.suggestion)
                }
            }))
        }
    }
}

internal object EaselContentGroup {
    val tools: List<AgentTool> = listOf(
        ContentPlanAdd, ContentPlanList, ContentPlanUpdate, ContentPublishLogAdd, ContentPublishLogList,
        ContentMetricsAdd, ContentMetricsList, ContentMetricsDelete, ContentAttributionSummary, ContentRiskScan,
    )
}

internal val ToolContext.easelContent: EaselContentPort?
    get() = this[EaselCapability.KEY]
