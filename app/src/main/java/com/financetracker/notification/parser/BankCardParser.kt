package com.financetracker.notification.parser

import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType

class BankCardParser : NotificationParser {
    override val supportedPackages = listOf(
        "com.icbc",                  // 工商银行
        "com.chinamworld.bocmbci",   // 中国银行
        "com.android.bankabc",       // 农业银行
        "com.chinamworld.main",      // 建设银行
        "cmb.pb",                    // 招商银行 (新版)
        "com.cmbchina.ccs.pluto",    // 招商银行 (旧版/企业版)
        "com.cmbc.ccs",              // 招商银行 (信用卡)
        "com.cmbchina.pocket",       // 招商银行 (招行 App)
        "com.bankcomm.maidanba",     // 交通银行
        "com.psbc.mobilebank",       // 邮储银行
        "cn.gov.pbc.dcep",           // 数字人民币
    )

    // 收入关键词
    private val incomeKeywords = listOf("收款", "入账", "到账", "转入", "存入", "收入", "奖励", "红包", "退款", "提现到账")
    // 支出关键词
    private val expenseKeywords = listOf("消费", "支付", "扣款", "支出", "转出", "提现", "还款", "刷卡", "快捷支付")

    // 匹配 "¥12.50" 格式
    private val amountSymbolPattern = Regex("[¥￥](\\d+\\.?\\d*)")
    // 匹配 "12.50元" 格式
    private val amountYuanPattern = Regex("(\\d+\\.?\\d*)\\s*元")
    // 匹配 "人民币12.50元" 格式（招行/部分银行用此格式）
    private val amountRmbPattern = Regex("人民币\\s*(\\d+\\.?\\d*)")
    // 匹配尾号
    private val cardNoPattern = Regex("[尾号账户]+[为:：]?\\*?(\\d{4,6})")
    // 商户提取：在...消费/在...支付/在...扣款
    private val merchantAtPattern = Regex("在(.+?)(?:消费|支付|扣款|收款|入账)")
    // 商户提取：商户:xxx / 收款方:xxx
    private val merchantColonPattern = Regex("(?:商户|收款方|交易对方)[:：]\\s*(.+)")
    // 招行入账提取：尾号XXXX的招行一卡通入账
    private val merchantCmbPattern = Regex("尾号(\\d+)\\s*的\\s*(?:招行|招商)[^入]*?入账")
    // 招行入账文本格式："您尾号2741的招行一卡通入账人民币X元"  提取"尾号2741"作商户标签
    private val cmbAccountLabelPattern = Regex("尾号(\\d+)")
    // 日期提取
    private val datePattern = Regex("(\\d{1,4}[年/]\\d{1,2}[月/]\\d{1,2}[日]?\\s*\\d{1,2}:\\d{1,2})")

    // 非交易类标题（账户/营销/积分/额度）一律跳过
    private val nonTransactionTitles = listOf(
        "额度", "积分", "优惠券", "理财", "活动", "营销",
        "白条额度", "信用额度", "账单日", "还款提醒", "还款日",
        "推广", "广告", "公告", "服务通知"
    )

    override fun parse(packageName: String, title: String, text: String): ParsedNotification? {
        if (packageName !in supportedPackages) return null

        // 非交易类标题直接跳过
        if (nonTransactionTitles.any { title.contains(it) }) {
            return null
        }

        val combined = "$title $text"

        // 整体包含额度提升/积分兑换等营销关键词，跳过
        if (combined.contains("额度提升") || combined.contains("额度已提升") ||
            combined.contains("提额") || combined.contains("积分兑换") ||
            combined.contains("信用额度") || combined.contains("账单提醒")) {
            return null
        }

        if (!(incomeKeywords + expenseKeywords).any { it in combined }) return null

        // 提取金额
        var amount = PaymentAmount.find(combined) ?: amountSymbolPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        if (amount == null) {
            amount = amountYuanPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) {
            amount = amountRmbPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null || amount == 0.0) return null

        // 判断收入还是支出
        val isIncome = incomeKeywords.any { combined.contains(it) }
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // 提取商户
        val cardNo = cardNoPattern.find(combined)?.groupValues?.get(1)?.trim()
        // 1.0.7 修复: 招行入账通知中 merchantAtPattern 无法匹配"尾号2741的招行一卡通入账"(没有"在"字),
        // 用 cmbAccountLabelPattern 提取尾号作为招行入账的兜底商户标签
        val cmbMatch = cmbAccountLabelPattern.find(combined)
        val merchant = merchantColonPattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: merchantAtPattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: if (cmbMatch != null && combined.contains("入账") && (title.contains("招商") || title.contains("招行"))) {
                "${title}尾号${cmbMatch.groupValues[1]}"
            } else if (cardNo != null) {
                "${title}尾号${cardNo}"
            } else if (type == TransactionType.INCOME) {
                "${title}入账"
            } else {
                "银行卡消费"
            }

        // 提取日期（如果解析失败会用当前时间）
        val payTime = datePattern.find(combined)?.groupValues?.get(1)?.trim()?.let {
            try {
                parseChineseDate(it)
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        } ?: System.currentTimeMillis()

        return ParsedNotification(
            amount = amount,
            merchant = merchant,
            accountType = "bank",
            transactionType = type,
            payTime = payTime,
        )
    }

    private fun parseChineseDate(dateStr: String): Long {
        // 支持格式：2024年1月15日 14:30 或 2024/1/15 14:30
        val cleaned = dateStr.replace("年", "/").replace("月", "/").replace("日", "").trim()
        val parts = cleaned.split(" ", "/")
        if (parts.size >= 5) {
            val year = parts[0].toIntOrNull() ?: return System.currentTimeMillis()
            val month = parts[1].toIntOrNull() ?: return System.currentTimeMillis()
            val day = parts[2].toIntOrNull() ?: return System.currentTimeMillis()
            val timeParts = parts[4].split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month - 1, day, hour, minute, 0)
            return cal.timeInMillis
        }
        return System.currentTimeMillis()
    }
}
