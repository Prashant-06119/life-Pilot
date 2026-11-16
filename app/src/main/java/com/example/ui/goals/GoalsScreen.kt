package com.example.ui.goals

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.DateUtils
import com.example.domain.model.CareerGoal
import com.example.domain.model.GoalMilestone
import com.example.domain.model.Priority
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.LifePilotWorkspace
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(
    workspace: LifePilotWorkspace,
    onCreateGoal: (title: String, targetRole: String, targetDateEpochDay: Long, priority: String) -> Unit,
    onDeleteGoal: (CareerGoal, List<GoalMilestone>) -> Unit,
    onAddMilestone: (goalId: String, title: String, description: String, orderIndex: Int, targetDateEpochDay: Long) -> Unit,
    onToggleMilestone: (GoalMilestone) -> Unit,
    onOpenAiPlanner: () -> Unit
) {
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var milestoneTargetGoal by remember { mutableStateOf<CareerGoal?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateGoalDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_goal_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Career Goal")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("goals_screen"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Career Roadmaps",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Long-term career goals broken into ordered milestones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onOpenAiPlanner) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("AI Roadmap")
                    }
                }
            }

            if (workspace.goalProgress.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Define Your Career North Star",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Create your first career goal (e.g., Summer Internship or Campus Placement) or let AI Planner draft milestones for you.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { showCreateGoalDialog = true }) {
                                    Text("Create Manually")
                                }
                                Button(onClick = onOpenAiPlanner) {
                                    Text("Generate with AI")
                                }
                            }
                        }
                    }
                }
            } else {
                items(workspace.goalProgress, key = { it.goal.id }) { summary ->
                    val goal = summary.goal
                    val goalMilestones = workspace.milestones.filter { it.goalId == goal.id }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${goal.priority} · ${goal.targetRole.ifBlank { "Career Track" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = goal.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Target: ${DateUtils.formatEpochDayShort(goal.targetDateEpochDay)} · ${(summary.progressFraction * 100).roundToInt()}% complete",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteGoal(goal, workspace.milestones) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete ${goal.title}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { summary.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            if (goalMilestones.isEmpty()) {
                                Text(
                                    text = "No milestones yet — add checkpoints to track progress.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    goalMilestones.forEach { ms ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(onClick = { onToggleMilestone(ms) }) {
                                                    Icon(
                                                        imageVector = if (ms.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                        contentDescription = "Toggle ${ms.title}",
                                                        tint = if (ms.isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = ms.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (ms.description.isNotBlank()) {
                                                        Text(
                                                            text = ms.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = { milestoneTargetGoal = goal },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Add Milestone")
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }

    if (showCreateGoalDialog) {
        var title by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(workspace.profile?.targetCareerRole ?: "Software Engineer") }
        var weeks by remember { mutableIntStateOf(8) }
        var priority by remember { mutableStateOf(Priority.HIGH) }

        AlertDialog(
            onDismissRequest = { showCreateGoalDialog = false },
            title = { Text("New Career Goal") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Goal Title") },
                        placeholder = { Text("e.g., Crack SDE Internship") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("goal_title_input")
                    )
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("Target Role") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Timeline: $weeks weeks", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 8, 12, 24).forEach { w ->
                            FilterChip(
                                selected = weeks == w,
                                onClick = { weeks = w },
                                label = { Text("${w}w") }
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDay = DateUtils.todayEpochDay() + (weeks * 7L)
                        onCreateGoal(title, role, targetDay, priority.name)
                        showCreateGoalDialog = false
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag("save_goal_button")
                ) {
                    Text("Save Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGoalDialog = false }) { Text("Cancel") }
            }
        )
    }

    milestoneTargetGoal?.let { targetGoal ->
        var msTitle by remember { mutableStateOf("") }
        var msDesc by remember { mutableStateOf("") }
        val existingCount = workspace.milestones.count { it.goalId == targetGoal.id }

        AlertDialog(
            onDismissRequest = { milestoneTargetGoal = null },
            title = { Text("Add Milestone") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = msTitle,
                        onValueChange = { msTitle = it },
                        label = { Text("Milestone Title") },
                        placeholder = { Text("e.g., Complete Graphs & DP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = msDesc,
                        onValueChange = { msDesc = it },
                        label = { Text("Success Criteria (Optional)") },
                        placeholder = { Text("e.g., Solve 25 curated problems") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDay = DateUtils.todayEpochDay() + ((existingCount + 1) * 7L)
                        onAddMilestone(targetGoal.id, msTitle, msDesc, existingCount, targetDay)
                        milestoneTargetGoal = null
                    },
                    enabled = msTitle.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { milestoneTargetGoal = null }) { Text("Cancel") }
            }
        )
    }
}
