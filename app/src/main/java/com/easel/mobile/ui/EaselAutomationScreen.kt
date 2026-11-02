package com.easel.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easel.mobile.ui.components.GroupCard
import com.easel.mobile.ui.components.GroupLabel
import com.easel.mobile.ui.components.ListRow
import com.easel.mobile.ui.components.EaselCard
import com.easel.mobile.ui.components.RowChevron
import com.easel.mobile.ui.components.SubPage
import com.easel.mobile.ui.theme.EaselIcons
import com.easel.mobile.ui.theme.EaselTheme
import com.easel.mobile.ui.theme.EaselType

/** Easel 的自动化中心：把 Easel 的手机、模型、网页、任务和记忆配置收在同一处。 */
@Composable
fun EaselAutomationScreen(
    phoneReady: Boolean,
    modelReady: Boolean,
    canStartTask: Boolean,
    onOpenPhoneSetup: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenScreens: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenWebSettings: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val c = EaselTheme.colors
    SubPage(title = "自动化中心", onBack = onBack) {
        EaselCard(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Easel 负责内容，Easel 负责执行。", style = EaselType.Title, color = c.ink)
                Text(
                    "手机操作、模型、网页登录、后台任务和记忆都沿用 Easel 的配置；这些能力只在这台手机上运行。",
                    style = EaselType.Caption,
                    color = c.ink3,
                )
            }
        }

        GroupLabel("运行状态")
        GroupCard {
            ListRow(
                icon = EaselIcons.Phone,
                title = "手机自动化",
                subtitle = "无障碍、内置助手与真实 App 操作",
                first = true,
                detail = if (phoneReady) "已就绪，可以直接操作手机 App" else "需要完成手机操作配置",
                detailColor = if (phoneReady) c.ok else c.warn,
                onClick = onOpenPhoneSetup,
                trailing = { RowChevron() },
            )
            ListRow(
                icon = EaselIcons.Spark,
                title = "主模型",
                subtitle = "对话、创作和判断使用的主模型",
                detail = if (modelReady) "已连接" else "还没有配置主模型",
                detailColor = if (modelReady) c.ok else c.warn,
                onClick = onOpenModels,
                trailing = { RowChevron() },
            )
            ListRow(
                icon = EaselIcons.Terminal,
                title = "任务执行",
                subtitle = "模型与运行环境是否允许开始任务",
                detail = if (canStartTask) "可以开始自动化任务" else "仍有阻塞项需要处理",
                detailColor = if (canStartTask) c.ok else c.warn,
                onClick = onOpenSettings,
                trailing = { RowChevron() },
            )
        }

        GroupLabel("Easel 能力")
        GroupCard {
            ListRow(
                icon = EaselIcons.Screens,
                title = "后台任务屏幕",
                subtitle = "查看正在运行的任务和实时虚拟屏",
                first = true,
                onClick = onOpenScreens,
                trailing = { RowChevron() },
            )
            ListRow(
                icon = EaselIcons.Heart,
                title = "记忆与技能",
                subtitle = "用户记忆、账号偏好、技能库和自学内容",
                onClick = onOpenMemory,
                trailing = { RowChevron() },
            )
            ListRow(
                icon = EaselIcons.Globe,
                title = "网页登录",
                subtitle = "管理内置浏览器里的网站会话",
                onClick = onOpenWebSettings,
                trailing = { RowChevron() },
            )
            ListRow(
                icon = EaselIcons.Globe,
                title = "内置浏览器",
                subtitle = "打开网页、登录和抓取公开内容",
                onClick = onOpenBrowser,
                trailing = { RowChevron() },
            )
        }

        GroupLabel("更多")
        GroupCard {
            ListRow(
                icon = EaselIcons.Gear,
                title = "完整设置",
                subtitle = "外观、通知、服务商、沙箱、实验开关和开发者选项",
                first = true,
                onClick = onOpenSettings,
                trailing = { RowChevron() },
            )
            ListRow(
                icon = EaselIcons.Info,
                title = "关于 Easel",
                subtitle = "版本、诊断、许可和更新",
                onClick = onOpenAbout,
                trailing = { RowChevron() },
            )
        }
        Text(
            "Easel 和 Easel 共用同一套自动化配置，不会复制第二份密钥或登录状态。",
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            style = EaselType.Hint,
            color = c.ink3,
        )
    }
}
