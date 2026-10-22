package com.financetracker.notification

/** Screen access is opt-in by supported app, never inferred from payment notifications. */
object ScreenCapturePolicy {
    data class App(val label: String, val packages: Set<String>)
    val apps = listOf(
        App("微信", setOf("com.tencent.mm")),
        App("支付宝", setOf("com.eg.android.AlipayGphone", "com.eg.android.AlipayGphoneRC")),
        App("京东", setOf("com.jingdong.app.mall")),
        App("美团", setOf("com.sankuai.meituan")),
        App("美团外卖", setOf("com.sankuai.meituan.takeoutnew")),
        App("大众点评", setOf("com.dianping.v1")),
    )
    val packages: Set<String> = apps.flatMap { it.packages }.toSet()
    fun allows(packageName: String) = packageName in packages
}
