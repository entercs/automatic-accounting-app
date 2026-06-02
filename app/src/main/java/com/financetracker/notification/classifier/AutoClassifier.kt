package com.financetracker.notification.classifier

import com.financetracker.domain.model.TransactionType

data class CategoryRule(val categoryId: Long, val keywords: List<String>)

class AutoClassifier {

    // 支出分类
    private val expenseRules = listOf(
        CategoryRule(1, listOf("麦当劳", "肯德基", "汉堡王", "必胜客", "星巴克", "瑞幸",
            "喜茶", "奈雪", "蜜雪冰城", "茶颜悦色", "美团", "饿了么", "外卖", "餐厅",
            "饭店", "小吃", "超市", "便利店", "菜场", "水果", "面包", "蛋糕")),
        CategoryRule(2, listOf("淘宝", "京东", "拼多多", "唯品会", "苏宁", "天猫",
            "网易考拉", "当当", "得物", "闲鱼", "商场", "百货", "服装", "鞋")),
        CategoryRule(3, listOf("加油", "中石化", "中石油", "滴滴", "哈啰", "曹操出行",
            "地铁", "公交", "高铁", "火车", "机票", "航班", "停车场", "高速", "ETC",
            "北京一卡通", "上海公共交通卡", "羊城通", "深圳通", "公交卡", "交通卡",
            "一卡通", "出行", "打车", "首汽", "T3出行", "高德打车",
            "摩拜", "美团单车", "青桔", "哈啰单车")),
        CategoryRule(4, listOf("房租", "水电", "物业", "燃气", "暖气", "链家", "自如")),
        CategoryRule(5, listOf("医院", "药店", "挂号", "体检", "诊所", "医保")),
        CategoryRule(6, listOf("影院", "电影院", "KTV", "游戏", "视频会员", "旅游",
            "景点", "酒店", "网吧", "密室", "剧本杀")),
        CategoryRule(7, listOf("话费", "宽带", "流量", "联通", "移动", "电信", "手机充值")),
    )

    // 收入分类
    // 9 = 工资收入，10 = 退款报销，11 = 其他收入
    private val incomeRules = listOf(
        CategoryRule(9, listOf("工资", "薪资", "薪酬", "月薪", "奖金", "绩效",
            "招商银行", "招行", "入账", "代发")),
        CategoryRule(10, listOf("退款", "退货", "报销", "理赔")),
    )

    /** Returns the category ID that best matches the merchant name */
    fun classify(merchant: String, type: TransactionType = TransactionType.EXPENSE): Long {
        val rules = if (type == TransactionType.INCOME) incomeRules else expenseRules
        for (rule in rules) {
            for (keyword in rule.keywords) {
                if (keyword in merchant) {
                    return rule.categoryId
                }
            }
        }
        return if (type == TransactionType.INCOME) 11L else 8L
    }
}
