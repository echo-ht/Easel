package com.wangzan101.mika.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.wangzan101.mika.easel.EaselOutput
import com.wangzan101.mika.easel.EaselOutputs
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.SelectChip
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.viewer.FileViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class MaterialFilter(val label: String) {
    All("全部"), Image("图片"), Video("视频"), Audio("音频"), Doc("文档");

    fun matches(mime: String?): Boolean = when (this) {
        All -> true
        Image -> mime?.startsWith("image/") == true
        Video -> mime?.startsWith("video/") == true
        Audio -> mime?.startsWith("audio/") == true
        Doc -> mime == null || mime.startsWith("text/") || mime.contains("pdf") ||
            mime.contains("word") || mime.contains("document") || mime.contains("sheet") || mime.contains("presentation")
    }
}

/** 素材库：从手机导入、预览、分享和删除内容工作区的素材。 */
@Composable
fun EaselMaterialsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outputs by remember { mutableStateOf<List<EaselOutput>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(MaterialFilter.All) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EaselOutput?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        outputs = withContext(Dispatchers.IO) { EaselOutputs.list(context) }
        loading = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            message = "正在导入 ${uris.size} 个素材…"
            val imported = try {
                uris.mapNotNull { uri ->
                    runCatching { EaselOutputs.import(context, uri) }.getOrNull()
                }
            } finally {
                importing = false
            }
            message = if (imported.size == uris.size) "已导入 ${imported.size} 个素材" else "导入 ${imported.size}/${uris.size} 个，其余没有读成功"
            refresh++
        }
    }

    fun remove(item: EaselOutput) {
        scope.launch {
            val removed = runCatching { EaselOutputs.delete(context, item.uri) }.getOrDefault(false)
            message = if (removed) "已删除 ${item.name}" else "系统没有允许删除，可能需要在文件管理里确认"
            refresh++
        }
    }

    val filtered = outputs.filter { filter.matches(it.mime) }

    SubPage(
        title = "素材库",
        onBack = onBack,
        right = {
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !importing) {
                Text(if (importing) "导入中…" else "导入", style = MikaType.BodyStrong)
            }
        },
    ) {
        Text(
            "素材保存在手机的「下载/Easel」，Agent 导出的成品也在这里。导入的图片、视频、音频和文档可以直接预览或分享。",
            Modifier.padding(bottom = 12.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialFilter.entries.forEach { option ->
                SelectChip(option.label, filter == option, { filter = option }, small = true)
            }
        }
        message?.let {
            Text(it, Modifier.padding(bottom = 10.dp), style = MikaType.Hint, color = MikaTheme.colors.ink2)
        }

        GroupLabel(if (loading) "素材" else "素材 · ${filtered.size}")
        if (loading) {
            Text("正在读取…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MikaType.Hint, color = MikaTheme.colors.ink3)
        } else if (filtered.isEmpty()) {
            MikaCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("这里还没有素材。", style = MikaType.Body, color = MikaTheme.colors.ink)
                    Text("点右上角「导入」从手机选文件，或让 Agent 完成内容后导出成品。", style = MikaType.Caption, color = MikaTheme.colors.ink3)
                }
            }
        } else {
            GroupCard {
                filtered.forEachIndexed { index, item ->
                    ListRow(
                        icon = MikaIcons.Doc,
                        title = item.name,
                        subtitle = listOfNotNull(
                            typeLabel(item.mime),
                            formatSize(item.size),
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.addedAt)),
                        ).joinToString(" · "),
                        first = index == 0,
                        onClick = { FileViewer.open(context, item.uri, item.name) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { FileViewer.share(context, item.uri, item.name) }) {
                                    Icon(MikaIcons.Share, "分享", Modifier.size(MikaSize.SmallIcon), tint = MikaTheme.colors.ink2)
                                }
                                IconButton(onClick = { deleting = item }) {
                                    Icon(MikaIcons.Close, "删除", Modifier.size(MikaSize.SmallIcon), tint = MikaTheme.colors.bad)
                                }
                            }
                        },
                    )
                }
            }
        }
        Text(
            "素材库只读取本 App 在「下载/Easel」里创建的文件，不会扫描手机里的其他照片和文档。",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这个素材？") },
            text = { Text("${item.name}\n\n删除后无法从 App 恢复。") },
            confirmButton = {
                TextButton(onClick = { deleting = null; remove(item) }) {
                    Text("删除", color = MikaTheme.colors.bad)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

private fun typeLabel(mime: String?): String = when {
    mime?.startsWith("image/") == true -> "图片"
    mime?.startsWith("video/") == true -> "视频"
    mime?.startsWith("audio/") == true -> "音频"
    mime?.contains("pdf") == true -> "PDF"
    else -> "文档"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
