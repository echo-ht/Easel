package com.easel.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.easel.mobile.core.easel.ContentPlan
import com.easel.mobile.core.easel.PlanStatus
import com.easel.mobile.core.easel.PublishRecord
import com.easel.mobile.easel.EaselContentStore
import com.easel.mobile.ui.components.GroupLabel
import com.easel.mobile.ui.components.EaselCard
import com.easel.mobile.ui.components.SubPage
import com.easel.mobile.ui.theme.EaselTheme
import com.easel.mobile.ui.theme.EaselType

/** 五层内容工作流的手机端首页：发现、策划、制作、发布、归因。 */
@Composable
fun EaselWorkflowScreen(
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenPublishCenter: () -> Unit,
    onOpenMaterials: () -> Unit,
    onOpenAttribution: () -> Unit = {},
    onOpenRisk: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { EaselContentStore(context) }
    var plans by remember { mutableStateOf<List<ContentPlan>>(emptyList()) }
    var records by remember { mutableStateOf<List<PublishRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var copied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        plans = store.plans()
        records = store.publishLog(Int.MAX_VALUE)
        loading = false
    }

    fun copy(stage: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        copied = stage
    }

    val ideas = plans.count { it.status == PlanStatus.IDEA }
    val planning = plans.count { it.status == PlanStatus.IDEA || it.status == PlanStatus.DRAFTING }
    val creating = plans.count { it.status == PlanStatus.DRAFTING }
    val ready = plans.count { it.status == PlanStatus.READY || it.status == PlanStatus.SCHEDULED }
    val published = records.count { it.status == com.easel.mobile.core.easel.PublishStatus.PUBLISHED }

    SubPage(
        title = "五层工作流",
        onBack = onBack,
        right = { TextButton(onClick = { refresh++ }) { Text("刷新") } },
    ) {
        Text(
            "从想法到复盘走同一条链。每一层只做需要的那一步，跨层任务先给三到五行计划再执行。",
            Modifier.padding(bottom = 14.dp),
            style = EaselType.Hint,
            color = EaselTheme.colors.ink3,
        )

        GroupLabel(if (loading) "工作流" else "工作流 · ${plans.size} 条计划 / ${records.size} 条发布记录")
        StageCard(
            number = 1,
            title = "发现",
            count = ideas,
            countLabel = "个想法",
            hint = "找热点、竞品、受众问题和内容缺口。",
            action = "复制找选题指令",
            onClick = {
                copy(
                    "发现",
                    "请按我的账号画像，找 3 个今天能做的选题。每个写清：热点或需求、目标读者、和我的账号的连接点、时效。不要编数据。",
                )
            },
        )
        StageCard(
            number = 2,
            title = "策划",
            count = planning,
            countLabel = "条在策划",
            hint = "定位、选题、角度、结构、标题、排期和平台适配。",
            action = "打开内容日历",
            onClick = onOpenCalendar,
        )
        StageCard(
            number = 3,
            title = "制作",
            count = creating,
            countLabel = "条制作中",
            hint = "写文案、做卡片、配音、剪视频，把中间素材放进素材库。",
            action = "打开素材库",
            onClick = onOpenMaterials,
        )
        StageCard(
            number = 4,
            title = "发布",
            count = ready,
            countLabel = "条待发布",
            hint = "按平台检查标题、正文、素材和账号，发布后在 App 里核实。",
            action = "打开发布中心",
            onClick = onOpenPublishCenter,
            secondaryAction = "发布前风控扫描",
            onSecondary = onOpenRisk,
        )
        StageCard(
            number = 5,
            title = "归因",
            count = published,
            countLabel = "条已发布",
            hint = "读回播放、互动和评论，把有效经验写回画像和下一次计划。",
            action = "查看数据归因",
            onClick = onOpenAttribution,
        )

        copied?.let {
            Text(
                "已复制「$it」指令。回到对话粘贴发送，Agent 会接着做。",
                Modifier.padding(top = 8.dp),
                style = EaselType.Hint,
                color = EaselTheme.colors.accent,
            )
        }
        Text(
            "工作流数据只存在这台手机：内容日历和发布记录在 App 私有目录，素材在「下载/Easel」。",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = EaselType.Hint,
            color = EaselTheme.colors.ink3,
        )
    }
}

@Composable
private fun StageCard(
    number: Int,
    title: String,
    count: Int,
    countLabel: String,
    hint: String,
    action: String,
    onClick: () -> Unit,
    secondaryAction: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    EaselCard(Modifier.padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$number",
                    Modifier.background(EaselTheme.colors.accentSoft, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = EaselType.Hint,
                    color = EaselTheme.colors.accent,
                )
                Text(title, Modifier.padding(start = 10.dp).weight(1f), style = EaselType.BodyStrong, color = EaselTheme.colors.ink)
                Text("$count $countLabel", style = EaselType.Caption, color = EaselTheme.colors.ink3)
            }
            Text(hint, style = EaselType.Caption, color = EaselTheme.colors.ink2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (secondaryAction != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) { Text(secondaryAction) }
                }
                TextButton(onClick = onClick) { Text(action) }
            }
        }
    }
}
