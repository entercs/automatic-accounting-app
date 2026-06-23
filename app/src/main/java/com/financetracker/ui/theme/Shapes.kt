package com.financetracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 1.0.9 UI 规范: 全局圆角
//  16dp 卡片/按钮/输入框/弹窗
//  12dp 小元素(标签/小型按钮/图标底衬)
val CardLarge: RoundedCornerShape = RoundedCornerShape(16.dp)
val CardSmall: RoundedCornerShape = RoundedCornerShape(12.dp)

/** 1.0.9 UI 规范: 通用阴影 `0 2px 8px rgba(0,0,0,0.04)` */
fun Modifier.softCardShadow(
    elevation: Dp = 1.dp,
    shape: RoundedCornerShape = CardLarge,
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = Color.Black.copy(alpha = 0.04f),
    spotColor = Color.Black.copy(alpha = 0.04f),
    clip = false,
)

/** 1.0.9 UI 规范: 顶部渐变头 `rgba(115,201,145,0.15)` → `#FFFFFF` (净资产/月度结余专用) */
val HeaderGradient: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0x2673C991), // 0x26 ≈ 15% alpha,薄荷绿淡起
        Color.White,       // 底部纯白
    ),
)

/** 1.0.9 UI 规范: 规范要求"全局页边距 20px / 卡片内边距 16px / 模块纵向间距 24px" */
val PageHorizontalPadding: Dp = 20.dp
val CardInnerPadding: Dp = 16.dp
val SectionSpacing: Dp = 24.dp

/** 1.0.9 UI 规范: 列表行高 64dp */
val ListItemHeight: Dp = 64.dp

/** 1.0.9 UI 规范: Tab 选中态下划线 2dp */
val TabIndicatorHeight: Dp = 2.dp
