package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.easel.ContentPlan
import com.wangzan101.mika.core.easel.EaselContentDate
import com.wangzan101.mika.core.easel.NewContentPlan
import com.wangzan101.mika.core.easel.NewPublishRecord
import com.wangzan101.mika.core.easel.PlanStatus
import com.wangzan101.mika.core.easel.PublishRecord
import com.wangzan101.mika.core.easel.PublishStatus
import com.wangzan101.mika.easel.EaselContentStore
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.SelectChip
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.launch

/** 内容日历：手动记计划，也给 Agent 的 content_plan_* 工具同一份数据。 */
@Composable
fun EaselCalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EaselContentStore(context) }
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<ContentPlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        plans = store.plans().sortedWith(compareBy<ContentPlan>({ it.scheduledAt == null }, { it.scheduledAt }, { it.createdAt }))
        loading = false
    }

    fun add() {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val slot = try {
            EaselContentDate.normalize(scheduledAt)
        } catch (e: IllegalArgumentException) {
            error = e.message ?: EaselContentDate.FORMAT_HINT
            return
        }
        scope.launch {
            runCatching {
                store.addPlan(NewContentPlan(cleanTitle, platform.trim(), slot, PlanStatus.IDEA, notes.trim()))
            }.onSuccess {
                title = ""; platform = ""; scheduledAt = ""; notes = ""; error = null; refresh++
            }.onFailure { error = it.message ?: "没有保存成功" }
        }
    }

    fun advance(plan: ContentPlan) {
        val next = when (plan.status) {
            PlanStatus.IDEA -> PlanStatus.DRAFTING
            PlanStatus.DRAFTING -> PlanStatus.READY
            PlanStatus.READY -> PlanStatus.SCHEDULED
            PlanStatus.SCHEDULED -> PlanStatus.PUBLISHED
            PlanStatus.PUBLISHED -> PlanStatus.ARCHIVED
            PlanStatus.ARCHIVED -> PlanStatus.ARCHIVED
        }
        scope.launch {
            runCatching { store.updatePlan(plan.id, com.wangzan101.mika.core.easel.ContentPlanPatch(status = next)) }
                .onSuccess { refresh++ }
                .onFailure { error = it.message ?: "没有更新成功" }
        }
    }

    fun remove(plan: ContentPlan) {
        scope.launch {
            runCatching { store.deletePlan(plan.id) }
                .onSuccess { refresh++ }
                .onFailure { error = it.message ?: "没有删除成功" }
        }
    }

    SubPage(
        title = "内容日历",
        onBack = onBack,
        right = { TextButton(onClick = { refresh++ }) { Text("刷新") } },
    ) {
        GroupLabel("新建计划")
        MikaCard(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("选题 / 标题") })
                OutlinedTextField(platform, { platform = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("平台，例如小红书") })
                OutlinedTextField(scheduledAt, { scheduledAt = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("排期（可选）") }, placeholder = { Text("2026-11-05 或 2026-11-05T20:00") })
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, label = { Text("角度、提纲或备注（可选）") })
                error?.let { Text(it, style = MikaType.Hint, color = MikaTheme.colors.bad) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::add, enabled = title.isNotBlank()) { Text("加入日历", style = MikaType.BodyStrong) }
                }
            }
        }

        GroupLabel(if (loading) "计划" else "计划 · ${plans.size}")
        if (loading) {
            Text("正在读取…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MikaType.Hint, color = MikaTheme.colors.ink3)
        } else if (plans.isEmpty()) {
            MikaCard { Text("还没有计划。先记下一条选题，或让 Agent 帮你排。", Modifier.padding(16.dp), style = MikaType.Caption, color = MikaTheme.colors.ink3) }
        } else {
            plans.forEach { plan ->
                PlanCard(plan, onAdvance = { advance(plan) }, onDelete = { remove(plan) })
            }
        }
        Text(
            "计划只存在这台手机里。Agent 用 content_plan_add / content_plan_list / content_plan_update 读写同一份日历；发布后到「发布记录」留结果。",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )
    }
}

