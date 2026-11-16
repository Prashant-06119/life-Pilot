package com.example.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ActionState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    initialDisplayName: String,
    email: String,
    preferredDarkMode: Boolean,
    actionState: ActionState,
    onCompleteOnboarding: (
        displayName: String,
        degreeProgram: String,
        academicYear: String,
        targetCareerRole: String,
        dailyStudyHours: Double
    ) -> Unit
) {
    var displayName by remember { mutableStateOf(initialDisplayName.ifBlank { "" }) }
    var degreeProgram by remember { mutableStateOf("B.Tech Computer Science") }
    var academicYear by remember { mutableStateOf("3rd Year") }
    var targetRole by remember { mutableStateOf("Software Engineer") }
    var dailyHours by remember { mutableFloatStateOf(3.0f) }

    val yearOptions = listOf("1st Year", "2nd Year", "3rd Year", "Final Year", "Postgrad")
    val roleSuggestions = listOf(
        "Software Engineer",
        "Data Analyst",
        "Product Manager",
        "ML Engineer",
        "UI/UX Designer",
        "Backend Intern"
    )
    val isSubmitting = actionState is ActionState.Loading

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RocketLaunch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = "Set Up Your Flight Plan",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tell Life Pilot your academic context and daily prep capacity so schedules stay realistic.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your Name") },
                    placeholder = { Text("e.g., Aarav Sharma") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_name_input")
                )

                OutlinedTextField(
                    value = degreeProgram,
                    onValueChange = { degreeProgram = it },
                    label = { Text("Degree / Major") },
                    placeholder = { Text("e.g., B.Tech CSE, B.Com, BBA") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_degree_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Academic Year",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        yearOptions.forEach { year ->
                            FilterChip(
                                selected = academicYear == year,
                                onClick = { academicYear = year },
                                label = { Text(year) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetRole,
                        onValueChange = { targetRole = it },
                        label = { Text("Target Career / Internship Role") },
                        placeholder = { Text("e.g., Software Engineer") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_role_input")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roleSuggestions.forEach { suggestion ->
                            FilterChip(
                                selected = targetRole.equals(suggestion, ignoreCase = true),
                                onClick = { targetRole = suggestion },
                                label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Daily Career Prep Capacity: ${String.format(Locale.US, "%.1f", dailyHours)} hrs / day",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Used by our deterministic scheduler & recovery engine to prevent burnout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = dailyHours,
                            onValueChange = { raw ->
                                dailyHours = ((raw * 2f).roundToInt() / 2f).coerceIn(0.5f, 8.0f)
                            },
                            valueRange = 0.5f..8.0f,
                            steps = 14,
                            modifier = Modifier.testTag("onboarding_hours_slider")
                        )
                    }
                }

                if (actionState is ActionState.Error) {
                    Text(
                        text = actionState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        onCompleteOnboarding(
                            displayName.ifBlank { email.substringBefore("@").ifBlank { "Student" } },
                            degreeProgram,
                            academicYear,
                            targetRole,
                            dailyHours.toDouble()
                        )
                    },
                    enabled = !isSubmitting && targetRole.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_submit_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = "Launch Life Pilot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
