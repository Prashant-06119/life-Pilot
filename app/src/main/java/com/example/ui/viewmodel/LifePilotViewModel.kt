package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ActionState
import com.example.core.DateUtils
import com.example.core.UiState
import com.example.core.toReadableFirestoreMessage
import com.example.data.repository.AiPlannerService
import com.example.data.repository.AiStudyPlanDraft
import com.example.data.repository.LifePilotRepository
import com.example.domain.engine.DashboardMetrics
import com.example.domain.engine.GoalProgressSummary
import com.example.domain.engine.ProgressCalculator
import com.example.domain.engine.RecoveryEngine
import com.example.domain.engine.RecoveryProposal
import com.example.domain.engine.SchedulingEngine
import com.example.domain.model.CareerGoal
import com.example.domain.model.FocusSession
import com.example.domain.model.GoalMilestone
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.example.domain.model.TimetableBlock
import com.example.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LifePilotWorkspace(
    val profile: UserProfile?,
    val goals: List<CareerGoal>,
    val milestones: List<GoalMilestone>,
    val timetable: List<TimetableBlock>,
    val tasks: List<StudyTask>,
    val focusSessions: List<FocusSession>,
    val goalProgress: List<GoalProgressSummary>,
    val metrics: DashboardMetrics,
    val rankedTasks: List<StudyTask>,
    val recoveryProposals: List<RecoveryProposal>,
    val todayAvailableMinutes: Int
)

data class FocusTimerState(
    val selectedTaskId: String = "",
    val selectedTaskTitle: String = "Deep Career Focus",
    val selectedGoalId: String = "",
    val plannedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val startedEpochMillis: Long = 0L
)

