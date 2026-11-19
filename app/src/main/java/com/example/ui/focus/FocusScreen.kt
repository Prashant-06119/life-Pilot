package com.example.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.blocker.FocusBlockerRuntimeState
import com.example.core.DateUtils
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.FocusTimerState
import com.example.ui.viewmodel.LifePilotWorkspace

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    workspace: LifePilotWorkspace,
    timerState: FocusTimerState,
    blockerRuntimeState: FocusBlockerRuntimeState,
    accessibilityGranted: Boolean,
    onRefreshAccessibilityStatus: () -> Unit,
    onToggleBlocking: (Boolean) -> Unit,
    onSaveBlockedPackages: (List<String>) -> Unit,
    onEmergencyDisableBlocking: () -> Unit,
    onSelectTask: (StudyTask?) -> Unit,
    onSetPresetMinutes: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onCompleteAndLog: () -> Unit
) {
    var showAppPickerDialog by remember { mutableStateOf(false) }
    var showBlockerSetupDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshAccessibilityStatus()
    }
    val actionableTasks = workspace.rankedTasks.filter {
        TaskStatus.fromString(it.status) != TaskStatus.COMPLETED
    }.take(6)

    val totalPlannedSec = (timerState.plannedMinutes * 60).coerceAtLeast(1)
    val progress = ((totalPlannedSec - timerState.remainingSeconds).toFloat() / totalPlannedSec).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("focus_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Focus Mode",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Every completed sprint logs directly to your career analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Countdown Ring Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = timerState.selectedTaskTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    val ringActive = MaterialTheme.colorScheme.secondary
                    val ringTrack = MaterialTheme.colorScheme.surfaceVariant
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(210.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 14.dp.toPx()
                            drawArc(
                                color = ringTrack,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = ringActive,
                                startAngle = -90f,
                                sweepAngle = 360f * (1f - progress),
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = DateUtils.formatDurationSeconds(timerState.remainingSeconds),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 42.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("focus_timer_readout")
                            )
                            Text(
                                text = if (timerState.isRunning) "FOCUSING" else "READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Duration Presets
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 50, 90).forEach { mins ->
                            FilterChip(
                                selected = timerState.plannedMinutes == mins,
                                onClick = { onSetPresetMinutes(mins) },
                                enabled = !timerState.isRunning,
                                label = { Text("${mins}m") }
                            )
                        }
                    }

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val canReset = timerState.isRunning ||
                            timerState.startedEpochMillis > 0L ||
                            timerState.remainingSeconds < timerState.plannedMinutes * 60
                        OutlinedButton(
                            onClick = onReset,
                            enabled = canReset,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("focus_reset_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset Timer")
                        }
                        Button(
                            onClick = { if (timerState.isRunning) onPause() else onStart() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(2f)
                                .height(50.dp)
                                .testTag("focus_start_pause_button")
                        ) {
                            Icon(
                                imageVector = if (timerState.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (timerState.isRunning) "Pause" else "Start Focus")
                        }
                        FilledTonalButton(
                            onClick = onCompleteAndLog,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("focus_log_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Complete & Log", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Log")
                        }
                    }
                }
            }
        }

        // Multi-App Focus Guard Card
        item {
            DistractionBlockerCard(
                blockerConfig = workspace.blockerConfig,
                runtimeState = blockerRuntimeState,
                timerState = timerState,
                accessibilityGranted = accessibilityGranted,
                onToggleBlocking = onToggleBlocking,
                onOpenAppPicker = { showAppPickerDialog = true },
                onOpenSetupDialog = { showBlockerSetupDialog = true },
                onEmergencyDisableForSession = onEmergencyDisableBlocking
            )
        }

        // Bind Task to Focus Session
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Attach Active Task",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = timerState.selectedTaskId.isEmpty(),
                        onClick = { onSelectTask(null) },
                        enabled = !timerState.isRunning,
                        label = { Text("Free Deep Work") }
                    )
                    actionableTasks.forEach { task ->
                        FilterChip(
                            selected = timerState.selectedTaskId == task.id,
                            onClick = { onSelectTask(task) },
                            enabled = !timerState.isRunning,
                            label = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }

        // Recent Focus Sessions
        item {
            Text(
                text = "Recent Focus Sessions (${workspace.metrics.totalFocusMinutes}m total)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            )
        }

        if (workspace.focusSessions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Complete your first focus sprint to start building momentum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(workspace.focusSessions.take(8), key = { it.id }) { session ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.taskTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${session.actualMinutes}m logged (target ${session.plannedMinutes}m)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (session.completed) "COMPLETED" else "LOGGED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }

    if (showAppPickerDialog) {
        BlockedAppsPickerDialog(
            initialSelectedPackages = workspace.blockerConfig.blockedPackages,
            onSaveSelection = { packages ->
                showAppPickerDialog = false
                onSaveBlockedPackages(packages)
            },
            onDismiss = { showAppPickerDialog = false }
        )
    }

    if (showBlockerSetupDialog) {
        BlockerSetupDialog(
            accessibilityGranted = accessibilityGranted,
            onRefreshStatus = onRefreshAccessibilityStatus,
            onDismiss = { showBlockerSetupDialog = false }
        )
    }
}
