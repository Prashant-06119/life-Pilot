package com.example.domain.engine

import com.example.core.DateUtils
import com.example.domain.model.BlockType
import com.example.domain.model.Priority
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.example.domain.model.TimetableBlock
import kotlin.math.max
import kotlin.math.min

data class TimeSlot(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
) {
    val durationMinutes: Int get() = (endMinuteOfDay - startMinuteOfDay).coerceAtLeast(0)
}

object SchedulingEngine {

    private const val STUDY_WINDOW_START_MIN = 7 * 60   // 7:00 AM
    private const val STUDY_WINDOW_END_MIN = 23 * 60    // 11:00 PM

    fun hasTimetableConflict(
        candidateDayOfWeek: Int,
        candidateStartMin: Int,
        candidateEndMin: Int,
        existingBlocks: List<TimetableBlock>,
        ignoreBlockId: String = ""
    ): TimetableBlock? {
        if (candidateStartMin >= candidateEndMin) return null
        return existingBlocks.firstOrNull { block ->
            block.id != ignoreBlockId &&
                block.dayOfWeek.toInt() == candidateDayOfWeek &&
                candidateStartMin < block.endMinuteOfDay.toInt() &&
                candidateEndMin > block.startMinuteOfDay.toInt()
        }
    }

    fun computeOpenGapsForDay(
        dayOfWeek: Int,
        blocks: List<TimetableBlock>,
        minDurationMinutes: Int = 30
    ): List<TimeSlot> {
        val dayBlocks = blocks
            .filter { it.dayOfWeek.toInt() == dayOfWeek }
            .sortedBy { it.startMinuteOfDay }

        val gaps = mutableListOf<TimeSlot>()
        var cursor = STUDY_WINDOW_START_MIN

        for (block in dayBlocks) {
            val blockStart = block.startMinuteOfDay.toInt().coerceIn(STUDY_WINDOW_START_MIN, STUDY_WINDOW_END_MIN)
            val blockEnd = block.endMinuteOfDay.toInt().coerceIn(STUDY_WINDOW_START_MIN, STUDY_WINDOW_END_MIN)
            if (blockStart > cursor) {
                val slot = TimeSlot(cursor, blockStart)
                if (slot.durationMinutes >= minDurationMinutes) {
                    gaps.add(slot)
                }
            }
            cursor = max(cursor, blockEnd)
        }

        if (cursor < STUDY_WINDOW_END_MIN) {
            val tail = TimeSlot(cursor, STUDY_WINDOW_END_MIN)
            if (tail.durationMinutes >= minDurationMinutes) {
                gaps.add(tail)
            }
        }
        return gaps
    }

    fun computeDailyAvailableStudyMinutes(
        epochDay: Long,
        dailyTargetHours: Double,
        blocks: List<TimetableBlock>,
        tasks: List<StudyTask>
    ): Int {
        val targetCapMinutes = (dailyTargetHours.coerceIn(0.5, 12.0) * 60.0).toInt()
        val dow = DateUtils.dayOfWeekFromEpochDay(epochDay)
        val openGapMinutes = computeOpenGapsForDay(dow, blocks, minDurationMinutes = 15)
            .sumOf { it.durationMinutes }
        val fixedCap = min(targetCapMinutes, openGapMinutes)
        val alreadyScheduledMinutes = tasks
            .filter {
                it.scheduledEpochDay == epochDay &&
                    TaskStatus.fromString(it.status).isActionable
            }
            .sumOf { it.estimatedMinutes.toInt() }
        return (fixedCap - alreadyScheduledMinutes).coerceAtLeast(0)
    }

    fun rankTasksForExecution(tasks: List<StudyTask>, todayEpochDay: Long): List<StudyTask> {
        return tasks.sortedWith(
            compareByDescending<StudyTask> { task ->
                val status = TaskStatus.fromString(task.status)
                when (status) {
                    TaskStatus.IN_PROGRESS -> 5
                    TaskStatus.RECOVERED -> 4
                    TaskStatus.TODO -> if (task.scheduledEpochDay <= todayEpochDay) 3 else 2
                    TaskStatus.MISSED -> 1
                    TaskStatus.COMPLETED -> 0
                }
            }
                .thenByDescending { Priority.fromString(it.priority).weight }
                .thenBy { it.scheduledEpochDay }
                .thenBy { it.estimatedMinutes }
        )
    }

    fun fixedAcademicMinutesForDay(dayOfWeek: Int, blocks: List<TimetableBlock>): Int {
        return blocks
            .filter {
                it.dayOfWeek.toInt() == dayOfWeek &&
                    BlockType.fromString(it.blockType).isFixedAcademic
            }
            .sumOf { (it.endMinuteOfDay - it.startMinuteOfDay).toInt().coerceAtLeast(0) }
    }
}
