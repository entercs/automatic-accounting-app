package com.financetracker.notification.parser

/** Extract complete names before payment verbs, respecting brackets and platform display names. */
object MerchantText {
    private val brackets = mapOf('【' to '】', '[' to ']', '（' to '）', '(' to ')', '「' to '」', '『' to '』', '《' to '》')
    private val actions = listOf("付款", "转账", "支付", "消费", "扣款")
    private val inline = Regex("你在(.+?)有一笔")
    private val label = Regex("(?:收款方|收款商户|交易商户|商户(?:名称)?|交易对方|商家)(?:[：:]\\s*|\\s*(?=[【\\[（(「『《]))")
    private val prefix = Regex("(?:转给|向|给|在(?=\\s*[【\\[（(「『《]))")

    fun extract(text: String): String? {
        inline.find(text)?.groupValues?.get(1)?.let { return clean(it).ifBlank { null } }
        label.find(text)?.let { match ->
            val value = readUntil(text.substring(match.range.last + 1), stopAtAction = false)
            if (value.isNotBlank()) return clean(value).ifBlank { null }
        }
        prefix.findAll(text).forEach { match ->
            val tail = text.substring(match.range.last + 1)
            val value = readUntil(tail, stopAtAction = true)
            // A prefix alone is not evidence: there must be an action outside any bracket.
            if (value.isNotBlank() && value.length < tail.length && actions.any { tail.substring(value.length).startsWith(it) }) {
                return clean(value).ifBlank { null }
            }
        }
        return null
    }

    private fun readUntil(text: String, stopAtAction: Boolean): String {
        val expected = ArrayDeque<Char>()
        for ((i, c) in text.withIndex()) {
            if (expected.isEmpty()) {
                val nextField = i > 0 && text[i - 1].isWhitespace() && listOf("实付金额", "支付金额", "交易金额", "付款方式", "支付方式", "扣款金额", "收款金额", "退款金额", "余额").any { text.startsWith(it, i) }
                if (c in "\n\r，,。；;|" || nextField || (stopAtAction && actions.any { text.startsWith(it, i) })) return text.substring(0, i)
            }
            brackets[c]?.let { expected.addLast(it) }
            if (expected.lastOrNull() == c) expected.removeLast()
        }
        return text
    }

    fun clean(raw: String): String {
        var value = raw.trim().trimEnd('，', ',', '。', '；', ';')
        // Remove a complete decorative wrapper before applying explicit platform display rules.
        while (value.length >= 2 && brackets[value.first()] == value.last() && wrapperClosesAtEnd(value)) {
            value = value.substring(1, value.lastIndex).trim()
        }
        // A genuinely truncated source cannot be reconstructed; avoid displaying a lone leading bracket.
        if (value.isNotEmpty() && value.first() in brackets && brackets[value.first()]?.let { it !in value } == true) value = value.drop(1).trim()
        // Merchant/platform and funding account are independent. Douyin details are not its payment method.
        if (value.trimStart { it in brackets }.trimStart().startsWith("抖音")) return "抖音"
        return value
    }

    private fun wrapperClosesAtEnd(value: String): Boolean {
        val expected = ArrayDeque<Char>()
        value.forEachIndexed { i, c ->
            brackets[c]?.let { expected.addLast(it) }
            if (expected.lastOrNull() == c) expected.removeLast()
            if (expected.isEmpty() && i < value.lastIndex) return false
        }
        return expected.isEmpty()
    }
}
