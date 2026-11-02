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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easel.mobile.core.easel.EaselRiskScanner
import com.easel.mobile.core.easel.RiskFinding
import com.easel.mobile.core.easel.RiskLevel
import com.easel.mobile.core.easel.RiskReport
import com.easel.mobile.ui.components.GroupLabel
import com.easel.mobile.ui.components.EaselCard
import com.easel.mobile.ui.components.SubPage
import com.easel.mobile.ui.theme.EaselTheme
import com.easel.mobile.ui.theme.EaselType

/** 发布前的本机确定性风控：不联网，不替代平台审核。 */
@Composable
fun EaselRiskScreen(onBack: () -> Unit) {
    val c = EaselTheme.colors
    var text by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<RiskReport?>(null) }

    SubPage(title = "发布前风控", onBack = onBack) {
        Text(
            "检查标题、正文、字幕或脚本里明显的凭据、隐私、内网地址、绝对化宣传和医疗金融高风险措辞。证据会脱敏。",
            Modifier.padding(bottom = 12.dp),
            style = EaselType.Hint,
            color = c.ink3,
        )
        GroupLabel("待检查文本")
        EaselCard(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = EaselType.Body,
                    minLines = 8,
                    maxLines = 30,
                    placeholder = { Text("粘贴准备发布的标题和正文…", style = EaselType.Caption) },
                )
                OutlinedTextField(
                    value = platform,
                    onValueChange = { platform = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("平台（可选）") },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { report = EaselRiskScanner.scan(text, platform) },
                        enabled = text.isNotBlank(),
                    ) { Text("开始扫描", style = EaselType.BodyStrong) }
                }
            }
        }

        report?.let { result ->
            GroupLabel("扫描结果")
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LevelPill(result.level, Modifier.weight(1f))
                StatBox("阻断", result.blockCount.toString(), Modifier.weight(1f))
                StatBox("提醒", result.warnCount.toString(), Modifier.weight(1f))
            }
            if (result.findings.isEmpty()) {
                EaselCard(Modifier.padding(bottom = 10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("没有命中本机规则。", style = EaselType.BodyStrong, color = c.ok)
                        Text("这不代表平台一定通过，也不代表查重通过；发布前仍要人工核对事实、授权和素材。", style = EaselType.Caption, color = c.ink3)
                    }
                }
            } else {
                result.findings.forEach { FindingCard(it) }
            }
            Text(
                "规则只做确定性文本匹配。截图、图片里的小字、隐含承诺和平台最新规则不在本机检查范围内。",
                Modifier.padding(top = 4.dp, bottom = 12.dp),
                style = EaselType.Hint,
                color = c.ink3,
            )
        }
    }
}

@Composable
private fun LevelPill(level: RiskLevel, modifier: Modifier = Modifier) {
    val c = EaselTheme.colors
    val (label, color, soft) = when (level) {
        RiskLevel.HIGH -> Triple("高风险", c.bad, c.badSoft)
        RiskLevel.MEDIUM -> Triple("需复核", c.accent, c.accentSoft)
        RiskLevel.LOW -> Triple("低风险", c.ok, c.okSoft)
    }
    Text(
        label,
        modifier.background(soft, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        style = EaselType.BodyStrong,
        color = color,
    )
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    val c = EaselTheme.colors
    Column(modifier.background(c.surface, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value, style = EaselType.BodyStrong, color = c.ink)
        Text(label, style = EaselType.Hint, color = c.ink3)
    }
}

@Composable
private fun FindingCard(finding: RiskFinding) {
    val c = EaselTheme.colors
    val color = when (finding.level) {
        RiskLevel.HIGH -> c.bad
        RiskLevel.MEDIUM -> c.accent
        RiskLevel.LOW -> c.ok
    }
    EaselCard(Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(findingLabel(finding.code), Modifier.weight(1f), style = EaselType.BodyStrong, color = color)
                Text(finding.level.name.lowercase(), style = EaselType.Hint, color = color)
            }
            Text("证据：${finding.evidence}", style = EaselType.Caption, color = c.ink2)
            Text(finding.suggestion, style = EaselType.Hint, color = c.ink3)
        }
    }
}

private fun findingLabel(code: String): String = when (code) {
    "secret" -> "疑似密钥或令牌"
    "private_network" -> "内网或本机地址"
    "private_path" -> "本机私有路径"
    "identity_number" -> "身份证号"
    "phone_number" -> "手机号"
    "absolute_claim" -> "绝对化宣传"
    "medical_finance" -> "医疗或金融承诺"
    else -> code
}
