package com.financetracker.notification

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局无障碍服务状态持有者
 *
 * 解决问题: 用户在系统无障碍设置里切换"自动记账"无障碍服务后,
 * 我们 APP 内相关 UI 能实时反映状态变化,无需手动点"刷新"按钮
 *
 * 实现: 在 [FinanceTrackerApp.onCreate] 注册 [AccessibilityManager.addAccessibilityStateChangeListener],
 * 状态变化时重新查询并更新 [isEnabled]
 */
object AccessibilityStateHolder {

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var manager: AccessibilityManager? = null

    /** 应用启动时调用一次,注册全局监听器 */
    fun initialize(context: Context) {
        if (manager != null) return // 幂等保护,避免重复注册
        val am = context.applicationContext
            .getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        manager = am
        // 立即查一次当前状态
        refresh(am)
        // 注册全局监听器
        am.addAccessibilityStateChangeListener { _ ->
            // 系统无障碍状态有变化,重新查询并更新
            refresh(am)
        }
    }

    /** 主动重新查询(供用户在 APP 内点"刷新"按钮时调用) */
    fun refresh() {
        manager?.let { refresh(it) }
    }

    private fun refresh(am: AccessibilityManager) {
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val ourServiceEnabled = enabledServices.any { it.id?.contains("FinanceNotification") == true }
        _isEnabled.value = ourServiceEnabled
    }
}
