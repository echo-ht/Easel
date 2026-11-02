package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.easel.EaselOutput
import com.wangzan101.mika.easel.EaselOutputs
import com.wangzan101.mika.easel.EaselProfileStore
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.viewer.FileViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Easel 的内容工作台:账号画像、五层工作流、发布中心与素材库。 */
@Composable
fun EaselScreen(
    onBack: () -> Unit,
    onOpenWorkflow: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenPublishLog: () -> Unit = {},
    onOpenMaterials: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember { EaselProfileStore(context) }
    var profile by rememberSaveable { mutableStateOf(store.read().ifBlank { EaselProfileStore.DEFAULT_PROFILE }) }
    var saved by remember { mutableStateOf(false) }
    var outputs by remember { mutableStateOf<List<EaselOutput>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        outputs = withContext(Dispatchers.IO) { EaselOutputs.list(context) }
        loading = false
    }

    SubPage(
        title = "内容工作台",
        onBack = onBack,
        right = {
            TextButton(
                onClick = {
                    store.write(profile)
                    profile = store.read()
                    saved = true
                },
                enabled = profile.isNotBlank(),
            ) { Text(if (saved) "已保存" else "保存画像", style = MikaType.BodyStrong, color = MikaTheme.colors.accent) }
        },
    ) {
        GroupLabel("账号画像")
        MikaCard(Modifier.padding(bottom = 12.dp)) {
            OutlinedTextField(
                value = profile,
                onValueChange = { profile = it; saved = false },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                textStyle = MikaType.Body,
                minLines = 14,
                maxLines = 40,
                placeholder = { Text("写下账号定位、内容方向、风格、受众、平台、红线和长期记忆", style = MikaType.Caption) },
            )
        }
        Text(
            "画像会在每次创作任务开始时交给 Agent，作为账号上下文。",
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )

        GroupLabel("五层工作流")
        GroupCard {
            ListRow(
                icon = MikaIcons.Lines,
                title = "发现 → 策划 → 制作 → 发布 → 归因",
                subtitle = "看每一层现在有多少内容，复制下一步指令",
                first = true,
                onClick = onOpenWorkflow,
                trailing = { RowChevron() },
            )
        }

        GroupLabel("发布中心")
        GroupCard {
            ListRow(
                icon = MikaIcons.Clock,
                title = "内容日历",
                subtitle = "排选题、看进度，Agent 也能直接读写",
                first = true,
                onClick = onOpenCalendar,
                trailing = { RowChevron() },
            )
            ListRow(
                icon = MikaIcons.Lines,
                title = "发布记录",
                subtitle = "待发布队列、已发布与失败的真实结果",
                onClick = onOpenPublishLog,
                trailing = { RowChevron() },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GroupLabel("素材库")
            IconButton(onClick = { refresh++ }) {
                Icon(MikaIcons.Refresh, "刷新", Modifier.size(MikaSize.SmallIcon), tint = MikaTheme.colors.ink3)
            }
        }
        GroupCard {
            ListRow(
                icon = MikaIcons.Plus,
                title = "打开素材库",
                subtitle = "导入、预览、分享和删除图片、视频、音频和文档",
                first = true,
                onClick = onOpenMaterials,
                trailing = { RowChevron() },
            )
        }
        if (loading) {
            Text("正在读取…", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MikaType.Caption, color = MikaTheme.colors.ink3)
        } else if (outputs.isEmpty()) {
            MikaCard {
                Text(
                    "还没有成品。让 Agent 完成一条内容并导出，文件会出现在这里。",
                    Modifier.padding(16.dp),
                    style = MikaType.Caption,
                    color = MikaTheme.colors.ink3,
                )
            }
        } else {
            GroupCard {
                outputs.forEachIndexed { index, item ->
                    ListRow(
                        icon = MikaIcons.Doc,
                        title = item.name,
                        subtitle = formatSize(item.size) + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.addedAt)),
                        first = index == 0,
                        onClick = { FileViewer.open(context, item.uri, item.name) },
                        trailing = {
                            IconButton(onClick = { FileViewer.share(context, item.uri, item.name) }) {
                                Icon(MikaIcons.Share, "分享", Modifier.size(MikaSize.SmallIcon), tint = MikaTheme.colors.ink2)
                            }
                        },
                    )
                }
            }
        }
        Text(
            "成品由 Agent 通过 share_file 导出到手机的「下载/Easel」。素材库只读取本 App 创建的文件，不会扫描手机里的其他内容。",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
