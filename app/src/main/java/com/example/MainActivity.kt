package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ThemePreferences
import com.example.core.configureAppCheck
import com.example.ui.navigation.LifePilotApp
import com.example.ui.theme.LifePilotTheme

class MainActivity : ComponentActivity() {

    private val openFocusTabSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App Check MUST be configured before enableEdgeToEdge, Compose UI, ViewModels, or Firebase calls.
        configureAppCheck(this, intent)
        handleFocusNavigationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themePreferences = remember { ThemePreferences(applicationContext) }
            val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
            LifePilotTheme(darkTheme = isDarkMode) {
                LifePilotApp(
                    isDarkMode = isDarkMode,
                    themePreferences = themePreferences,
                    openFocusTabSignal = openFocusTabSignal.intValue
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFocusNavigationIntent(intent)
    }

    private fun handleFocusNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_FOCUS_TAB, false) == true) {
            openFocusTabSignal.intValue += 1
            intent.removeExtra(EXTRA_OPEN_FOCUS_TAB)
        }
    }

    companion object {
        const val EXTRA_OPEN_FOCUS_TAB = "extra_open_focus_tab"
    }
}
