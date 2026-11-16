package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ThemePreferences
import com.example.core.configureAppCheck
import com.example.ui.navigation.LifePilotApp
import com.example.ui.theme.LifePilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App Check MUST be configured before enableEdgeToEdge, Compose UI, ViewModels, or Firebase calls.
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val themePreferences = remember { ThemePreferences(applicationContext) }
            val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
            LifePilotTheme(darkTheme = isDarkMode) {
                LifePilotApp(
                    isDarkMode = isDarkMode,
                    themePreferences = themePreferences
                )
            }
        }
    }
}
