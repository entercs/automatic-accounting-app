package com.financetracker.notification

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.financetracker.notification.parser.PaymentParsers
import kotlinx.coroutines.*

/** Optional screen supplement. The notification listener remains usable when accessibility stops. */
class FinanceNotificationService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenParser = ScreenContentParser()
    private var capture: Job? = null
    private var lastCapture = 0L
    private var lastContent = ""
    companion object {
        private var active = java.lang.ref.WeakReference<FinanceNotificationService>(null)
        fun disableScreenRecognition(): Boolean {
            val service = active.get() ?: return false
            service.disableSelf()
            AccessibilityStateHolder.setConnected(false)
            return true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply { packageNames = ScreenCapturePolicy.packages.toTypedArray() }
        active = java.lang.ref.WeakReference(this)
        AccessibilityStateHolder.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (!ScreenCapturePolicy.allows(pkg)) return
        try {
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                // Legacy fallback for existing users who have not granted notification access yet.
                if (!PaymentMonitor.status.value.connected) (event.parcelableData as? Notification)?.let {
                    AccountingEngine.notification(pkg, it, null)
                }
                return
            }
            if (!ScreenCapturePolicy.allows(pkg) || capture?.isActive == true || System.currentTimeMillis() - lastCapture < 1500) return
            capture = scope.launch {
                try {
                    delay(150)
                    val root = rootInActiveWindow ?: return@launch
                    // Window changes may race the event; never inspect a different app's current window.
                    if (root.packageName?.toString() != pkg) return@launch
                    val texts = mutableListOf<String>()
                    collect(root, texts, 0)
                    val content = texts.joinToString(" | ")
                    lastCapture = System.currentTimeMillis()
                    if (content == lastContent) return@launch
                    lastContent = content
                    AccountingEngine.screen(pkg, content, screenParser.parse(pkg, root))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.e("Accounting", "Screen capture failed", e) }
            }
        } catch (e: Exception) { Log.e("Accounting", "Accessibility event failed", e) }
    }

    private fun collect(node: AccessibilityNodeInfo, texts: MutableList<String>, depth: Int) {
        if (texts.size >= 60 || depth > 20) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        for (index in 0 until node.childCount) {
            if (texts.size >= 60) break
            node.getChild(index)?.let { collect(it, texts, depth + 1) }
        }
    }

    override fun onInterrupt() { /* System interruption does not mean the service was unbound. */ }
    override fun onUnbind(intent: Intent?): Boolean { AccessibilityStateHolder.setConnected(false); return super.onUnbind(intent) }
    override fun onDestroy() {
        if (active.get() === this) active.clear()
        AccessibilityStateHolder.setConnected(false)
        scope.cancel()
        super.onDestroy()
    }
}
