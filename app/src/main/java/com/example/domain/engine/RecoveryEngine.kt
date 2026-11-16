package com.example.domain.engine

import com.example.core.DateUtils
import com.example.domain.model.Priority
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.example.domain.model.TimetableBlock

data class RecoveryProposal(
    val task: StudyTask,
    val proposedEpochDay: Long,
    val proposedDayLabel: String,
    val reason: String,
    val fitsWithinDailyCap: Boolean
)

object RecoveryEngine {

    fun findMissedOrOverdueTasks(
        tasks: List<StudyTask>,
        todayEpochDay: Long
    ): List<StudyTask> {
        return tasks.filter { task ->
            val status = TaskStatus.fromString(task.status)
            status == TaskStatus.MISSED ||
                (status.isActionable && task.scheduledEpochDay < todayEpochDay)
        }.sortedWith(
            compareByDescending<StudyTask> { Priority.fromString(it.priority).weight }
                .thenBy { it.scheduledEpochDay }
        )
    }

    fun generateRecoveryPlan(
        tasks: List<StudyTask>,
        timetableBlocks: List<TimetableBlock>,
        dailyTargetHours: Double,
        todayEpochDay: Long,
        aiReasonsByTaskId: Map<String, String> = emptyMap()
    ): List<RecoveryProposal> {
        val candidates = findMissedOrOverdueTasks(tasks, todayEpochDay)
        if (candidates.isEmpty()) return emptyList()

        // Track remaining free minutes across a 7-day horizon (today .. today+6)
        val remainingByDay = mutableMapOf<Long, Int>()
        for (offset in 0L..6L) {
            val day = todayEpochDay + offset
            remainingByDay[day] = SchedulingEngine.computeDailyAvailableStudyMinutes(
                epochDay = day,
                dailyTargetHours = dailyTargetHours,
                blocks = timetableBlocks,
                tasks = tasks
            )
        }

        val proposals = mutableListOf<RecoveryProposal>()
        for (task in candidates) {
            val duration = task.estimatedMinutes.toInt().coerceIn(15, 240)
            // If a task was explicitly marked missed on today or future day, schedule from tomorrow onward
            val startOffset = if (task.scheduledEpochDay >= todayEpochDay) 1L else 0L
            var chosenDay: Long? = null
            var fits = false

            for (offset in startOffset..6L) {
                val day = todayEpochDay + offset
                val available = remainingByDay[day] ?: 0
                if (available >= duration) {
                    chosenDay = day
                    remainingByDay[day] = available - duration
                    fits = true
                    break
                }
            }

            if (chosenDay == null) {
                // Fallback: place on the eligible day with the highest open capacity
                val eligibleDays = (startOffset..6L).map { todayEpochDay + it }
                chosenDay = eligibleDays.maxByOrNull { remainingByDay[it] ?: 0 } ?: (todayEpochDay + 1)
                remainingByDay[chosenDay] = ((remainingByDay[chosenDay] ?: 0) - duration).coerceAtLeast(0)
                fits = false
            }

            val dayLabel = DateUtils.formatEpochDayShort(chosenDay)
            val customReason = aiReasonsByTaskId[task.id]?.takeIf { it.isNotBlank() }
            val defaultReason = if (fits) {
                "Fits open $duration min gap on $dayLabel without exceeding your daily study cap."
            } else {
                "Placed on $dayLabel (lightest academic load day) — consider splitting if time gets tight."
            }

            proposals.add(
                RecoveryProposal(
                    task = task,
                    proposedEpochDay = chosenDay,
                    proposedDayLabel = dayLabel,
                    reason = customReason ?: defaultReason,
                    fitsWithinDailyCap = fits
                )
            )
        }
        return proposals
    }
}
