package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.core.ActionState
import com.example.core.ThemePreferences
import com.example.core.UiState
import com.example.data.repository.AiPlannerService
import com.example.data.repository.LifePilotRepository
import com.example.domain.model.TaskStatus
import com.example.ui.ai.AiPlannerSheet
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.focus.FocusScreen
import com.example.ui.goals.GoalsScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.planner.PlannerScreen
import com.example.ui.planner.RecoverySheet
import com.example.ui.settings.SettingsPrivacyDialog
import com.example.ui.viewmodel.LifePilotViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object Routes {
    const val DASHBOARD = "dashboard"
    const val GOALS = "goals"
    const val PLANNER = "planner"
    const val FOCUS = "focus"
}

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val navDestinations = listOf(
    NavDestination(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard, "nav_dashboard"),
    NavDestination(Routes.GOALS, R.string.nav_goals, Icons.Filled.Flag, "nav_goals"),
    NavDestination(Routes.PLANNER, R.string.nav_planner, Icons.Filled.CalendarMonth, "nav_planner"),
    NavDestination(Routes.FOCUS, R.string.nav_focus, Icons.Filled.Timer, "nav_focus")
)

@Composable
fun LifePilotApp(
    isDarkMode: Boolean,
    themePreferences: ThemePreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    var authChecked by remember { mutableStateOf(currentUser != null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            authChecked = true
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    authChecked = true
                },
                onUnauthenticated = {
                    authChecked = true
                },
                scope = scope
            )
        }
    }

    val activeUser = currentUser
    if (!authChecked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (activeUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
        return
    }

    // Scope ViewModel strictly to authenticated UID so sign-out/account-switch never leaks prior state.
    key(activeUser.uid) {
        val vm: LifePilotViewModel = viewModel(
            key = "life_pilot_${activeUser.uid}",
            factory = viewModelFactory {
                initializer {
                    val databaseId = context.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    val repository = LifePilotRepository(firestore)
                    LifePilotViewModel(
                        repository = repository,
                        aiService = AiPlannerService(),
                        currentUserId = activeUser.uid
                    )
                }
            }
        )

        LifePilotAuthenticatedHost(
            user = activeUser,
            viewModel = vm,
            isDarkMode = isDarkMode,
            onToggleDarkMode = { enabled ->
                scope.launch {
                    themePreferences.setDarkMode(enabled)
                    vm.syncThemePreference(enabled)
                }
            },
            onSignOut = {
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = {
                        currentUser = null
                    }
                )
            }
        )
    }
}

