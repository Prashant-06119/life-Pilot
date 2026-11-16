package com.example.domain.engine

import com.example.core.DateUtils
import com.example.domain.model.CareerGoal
import com.example.domain.model.FocusSession
import com.example.domain.model.GoalMilestone
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus

data class GoalProgressSummary(
    val goal: CareerGoal,
    val totalMilestones: Int,
    val completedMilestones: Int,
    val totalTasks: Int,
    val completedTasks: Int,
    val progressFraction: Float
)

data class DashboardMetrics(
    val weeklyExecutionRate: Float,
    val completedThisWeekCount: Int,
    val plannedThisWeekCount: Int,
    val weeklyFocusMinutes: Int,
    val totalFocusMinutes: Int,
    val activeStreakDays: Int,
    val missedTaskCount: Int,
    val todayCompletedCount: Int,
    val todayTotalCount: Int
)

object ProgressCalculator {

    fun calculateGoalProgress(
        goal: CareerGoal,
        milestones: List<GoalMilestone>,
        tasks: List<StudyTask>
    ): GoalProgressSummary {
        val goalMilestones = milestones.filter { it.goalId == goal.id }
        val goalTasks = tasks.filter { it.goalId == goal.id }

        val completedMilestones = goalMilestones.count { it.isCompleted }
        val completedTasks = goalTasks.count { TaskStatus.fromString(it.status) == TaskStatus.COMPLETED }

        val fraction = when {
            goalMilestones.isNotEmpty() && goalTasks.isNotEmpty() -> {
                val milestoneRatio = completedMilestones.toFloat() / goalMilestones.size
                val taskRatio = completedTasks.toFloat() / goalTasks.size
                (milestoneRatio * 0.6f + taskRatio * 0.4f).coerceIn(0f, 1f)
            }
            goalMilestones.isNotEmpty() ->
                (completedMilestones.toFloat() / goalMilestones.size).coerceIn(0f, 1f)
            goalTasks.isNotEmpty() ->
                (completedTasks.toFloat() / goalTasks.size).coerceIn(0f, 1f)
            else -> 0f
        }

        return GoalProgressSummary(
            goal = goal,
            totalMilestones = goalMilestones.size,
            completedMilestones = completedMilestones,
            totalTasks = goalTasks.size,
            completedTasks = completedTasks,
            progressFraction = fraction
        )
    }

    fun calculateDashboardMetrics(
        tasks: List<StudyTask>,
        focusSessions: List<FocusSession>,
        todayEpochDay: Long
    ): DashboardMetrics {
        val weekStart = todayEpochDay - 6L
        val weekTasks = tasks.filter { it.scheduledEpochDay in weekStart..todayEpochDay }
        val completedWeek = weekTasks.count { TaskStatus.fromString(it.status) == TaskStatus.COMPLETED }
        val executionRate = if (weekTasks.isEmpty()) 0f else (completedWeek.toFloat() / weekTasks.size).coerceIn(0f, 1f)

        val todayTasks = tasks.filter { it.scheduledEpochDay == todayEpochDay }
        val todayCompleted = todayTasks.count { TaskStatus.fromString(it.status) == TaskStatus.COMPLETED }

        val missedCount = RecoveryEngine.findMissedOrOverdueTasks(tasks, todayEpochDay).size

        val weeklyFocusMinutes = focusSessions
            .filter { DateUtils.epochMillisToEpochDay(it.startedEpochMillis) in weekStart..todayEpochDay }
            .sumOf { it.actualMinutes.toInt() }
        val totalFocusMinutes = focusSessions.sumOf { it.actualMinutes.toInt() }

        val completedTaskDays = tasks
            .filter { TaskStatus.fromString(it.status) == TaskStatus.COMPLETED }
            .map { it.scheduledEpochDay }
        val completedFocusDays = focusSessions
            .filter { it.completed && it.actualMinutes > 0 }
            .map { DateUtils.epochMillisToEpochDay(it.startedEpochMillis) }
        val completedDays = (completedTaskDays + completedFocusDays).toSet()

        var streak = 0
        var cursor = if (completedDays.contains(todayEpochDay)) todayEpochDay else todayEpochDay - 1L
        while (completedDays.contains(cursor)) {
            streak++
            cursor--
        }

        return DashboardMetrics(
            weeklyExecutionRate = executionRate,
            completedThisWeekCount = completedWeek,
            plannedThisWeekCount = weekTasks.size,
            weeklyFocusMinutes = weeklyFocusMinutes,
            totalFocusMinutes = totalFocusMinutes,
            activeStreakDays = streak,
            missedTaskCount = missedCount,
            todayCompletedCount = todayCompleted,
            todayTotalCount = todayTasks.size
        )
    }
}