class LifePilotViewModel(
    private val repository: LifePilotRepository,
    private val aiService: AiPlannerService,
    private val currentUserId: String
) : ViewModel() {

    private data class CoreCollections(
        val profile: UserProfile?,
        val goals: List<CareerGoal>,
        val milestones: List<GoalMilestone>,
        val timetable: List<TimetableBlock>,
        val tasks: List<StudyTask>
    )

    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceState: StateFlow<UiState<LifePilotWorkspace>> = retryTrigger.flatMapLatest {
        combine(
            combine(
                repository.observeProfile(currentUserId),
                repository.observeGoals(currentUserId),
                repository.observeMilestones(currentUserId),
                repository.observeTimetable(currentUserId),
                repository.observeTasks(currentUserId)
            ) { profile, goals, milestones, timetable, tasks ->
                CoreCollections(profile, goals, milestones, timetable, tasks)
            },
            repository.observeFocusSessions(currentUserId)
        ) { core, sessions ->
            val today = DateUtils.todayEpochDay()
            val dailyHours = core.profile?.dailyStudyHoursTarget ?: 3.0
            val goalProgress = core.goals.map { goal ->
                ProgressCalculator.calculateGoalProgress(goal, core.milestones, core.tasks)
            }
            val metrics = ProgressCalculator.calculateDashboardMetrics(core.tasks, sessions, today)
            val rankedTasks = SchedulingEngine.rankTasksForExecution(core.tasks, today)
            val recoveryProposals = RecoveryEngine.generateRecoveryPlan(
                tasks = core.tasks,
                timetableBlocks = core.timetable,
                dailyTargetHours = dailyHours,
                todayEpochDay = today
            )
            val todayAvailable = SchedulingEngine.computeDailyAvailableStudyMinutes(
                epochDay = today,
                dailyTargetHours = dailyHours,
                blocks = core.timetable,
                tasks = core.tasks
            )
            LifePilotWorkspace(
                profile = core.profile,
                goals = core.goals,
                milestones = core.milestones,
                timetable = core.timetable,
                tasks = core.tasks,
                focusSessions = sessions,
                goalProgress = goalProgress,
                metrics = metrics,
                rankedTasks = rankedTasks,
                recoveryProposals = recoveryProposals,
                todayAvailableMinutes = todayAvailable
            )
        }
            .map<LifePilotWorkspace, UiState<LifePilotWorkspace>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing Life Pilot workspace", rootError)
                emit(UiState.Error(rootError.toReadableFirestoreMessage("Failed to load your Life Pilot workspace.")))
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    fun retryWorkspace() {
        retryTrigger.value += 1
    }

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    private val _aiPlanState = MutableStateFlow<UiState<AiStudyPlanDraft>?>(null)
    val aiPlanState: StateFlow<UiState<AiStudyPlanDraft>?> = _aiPlanState.asStateFlow()

    private val _enrichedRecoveryProposals = MutableStateFlow<List<RecoveryProposal>?>(null)
    val enrichedRecoveryProposals: StateFlow<List<RecoveryProposal>?> = _enrichedRecoveryProposals.asStateFlow()

    private val _isEnrichingRecovery = MutableStateFlow(false)
    val isEnrichingRecovery: StateFlow<Boolean> = _isEnrichingRecovery.asStateFlow()

    private val _focusTimerState = MutableStateFlow(FocusTimerState())
    val focusTimerState: StateFlow<FocusTimerState> = _focusTimerState.asStateFlow()

    private var timerJob: Job? = null

    fun clearActionState() {
        _actionState.value = ActionState.Idle
    }

    // --- Onboarding & Profile ---

    fun completeOnboarding(
        displayName: String,
        email: String,
        degreeProgram: String,
        academicYear: String,
        targetCareerRole: String,
        dailyStudyHoursTarget: Double,
        preferredDarkMode: Boolean
    ) {
        if (displayName.isBlank() || targetCareerRole.isBlank()) {
            _actionState.value = ActionState.Error("Please enter your name and target career role.")
            return
        }
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.saveUserProfile(
                displayName = displayName,
                email = email,
                degreeProgram = degreeProgram,
                academicYear = academicYear,
                targetCareerRole = targetCareerRole,
                dailyStudyHoursTarget = dailyStudyHoursTarget,
                onboardingCompleted = true,
                preferredDarkMode = preferredDarkMode
            ).onSuccess {
                _actionState.value = ActionState.Success("Welcome aboard, $displayName!")
            }.onFailure { err ->
                _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Could not save onboarding profile."))
            }
        }
    }

    fun syncThemePreference(darkMode: Boolean) {
        viewModelScope.launch {
            repository.updateThemePreference(darkMode)
        }
    }

    // --- Career Goals & Milestones ---

    fun createGoal(
        title: String,
        targetRole: String,
        targetDateEpochDay: Long,
        priority: String
    ) {
        if (title.isBlank()) {
            _actionState.value = ActionState.Error("Goal title cannot be empty.")
            return
        }
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.createCareerGoal(title, targetRole, targetDateEpochDay, priority)
                .onSuccess {
                    _actionState.value = ActionState.Success("Career goal added.")
                }
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to create career goal."))
                }
        }
    }

    fun deleteGoal(
        goal: CareerGoal,
        allMilestones: List<GoalMilestone>,
        allTasks: List<StudyTask> = emptyList()
    ) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.deleteCareerGoal(goal.id, allMilestones, allTasks)
                .onSuccess {
                    _actionState.value = ActionState.Success("Goal removed.")
                }
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to delete goal."))
                }
        }
    }

    fun addMilestone(
        goalId: String,
        title: String,
        description: String,
        existingCount: Int,
        targetDateEpochDay: Long
    ) {
        if (title.isBlank()) {
            _actionState.value = ActionState.Error("Milestone title cannot be empty.")
            return
        }
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.createMilestone(goalId, title, description, existingCount.toLong(), targetDateEpochDay)
                .onSuccess {
                    _actionState.value = ActionState.Success("Milestone added.")
                }
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to add milestone."))
                }
        }
    }

    fun toggleMilestone(milestone: GoalMilestone) {
        viewModelScope.launch {
            repository.toggleMilestoneCompletion(milestone)
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to update milestone."))
                }
        }
    }

    // --- Timetable ---

    fun addTimetableBlock(
        title: String,
        dayOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        blockType: String,
        location: String,
        existingBlocks: List<TimetableBlock>
    ) {
        if (title.isBlank()) {
            _actionState.value = ActionState.Error("Class or block title is required.")
            return
        }
        if (startMinuteOfDay >= endMinuteOfDay) {
            _actionState.value = ActionState.Error("End time must be after start time.")
            return
        }
        val conflict = SchedulingEngine.hasTimetableConflict(
            candidateDayOfWeek = dayOfWeek,
            candidateStartMin = startMinuteOfDay,
            candidateEndMin = endMinuteOfDay,
            existingBlocks = existingBlocks
        )
        if (conflict != null) {
            _actionState.value = ActionState.Error(
                "Overlaps with '${conflict.title}' (${DateUtils.formatMinuteOfDay(conflict.startMinuteOfDay.toInt())}–${DateUtils.formatMinuteOfDay(conflict.endMinuteOfDay.toInt())})."
            )
            return
        }
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.createTimetableBlock(title, dayOfWeek, startMinuteOfDay, endMinuteOfDay, blockType, location)
                .onSuccess {
                    _actionState.value = ActionState.Success("Timetable block saved.")
                }
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to save block."))
                }
        }
    }

    fun deleteTimetableBlock(blockId: String) {
        viewModelScope.launch {
            repository.deleteTimetableBlock(blockId)
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to remove block."))
                }
        }
    }

    // --- Smart Tasks ---

    fun addTask(
        goalId: String,
        milestoneId: String,
        title: String,
        notes: String,
        scheduledEpochDay: Long,
        estimatedMinutes: Int,
        priority: String
    ) {
        if (title.isBlank()) {
            _actionState.value = ActionState.Error("Task title cannot be empty.")
            return
        }
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.createTask(goalId, milestoneId, title, notes, scheduledEpochDay, estimatedMinutes, priority)
                .onSuccess {
                    _actionState.value = ActionState.Success("Task scheduled.")
                }
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to add task."))
                }
        }
    }

    fun updateTaskStatus(task: StudyTask, newStatus: TaskStatus) {
        viewModelScope.launch {
            repository.updateTaskStatus(task, newStatus)
                .onSuccess {
                    if (newStatus == TaskStatus.COMPLETED) {
                        _actionState.value = ActionState.Success("Task completed!")
                    } else if (newStatus == TaskStatus.MISSED) {
                        _actionState.value = ActionState.Success("Marked missed — ready for 1-tap recovery.")
                    }
                }
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to update task."))
                }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to delete task."))
                }
        }
    }

    // --- Adaptive Task Recovery ---

    fun prepareAiRecoveryAdvice(proposals: List<RecoveryProposal>) {
        if (proposals.isEmpty()) return
        _enrichedRecoveryProposals.value = proposals
        viewModelScope.launch {
            _isEnrichingRecovery.value = true
            val reasons = aiService.enrichRecoveryReasons(proposals)
            if (reasons.isNotEmpty()) {
                _enrichedRecoveryProposals.value = proposals.map { p ->
                    val aiReason = reasons[p.task.id]
                    if (!aiReason.isNullOrBlank()) p.copy(reason = aiReason) else p
                }
            }
            _isEnrichingRecovery.value = false
        }
    }

    fun dismissRecoveryPreview() {
        _enrichedRecoveryProposals.value = null
    }

    fun applyRecoveryPlan(proposals: List<RecoveryProposal>) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.applyRecoveryProposals(proposals)
                .onSuccess {
                    _enrichedRecoveryProposals.value = null
                    _actionState.value = ActionState.Success("Recovered ${proposals.size} task(s) into open slots.")
                }
                .onFailure { err ->
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to recover tasks."))
                }
        }
    }

    // --- AI Study Planner ---

    fun generateAiStudyPlan(
        targetRole: String,
        degreeProgram: String,
        dailyStudyHours: Double,
        horizonWeeks: Int,
        timetableBlocks: List<TimetableBlock>
    ) {
        if (targetRole.isBlank()) {
            _aiPlanState.value = UiState.Error("Please enter a target career role.")
            return
        }
        viewModelScope.launch {
            _aiPlanState.value = UiState.Loading
            aiService.generateStudyPlan(
                targetRole = targetRole.trim(),
                degreeProgram = degreeProgram.trim(),
                dailyStudyHours = dailyStudyHours,
                horizonWeeks = horizonWeeks,
                timetableBlocks = timetableBlocks
            ).onSuccess { draft ->
                _aiPlanState.value = UiState.Success(draft)
            }.onFailure { err ->
                _aiPlanState.value = UiState.Error(err.localizedMessage ?: "AI planning failed.")
            }
        }
    }

    fun clearAiPlanState() {
        _aiPlanState.value = null
    }

    fun applyAiStudyPlan(draft: AiStudyPlanDraft, targetRole: String, horizonWeeks: Int) {
        val today = DateUtils.todayEpochDay()
        val targetDate = today + (horizonWeeks.coerceIn(1, 52) * 7L)
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            val pairs = draft.milestones.map { it to it.tasks }
            repository.applyAiStudyPlan(
                goalTitle = draft.goalTitle,
                targetRole = targetRole,
                targetDateEpochDay = targetDate,
                priority = "HIGH",
                milestonesWithTasks = pairs,
                baseEpochDay = today
            ).onSuccess {
                _aiPlanState.value = null
                _actionState.value = ActionState.Success("AI Study Plan added to your Roadmap & Tasks!")
            }.onFailure { err ->
                _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to save AI study plan."))
            }
        }
    }

    // --- Focus Timer ---

    fun selectFocusTask(task: StudyTask?) {
        val current = _focusTimerState.value
        if (current.isRunning) return
        if (task == null) {
            _focusTimerState.value = current.copy(
                selectedTaskId = "",
                selectedTaskTitle = "Deep Career Focus",
                selectedGoalId = ""
            )
        } else {
            val presetMins = task.estimatedMinutes.toInt().coerceIn(15, 90)
            _focusTimerState.value = current.copy(
                selectedTaskId = task.id,
                selectedTaskTitle = task.title,
                selectedGoalId = task.goalId,
                plannedMinutes = presetMins,
                remainingSeconds = presetMins * 60
            )
        }
    }

    fun setFocusPresetMinutes(minutes: Int) {
        val current = _focusTimerState.value
        if (current.isRunning) return
        val clamped = minutes.coerceIn(5, 120)
        _focusTimerState.value = current.copy(
            plannedMinutes = clamped,
            remainingSeconds = clamped * 60
        )
    }

    fun startFocusTimer() {
        val current = _focusTimerState.value
        if (current.isRunning) return
        val startMillis = if (current.startedEpochMillis == 0L) System.currentTimeMillis() else current.startedEpochMillis
        val remaining = current.remainingSeconds.coerceAtLeast(1)
        val targetEndMillis = System.currentTimeMillis() + (remaining * 1000L)
        _focusTimerState.value = current.copy(
            isRunning = true,
            startedEpochMillis = startMillis,
            remainingSeconds = remaining
        )

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_focusTimerState.value.isRunning) {
                val secsLeft = ((targetEndMillis - System.currentTimeMillis() + 999L) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
                _focusTimerState.value = _focusTimerState.value.copy(remainingSeconds = secsLeft)
                if (secsLeft <= 0) {
                    completeFocusSession(completed = true, naturalFinish = true)
                    break
                }
                delay(500L)
            }
        }
    }

    fun pauseFocusTimer() {
        timerJob?.cancel()
        _focusTimerState.value = _focusTimerState.value.copy(isRunning = false)
    }

    fun resetFocusTimer() {
        timerJob?.cancel()
        val planned = _focusTimerState.value.plannedMinutes
        _focusTimerState.value = _focusTimerState.value.copy(
            isRunning = false,
            remainingSeconds = planned * 60,
            startedEpochMillis = 0L
        )
    }

    fun completeFocusSession(completed: Boolean, naturalFinish: Boolean = false) {
        val state = _focusTimerState.value
        val elapsedSeconds = ((state.plannedMinutes * 60) - state.remainingSeconds).coerceAtLeast(0)
        if (!naturalFinish && state.startedEpochMillis == 0L && elapsedSeconds == 0) {
            _actionState.value = ActionState.Error("Start the focus timer before logging a session.")
            return
        }
        timerJob?.cancel()

        val actualMinutes = if (naturalFinish) {
            state.plannedMinutes
        } else {
            (elapsedSeconds / 60).coerceAtLeast(1)
        }
        val isFullOrMajority = naturalFinish || elapsedSeconds >= (state.plannedMinutes * 30)
        val started = if (state.startedEpochMillis > 0L) state.startedEpochMillis else System.currentTimeMillis()

        _focusTimerState.value = state.copy(
            isRunning = false,
            remainingSeconds = state.plannedMinutes * 60,
            startedEpochMillis = 0L
        )

        viewModelScope.launch {
            repository.logFocusSession(
                taskId = state.selectedTaskId,
                taskTitle = state.selectedTaskTitle,
                goalId = state.selectedGoalId,
                plannedMinutes = state.plannedMinutes,
                actualMinutes = actualMinutes,
                completed = completed && isFullOrMajority,
                startedEpochMillis = started
            ).onSuccess {
                _actionState.value = ActionState.Success("Logged ${actualMinutes}m focus session!")
            }.onFailure { err ->
                _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to log focus session."))
            }
        }
    }

    // --- Account Deletion (Phase 8 Privacy Compliance) ---

    fun deleteAccountData(onDeleted: () -> Unit, onError: () -> Unit = {}) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            repository.deleteAllUserData()
                .onSuccess {
                    _actionState.value = ActionState.Success("All cloud data deleted.")
                    onDeleted()
                }
                .onFailure { err ->
                    onError()
                    _actionState.value = ActionState.Error(err.toReadableFirestoreMessage("Failed to delete account data."))
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private companion object {
        const val TAG = "LifePilotVM"
    }
}