@Composable
private fun PlanCard(plan: ContentPlan, onAdvance: () -> Unit, onDelete: () -> Unit) {
    val next = when (plan.status) {
        PlanStatus.IDEA -> "开始制作"
        PlanStatus.DRAFTING -> "标记待发布"
        PlanStatus.READY -> "标记已排期"
        PlanStatus.SCHEDULED -> "标记已发布"
        PlanStatus.PUBLISHED -> "归档"
        PlanStatus.ARCHIVED -> "已归档"
    }
    MikaCard(Modifier.padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plan.title, Modifier.weight(1f), style = MikaType.BodyStrong, color = MikaTheme.colors.ink)
                StatusPill(EaselContentStore.STATUS_LABELS[plan.status] ?: plan.status.name, plan.status == PlanStatus.PUBLISHED)
            }
            Text(
                listOf(plan.platform.ifBlank { "未指定平台" }, plan.scheduledAt?.replace('T', ' ') ?: "未排期").joinToString(" · "),
                style = MikaType.Caption, color = MikaTheme.colors.ink3,
            )
            if (plan.notes.isNotBlank()) Text(plan.notes, style = MikaType.Caption, color = MikaTheme.colors.ink2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("删除", color = MikaTheme.colors.bad) }
                TextButton(onClick = onAdvance, enabled = plan.status != PlanStatus.ARCHIVED) { Text(next) }
            }
        }
    }
}

/** 发布记录：只记真实结果；手动补登与 Agent 的 content_publish_log_* 共用同一份数据。 */
@Composable
fun EaselPublishLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EaselContentStore(context) }
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<PublishRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }
    var postUrl by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(PublishStatus.PUBLISHED) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        records = store.publishLog(200)
        loading = false
    }

    fun add() {
        if (title.isBlank() || platform.isBlank()) return
        scope.launch {
            runCatching {
                store.addPublish(NewPublishRecord(title.trim(), platform.trim(), status, postUrl = postUrl.trim().ifEmpty { null }, note = note.trim()))
            }.onSuccess {
                title = ""; platform = ""; postUrl = ""; note = ""; error = null; refresh++
            }.onFailure { error = it.message ?: "没有保存成功" }
        }
    }

    fun remove(record: PublishRecord) {
        scope.launch {
            runCatching { store.deletePublish(record.id) }
                .onSuccess { refresh++ }
                .onFailure { error = it.message ?: "没有删除成功" }
        }
    }

    SubPage(
        title = "发布记录",
        onBack = onBack,
        right = { TextButton(onClick = { refresh++ }) { Text("刷新") } },
    ) {
        GroupLabel("手动补记")
        MikaCard(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("标题") })
                OutlinedTextField(platform, { platform = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("平台") })
                OutlinedTextField(postUrl, { postUrl = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("链接（可选）") })
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("结果、数据或失败原因（可选）") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectChip("已发布", status == PublishStatus.PUBLISHED, { status = PublishStatus.PUBLISHED }, small = true)
                    SelectChip("失败", status == PublishStatus.FAILED, { status = PublishStatus.FAILED }, small = true)
                }
                error?.let { Text(it, style = MikaType.Hint, color = MikaTheme.colors.bad) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::add, enabled = title.isNotBlank() && platform.isNotBlank()) { Text("记一条", style = MikaType.BodyStrong) }
                }
            }
        }

        GroupLabel(if (loading) "最近发布" else "最近发布 · ${records.size}")
        if (loading) {
            Text("正在读取…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MikaType.Hint, color = MikaTheme.colors.ink3)
        } else if (records.isEmpty()) {
            MikaCard { Text("还没有发布记录。真实发布并核实后，让 Agent 记一条，或在这里补记。", Modifier.padding(16.dp), style = MikaType.Caption, color = MikaTheme.colors.ink3) }
        } else {
            records.forEach { record ->
                PublishCard(record, onDelete = { remove(record) })
            }
        }
        Text(
            "发布记录是已经发生的结果，不是待办。Agent 用 content_publish_log_add 在核实后写入；写之前不会把计划当成已发布。",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )
    }
}

@Composable
private fun PublishCard(record: PublishRecord, onDelete: () -> Unit) {
    MikaCard(Modifier.padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(record.title, Modifier.weight(1f), style = MikaType.BodyStrong, color = MikaTheme.colors.ink)
                StatusPill(if (record.status == PublishStatus.PUBLISHED) "已发布" else "失败", record.status == PublishStatus.PUBLISHED)
            }
            Text(
                listOf(record.platform, record.publishedAt.replace('T', ' ')).joinToString(" · "),
                style = MikaType.Caption, color = MikaTheme.colors.ink3,
            )
            record.postUrl?.takeIf { it.isNotBlank() }?.let { Text(it, style = MikaType.Hint, color = MikaTheme.colors.accent, maxLines = 2) }
            if (record.note.isNotBlank()) Text(record.note, style = MikaType.Caption, color = MikaTheme.colors.ink2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("删除", color = MikaTheme.colors.bad) }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, good: Boolean) {
    val c = MikaTheme.colors
    Text(
        text,
        Modifier.background(if (good) c.okSoft else c.accentSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MikaType.Hint,
        color = if (good) c.ok else c.accent,
    )
}
