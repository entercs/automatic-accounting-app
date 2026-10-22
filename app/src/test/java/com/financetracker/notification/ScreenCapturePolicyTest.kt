package com.financetracker.notification

import com.financetracker.notification.parser.BankCardParser
import org.junit.Assert.*
import org.junit.Test

class ScreenCapturePolicyTest {
    @Test fun bankApplicationsNeverEnterScreenCapture() { BankCardParser().supportedPackages.forEach { assertFalse(it, ScreenCapturePolicy.allows(it)) } }
    @Test fun unknownAndSystemAppsAreNotImplicitlyAllowed() { listOf("unknown.payment.app", "com.android.settings", "com.taobao.taobao", "com.tmall.wireless").forEach { assertFalse(ScreenCapturePolicy.allows(it)) } }
    @Test fun supportedPaymentScreensRemainAllowed() { assertTrue(ScreenCapturePolicy.allows("com.tencent.mm")); assertTrue(ScreenCapturePolicy.allows("com.eg.android.AlipayGphone")); assertTrue(ScreenCapturePolicy.allows("com.jingdong.app.mall")); assertTrue(ScreenCapturePolicy.allows("com.sankuai.meituan.takeoutnew")) }
}
