package com.financetracker.notification

import com.financetracker.notification.parser.PaymentParsers
import com.financetracker.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class PaymentParsingTest {
    private val wx = "com.tencent.mm"
    private val ali = "com.eg.android.AlipayGphone"
    private val bank = "cmb.pb"
    private fun amount(pkg: String, title: String, text: String, expected: Double, type: TransactionType = TransactionType.EXPENSE) {
        val result = PaymentParsers.parse(pkg, title, text)
        assertNotNull("$title: $text", result)
        assertEquals(expected, result!!.amount, .001)
        assertEquals(type, result.transactionType)
    }
    @Test fun wechatRecipientLabelIsNotIncome() = amount(wx, "微信支付", "支付成功 收款方：星巴克 实付金额：32.50元", 32.50)
    @Test fun wechatReceiptIsIncome() = amount(wx, "微信支付", "收款到账 18.80元", 18.80, TransactionType.INCOME)
    @Test fun ordinaryChatIsNotAPayment() { assertNull(PaymentParsers.parse(wx, "小明", "支付成功100元")); assertFalse(PaymentParsers.isCandidate(wx, "小明", "今天吃饭20元")) }
    @Test fun unclaimedRedPacketIsNotIncome() { assertNull(PaymentParsers.parse(wx, "微信红包", "小明发来红包 50元")) }
    @Test fun claimedRedPacketIsIncome() = amount(wx, "微信红包", "你已领取红包5.20元，已存入零钱", 5.20, TransactionType.INCOME)
    @Test fun alipayGenericExpense() = amount(ali, "交易提醒", "你有一笔2.00元的支出", 2.0)
    @Test fun alipayRefundOverridesPaymentWord() = amount(ali, "支付宝退款成功", "支付订单退款成功，退款金额：23.40元", 23.40, TransactionType.INCOME)
    @Test fun preferActualAmountOverCoupon() = amount(ali, "交易提醒", "优惠10.00元，花呗支付成功，实付金额：28.00元", 28.0)
    @Test fun commaSeparatedBankAmount() = amount(bank, "消费通知", "您消费人民币1,234.56元，余额9,999.00元", 1234.56)
    @Test fun bankBalanceAloneIsNotTransaction() { assertNull(PaymentParsers.parse(bank, "账户通知", "账户余额人民币123.45元")) }
    @Test fun creditCardConsumptionIsSupported() = amount(bank, "信用卡消费通知", "信用卡消费人民币20.00元，余额888.00元", 20.0)
    @Test fun bankIncome() = amount(bank, "入账通知", "您尾号2741的招行一卡通入账人民币3200.00元", 3200.0, TransactionType.INCOME)
    @Test fun pendingAndFailedPaymentsAreRejected() {
        for (text in listOf("支付失败 20元", "待支付 20元", "退款处理中 20元", "退款申请 20元")) assertNull(PaymentParsers.parse(ali, "交易提醒", text))
    }
    @Test fun marketingIsRejected() { assertNull(PaymentParsers.parse(ali, "国补上新", "可领补贴200元")); assertNull(PaymentParsers.parse("com.jingdong.app.mall", "PLUS会员到期", "价值240元生活服务免费兑换")) }
    @Test fun meituanPaidAndRefund() {
        amount("com.sankuai.meituan", "支付成功", "实付金额：19.90元 商家：小店", 19.90)
        amount("com.sankuai.meituan.takeoutnew", "退款成功", "退款金额：19.90元", 19.90, TransactionType.INCOME)
    }
    @Test fun meituanOrderReminderIsNotPayment() { assertNull(PaymentParsers.parse("com.sankuai.meituan", "订单提醒", "待付款 19.90元")) }
    @Test fun jdPrefersPaymentAmount() = amount("com.jingdong.app.mall", "订单支付成功", "原价100元，实付金额：80.00元", 80.0)
    @Test fun refundAmountTakesPriorityOverOriginalPayment() = amount(ali, "退款成功", "原支付金额：100.00元，退款金额：20.00元", 20.0, TransactionType.INCOME)
    @Test fun actualAmountTakesPriorityOverOtherPaymentLabels() = amount(ali, "交易提醒", "花呗支付成功，支付金额100.00元，实付金额80.00元", 80.0)
    @Test fun balanceBeforeGenericExpenseIsIgnored() = amount(ali, "交易提醒", "余额100.00元，你有一笔20.00元的支出", 20.0)
    @Test fun foreignCurrencyIsKeptForManualReview() { assertNull(PaymentParsers.parse(ali, "交易提醒", "支付成功，支付金额100.00美元")) }
    @Test fun unknownSourceAndZeroAreRejected() { assertNull(PaymentParsers.parse("example.unknown", "支付成功", "20元")); assertNull(PaymentParsers.parse(ali, "交易提醒", "你有一笔0.00元的支出")) }
    @Test fun cashierAndHistoryAreNotSuccessScreens() {
        val parser = ScreenContentParser()
        assertFalse(parser.isPaymentResultPage(listOf("京东收银台", "白条付款", "¥20.00", "输入密码")))
        assertFalse(parser.isPaymentResultPage(listOf("账单详情", "支付成功", "¥20.00")))
        assertTrue(parser.isPaymentResultPage(listOf("支付成功", "¥20.00", "返回商户")))
    }
}
