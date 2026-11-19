package com.example.blocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FocusBlockerRuntimeState(
    val blockingEnabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val sessionActive: Boolean = false,
    val sessionPaused: Boolean = false,
    val sessionEndEpochMillis: Long = 0L,
    val activeTaskTitle: String = "Deep Career Focus"
) {
    val isCurrentlyEnforcing: Boolean
        get() = blockingEnabled && sessionActive && !sessionPaused && blockedPackages.isNotEmpty()
}

class FocusBlockerStateStore private constructor(
    private val appContext: Context,
    private val prefs: SharedPreferences
) {
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<FocusBlockerRuntimeState> = _state.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = readState()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun readState(): FocusBlockerRuntimeState {
        val enabled = prefs.getBoolean(KEY_BLOCKING_ENABLED, false)
        val packages = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        val active = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        val paused = prefs.getBoolean(KEY_SESSION_PAUSED, false)
        val endMillis = prefs.getLong(KEY_SESSION_END_MILLIS, 0L)
        val taskTitle = prefs.getString(KEY_TASK_TITLE, null) ?: "Deep Career Focus"
        return FocusBlockerRuntimeState(
            blockingEnabled = enabled,
            blockedPackages = packages,
            sessionActive = active,
            sessionPaused = paused,
            sessionEndEpochMillis = endMillis,
            activeTaskTitle = taskTitle
        )
    }

    fun currentState(): FocusBlockerRuntimeState {
        val current = readState()
        _state.value = current
        return current
    }

    fun updateUserPreferences(
        enabled: Boolean,
        packages: Collection<String>
    ) {
        val dynamicProtected = InstalledAppDiscovery.discoverDynamicProtectedPackages(appContext)
        val safePackages = PackageEligibilityFilter.filterEligiblePackageNames(
            candidatePackages = packages,
            selfPackageName = appContext.packageName,
            dynamicProtectedPackages = dynamicProtected
        ).toSet()

        prefs.edit().apply {
            putBoolean(KEY_BLOCKING_ENABLED, enabled)
            putStringSet(KEY_BLOCKED_PACKAGES, safePackages)
            if (!enabled || safePackages.isEmpty()) {
                putBoolean(KEY_SESSION_ACTIVE, false)
                putBoolean(KEY_SESSION_PAUSED, false)
                putLong(KEY_SESSION_END_MILLIS, 0L)
            }
        }.apply()
        _state.value = readState()
    }

    fun activateForSession(
        taskTitle: String,
        sessionEndEpochMillis: Long
    ) {
        val current = readState()
        if (!current.blockingEnabled || current.blockedPackages.isEmpty()) {
            releaseSession()
            return
        }
        prefs.edit().apply {
            putBoolean(KEY_SESSION_ACTIVE, true)
            putBoolean(KEY_SESSION_PAUSED, false)
            putLong(KEY_SESSION_END_MILLIS, sessionEndEpochMillis)
            putString(KEY_TASK_TITLE, taskTitle.ifBlank { "Deep Career Focus" })
        }.apply()
        _state.value = readState()
    }

    fun pauseSession() {
        val current = readState()
        if (!current.sessionActive) return
        prefs.edit().putBoolean(KEY_SESSION_PAUSED, true).apply()
        _state.value = readState()
    }

    fun resumeSession(newEndEpochMillis: Long) {
        val current = readState()
        if (!current.blockingEnabled || current.blockedPackages.isEmpty() || !current.sessionActive) {
            return
        }
        prefs.edit().apply {
            putBoolean(KEY_SESSION_PAUSED, false)
            putLong(KEY_SESSION_END_MILLIS, newEndEpochMillis)
        }.apply()
        _state.value = readState()
    }

    fun releaseSession() {
        prefs.edit().apply {
            putBoolean(KEY_SESSION_ACTIVE, false)
            putBoolean(KEY_SESSION_PAUSED, false)
            putLong(KEY_SESSION_END_MILLIS, 0L)
        }.apply()
        _state.value = readState()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _state.value = readState()
    }

    fun shouldBlockPackage(
        packageName: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val pkg = packageName?.trim() ?: return false
        val current = readState()
        if (!current.blockingEnabled || !current.sessionActive || current.sessionPaused) {
            return false
        }
        if (current.sessionEndEpochMillis <= 0L || nowMillis >= current.sessionEndEpochMillis) {
            // Auto-heal stale session if timer elapsed or process died unexpectedly
            releaseSession()
            return false
        }
        val dynamicProtected = InstalledAppDiscovery.discoverDynamicProtectedPackages(appContext)
        if (PackageEligibilityFilter.isProtectedPackage(pkg, appContext.packageName, dynamicProtected)) {
            return false
        }
        return pkg in current.blockedPackages
    }

    companion object {
        private const val PREFS_NAME = "focus_blocker_state"
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_SESSION_PAUSED = "session_paused"
        private const val KEY_SESSION_END_MILLIS = "session_end_epoch_millis"
        private const val KEY_TASK_TITLE = "active_task_title"

        @Volatile
        private var instance: FocusBlockerStateStore? = null

        fun getInstance(context: Context): FocusBlockerStateStore {
            return instance ?: synchronized(this) {
                instance ?: FocusBlockerStateStore(
                    appContext = context.applicationContext,
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }

        fun createForTesting(context: Context, prefsName: String = PREFS_NAME): FocusBlockerStateStore {
            val appCtx = context.applicationContext
            return FocusBlockerStateStore(
                appContext = appCtx,
                prefs = appCtx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            )
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, FocusBlockerAccessibilityService::class.java)
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                val matchedManager = enabledList.any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == expectedComponent.packageName &&
                        serviceInfo.name == expectedComponent.className
                }
                if (matchedManager) return true
            }

            val enabledServicesSetting = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (_: Exception) {
                null
            } ?: return false

            val flatName = expectedComponent.flattenToString()
            val shortName = expectedComponent.flattenToShortString()
            return enabledServicesSetting.split(':').any { entry ->
                entry.equals(flatName, ignoreCase = true) || entry.equals(shortName, ignoreCase = true)
            }
        }
    }
}
