package com.financetracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.financetracker.domain.model.Banks

// Warm cream surfaces, sage accents and terracotta expenses.
// 品牌主色(薄荷绿)
val Mint500 = Color(0xFF607B61)
val Mint700 = Color(0xFF49664B)
// 支出色(砖红)
val Brick500 = Color(0xFFB85F48)
val Brick700 = Color(0xFF984B38)
// 暖橙(图表/辅助强调)
val Amber500 = Color(0xFFBA8B46)
// 雾霾蓝(购物分类/次要图表)
val Mist500 = Color(0xFF758C9B)

// 中性色(规范第二/三节)
val PageBg = Color(0xFFFAF6EE)       // 页面底色
val TextPrimary = Color(0xFF3B332B)  // 一级文字(核心数字/标题)
val TextSecondary = Color(0xFF776B5D) // 二级文字(正文/分类/商户)
val TextTertiary = Color(0xFF8B7C6B)  // 辅助文字(时间/备注/未选中Tab)
val Divider = Color(0xFFE9E1D4)       // 分割线/边框

// 账户品牌色(规范未覆盖,保留)
val WeChatGreen = Color(0xFF07C160)
val AlipayBlue = Color(0xFF1677FF)
val JDRed = Color(0xFFE3312C)
val BankOrange = Color(0xFFF5A623)

fun accountColorHex(type: String): String = when (type) {
    "wechat" -> "#07C160"
    "alipay" -> "#1677FF"
    "jd" -> "#E3312C"
    "bank" -> "#F5A623"
    else -> "#757575"
}

fun accountColor(type: String, accountName: String = ""): Color = when (type) {
    "wechat" -> WeChatGreen
    "alipay" -> AlipayBlue
    "jd" -> JDRed
    "bank" -> {
        val bank = Banks.all.firstOrNull { it.name in accountName }
        bank?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: BankOrange
    }
    else -> TextTertiary
}

fun accountIcon(type: String, accountName: String = ""): String = when (type) {
    "wechat" -> "💬"
    "alipay" -> "🔵"
    "jd" -> "🛒"
    "bank" -> {
        val bank = Banks.all.firstOrNull { it.name in accountName }
        bank?.icon ?: "🏦"
    }
    else -> "💳"
}

@Composable
fun AccountIconDisplay(type: String, accountName: String = "", size: Dp = 22.dp) {
    com.financetracker.ui.component.AccountLogo(type = type, accountName = accountName, size = size)
}

// 1.0.9 UI 规范: 图表专属配色(莫兰迪 5 色,支出分类环形图专用)
val PieChartColors = listOf(
    Mint500,       // 其他支出 #73C991
    Mist500,       // 购物消费 #5294E3
    Amber500,      // 餐饮日常 #FF9529
    Color(0xFFC98972),  // 汽车交通 #F25549
    Color(0xFF9C8DA5),  // 通讯网络 #A168C7
)
