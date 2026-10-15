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
    private val paySuccessKeywords = listOf("支付成功", "付款成功", "交易成功", "支付完成", "扣款成功")

    // 1.1.1 修复: 支付结果页"返回/收银台"特征词 — 命中其一强烈暗示当前是真正的支付完成页
    // 没有这些特征词 + 出现"消息/通知/列表/会话"等列表页特征, 则拒绝(避免微信消息列表里的过往支付被误记)
    private val payResultPageSignals = listOf(
        "返回商户", "完成支付", "支付完成", "交易完成",
        "收银台", "安全键盘", "输入密码", "免密支付",
        "支付详情", "账单详情",
    )

    // 1.1.1 修复: 列表/消息页特征词 — 出现这些强烈暗示当前是消息列表/通知中心, 不是支付结果页
    private val listPageSignals = listOf(
        "消息", "通知", "全部消息", "消息列表", "通知中心",
        "会话", "聊天", "公众号", "服务通知",
    )

    // 收款成功关键词（支持"你已收款""已收款""收款成功""到账"等微信/支付宝收款页文案）
    private val receiveSuccessKeywords = listOf(
        "你已收款", "已收款", "收款成功", "收款到账", "退款成功", "退款到账",
    )

    // 微信红包已领取关键词（1.0.5 新增:微信红包没有通知,只能靠屏幕捕获）
    // 红包被拆开的页面会显示 "你已领取"/"已存入零钱"/"恭喜发财,大吉大利" 等
    private val redPacketKeywords = listOf(
        "已存入零钱", "已领取", "你已领取",
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

    /**
     * 1.1.1 修复: 判断当前屏幕是否为支付结果页(而非消息列表/通知中心)
     * 逻辑: 同时满足"有支付成功关键词" + "不是列表页",才算支付结果页
     * 解决「微信消息列表里翻到微信支付公众号的过往支付消息被误识别为新交易」bug
     */
    fun isPaymentResultPage(texts: List<String>): Boolean {
        if (!hasPayKeywords(texts)) return false
        if (texts.any { text -> listOf("待支付", "支付失败", "输入密码", "安全键盘", "账单详情", "交易记录").any { it in text } }) return false
        val combined = texts.joinToString(" ")
        val hasListSignal = listPageSignals.any { it in combined }
        val hasResultSignal = payResultPageSignals.any { it in combined }
        // 命中列表特征 + 没有结果页特征 → 是列表页,拒绝
        if (hasListSignal && !hasResultSignal) return false
        return true
    }

    fun parse(packageName: String, root: AccessibilityNodeInfo?): ParsedNotification? {
        if (root == null) return null
        val texts = collectTexts(root, maxNodes = 120)
        if (texts.isEmpty()) {
            Log.d(TAG, "屏幕解析: 无文本节点 ($packageName)")
            return null
        }

        // 1.0.8 修复: 取消"节点数 > 25 跳过"的硬限制。
        // 原限制会导致京东支付成功页(底部有大量推荐广告)被误判为非支付页,京东白条 ¥25.75 识别不到。
        // 改为依赖 hasPayKeywords 关键词 + 后续金额验证,更准。
        // STRICT: must contain payment SUCCESS keywords (not just "支付")
        // 1.1.1 修复: 进一步收紧 — 必须同时不是"消息列表/通知中心"页(微信消息列表里的过往支付会被误识别)
        if (!isPaymentResultPage(texts)) {
            Log.d(TAG, "屏幕解析: 不是支付结果页 ($packageName), 文本=${texts.take(8)}")
            return null
        }

        val combined = texts.joinToString(" ")

        val amount = com.financetracker.notification.parser.PaymentAmount.find(combined) ?: amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) {
            Log.d(TAG, "屏幕解析: 未找到金额 ($packageName), 文本摘要=${combined.take(200)}")
            return null
        }

        // STRICT: amount must be reasonable
        if (amount < 0.01 || amount > 100000) return null

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
            packageName.contains("meituan") || packageName == "com.dianping.v1" -> "meituan"
            else -> return null
        }

        return ParsedNotification(amount, merchant, accountType, type, System.currentTimeMillis())
    }

    private fun collectTexts(node: AccessibilityNodeInfo, maxNodes: Int): List<String> {
        val result = mutableListOf<String>()
        collectRecursive(node, result, maxNodes, 0)
        return result
    }

    private fun collectRecursive(node: AccessibilityNodeInfo?, result: MutableList<String>, max: Int, depth: Int) {
        if (node == null || result.size >= max || depth > 20) return
        node.text?.toString()?.trim()?.takeIf { it.length in 2..80 }?.let { result.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.length in 2..80 }?.let { result.add(it) }
        for (i in 0 until node.childCount) {
            if (result.size >= max) break
            collectRecursive(node.getChild(i), result, max, depth + 1)
        }
    }
}
