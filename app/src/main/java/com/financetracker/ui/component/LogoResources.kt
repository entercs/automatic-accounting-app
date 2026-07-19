package com.financetracker.ui.component

import com.financetracker.R

/** Returns drawable resource ID for a payment account, or null for emoji fallback */
fun getAccountLogoRes(type: String, accountName: String = ""): Int? = when (type) {
    "wechat" -> R.drawable.ic_wechat
    "alipay" -> R.drawable.ic_alipay
    "jd" -> R.drawable.ic_jd
    "bank" -> getBankLogoRes(accountName)
    else -> R.drawable.ic_currency // custom payment method
}

/** Returns drawable resource ID for a bank name, or UnionPay fallback */
private fun getBankLogoRes(name: String): Int = when {
    "工商" in name || "ICBC" in name.uppercase() -> R.drawable.ic_icbc
    "建设" in name || "CCB" in name.uppercase() -> R.drawable.ic_ccb
    "农业" in name || "ABC" in name.uppercase() -> R.drawable.ic_abc
    "中国银行" in name || "中行" in name || "BOC" in name.uppercase() -> R.drawable.ic_boc
    "招商" in name || "CMB" in name.uppercase() -> R.drawable.ic_cmb
    "交通" in name || "BCM" in name.uppercase() -> R.drawable.ic_bcm
    "邮储" in name || "PSBC" in name.uppercase() -> R.drawable.ic_psbc
    else -> R.drawable.ic_unionpay
}

/** Returns drawable resource ID for a category by name, or null for emoji fallback */
fun getCategoryLogoRes(categoryName: String): Int? = when {
    "餐饮" in categoryName -> R.drawable.ic_cat_dining
    "购物" in categoryName -> R.drawable.ic_cat_shopping
    "交通" in categoryName -> R.drawable.ic_cat_transport
    "住房" in categoryName || "物业" in categoryName -> R.drawable.ic_cat_housing
    "医疗" in categoryName || "健康" in categoryName -> R.drawable.ic_cat_medical
    "娱乐" in categoryName -> R.drawable.ic_cat_entertainment
    "通讯" in categoryName || "网络" in categoryName -> R.drawable.ic_cat_communication
    // 1.1.1: 收入类分类统一走专用 drawable,不再回退到 emoji
    "工资" in categoryName -> R.drawable.ic_income_salary
    "退款" in categoryName || "报销" in categoryName -> R.drawable.ic_income_refund
    "其他收入" in categoryName -> R.drawable.ic_income_other
    "其他支出" in categoryName -> R.drawable.ic_cat_other
    else -> null
}

/** Returns drawable resource ID for the transfer icon, or null for emoji fallback */
fun getTransferLogoRes(): Int? = R.drawable.ic_transfer
