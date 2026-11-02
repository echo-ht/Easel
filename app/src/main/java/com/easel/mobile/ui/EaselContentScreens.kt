package com.easel.mobile.ui

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.easel.mobile.core.easel.ContentPlan
import com.easel.mobile.core.easel.EaselContentDate
import com.easel.mobile.core.easel.NewContentPlan
import com.easel.mobile.core.easel.NewPublishRecord
import com.easel.mobile.core.easel.PlanStatus
import com.easel.mobile.core.easel.PublishRecord
import com.easel.mobile.core.easel.PublishStatus
import com.easel.mobile.easel.EaselContentStore
import com.easel.mobile.ui.components.GroupLabel
import com.easel.mobile.ui.components.EaselCard
import com.easel.mobile.ui.components.SelectChip
import com.easel.mobile.ui.components.SubPage
import com.easel.mobile.ui.theme.EaselTheme
import com.easel.mobile.ui.theme.EaselType
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
            runCatching { store.updatePlan(plan.id, com.easel.mobile.core.easel.ContentPlanPatch(status = next)) }
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
        EaselCard(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("选题 / 标题") })
                OutlinedTextField(platform, { platform = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("平台，例如小红书") })
                OutlinedTextField(scheduledAt, { scheduledAt = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("排期（可选）") }, placeholder = { Text("2026-11-05 或 2026-11-05T20:00") })
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, label = { Text("角度、提纲或备注（可选）") })
                error?.let { Text(it, style = EaselType.Hint, color = EaselTheme.colors.bad) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::add, enabled = title.isNotBlank()) { Text("加入日历", style = EaselType.BodyStrong) }
                }
            }
        }

        GroupLabel(if (loading) "计划" else "计划 · ${plans.size}")
        if (loading) {
            Text("正在读取…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = EaselType.Hint, color = EaselTheme.colors.ink3)
        } else if (plans.isEmpty()) {
            EaselCard { Text("还没有计划。先记下一条选题，或让 Agent 帮你排。", Modifier.padding(16.dp), style = EaselType.Caption, color = EaselTheme.colors.ink3) }
        } else {
            plans.forEach { plan ->
                PlanCard(plan, onAdvance = { advance(plan) }, onDelete = { remove(plan) })
            }
        }
        Text(
            "计划只存在这台手机里。Agent 用 content_plan_add / content_plan_list / content_plan_update 读写同一份日历；发布后到「发布记录」留结果。",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = EaselType.Hint,
            color = EaselTheme.colors.ink3,
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
    EaselCard(Modifier.padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plan.title, Modifier.weight(1f), style = EaselType.BodyStrong, color = EaselTheme.colors.ink)
                StatusPill(EaselContentStore.STATUS_LABELS[plan.status] ?: plan.status.name, plan.status == PlanStatus.PUBLISHED)
            }
            Text(
                listOf(plan.platform.ifBlank { "未指定平台" }, plan.scheduledAt?.replace('T', ' ') ?: "未排期").joinToString(" · "),
                style = EaselType.Caption, color = EaselTheme.colors.ink3,
            )
            if (plan.notes.isNotBlank()) Text(plan.notes, style = EaselType.Caption, color = EaselTheme.colors.ink2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("删除", color = EaselTheme.colors.bad) }
                TextButton(onClick = onAdvance, enabled = plan.status != PlanStatus.ARCHIVED) { Text(next) }
            }
        }
    }
}

private enum class PublishFilter(val label: String) {
    All("全部"), Published("已发布"), Failed("失败");

    fun matches(record: PublishRecord): Boolean = when (this) {
        All -> true
        Published -> record.status == PublishStatus.PUBLISHED
        Failed -> record.status == PublishStatus.FAILED
    }
}

