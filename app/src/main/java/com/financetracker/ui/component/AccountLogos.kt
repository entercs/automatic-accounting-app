package com.financetracker.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Payment account logo — uses real PNG image, no Canvas fallback needed */
@Composable
fun AccountLogo(type: String, accountName: String = "", size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val resId = getAccountLogoRes(type, accountName)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = accountName,
            modifier = modifier.size(size).clip(RoundedCornerShape(size / 5)),
            contentScale = ContentScale.Fit,
        )
    } else {
        // Emoji fallback
        Text(
            text = getAccountEmoji(type, accountName),
            fontSize = (size.value * 0.85f).sp,
            modifier = modifier.size(size),
        )
    }
}

/** Category icon — uses real PNG image if available, emoji otherwise */
@Composable
fun CategoryLogo(categoryName: String, categoryIcon: String = "", size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val resId = getCategoryLogoRes(categoryName)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = categoryName,
            modifier = modifier.size(size).clip(RoundedCornerShape(size / 5)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = categoryIcon.ifBlank { "📂" },
            fontSize = (size.value * 0.85f).sp,
            modifier = modifier.size(size),
        )
    }
}

/**
 * 1.1.1: 转账专用 logo — 用专门的 ic_transfer drawable,不再用 emoji 🔄
 */
@Composable
fun TransferLogo(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val resId = getTransferLogoRes()
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = "转账",
            modifier = modifier.size(size).clip(RoundedCornerShape(size / 5)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = "🔄",
            fontSize = (size.value * 0.85f).sp,
            modifier = modifier.size(size),
        )
    }
}

private fun getAccountEmoji(type: String, accountName: String = ""): String = when (type) {
    "wechat" -> "💬"
    "alipay" -> "🔵"
    "jd" -> "🛒"
    "bank" -> "🏦"
    else -> "💳"
}
