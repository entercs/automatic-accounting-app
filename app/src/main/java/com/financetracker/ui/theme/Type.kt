package com.financetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 1.0.9 UI 规范: 5 级字号
//  28sp/600 大数字标题(月结余/净资产/详情金额)
//  18sp/600 页面主标题(模块标题)
//  16sp/600 金额数字(列表内收支)
//  14sp/400 正文(分类/商户名)
//  12sp/400 辅助(时间/占比/备注)
val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)
