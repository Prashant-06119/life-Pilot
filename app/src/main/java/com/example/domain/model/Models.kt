package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class Priority(val label: String, val weight: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1);

    companion object {
        fun fromString(value: String): Priority =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}

enum class GoalStatus(val label: String) {
    ACTIVE("Active"),
    COMPLETED("Completed"),
    ARCHIVED("Archived");

    companion object {
        fun fromString(value: String): GoalStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

enum class BlockType(val label: String, val isFixedAcademic: Boolean) {
    CLASS("Class", true),
    LAB("Lab", true),
    EXAM("Exam", true),
    STUDY("Study Block", false);

    companion object {
        fun fromString(value: String): BlockType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLASS
    }
}

enum class TaskStatus(val label: String) {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    MISSED("Missed"),
    RECOVERED("Recovered");

    val isActionable: Boolean
        get() = this == TODO || this == IN_PROGRESS || this == RECOVERED

    companion object {
        fun fromString(value: String): TaskStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TODO
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val degreeProgram: String = "",
    val academicYear: String = "",
    val targetCareerRole: String = "",
    val dailyStudyHoursTarget: Double = 3.0,
    val onboardingCompleted: Boolean = false,
    val preferredDarkMode: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CareerGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetRole: String = "",
    val targetDateEpochDay: Long = 0L,
    val priority: String = Priority.HIGH.name,
    val status: String = GoalStatus.ACTIVE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GoalMilestone(
    @DocumentId val id: String = "",
    val userId: String = "",
    val goalId: String = "",
    val title: String = "",
    val description: String = "",
    val orderIndex: Long = 0L,
    val targetDateEpochDay: Long = 0L,
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TimetableBlock(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val dayOfWeek: Long = 1L,
    val startMinuteOfDay: Long = 540L,
    val endMinuteOfDay: Long = 600L,
    val blockType: String = BlockType.CLASS.name,
    val location: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val goalId: String = "",
    val milestoneId: String = "",
    val title: String = "",
    val notes: String = "",
    val scheduledEpochDay: Long = 0L,
    val estimatedMinutes: Long = 45L,
    val priority: String = Priority.MEDIUM.name,
    val status: String = TaskStatus.TODO.name,
    val recoveryCount: Long = 0L,
    val aiReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FocusSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val goalId: String = "",
    val plannedMinutes: Long = 25L,
    val actualMinutes: Long = 0L,
    val completed: Boolean = false,
    val startedEpochMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DistractionBlockerConfig(
    @DocumentId val id: String = "default",
    val userId: String = "",
    val blockingEnabled: Boolean = false,
    val blockedPackages: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

