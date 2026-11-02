package com.easel.mobile.ui.components

import com.easel.mobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easel.mobile.ui.theme.EaselIcons
import com.easel.mobile.ui.theme.EaselSpace

/**
 * 子页面的骨架:页面底 + 带返回的顶栏 + 一列可滚动的内容(左右留 [EaselSpace.Page],避开导航栏)。
 * 内容里直接放 [GroupLabel] / [GroupCard] / [FooterNote]。
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, EaselIcons.Back, onBack, stringResource(R.string.back))
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = EaselSpace.Page),
            ) {
                Spacer(Modifier.height(EaselSpace.S4))
                content()
                Spacer(Modifier.height(EaselSpace.S24))
            }
        }
    }
}