/** 发布中心：待发布队列、手动补记和真实发布结果共用同一份本机数据。 */
@Composable
fun EaselPublishLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { EaselContentStore(context) }
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<ContentPlan>>(emptyList()) }
    var records by remember { mutableStateOf<List<PublishRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(PublishFilter.All) }
    var copied by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }
    var postUrl by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(PublishStatus.PUBLISHED) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        plans = store.plans()
        records = store.publishLog(200)
        loading = false
    }

    fun copyPublishPrompt(plan: ContentPlan) {
        val prompt = buildString {
            append("请把这条内容发布到")
            append(plan.platform.ifBlank { "我的平台" })
            append("：\n标题：")
            append(plan.title)
            if (plan.notes.isNotBlank()) append("\n备注：").append(plan.notes)
            append("\n\n发布前按 publish-checklist 检查标题、正文、素材和账号；发布后在 App 里核实结果，")
            append("再用 content_publish_log_add 记录真实结果和链接。")
        }
        clipboard.setText(AnnotatedString(prompt))
        copied = plan.id
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
        title = "发布中心",
        onBack = onBack,
        right = { TextButton(onClick = { refresh++ }) { Text("刷新") } },
    ) {
        Text(
            "待发布队列来自内容日历。复制发布指令发给 Agent，由 Agent 在手机上打开对应 App、检查并发布；发布后在 App 里核实再记结果。",
            Modifier.padding(bottom = 12.dp),
            style = EaselType.Hint,
            color = EaselTheme.colors.ink3,
        )

        val pending = plans
            .filter { it.status == PlanStatus.READY || it.status == PlanStatus.SCHEDULED }
            .sortedWith(compareBy<ContentPlan>({ it.scheduledAt == null }, { it.scheduledAt }, { it.createdAt }))
        GroupLabel(if (loading) "待发布" else "待发布 · ${pending.size}")
        if (loading) {
            Text("正在读取…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = EaselType.Hint, color = EaselTheme.colors.ink3)
        } else if (pending.isEmpty()) {
            EaselCard(Modifier.padding(bottom = 12.dp)) {
                Text(
                    "没有待发布内容。把内容日历里的计划推进到「待发布」或「已排期」后会出现在这里。",
                    Modifier.padding(16.dp), style = EaselType.Caption, color = EaselTheme.colors.ink3,
                )
            }
        } else {
            pending.forEach { plan ->
                PendingPublishCard(plan, copied = copied == plan.id, onCopy = { copyPublishPrompt(plan) })
            }
        }

        GroupLabel("手动补记")
        EaselCard(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("标题") })
                OutlinedTextField(platform, { platform = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("平台") })
                OutlinedTextField(postUrl, { postUrl = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("链接（可选）") })
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("结果、数据或失败原因（可选）") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectChip("已发布", status == PublishStatus.PUBLISHED, { status = PublishStatus.PUBLISHED }, small = true)
                    SelectChip("失败", status == PublishStatus.FAILED, { status = PublishStatus.FAILED }, small = true)
                }
                error?.let { Text(it, style = EaselType.Hint, color = EaselTheme.colors.bad) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::add, enabled = title.isNotBlank() && platform.isNotBlank()) { Text("记一条", style = EaselType.BodyStrong) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PublishFilter.entries.forEach { option ->
                SelectChip(option.label, filter == option, { filter = option }, small = true)
            }
        }
        val filtered = records.filter { filter.matches(it) }
        GroupLabel(if (loading) "发布结果" else "发布结果 · ${filtered.size}")
        if (loading) {
            Text("正在读取…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = EaselType.Hint, color = EaselTheme.colors.ink3)
        } else if (filtered.isEmpty()) {
            EaselCard { Text("这个筛选下还没有发布记录。真实发布并核实后，让 Agent 记一条，或在这里补记。", Modifier.padding(16.dp), style = EaselType.Caption, color = EaselTheme.colors.ink3) }
        } else {
            filtered.forEach { record ->
                PublishCard(record, onDelete = { remove(record) })
            }
        }
        Text(
            "发布记录是已经发生的结果，不是待办。Agent 用 content_publish_log_add 在核实后写入；写之前不会把计划当成已发布。",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = EaselType.Hint,
            color = EaselTheme.colors.ink3,
        )
    }
}

@Composable
private fun PendingPublishCard(plan: ContentPlan, copied: Boolean, onCopy: () -> Unit) {
    EaselCard(Modifier.padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plan.title, Modifier.weight(1f), style = EaselType.BodyStrong, color = EaselTheme.colors.ink)
                StatusPill("待发布", true)
            }
            Text(
                listOfNotNull(plan.platform.ifBlank { "未填平台" }, plan.scheduledAt?.replace('T', ' ')).joinToString(" · "),
                style = EaselType.Caption,
                color = EaselTheme.colors.ink3,
            )
            if (plan.notes.isNotBlank()) Text(plan.notes, style = EaselType.Caption, color = EaselTheme.colors.ink2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCopy) { Text(if (copied) "已复制" else "复制发布指令", style = EaselType.BodyStrong) }
            }
        }
    }
}

@Composable
private fun PublishCard(record: PublishRecord, onDelete: () -> Unit) {
    EaselCard(Modifier.padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(record.title, Modifier.weight(1f), style = EaselType.BodyStrong, color = EaselTheme.colors.ink)
                StatusPill(if (record.status == PublishStatus.PUBLISHED) "已发布" else "失败", record.status == PublishStatus.PUBLISHED)
            }
            Text(
                listOf(record.platform, record.publishedAt.replace('T', ' ')).joinToString(" · "),
                style = EaselType.Caption, color = EaselTheme.colors.ink3,
            )
            record.postUrl?.takeIf { it.isNotBlank() }?.let { Text(it, style = EaselType.Hint, color = EaselTheme.colors.accent, maxLines = 2) }
            if (record.note.isNotBlank()) Text(record.note, style = EaselType.Caption, color = EaselTheme.colors.ink2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("删除", color = EaselTheme.colors.bad) }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, good: Boolean) {
    val c = EaselTheme.colors
    Text(
        text,
        Modifier.background(if (good) c.okSoft else c.accentSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = EaselType.Hint,
        color = if (good) c.ok else c.accent,
    )
}
