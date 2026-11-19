package com.example.ui.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.blocker.FocusBlockerStateStore
import com.example.blocker.InstalledAppDiscovery
import com.example.core.DateUtils
import com.example.core.ThemePreferences
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LifePilotTheme
import kotlinx.coroutines.delay

class FocusBlockOverlayActivity : ComponentActivity() {

    private val blockedPkgState = mutableStateOf("")
    private val taskTitleState = mutableStateOf("Deep Career Focus")
    private val sessionEndState = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntentExtras(intent)

        setContent {
            val themePreferences = remember { ThemePreferences(applicationContext) }
            val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
            val appInfo = remember(blockedPkgState.value) {
                InstalledAppDiscovery.resolveAppMetadata(applicationContext, blockedPkgState.value)
            }
            val store = remember { FocusBlockerStateStore.getInstance(applicationContext) }

            LifePilotTheme(darkTheme = isDarkMode) {
                FocusRestrictionScreen(
                    blockedAppName = appInfo.appName,
                    blockedAppIcon = appInfo.icon?.let {
                        remember(blockedPkgState.value) {
                            it.toBitmap(width = 128, height = 128).asImageBitmap()
                        }
                    },
                    taskTitle = taskTitleState.value,
                    sessionEndEpochMillis = sessionEndState.value,
                    onReturnToFocus = { navigateBackToLifePilotFocus() },
                    onEmergencyDisable = {
                        store.releaseSession()
                        finish()
                    },
                    onSessionExpired = {
                        store.releaseSession()
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntentExtras(intent)
    }

    private fun readIntentExtras(intent: Intent?) {
        val storeState = FocusBlockerStateStore.getInstance(applicationContext).currentState()
        blockedPkgState.value = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        taskTitleState.value = intent?.getStringExtra(EXTRA_TASK_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: storeState.activeTaskTitle
        val endMillis = intent?.getLongExtra(EXTRA_SESSION_END_MILLIS, 0L) ?: 0L
        sessionEndState.value = if (endMillis > 0L) endMillis else storeState.sessionEndEpochMillis
    }

    private fun navigateBackToLifePilotFocus() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_FOCUS_TAB, true)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_SESSION_END_MILLIS = "extra_session_end_millis"
    }
}

@Composable
private fun FocusRestrictionScreen(
    blockedAppName: String,
    blockedAppIcon: androidx.compose.ui.graphics.ImageBitmap?,
    taskTitle: String,
    sessionEndEpochMillis: Long,
    onReturnToFocus: () -> Unit,
    onEmergencyDisable: () -> Unit,
    onSessionExpired: () -> Unit
) {
    BackHandler {
        onReturnToFocus()
    }

    var remainingSeconds by remember(sessionEndEpochMillis) {
        mutableIntStateOf(((sessionEndEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt())
    }
    var confirmEmergencyExit by remember { mutableStateOf(false) }

    LaunchedEffect(sessionEndEpochMillis) {
        while (true) {
            val left = ((sessionEndEpochMillis - System.currentTimeMillis() + 999L) / 1000L).toInt()
            if (left <= 0) {
                remainingSeconds = 0
                onSessionExpired()
                break
            }
            remainingSeconds = left
            delay(500L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "FOCUS GUARD ACTIVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (blockedAppIcon != null) {
                Image(
                    bitmap = blockedAppIcon,
                    contentDescription = "$blockedAppName icon",
                    modifier = Modifier.size(72.dp)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$blockedAppName is paused",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "You chose to block $blockedAppName during your Life Pilot focus sprint so you can finish what matters today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CURRENT FOCUS TASK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = taskTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = DateUtils.formatDurationSeconds(remainingSeconds),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("overlay_remaining_countdown")
                    )
                    Text(
                        text = "Remaining in sprint — unlocks automatically at 00:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = onReturnToFocus,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("overlay_return_focus_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Return to Life Pilot Focus",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!confirmEmergencyExit) {
                OutlinedButton(
                    onClick = { confirmEmergencyExit = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("overlay_emergency_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Need Urgent Access? Emergency Exit",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "End Distraction Blocking for This Sprint?",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Your selected app list stays saved for future sessions, and app access will resume immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { confirmEmergencyExit = false }) {
                                Text("Keep Blocking")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onEmergencyDisable,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.testTag("overlay_confirm_emergency_exit")
                            ) {
                                Text("Unlock Apps Now")
                            }
                        }
                    }
                }
            }
        }
    }
}
