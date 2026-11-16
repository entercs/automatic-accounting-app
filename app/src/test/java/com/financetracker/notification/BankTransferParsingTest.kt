package com.financetracker.notification

import com.financetracker.domain.model.TransactionType
import com.financetracker.domain.model.TransferPurpose
import com.financetracker.notification.parser.PaymentParsers
import org.junit.Assert.*
import org.junit.Test

class BankTransferParsingTest {
    private val bank = "cmb.pb"
    private val walletTitle = "数字人民币充钱包扣款"
    private val wallet = "您尾号2741的银行卡于11月16日18:26支出人民币490.54元，用于充值网商银行（支付宝）数字人民币钱包\n金额: 490.54"
    private val jdTitle = "快捷支付扣款"
    private val jd = "您账户2741于11月16日18:26在【网银在线-京东白条】发生快捷支付扣款，人民币248.66"

    @Test fun recognizesCmbWalletTopUpAndBaitiaoRepayment() {
        val topUp = PaymentParsers.parse(bank, walletTitle, wallet)!!
        val repayment = PaymentParsers.parse(bank, jdTitle, jd)!!
        assertEquals(TransactionType.TRANSFER, topUp.transactionType)
        assertEquals(TransactionType.TRANSFER, repayment.transactionType)
        assertEquals(490.54, topUp.amount, 0.0)
        assertEquals(248.66, repayment.amount, 0.0)
        assertEquals("2741", topUp.bankTransfer!!.cardTail)
        assertEquals("2741", repayment.bankTransfer!!.cardTail)
        assertEquals(TransferPurpose.WALLET_TOP_UP, topUp.bankTransfer!!.purpose)
        assertEquals(TransferPurpose.JD_REPAYMENT, repayment.bankTransfer!!.purpose)
        assertNotEquals(topUp.bankTransfer!!.bindingKey, repayment.bankTransfer!!.bindingKey)
    }

    @Test fun rejectsIncompleteAndFailedTransferTemplatesEvenWithAnotherAmount() {
        assertNull(PaymentParsers.parse(bank, walletTitle, wallet.replace("490.54元", "金额未知") + "余额1000元"))
        assertNull(PaymentParsers.parse(bank, walletTitle, "$wallet，充值失败"))
        assertNull(PaymentParsers.parse(bank, jdTitle, jd.replace("248.66", "248.666")))
    }

    @Test fun ordinaryPurchasesAndRefundsKeepTheirDirection() {
        val cardPurchase = jd.replace("网银在线-京东白条", "网银在线-京东商城")
        val payment = PaymentParsers.parse(bank, jdTitle, cardPurchase)!!
        val refund = PaymentParsers.parse(bank, "退款到账", "退款金额248.66元，商户：网银在线-京东白条")!!
        val jdPurchase = PaymentParsers.parse("com.jd.jrapp", "白条交易提醒", "【未分期】您于11月16日消费8.49元")!!
        val alipayPurchase = PaymentParsers.parse("com.eg.android.AlipayGphone", "交易提醒", "你在便利店有一笔32.50元支出")!!
        assertEquals(TransactionType.EXPENSE, payment.transactionType)
        assertEquals(TransactionType.INCOME, refund.transactionType)
        assertEquals(TransactionType.EXPENSE, jdPurchase.transactionType)
        assertEquals(TransactionType.EXPENSE, alipayPurchase.transactionType)
        assertNull(payment.bankTransfer)
    }

    @Test fun platformConfirmationsDoNotProduceIncomeOrExpense() {
        for ((pkg, title, body) in listOf(
            Triple("com.jd.jrapp", "白条还款成功", "您已还款248.66元"),
            Triple("cn.gov.pbc.dcep", "数字人民币钱包充值", "充值到账人民币490.54元"),
            Triple("com.eg.android.AlipayGphone", "数字人民币钱包充值成功", "充值金额490.54元"),
        )) {
            assertTrue(PaymentParsers.isCandidate(pkg, title, body))
            assertNotNull(PaymentParsers.transferConfirmationReason(pkg, title, body))
            assertNull(PaymentParsers.parse(pkg, title, body))
        }
    }
}
