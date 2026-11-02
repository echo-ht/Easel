package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.core.easel.ContentMetric
import com.wangzan101.mika.core.easel.ContentPlan
import com.wangzan101.mika.core.easel.PlanStatus
import com.wangzan101.mika.core.easel.PublishRecord
import com.wangzan101.mika.core.easel.PublishStatus
import com.wangzan101.mika.easel.EaselContentStore
import com.wangzan101.mika.easel.EaselOutput
import com.wangzan101.mika.easel.EaselOutputs
import com.wangzan101.mika.easel.EaselProfileStore
import com.wangzan101.mika.ui.components.EaselTab
import com.wangzan101.mika.ui.components.EaselTabBar
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.IconTile
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Easel 主工作台。这里是 App 的根页面：内容生产、发布、数据和自动化配置都从这里进入；
 * 创作对话与 Mika 的自动化能力作为工作台的一部分，而不是另一个产品首页。
 */
@Composable
fun EaselScreen(
    onOpenChat: () -> Unit,
    onOpenWorkflow: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenPublishLog: () -> Unit,
    onOpenMaterials: () -> Unit,
    onOpenAttribution: () -> Unit,
    onOpenRisk: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenSettings: () -> Unit,
    phoneReady: Boolean,
    modelReady: Boolean,
    canStartTask: Boolean,
) {
    val context = LocalContext.current
    val store = remember { EaselContentStore(context) }
    val profileStore = remember { EaselProfileStore(context) }
    var plans by remember { mutableStateOf<List<ContentPlan>>(emptyList()) }
    var records by remember { mutableStateOf<List<PublishRecord>>(emptyList()) }
    var metrics by remember { mutableStateOf<List<ContentMetric>>(emptyList()) }
    var outputs by remember { mutableStateOf<List<EaselOutput>>(emptyList()) }
    var profile by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            plans = store.plans()
            records = store.publishLog(200)
            metrics = store.metrics(200)
            outputs = EaselOutputs.list(context)
            profile = profileStore.read()
        }
        loading = false
    }

    val pending = plans.count { it.status == PlanStatus.READY || it.status == PlanStatus.SCHEDULED }
    val published = records.count { it.status == PublishStatus.PUBLISHED }
    val ready = canStartTask && modelReady && phoneReady
    val today = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }

    PageBackground {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                WorkspaceHeader(today, ready)
                Spacer(Modifier.height(16.dp))
                WorkspaceHero(onOpenChat = onOpenChat, onOpenWorkflow = onOpenWorkflow)

                GroupLabel("快捷入口")
                QuickRow(
                    QuickEntry("内容日历", "排选题与发布", MikaIcons.Clock, onOpenCalendar, TileTone.Neutral),
                    QuickEntry("发布中心", "队列和真实结果", MikaIcons.Spark, onOpenPublishLog, TileTone.Accent),
                )
                QuickRow(
                    QuickEntry("素材库", "图片与视频成品", MikaIcons.Photo, onOpenMaterials, TileTone.Neutral),
                    QuickEntry("数据归因", "快照与平台表现", MikaIcons.Chart, onOpenAttribution, TileTone.Accent),
                )
                QuickRow(
                    QuickEntry("发布风控", "凭据、隐私与措辞", MikaIcons.Shield, onOpenRisk, TileTone.Neutral),
                    QuickEntry("五层工作流", "从发现到复盘", MikaIcons.Lines, onOpenWorkflow, TileTone.Accent),
                )

                GroupLabel("自动化 · Easel × Mika")
                GroupCard {
                    ListRow(
                        icon = MikaIcons.Phone,
                        title = "手机自动化",
                        subtitle = "真实 App、网页与任务执行",
                        first = true,
                        detail = if (phoneReady) "已就绪" else "待配置",
                        detailColor = if (phoneReady) MikaTheme.colors.ok else MikaTheme.colors.warn,
                        onClick = onOpenAutomation,
                        trailing = { RowChevron() },
                    )
                    ListRow(
                        icon = MikaIcons.Spark,
                        title = "模型与任务",
                        subtitle = "主模型、运行环境和后台任务",
                        detail = when {
                            !modelReady -> "未配置模型"
                            canStartTask -> "可以开始"
                            else -> "有阻塞项"
                        },
                        detailColor = if (modelReady && canStartTask) MikaTheme.colors.ok else MikaTheme.colors.warn,
                        onClick = onOpenAutomation,
                        trailing = { RowChevron() },
                    )
                    ListRow(
                        icon = MikaIcons.Gear,
                        title = "自动化中心",
                        subtitle = "手机操作、网页登录、记忆与技能、完整设置",
                        onClick = onOpenAutomation,
                        trailing = { RowChevron() },
                    )
                }

                GroupLabel("内容脉搏")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PulseCell("计划", plans.size.toString(), "条", onOpenCalendar, Modifier.weight(1f))
                    PulseCell("待发布", pending.toString(), "条", onOpenPublishLog, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PulseCell("已发布", published.toString(), "条", onOpenPublishLog, Modifier.weight(1f))
                    PulseCell("数据快照", metrics.size.toString(), "次", onOpenAttribution, Modifier.weight(1f))
                }

                GroupLabel("账号画像")
                MikaCard(Modifier.padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("这次为谁创作", Modifier.weight(1f), style = MikaType.BodyStrong, color = MikaTheme.colors.ink)
                            TextButton(onClick = onOpenProfile) { Text("编辑画像") }
                        }
                        Text(
                            profile.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(4).joinToString("\n")
                                .ifBlank { "还没有账号画像。先写下定位、受众、风格和红线，Agent 才能按你的账号创作。" },
                            style = MikaType.Caption,
                            color = MikaTheme.colors.ink3,
                        )
                    }
                }

                GroupLabel(if (loading) "最近成品" else "最近成品 · ${outputs.size}")
                if (loading) {
                    Text("正在读取…", Modifier.padding(vertical = 8.dp), style = MikaType.Hint, color = MikaTheme.colors.ink3)
                } else if (outputs.isEmpty()) {
                    MikaCard(Modifier.padding(bottom = 12.dp)) {
                        Text(
                            "还没有成品。让 Agent 完成一条内容并用 share_file 导出，文件会出现在素材库。",
                            Modifier.padding(16.dp),
                            style = MikaType.Caption,
                            color = MikaTheme.colors.ink3,
                        )
                    }
                } else {
                    GroupCard {
                        outputs.take(3).forEachIndexed { index, output ->
                            ListRow(
                                icon = MikaIcons.Doc,
                                title = output.name,
                                subtitle = formatSize(output.size) + " · " +
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(output.addedAt)),
                                first = index == 0,
                                onClick = onOpenMaterials,
                                trailing = { RowChevron() },
                            )
                        }
                    }
                }

                Text(
                    "工作台、发布数据、素材和画像只保存在这台手机；自动化配置与 Mika 共用。",
                    Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                    style = MikaType.Hint,
                    color = MikaTheme.colors.ink3,
                )
            }
            EaselTabBar(EaselTab.Home, onSelect = { tab ->
                when (tab) {
                    EaselTab.Home -> Unit
                    EaselTab.Create -> onOpenChat()
                    EaselTab.Publish -> onOpenPublishLog()
                    EaselTab.Settings -> onOpenSettings()
                }
            })
        }
    }
}

