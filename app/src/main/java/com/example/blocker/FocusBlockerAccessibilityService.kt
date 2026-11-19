package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.ui.focus.FocusBlockOverlayActivity

/**
 * Privacy-preserving AccessibilityService for Focus Mode.
 * Only inspects foreground window package transitions ([AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED])
 * when a focus session is actively running with blocking enabled.
 * Never reads screen content (`canRetrieveWindowContent=false`), never captures keystrokes,
 * and never logs or uploads app usage data off-device.
 */
class FocusBlockerAccessibilityService : AccessibilityService() {

    private var lastInterceptedPkg: String? = null
    private var lastInterceptEpochMillis: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foregroundPkg = event.packageName?.toString()?.trim() ?: return

        val now = System.currentTimeMillis()
        val store = FocusBlockerStateStore.getInstance(applicationContext)
        if (!store.shouldBlockPackage(foregroundPkg, now)) return

        // Avoid launching overlapping overlays from rapid duplicate window state events
        if (foregroundPkg == lastInterceptedPkg && (now - lastInterceptEpochMillis) < DEBOUNCE_MILLIS) {
            return
        }
        lastInterceptedPkg = foregroundPkg
        lastInterceptEpochMillis = now

        val runtime = store.currentState()
        val overlayIntent = Intent(applicationContext, FocusBlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(FocusBlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, foregroundPkg)
            putExtra(FocusBlockOverlayActivity.EXTRA_TASK_TITLE, runtime.activeTaskTitle)
            putExtra(FocusBlockOverlayActivity.EXTRA_SESSION_END_MILLIS, runtime.sessionEndEpochMillis)
        }

        try {
            startActivity(overlayIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to launch restriction overlay directly; falling back to home.", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        // No persistent state retained in service memory
    }

    private companion object {
        const val TAG = "FocusBlockerSvc"
        const val DEBOUNCE_MILLIS = 900L
    }
}
