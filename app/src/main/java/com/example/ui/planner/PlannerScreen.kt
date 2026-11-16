package com.example.ui.planner

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.DateUtils
import com.example.domain.engine.SchedulingEngine
import com.example.domain.model.BlockType
import com.example.domain.model.Priority
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.LifePilotWorkspace

private enum class TaskFilter(val label: String) {
    ACTIONABLE("Active"),
    TODAY("Today"),
    MISSED("Missed"),
    COMPLETED("Done")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(
    workspace: LifePilotWorkspace,
    onAddTask: (goalId: String, milestoneId: String, title: String, notes: String, epochDay: Long, minutes: Int, priority: String) -> Unit,
    onUpdateTaskStatus: (StudyTask, TaskStatus) -> Unit,
    onDeleteTask: (String) -> Unit,
    onAddTimetableBlock: (title: String, dayOfWeek: Int, startMin: Int, endMin: Int, blockType: String, location: String) -> Unit,
    onDeleteTimetableBlock: (String) -> Unit,
    onStartTaskFocus: (StudyTask) -> Unit,
    onOpenRecovery: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Smart Tasks, 1 = Study Timetable
    var taskFilter by remember { mutableStateOf(TaskFilter.ACTIONABLE) }
    var selectedDayOfWeek by remember { mutableIntStateOf(DateUtils.todayIsoDayOfWeek()) }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddBlockDialog by remember { mutableStateOf(false) }

    val today = DateUtils.todayEpochDay()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddTaskDialog = true else showAddBlockDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("planner_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (selectedTab == 0) "Add Task" else "Add Timetable Block"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("planner_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Planner & Schedule",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (workspace.metrics.missedTaskCount > 0) {
                        OutlinedButton(
                            onClick = onOpenRecovery,
                            modifier = Modifier.testTag("planner_recover_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Recover (${workspace.metrics.missedTaskCount})")
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("tab_smart_tasks")
                    ) {
                        Text("Smart Tasks")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("tab_timetable")
                    ) {
                        Text("Study Timetable")
                    }
                }
            }

