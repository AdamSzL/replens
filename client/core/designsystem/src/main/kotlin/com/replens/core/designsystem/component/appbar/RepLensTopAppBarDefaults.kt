package com.replens.core.designsystem.component.appbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun Modifier.topAppBarContainer(vertical: Dp = 8.dp): Modifier = this
    .fillMaxWidth()
    .statusBarsPadding()
    .padding(horizontal = 4.dp, vertical = vertical)
