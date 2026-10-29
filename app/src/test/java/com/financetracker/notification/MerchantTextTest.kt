package com.financetracker.notification

import com.financetracker.notification.parser.MerchantText
import com.financetracker.notification.parser.PaymentParsers
import org.junit.Assert.*
import org.junit.Test

class MerchantTextTest {
    @Test fun douyinDisplayDropsUnimportantSuffixAndDecorativeBrackets() {
        assertEquals("抖音", MerchantText.clean("【抖音***】"))
        assertEquals("抖音", MerchantText.extract("支付成功 收款方：【抖音***】，实付金额16.90元"))
    }
    @Test fun paymentWordInsideBracketsDoesNotCutTheName() {
        val result = PaymentParsers.parse("com.tencent.mm", "微信支付", "向【抖音支付***】付款16.90元")!!
        assertEquals("抖音", result.merchant)
        assertEquals(16.90, result.amount, .001)
    }
    @Test fun alipayBracketedPayeeAlsoSurvivesInternalPaymentWord() {
        val result = PaymentParsers.parse("com.eg.android.AlipayGphone", "交易提醒", "你向【抖音支付***】支付16.90元")!!
        assertEquals("抖音", result.merchant)
    }
    @Test fun inlineMerchantKeepsTheWholeMaskedName() {
        assertEquals("抖音", MerchantText.extract("你在【抖音***】有一笔16.90元的支出"))
    }
    @Test fun commasInsideBalancedBracketsArePartOfMerchant() {
        assertEquals("商家A,旗舰店", MerchantText.extract("收款方：【商家A,旗舰店】，实付金额16.90元"))
    }
    @Test fun paymentFieldsAfterMerchantAreNotPartOfItsName() {
        assertEquals("星巴克", MerchantText.extract("收款方：星巴克 实付金额：16.90元"))
    }
    @Test fun mixedBracketsAndInternationalNamesArePreserved() {
        assertEquals("Store A (上海)", MerchantText.clean("【Store A (上海)】"))
        assertEquals("门店(抖音支付)", MerchantText.extract("给【门店(抖音支付)】付款16.90元"))
    }
    @Test fun bracketsWithinARealCompanyNameAreNotDeleted() {
        assertEquals("【品牌A】科技", MerchantText.clean("【品牌A】科技"))
    }
    @Test fun truncatedSourceCannotInventTheHiddenSuffix() {
        assertEquals("抖音", MerchantText.clean("【抖音"))
        assertNull(MerchantText.extract("给【抖音"))
    }
    @Test fun bankCardConsumptionUsesDouyinMerchantAndBankAccountIndependently() {
        val result = PaymentParsers.parse("cmb.pb", "消费通知", "您的银行卡消费人民币16.90元，商户：【抖音商城-订单123456】，余额100.00元")!!
        assertEquals("抖音", result.merchant)
        assertEquals("bank", result.accountType)
        assertEquals(com.financetracker.domain.model.TransactionType.EXPENSE, result.transactionType)
        assertEquals(16.90, result.amount, .001)
        assertNull(result.paymentMethod)
    }
    @Test fun bankPayeeLabelDoesNotTurnConsumptionIntoIncome() {
        val result = PaymentParsers.parse("cmb.pb", "消费通知", "银行卡消费人民币16.90元，收款方：【抖音电商-店铺订单】")!!
        assertEquals("抖音", result.merchant)
        assertEquals(com.financetracker.domain.model.TransactionType.EXPENSE, result.transactionType)
        assertEquals("bank", result.accountType)
    }
    @Test fun bankMerchantWithoutColonAndBracketedConsumptionAreSupported() {
        listOf("交易商户【抖音电商-订单123】，消费人民币16.90元", "您在【抖音商城-店铺】消费人民币16.90元").forEach { text ->
            assertEquals("抖音", PaymentParsers.parse("cmb.pb", "消费通知", text)!!.merchant)
        }
    }
    @Test fun unrelatedMerchantsAreNotReplacedByADouyinMentionElsewhere() {
        val result = PaymentParsers.parse("cmb.pb", "消费通知", "消费人民币16.90元，商户：【星巴克】，参加抖音活动")!!
        assertEquals("星巴克", result.merchant)
        assertEquals("某店(抖音渠道)", MerchantText.clean("【某店(抖音渠道)】"))
    }
    @Test fun refundDirectionIsPreservedDespiteTheOriginalConsumptionAmount() {
        val result = PaymentParsers.parse("cmb.pb", "退款到账", "原消费金额16.90元，退款金额3.00元，商户：【抖音商城-订单123】")!!
        assertEquals("抖音", result.merchant)
        assertEquals(com.financetracker.domain.model.TransactionType.INCOME, result.transactionType)
        assertEquals(3.0, result.amount, .001)
    }

}