            if (selectedTab == 0) {
                // SMART TASKS VIEW
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = 720.dp)
                ) {
                    items(TaskFilter.entries) { filter ->
                        FilterChip(
                            selected = taskFilter == filter,
                            onClick = { taskFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }

                val filteredTasks = remember(workspace.rankedTasks, taskFilter, today) {
                    workspace.rankedTasks.filter { task ->
                        val status = TaskStatus.fromString(task.status)
                        when (taskFilter) {
                            TaskFilter.ACTIONABLE -> status != TaskStatus.COMPLETED
                            TaskFilter.TODAY -> task.scheduledEpochDay == today
                            TaskFilter.MISSED -> status == TaskStatus.MISSED || (status.isActionable && task.scheduledEpochDay < today)
                            TaskFilter.COMPLETED -> status == TaskStatus.COMPLETED
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (filteredTasks.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "No tasks in ${taskFilter.label}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Tap + to schedule a study task linked to your career goal.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredTasks, key = { it.id }) { task ->
                            val status = TaskStatus.fromString(task.status)
                            val isOverdue = status == TaskStatus.MISSED || (status.isActionable && task.scheduledEpochDay < today)
                            val goalTitle = workspace.goals.firstOrNull { it.id == task.goalId }?.title

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val next = if (status == TaskStatus.COMPLETED) TaskStatus.TODO else TaskStatus.COMPLETED
                                                onUpdateTaskStatus(task, next)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (status == TaskStatus.COMPLETED) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                contentDescription = "Toggle task completion",
                                                tint = if (status == TaskStatus.COMPLETED) EmeraldSuccess else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    textDecoration = if (status == TaskStatus.COMPLETED) TextDecoration.LineThrough else TextDecoration.None
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    append(DateUtils.formatEpochDayShort(task.scheduledEpochDay))
                                                    append(" · ${task.estimatedMinutes}m · ${task.priority}")
                                                    if (!goalTitle.isNullOrBlank()) append(" · $goalTitle")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isOverdue) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (status != TaskStatus.COMPLETED) {
                                            IconButton(onClick = { onStartTaskFocus(task) }) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Focus on task",
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            if (status != TaskStatus.MISSED) {
                                                IconButton(onClick = { onUpdateTaskStatus(task, TaskStatus.MISSED) }) {
                                                    Icon(
                                                        imageVector = Icons.Filled.EventBusy,
                                                        contentDescription = "Mark task missed",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(onClick = { onDeleteTask(task.id) }) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete task",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (task.aiReason.isNotBlank()) {
                                        Text(
                                            text = task.aiReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(start = 48.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            } else {
                // STUDY TIMETABLE VIEW
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = 720.dp)
                ) {
                    items((1..7).toList()) { dow ->
                        FilterChip(
                            selected = selectedDayOfWeek == dow,
                            onClick = { selectedDayOfWeek = dow },
                            label = { Text(DateUtils.dayOfWeekLabel(dow)) }
                        )
                    }
                }

                val dayBlocks = workspace.timetable.filter { it.dayOfWeek.toInt() == selectedDayOfWeek }
                val openGaps = SchedulingEngine.computeOpenGapsForDay(selectedDayOfWeek, workspace.timetable)
                val openMinutes = openGaps.sumOf { it.durationMinutes }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                        ) {
                            Text(
                                text = "${DateUtils.dayOfWeekFullLabel(selectedDayOfWeek)}: ${dayBlocks.size} block(s) · ${openMinutes / 60}h ${openMinutes % 60}m open study window",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (dayBlocks.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "No blocks for ${DateUtils.dayOfWeekFullLabel(selectedDayOfWeek)}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Add your college classes, labs, or dedicated career-prep blocks so Life Pilot avoids collisions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(dayBlocks, key = { it.id }) { block ->
                            val type = BlockType.fromString(block.blockType)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (type.isFixedAcademic) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    }
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = block.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${DateUtils.formatMinuteOfDay(block.startMinuteOfDay.toInt())} – ${DateUtils.formatMinuteOfDay(block.endMinuteOfDay.toInt())} · ${type.label}" +
                                                if (block.location.isNotBlank()) " · ${block.location}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteTimetableBlock(block.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete block",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var dayOffset by remember { mutableIntStateOf(0) }
        var minutes by remember { mutableIntStateOf(45) }
        var priority by remember { mutableStateOf(Priority.HIGH) }
        var selectedGoalId by remember { mutableStateOf(workspace.goals.firstOrNull()?.id ?: "") }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("New Study Task") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        placeholder = { Text("e.g., Practice 3 Tree Problems") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Schedule For", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Today", 1 to "Tomorrow", 2 to "+2d", -1 to "Yesterday").forEach { (off, label) ->
                            FilterChip(
                                selected = dayOffset == off,
                                onClick = { dayOffset = off },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text("Estimated Duration", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 45, 60, 90).forEach { mins ->
                            FilterChip(
                                selected = minutes == mins,
                                onClick = { minutes = mins },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                    Text("Priority", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Priority.entries.forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p.label) }
                            )
                        }
                    }
                    if (workspace.goals.isNotEmpty()) {
                        Text("Link Career Goal", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedGoalId.isEmpty(),
                                onClick = { selectedGoalId = "" },
                                label = { Text("General") }
                            )
                            workspace.goals.forEach { goal ->
                                FilterChip(
                                    selected = selectedGoalId == goal.id,
                                    onClick = { selectedGoalId = goal.id },
                                    label = { Text(goal.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddTask(
                            selectedGoalId,
                            "",
                            title,
                            notes,
                            today + dayOffset,
                            minutes,
                            priority.name
                        )
                        showAddTaskDialog = false
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag("save_task_button")
                ) {
                    Text("Add Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddBlockDialog) {
        var blockTitle by remember { mutableStateOf("") }
        var location by remember { mutableStateOf("") }
        var blockType by remember { mutableStateOf(BlockType.CLASS) }
        var startHour by remember { mutableIntStateOf(9) }
        var durationHours by remember { mutableIntStateOf(1) }

        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            title = { Text("Add ${DateUtils.dayOfWeekFullLabel(selectedDayOfWeek)} Block") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = blockTitle,
                        onValueChange = { blockTitle = it },
                        label = { Text("Subject / Block Name") },
                        placeholder = { Text("e.g., Operating Systems") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("block_title_input")
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Room / Link (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Type", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlockType.entries.forEach { t ->
                            FilterChip(
                                selected = blockType == t,
                                onClick = { blockType = t },
                                label = { Text(t.label) }
                            )
                        }
                    }
                    Text("Start Time", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(8, 9, 10, 11, 14, 16, 18, 20).forEach { hr ->
                            FilterChip(
                                selected = startHour == hr,
                                onClick = { startHour = hr },
                                label = { Text(DateUtils.formatMinuteOfDay(hr * 60)) }
                            )
                        }
                    }
                    Text("Duration", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { dur ->
                            FilterChip(
                                selected = durationHours == dur,
                                onClick = { durationHours = dur },
                                label = { Text("${dur}h") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val startMin = startHour * 60
                        val endMin = ((startHour + durationHours) * 60).coerceAtMost(1440)
                        onAddTimetableBlock(
                            blockTitle,
                            selectedDayOfWeek,
                            startMin,
                            endMin,
                            blockType.name,
                            location
                        )
                        showAddBlockDialog = false
                    },
                    enabled = blockTitle.isNotBlank(),
                    modifier = Modifier.testTag("save_block_button")
                ) {
                    Text("Save Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockDialog = false }) { Text("Cancel") }
            }
        )
    }
}
