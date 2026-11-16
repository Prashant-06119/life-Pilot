package com.example.domain.engine

import com.example.core.DateUtils
import com.example.domain.model.BlockType
import com.example.domain.model.CareerGoal
import com.example.domain.model.FocusSession
import com.example.domain.model.GoalMilestone
import com.example.domain.model.Priority
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.example.domain.model.TimetableBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginesUnitTest {

    @Test
    fun schedulingEngine_detectsOverlapAndFindsOpenGaps() {
        val blocks = listOf(
            TimetableBlock(
                id = "b1",
                title = "Algorithms",
                dayOfWeek = 1L,
                startMinuteOfDay = 540L, // 9:00 AM
                endMinuteOfDay = 660L,   // 11:00 AM
                blockType = BlockType.CLASS.name
            )
        )
        assertNotNull(SchedulingEngine.hasTimetableConflict(1, 600, 700, blocks))
        assertNull(SchedulingEngine.hasTimetableConflict(1, 660, 720, blocks))

        val gaps = SchedulingEngine.computeOpenGapsForDay(1, blocks, minDurationMinutes = 30)
        assertEquals(2, gaps.size)
        assertEquals(420, gaps[0].startMinuteOfDay) // 7:00 AM - 9:00 AM
        assertEquals(540, gaps[0].endMinuteOfDay)
        assertEquals(660, gaps[1].startMinuteOfDay) // 11:00 AM - 11:00 PM
    }

    @Test
    fun recoveryEngine_reschedulesMissedTasksWithinDailyCap() {
        val today = DateUtils.todayEpochDay()
        val tasks = listOf(
            StudyTask(
                id = "t_missed",
                title = "Dynamic Programming Revision",
                scheduledEpochDay = today - 2,
                estimatedMinutes = 60L,
                priority = Priority.HIGH.name,
                status = TaskStatus.MISSED.name
            ),
            StudyTask(
                id = "t_today_full",
                title = "System Design Reading",
                scheduledEpochDay = today,
                estimatedMinutes = 120L,
                priority = Priority.MEDIUM.name,
                status = TaskStatus.TODO.name
            )
        )
        // Target = 2.0h (120m). Today is already full (120m), so missed task (60m) must move to tomorrow!
        val proposals = RecoveryEngine.generateRecoveryPlan(
            tasks = tasks,
            timetableBlocks = emptyList(),
            dailyTargetHours = 2.0,
            todayEpochDay = today
        )
        assertEquals(1, proposals.size)
        assertEquals(today + 1, proposals[0].proposedEpochDay)
        assertTrue(proposals[0].fitsWithinDailyCap)
    }

    @Test
    fun progressCalculator_computesGoalAndDashboardMetrics() {
        val today = DateUtils.todayEpochDay()
        val goal = CareerGoal(id = "g1", title = "SWE Intern")
        val milestones = listOf(
            GoalMilestone(id = "m1", goalId = "g1", isCompleted = true),
            GoalMilestone(id = "m2", goalId = "g1", isCompleted = false)
        )
        val tasks = listOf(
            StudyTask(id = "t1", goalId = "g1", scheduledEpochDay = today, status = TaskStatus.COMPLETED.name),
            StudyTask(id = "t2", goalId = "g1", scheduledEpochDay = today, status = TaskStatus.TODO.name)
        )
        val sessions = listOf(
            FocusSession(id = "f1", actualMinutes = 50L, startedEpochMillis = System.currentTimeMillis())
        )
        val progress = ProgressCalculator.calculateGoalProgress(goal, milestones, tasks)
        assertEquals(0.5f, progress.progressFraction, 0.01f)

        val metrics = ProgressCalculator.calculateDashboardMetrics(tasks, sessions, today)
        assertEquals(0.5f, metrics.weeklyExecutionRate, 0.01f)
        assertEquals(50, metrics.weeklyFocusMinutes)
        assertEquals(1, metrics.activeStreakDays)
    }

    @Test
    fun recoveryEngine_includesInProgressOverdue_andReschedulesTodayMissedToTomorrow() {
        val today = DateUtils.todayEpochDay()
        val tasks = listOf(
            StudyTask(
                id = "t_today_missed",
                title = "Missed Today Task",
                scheduledEpochDay = today,
                estimatedMinutes = 30L,
                priority = Priority.HIGH.name,
                status = TaskStatus.MISSED.name
            ),
            StudyTask(
                id = "t_in_progress_overdue",
                title = "Stale In Progress Task",
                scheduledEpochDay = today - 1,
                estimatedMinutes = 30L,
                priority = Priority.MEDIUM.name,
                status = TaskStatus.IN_PROGRESS.name
            )
        )
        val proposals = RecoveryEngine.generateRecoveryPlan(
            tasks = tasks,
            timetableBlocks = emptyList(),
            dailyTargetHours = 3.0,
            todayEpochDay = today
        )
        assertEquals(2, proposals.size)
        val missedTodayProposal = proposals.first { it.task.id == "t_today_missed" }
        val overdueProposal = proposals.first { it.task.id == "t_in_progress_overdue" }
        assertTrue(missedTodayProposal.proposedEpochDay >= today + 1)
        assertEquals(today, overdueProposal.proposedEpochDay)
    }

    @Test
    fun progressCalculator_countsCompletedFocusSessionTowardStreak() {
        val today = DateUtils.todayEpochDay()
        val sessions = listOf(
            FocusSession(
                id = "f_today",
                actualMinutes = 25L,
                completed = true,
                startedEpochMillis = System.currentTimeMillis()
            )
        )
        val metrics = ProgressCalculator.calculateDashboardMetrics(emptyList(), sessions, today)
        assertEquals(1, metrics.activeStreakDays)
        assertEquals(25, metrics.weeklyFocusMinutes)
    }
}
