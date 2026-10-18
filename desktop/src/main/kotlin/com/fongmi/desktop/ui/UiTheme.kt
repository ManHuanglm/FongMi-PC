package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 桌面端统一配色（与 HomeScreen/DetailScreen 既有取值一致） */
object UiTheme {
    val Bg = Color(0xFF10141A)
    val Panel = Color(0xFF161B24)
    val Chip = Color(0xFF232B38)
    val ChipBorder = Color(0xFF2E3745)
    val Accent = Color(0xFF3D8BFF)
    val TextMain = Color(0xFFE8ECF2)
    val TextSub = Color(0xFF98A2B0)
    val Error = Color(0xFFE06A6A)
}

/** 通用胶囊按钮 */
@Composable
fun ChipButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bgColor = if (selected) UiTheme.Accent else UiTheme.Chip
    val textColor = when {
        selected -> Color.White
        enabled -> UiTheme.TextMain
        else -> UiTheme.TextSub.copy(alpha = 0.5f)
    }
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = textColor, fontSize = 13.sp, maxLines = 1)
    }
}

/** 居中状态提示（加载中/错误/空数据共用） */
@Composable
fun CenterMessage(message: String, showSpinner: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSpinner) {
                CircularProgressIndicator(color = UiTheme.Accent)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = message,
                color = UiTheme.TextSub,
                fontSize = 14.sp,
                fontWeight = if (showSpinner) FontWeight.Normal else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
