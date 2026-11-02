package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.easel.EaselProfileStore
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/** 账号画像编辑页。画像会作为每次创作任务的账号上下文。 */
@Composable
fun EaselProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EaselProfileStore(context) }
    var profile by rememberSaveable { mutableStateOf(store.read().ifBlank { EaselProfileStore.DEFAULT_PROFILE }) }
    var saved by remember { mutableStateOf(false) }

    SubPage(
        title = "账号画像",
        onBack = onBack,
        right = {
            TextButton(
                onClick = {
                    store.write(profile)
                    profile = store.read()
                    saved = true
                },
                enabled = profile.isNotBlank(),
            ) { Text(if (saved) "已保存" else "保存", style = MikaType.BodyStrong, color = MikaTheme.colors.accent) }
        },
    ) {
        Text(
            "写清账号定位、内容方向、风格、受众、平台和红线。Easel 会在每次创作任务开始时把这份画像交给 Agent。",
            Modifier.padding(bottom = 12.dp),
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )
        GroupLabel("画像内容")
        MikaCard(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = profile,
                    onValueChange = { profile = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MikaType.Body,
                    minLines = 18,
                    maxLines = 60,
                    placeholder = { Text("账号定位、目标读者、内容方向、语气、平台、长期记忆和红线…", style = MikaType.Caption) },
                )
            }
        }
        Text(
            "画像只保存在这台手机。修改后记得保存；保存成功会显示「已保存」。",
            style = MikaType.Hint,
            color = MikaTheme.colors.ink3,
        )
    }
}
