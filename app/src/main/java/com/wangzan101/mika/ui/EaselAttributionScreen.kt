package com.wangzan101.mika.ui

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.easel.AttributedPost
import com.wangzan101.mika.core.easel.ContentMetric
import com.wangzan101.mika.core.easel.EaselAttribution
import com.wangzan101.mika.easel.EaselContentStore
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.launch

/** 发布后的数据归因页：本机指标快照的确定性汇总，不把相关当因果。 */
@Composable
fun EaselAttributionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { EaselContentStore(context) }
    val scope = rememberCoroutineScope()
    var metrics by remember { mutableStateOf<List<ContentMetric>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        runCatching { store.metrics(500) }
            .onSuccess { metrics = it; error = null }
            .onFailure { error = it.message ?: "没有读取到指标" }
        loading = false
    }

    val summary = remember(metrics) { EaselAttribution.summarize(metrics, top = 5) }

    fun copyCapturePrompt() {
        clipboard.setText(AnnotatedString(
            "请打开各平台创作者后台，读取最近发布内容的真实数据：播放/阅读、点赞、收藏、评论、分享、涨粉。" +
                "逐条用 content_metrics_add 记录；发布记录里有的就带 publishId，平台显示区间或缩写时原样写进 note。" +
                "全部记完后调用 content_attribution_summary 汇总，先给事实，再给可验证的下一步。",
        ))
        copied = true
    }

    fun remove(metric: ContentMetric) {
        scope.launch {
            runCatching { store.deleteMetric(metric.id) }
                .onSuccess { refresh++ }
                .onFailure { error = it.message ?: "没有删除成功" }
        }
    }

    SubPage(
        title = "数据归因",
        onBack = onBack,
        right = { TextButton(onClick = { refresh++ }) { Text("刷新") } },
    ) {
        Text(
            "同一篇内容可以有多次快照。当前表现按最新快照算，变化量按最早快照对比；没有触达数据时不计算互动率。",
            Modifier.padding(bottom = 12.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )

        val c = MikaTheme.colors
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("内容", summary.postCount.toString(), Modifier.weight(1f))
            StatCard("快照", summary.snapshotCount.toString(), Modifier.weight(1f))
            StatCard("平台", summary.platforms.size.toString(), Modifier.weight(1f))
        }

        GroupLabel("平台表现")
        if (loading) {
            Text("正在读取…", Modifier.padding(vertical = 8.dp), style = MikaType.Hint, color = c.ink3)
        } else if (summary.platforms.isEmpty()) {
            MikaCard(Modifier.padding(bottom = 10.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有指标快照。", style = MikaType.BodyStrong, color = c.ink)
                    Text(
                        "让 Agent 读取真实后台数据后用 content_metrics_add 记录；有发布记录时带上 publishId。",
                        style = MikaType.Caption,
                        color = c.ink3,
                    )
                    TextButton(onClick = ::copyCapturePrompt) { Text(if (copied) "已复制指令" else "复制数据回读指令") }
                }
            }
        } else {
            summary.platforms.forEach { platform ->
                MikaCard(Modifier.padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(platform.platform, Modifier.weight(1f), style = MikaType.BodyStrong, color = c.ink)
                            Text("${platform.posts} 篇", style = MikaType.Caption, color = c.ink3)
                        }
                        Text(
                            "触达 ${shortNumber(platform.views)} · 互动 ${shortNumber(platform.engagement)}" +
                                (platform.engagementRate?.let { " · 互动率 ${percent(it)}" } ?: ""),
                            style = MikaType.Caption,
                            color = c.ink2,
                        )
                    }
                }
            }
        }

        if (summary.topPosts.isNotEmpty()) {
            GroupLabel("表现最好的内容")
            summary.topPosts.forEach { AttributedPostCard(it) }
        }

        GroupLabel(if (loading) "最近快照" else "最近快照 · ${metrics.size}")
        if (!loading && metrics.isEmpty()) {
            MikaCard {
                Text("还没有快照。数据只存在这台手机上，删除快照不会删除发布记录。", Modifier.padding(16.dp), style = MikaType.Caption, color = c.ink3)
            }
        } else {
            metrics.take(20).forEach { metric ->
                MetricCard(metric, onDelete = { remove(metric) })
            }
            if (metrics.size > 20) {
                Text("只显示最近 20 条，共 ${metrics.size} 条；Agent 可通过 content_metrics_list 读取更多。",
                    Modifier.padding(vertical = 6.dp), style = MikaType.Hint, color = c.ink3)
            }
        }
        error?.let { Text(it, Modifier.padding(top = 8.dp), style = MikaType.Hint, color = c.bad) }
        Text(
            "互动分 = 点赞 + 收藏×2 + 评论×2 + 分享×3。这是本机定义的排序口径，不是平台官方指标。",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = MikaType.Hint,
            color = c.ink3,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    Column(
        modifier.background(c.surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, style = MikaType.Title, color = c.ink)
        Text(label, style = MikaType.Hint, color = c.ink3)
    }
}

@Composable
private fun AttributedPostCard(post: AttributedPost) {
    val c = MikaTheme.colors
    MikaCard(Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(post.title.ifBlank { "未命名内容" }, style = MikaType.BodyStrong, color = c.ink)
            Text(
                listOf(
                    post.platform.ifBlank { "未指定平台" },
                    "互动 ${shortNumber(post.engagement)}",
                    post.engagementRate?.let { "互动率 ${percent(it)}" },
                    "变化 ${signedNumber(post.engagementChange)}",
                ).filterNotNull().joinToString(" · "),
                style = MikaType.Caption,
                color = c.ink2,
            )
            post.latest.views?.let { Text("触达 ${shortNumber(it)} · ${post.snapshotCount} 次快照", style = MikaType.Hint, color = c.ink3) }
        }
    }
}

@Composable
private fun MetricCard(metric: ContentMetric, onDelete: () -> Unit) {
    val c = MikaTheme.colors
    MikaCard(Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(metric.title.ifBlank { "未命名内容" }, Modifier.weight(1f), style = MikaType.BodyStrong, color = c.ink)
                Text(metric.capturedAt.replace('T', ' '), style = MikaType.Hint, color = c.ink3)
            }
            Text(metric.platform.ifBlank { "未指定平台" }, style = MikaType.Caption, color = c.ink3)
            Text(metricNumbers(metric), style = MikaType.Caption, color = c.ink2)
            if (metric.note.isNotBlank()) Text(metric.note, style = MikaType.Hint, color = c.ink3)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("删除", color = c.bad) }
            }
        }
    }
}

private fun metricNumbers(metric: ContentMetric): String = listOfNotNull(
    metric.views?.let { "触达 $it" },
    metric.likes?.let { "赞 $it" },
    metric.collects?.let { "藏 $it" },
    metric.comments?.let { "评 $it" },
    metric.shares?.let { "转 $it" },
    metric.followers?.let { "粉 $it" },
).joinToString(" · ").ifBlank { "未记录数值" }

private fun shortNumber(value: Long): String = when {
    value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

private fun signedNumber(value: Long): String = if (value > 0) "+$value" else value.toString()

private fun percent(value: Double): String = "%.1f%%".format(value * 100)
