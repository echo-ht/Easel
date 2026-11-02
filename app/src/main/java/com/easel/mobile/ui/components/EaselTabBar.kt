package com.easel.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.easel.mobile.ui.theme.EaselIcons
import com.easel.mobile.ui.theme.EaselTheme
import com.easel.mobile.ui.theme.EaselType

enum class EaselTab(val label: String, val icon: ImageVector) {
    Home("工作台", EaselIcons.Lines),
    Create("创作", EaselIcons.Spark),
    Publish("发布", EaselIcons.Clock),
    Settings("设置", EaselIcons.Gear),
}

/** Easel 主导航:工作台、创作、发布、设置。Easel 的自动化配置收在设置入口里。 */
@Composable
fun EaselTabBar(
    current: EaselTab,
    onSelect: (EaselTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EaselTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.surface)
            .drawBehind { drawLine(c.hair, Offset.Zero, Offset(size.width, 0f), 0.5.dp.toPx()) }
            .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EaselTab.entries.forEach { tab ->
            val active = tab == current
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !active) { onSelect(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    Modifier.size(width = 36.dp, height = 26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) c.accentSoft else c.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, tab.label, Modifier.size(19.dp), tint = if (active) c.accent else c.ink3)
                }
                Text(tab.label, style = EaselType.Hint, color = if (active) c.accent else c.ink3)
            }
        }
    }
}