@Composable
private fun LifePilotAuthenticatedHost(
    user: FirebaseUser,
    viewModel: LifePilotViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val aiPlanState by viewModel.aiPlanState.collectAsStateWithLifecycle()
    val enrichedRecoveryProposals by viewModel.enrichedRecoveryProposals.collectAsStateWithLifecycle()
    val isEnrichingRecovery by viewModel.isEnrichingRecovery.collectAsStateWithLifecycle()
    val focusTimerState by viewModel.focusTimerState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAiPlannerSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isClosingSession by remember { mutableStateOf(false) }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is ActionState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearActionState()
            }
            is ActionState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearActionState()
            }
            else -> Unit
        }
    }

    if (isClosingSession) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Closing your session...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    when (val uiState = workspaceState) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading your Life Pilot workspace...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Could not load workspace",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.retryWorkspace() }) {
                            Text("Retry")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = onSignOut) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }

        is UiState.Success -> {
            val workspace = uiState.data
            if (workspace.profile == null || !workspace.profile.onboardingCompleted) {
                OnboardingScreen(
                    initialDisplayName = user.displayName ?: "",
                    email = user.email ?: "",
                    preferredDarkMode = isDarkMode,
                    actionState = actionState,
                    onCompleteOnboarding = { name, degree, year, role, dailyHours ->
                        viewModel.completeOnboarding(
                            displayName = name,
                            email = user.email ?: "",
                            degreeProgram = degree,
                            academicYear = year,
                            targetCareerRole = role,
                            dailyStudyHoursTarget = dailyHours,
                            preferredDarkMode = isDarkMode
                        )
                    }
                )
                return
            }

            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: Routes.DASHBOARD

            fun navigateToTab(route: String) {
                navController.navigate(route) {
                    popUpTo(Routes.DASHBOARD) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BackHandler(enabled = currentRoute != Routes.DASHBOARD) {
                navigateToTab(Routes.DASHBOARD)
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 700.dp

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useNavigationRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                navDestinations.forEach { dest ->
                                    val label = stringResource(dest.labelRes)
                                    NavigationBarItem(
                                        selected = currentRoute == dest.route,
                                        onClick = { navigateToTab(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                navDestinations.forEach { dest ->
                                    val label = stringResource(dest.labelRes)
                                    NavigationRailItem(
                                        selected = currentRoute == dest.route,
                                        onClick = { navigateToTab(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = Routes.DASHBOARD,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            composable(Routes.DASHBOARD) {
                                DashboardScreen(
                                    workspace = workspace,
                                    onOpenAiPlanner = { showAiPlannerSheet = true },
                                    onOpenRecovery = {
                                        viewModel.prepareAiRecoveryAdvice(workspace.recoveryProposals)
                                    },
                                    onToggleTaskComplete = { task ->
                                        viewModel.updateTaskStatus(task, TaskStatus.COMPLETED)
                                    },
                                    onStartTaskFocus = { task ->
                                        viewModel.selectFocusTask(task)
                                        navigateToTab(Routes.FOCUS)
                                    },
                                    onNavigateToGoals = { navigateToTab(Routes.GOALS) },
                                    onNavigateToPlanner = { navigateToTab(Routes.PLANNER) },
                                    onOpenSettings = { showSettingsDialog = true }
                                )
                            }

                            composable(Routes.GOALS) {
                                GoalsScreen(
                                    workspace = workspace,
                                    onCreateGoal = { title, role, targetDate, priority ->
                                        viewModel.createGoal(title, role, targetDate, priority)
                                    },
                                    onDeleteGoal = { goal, milestones ->
                                        viewModel.deleteGoal(goal, milestones, workspace.tasks)
                                    },
                                    onAddMilestone = { goalId, title, desc, orderIndex, targetDate ->
                                        viewModel.addMilestone(goalId, title, desc, orderIndex, targetDate)
                                    },
                                    onToggleMilestone = { milestone ->
                                        viewModel.toggleMilestone(milestone)
                                    },
                                    onOpenAiPlanner = { showAiPlannerSheet = true }
                                )
                            }

                            composable(Routes.PLANNER) {
                                PlannerScreen(
                                    workspace = workspace,
                                    onAddTask = { goalId, milestoneId, title, notes, epochDay, mins, priority ->
                                        viewModel.addTask(goalId, milestoneId, title, notes, epochDay, mins, priority)
                                    },
                                    onUpdateTaskStatus = { task, status ->
                                        viewModel.updateTaskStatus(task, status)
                                    },
                                    onDeleteTask = { taskId ->
                                        viewModel.deleteTask(taskId)
                                    },
                                    onAddTimetableBlock = { title, dow, startMin, endMin, type, location ->
                                        viewModel.addTimetableBlock(
                                            title = title,
                                            dayOfWeek = dow,
                                            startMinuteOfDay = startMin,
                                            endMinuteOfDay = endMin,
                                            blockType = type,
                                            location = location,
                                            existingBlocks = workspace.timetable
                                        )
                                    },
                                    onDeleteTimetableBlock = { blockId ->
                                        viewModel.deleteTimetableBlock(blockId)
                                    },
                                    onStartTaskFocus = { task ->
                                        viewModel.selectFocusTask(task)
                                        navigateToTab(Routes.FOCUS)
                                    },
                                    onOpenRecovery = {
                                        viewModel.prepareAiRecoveryAdvice(workspace.recoveryProposals)
                                    }
                                )
                            }

                            composable(Routes.FOCUS) {
                                FocusScreen(
                                    workspace = workspace,
                                    timerState = focusTimerState,
                                    onSelectTask = { task -> viewModel.selectFocusTask(task) },
                                    onSetPresetMinutes = { mins -> viewModel.setFocusPresetMinutes(mins) },
                                    onStart = { viewModel.startFocusTimer() },
                                    onPause = { viewModel.pauseFocusTimer() },
                                    onReset = { viewModel.resetFocusTimer() },
                                    onCompleteAndLog = { viewModel.completeFocusSession(completed = true) }
                                )
                            }
                        }
                    }
                }
            }

            if (enrichedRecoveryProposals != null) {
                RecoverySheet(
                    proposals = enrichedRecoveryProposals!!,
                    isEnrichingWithAi = isEnrichingRecovery,
                    onDismiss = { viewModel.dismissRecoveryPreview() },
                    onApplyRecovery = { proposals ->
                        viewModel.applyRecoveryPlan(proposals)
                    }
                )
            }

            if (showAiPlannerSheet) {
                AiPlannerSheet(
                    defaultRole = workspace.profile.targetCareerRole,
                    defaultDegree = workspace.profile.degreeProgram,
                    defaultDailyHours = workspace.profile.dailyStudyHoursTarget,
                    timetableBlocks = workspace.timetable,
                    aiPlanState = aiPlanState,
                    onGeneratePlan = { role, degree, dailyHours, horizonWeeks ->
                        viewModel.generateAiStudyPlan(
                            targetRole = role,
                            degreeProgram = degree,
                            dailyStudyHours = dailyHours,
                            horizonWeeks = horizonWeeks,
                            timetableBlocks = workspace.timetable
                        )
                    },
                    onApplyPlan = { draft, role, horizonWeeks ->
                        showAiPlannerSheet = false
                        viewModel.applyAiStudyPlan(draft, role, horizonWeeks)
                    },
                    onClearDraft = { viewModel.clearAiPlanState() },
                    onDismiss = {
                        showAiPlannerSheet = false
                        viewModel.clearAiPlanState()
                    }
                )
            }

            if (showSettingsDialog) {
                SettingsPrivacyDialog(
                    profile = workspace.profile,
                    userEmail = user.email ?: "",
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onSignOut = {
                        showSettingsDialog = false
                        isClosingSession = true
                        onSignOut()
                    },
                    onDeleteAccountData = {
                        showSettingsDialog = false
                        isClosingSession = true
                        viewModel.deleteAccountData(
                            onDeleted = onSignOut,
                            onError = { isClosingSession = false }
                        )
                    },
                    onDismiss = { showSettingsDialog = false }
                )
            }
        }
    }
}
