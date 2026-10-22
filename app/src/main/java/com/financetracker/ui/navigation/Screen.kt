package com.financetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Filled.Home)
    data object Statistics : Screen("statistics", "统计", Icons.Filled.PieChart)
    data object Search : Screen("search", "搜索", Icons.Filled.Search)
    data object Settings : Screen("settings", "我的", Icons.Filled.Person)
    data object Preferences : Screen("preferences", "设置", Icons.Filled.Person)
    data object AddTransaction : Screen("add_transaction?editId={editId}", "记账", Icons.Filled.AddCircle)
    data object TransactionDetail : Screen("transaction_detail/{transactionId}", "详情", Icons.Filled.Home)

    companion object {
        val bottomNavItems = listOf(Home, Statistics, Settings)

        fun addTransactionRoute(editId: Long? = null, refundId: Long? = null): String {
            val params = mutableListOf<String>()
            editId?.let { params.add("editId=$it") }
            refundId?.let { params.add("refundId=$it") }
            return if (params.isEmpty()) "add_transaction" else "add_transaction?${params.joinToString("&")}"
        }

        fun detailRoute(transactionId: Long): String =
            "transaction_detail/$transactionId"
    }
}
