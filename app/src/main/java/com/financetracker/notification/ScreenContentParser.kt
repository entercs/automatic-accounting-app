package com.financetracker.notification

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType

class ScreenContentParser {

    companion object { private const val TAG = "FT" }

    private val amountPatterns = listOf(
        Regex("¥(\\d+\\.?\\d*)"),
        Regex("￥(\\d+\\.?\\d*)"),
        Regex("(\\d+\\.?\\d*)\\s*元"),
    )

    // 支出支付成功关键词
    private val paySuccessKeywords = listOf(
        "支付成功", "付款成功", "交易成功",
        "扫码成功",
    )

    // 收款成功关键词（支持"你已收款""已收款""收款成功""到账"等微信/支付宝收款页文案）
    private val receiveSuccessKeywords = listOf(
        "你已收款", "已收款", "收款成功", "到账",
    )

    // 微信红包已领取关键词（1.0.5 新增:微信红包没有通知,只能靠屏幕捕获）
    // 红包被拆开的页面会显示 "你已领取"/"已存入零钱"/"恭喜发财,大吉大利" 等
    private val redPacketKeywords = listOf(
        "已存入零钱", "存入零钱", "已到账", "已领取", "你已领取",
        "微信红包", "恭喜发财",
    )

    // Reject merchant names containing these garbage keywords
    private val garbageKeywords = listOf(
        "刷新", "搜索", "天气", "℃", "车险", "更多操作", "松开",
        "首页", "我的", "收付款", "余额", "理财",
        "消息", "通知", "设置", "返回", "确认", "取消",
    )

    fun hasPayKeywords(texts: List<String>): Boolean {
        return texts.any { text ->
            paySuccessKeywords.any { kw -> kw in text } ||
                receiveSuccessKeywords.any { kw -> kw in text } ||
                redPacketKeywords.any { kw -> kw in text }
        }
    }

    fun parse(packageName: String, root: AccessibilityNodeInfo?): ParsedNotification? {
        if (root == null) return null
        val texts = collectTexts(root, maxNodes = 120)
        if (texts.isEmpty()) {
            Log.d(TAG, "屏幕解析: 无文本节点 ($packageName)")
            return null
        }

        // Payment result screens are simple; complex screens = non-payment pages
        if (texts.size > 25) {
            Log.d(TAG, "屏幕解析: 节点过多(${texts.size})，疑似非支付页 ($packageName)")
            return null
        }

        // STRICT: must contain payment SUCCESS keywords (not just "支付")
        if (!hasPayKeywords(texts)) {
            Log.d(TAG, "屏幕解析: 未找到支付关键词 ($packageName), 文本=${texts.take(8)}")
            return null
        }

        val combined = texts.joinToString(" ")

        val amount = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) {
            Log.d(TAG, "屏幕解析: 未找到金额 ($packageName), 文本摘要=${combined.take(200)}")
            return null
        }

        // STRICT: amount must be reasonable
        if (amount <= 0.01 || amount > 100000) return null

        // 判断收入还是支出(1.0.5: 红包已领取/已存入零钱 也算收入)
        val isRedPacket = redPacketKeywords.any { kw -> texts.any { it.contains(kw) } }
        val isIncome = receiveSuccessKeywords.any { kw -> texts.any { it.contains(kw) } } || isRedPacket
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // Extract merchant — try known patterns
        var merchant = ""
        val merchantPatterns = listOf(
            Regex("收款方[:：]\\s*(.+)"),
            Regex("商户[:：]\\s*(.+)"),
            Regex("来自(.+?)的(?:收款|红包)"),
            Regex("(.+?)的收款"),
            Regex("(.+?)向你转账"),
            Regex("(.+?)已收到"),
            Regex("你在(.+?)有一笔"),
            Regex("向(.+?)(?:付款|支付|转账)"),
        )
        for (p in merchantPatterns) {
            val m = p.find(combined)?.groupValues?.get(1)?.trim()
            if (m != null && m.length in 2..20) {
                merchant = m
                break
            }
        }

        // Quality check: reject garbage merchants
        if (merchant.isNotBlank() && garbageKeywords.any { it in merchant }) {
            merchant = ""
        }

        // If no good merchant found, find lines with amount nearby
        if (merchant.isBlank()) {
            val amountIdx = combined.indexOf("${String.format("%.2f", amount)}")
            if (amountIdx > 0) {
                val before = combined.substring(0, amountIdx).trim()
                val candidate = before.takeLast(30).trim()
                if (candidate.isNotBlank() && candidate.length in 2..20 && garbageKeywords.none { it in candidate }) {
                    merchant = candidate
                }
            }
        }

        if (merchant.isBlank()) {
            merchant = when {
                packageName.contains("Alipay") -> if (type == TransactionType.INCOME) "支付宝收入" else "支付宝"
                packageName.contains("tencent") -> when {
                    isRedPacket -> "微信红包"
                    type == TransactionType.INCOME -> "微信收入"
                    else -> "微信"
                }
                else -> if (type == TransactionType.INCOME) "收款" else "支付"
            }
        }

        val accountType = when {
            packageName.contains("tencent.mm") -> "wechat"
            packageName.contains("Alipay") || packageName.contains("alipay") -> "alipay"
            packageName.contains("jingdong") -> "jd"
            else -> "bank"
        }

        return ParsedNotification(amount, merchant, accountType, type, System.currentTimeMillis())
    }

    private fun collectTexts(node: AccessibilityNodeInfo, maxNodes: Int): List<String> {
        val result = mutableListOf<String>()
        collectRecursive(node, result, maxNodes)
        return result
    }

    private fun collectRecursive(node: AccessibilityNodeInfo?, result: MutableList<String>, max: Int) {
        if (node == null || result.size >= max) return
        node.text?.toString()?.trim()?.takeIf { it.length in 2..80 }?.let { result.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.length in 2..80 }?.let { result.add(it) }
        for (i in 0 until node.childCount) {
            if (result.size >= max) break
            collectRecursive(node.getChild(i), result, max)
        }
    }
}
