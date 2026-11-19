package com.example.blocker

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocusBlockerUnitTest {

    @Test
    fun packageEligibilityFilter_excludesSystemEmergencyAndSelfPackages() {
        val candidates = listOf(
            "com.aistudio.lifepilot.kqxwrm",
            "com.google.android.dialer",
            "com.android.settings",
            "com.android.emergency",
            "com.android.systemui",
            "com.custom.launcher",
            "com.instagram.android",
            "com.google.android.youtube",
            "com.instagram.android",
            "com.reddit.frontpage"
        )

        val eligible = PackageEligibilityFilter.filterEligiblePackageNames(
            candidatePackages = candidates,
            selfPackageName = "com.aistudio.lifepilot.kqxwrm",
            dynamicProtectedPackages = setOf("com.custom.launcher")
        )

        assertEquals(
            listOf("com.instagram.android", "com.google.android.youtube", "com.reddit.frontpage"),
            eligible
        )
    }

    @Test
    fun stateStore_lifecycleStartPauseResumeCompleteAndStaleExpiry() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = FocusBlockerStateStore.createForTesting(context, prefsName = "test_blocker_lifecycle")
        store.clearAll()

        store.updateUserPreferences(
            enabled = true,
            packages = listOf("com.instagram.android", "com.google.android.youtube", "com.android.settings")
        )

        // Settings was stripped safely
        assertEquals(setOf("com.instagram.android", "com.google.android.youtube"), store.currentState().blockedPackages)

        // Before starting a Focus session, nothing is blocked
        val t0 = 1_750_000_000_000L
        assertFalse(store.shouldBlockPackage("com.instagram.android", nowMillis = t0))

        // Activate for a 25m sprint
        val sessionEnd = t0 + 1_500_000L
        store.activateForSession("DSA Graph Practice", sessionEnd)
        assertTrue(store.shouldBlockPackage("com.instagram.android", nowMillis = t0 + 10_000L))
        assertTrue(store.shouldBlockPackage("com.google.android.youtube", nowMillis = t0 + 10_000L))
        assertFalse(store.shouldBlockPackage("com.slack.app", nowMillis = t0 + 10_000L))
        assertFalse(store.shouldBlockPackage("com.google.android.dialer", nowMillis = t0 + 10_000L))

        // Pause Focus session -> restrictions temporarily lifted
        store.pauseSession()
        assertFalse(store.shouldBlockPackage("com.instagram.android", nowMillis = t0 + 20_000L))

        // Resume Focus session -> restrictions restored
        store.resumeSession(sessionEnd + 10_000L)
        assertTrue(store.shouldBlockPackage("com.instagram.android", nowMillis = t0 + 30_000L))

        // Stale session past end timestamp -> automatically heals and unlocks
        assertFalse(store.shouldBlockPackage("com.instagram.android", nowMillis = sessionEnd + 20_000L))
        assertFalse(store.currentState().sessionActive)

        // Explicit release on cancel/complete also unlocks immediately
        store.activateForSession("System Design", t0 + 900_000L)
        assertTrue(store.shouldBlockPackage("com.instagram.android", nowMillis = t0 + 1_000L))
        store.releaseSession()
        assertFalse(store.shouldBlockPackage("com.instagram.android", nowMillis = t0 + 1_000L))
    }
}