@Composable
private fun WorkspaceHeader(today: String, ready: Boolean) {
    val c = MikaTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("EASEL", style = MikaType.Label.copy(letterSpacing = 2.4.sp), color = c.accent, fontWeight = FontWeight.Bold)
            Text("内容工作台", style = MikaType.LargeTitle, color = c.ink)
            Text(today, style = MikaType.Hint, color = c.ink3)
        }
        Row(
            Modifier.clip(CircleShape).background(if (ready) c.okSoft else c.warnSoft)
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).background(if (ready) c.ok else c.warnSolid, CircleShape))
            Text(if (ready) "自动化就绪" else "需要配置", style = MikaType.Hint, color = if (ready) c.ok else c.warn)
        }
    }
}

@Composable
private fun WorkspaceHero(onOpenChat: () -> Unit, onOpenWorkflow: () -> Unit) {
    val c = MikaTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.accent)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("把一条内容\n从想法做到发布。", style = MikaType.LargeTitle, color = c.onAccent)
            Text(
                "选题、制作、发布和数据回读，都在手机上完成。Easel 负责内容，Mika 负责执行。",
                style = MikaType.Caption,
                color = c.onAccent.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(13.dp)).background(c.surface).clickable(onClick = onOpenChat)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("开始创作", style = MikaType.BodyStrong, color = c.accent)
                }
                TextButton(onClick = onOpenWorkflow) {
                    Text("五层工作流", style = MikaType.BodyStrong, color = c.onAccent)
                }
            }
        }
    }
}

private data class QuickEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val tone: TileTone,
)

@Composable
private fun QuickRow(first: QuickEntry, second: QuickEntry) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickTile(first, Modifier.weight(1f))
        QuickTile(second, Modifier.weight(1f))
    }
}

@Composable
private fun QuickTile(entry: QuickEntry, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    MikaCard(modifier.heightIn(min = 108.dp).clickable(onClick = entry.onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            IconTile(entry.icon, entry.tone)
            Text(entry.title, style = MikaType.BodyStrong, color = c.ink)
            Text(entry.subtitle, style = MikaType.Hint, color = c.ink3)
        }
    }
}

@Composable
private fun PulseCell(label: String, value: String, unit: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    MikaCard(modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MikaType.Hint, color = c.ink3)
                Text(value, style = MikaType.Title, color = c.ink)
            }
            Text(unit, Modifier.padding(bottom = 2.dp), style = MikaType.Hint, color = c.ink3)
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
