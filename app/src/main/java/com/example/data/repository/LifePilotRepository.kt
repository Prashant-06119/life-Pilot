package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.engine.RecoveryProposal
import com.example.domain.model.BlockType
import com.example.domain.model.CareerGoal
import com.example.domain.model.DistractionBlockerConfig
import com.example.domain.model.FocusSession
import com.example.domain.model.GoalMilestone
import com.example.domain.model.Priority
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.example.domain.model.TimetableBlock
import com.example.domain.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LifePilotRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Life Pilot data.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- Real-Time Observers ---

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = userDoc(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, userId)
                    throw error
                }
        )
    }

    fun observeGoals(userId: String): Flow<List<CareerGoal>> = flow {
        val colRef = userDoc(userId).collection("goals")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(CareerGoal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                    throw error
                }
        )
    }

    fun observeMilestones(userId: String): Flow<List<GoalMilestone>> = flow {
        val colRef = userDoc(userId).collection("milestones")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(GoalMilestone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.orderIndex }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                    throw error
                }
        )
    }

    fun observeTimetable(userId: String): Flow<List<TimetableBlock>> = flow {
        val colRef = userDoc(userId).collection("timetable")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(TimetableBlock::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy({ it.dayOfWeek }, { it.startMinuteOfDay }))
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                    throw error
                }
        )
    }

    fun observeTasks(userId: String): Flow<List<StudyTask>> = flow {
        val colRef = userDoc(userId).collection("tasks")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                    throw error
                }
        )
    }

    fun observeFocusSessions(userId: String): Flow<List<FocusSession>> = flow {
        val colRef = userDoc(userId).collection("focusSessions")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(FocusSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.startedEpochMillis }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                    throw error
                }
        )
    }

    fun observeBlockerConfig(userId: String): Flow<DistractionBlockerConfig> = flow {
        val docRef = userDoc(userId).collection("blockerConfig").document("default")
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            DistractionBlockerConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: DistractionBlockerConfig(userId = userId)
                    } else {
                        DistractionBlockerConfig(userId = userId)
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, userId)
                    throw error
                }
        )
    }

    private fun handleFlowError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val toLog = firestoreError ?: error
        if (auth.currentUser?.uid == expectedUid || auth.currentUser == null) {
            handleFirestoreError(toLog, op, path)
        }
    }

    // --- Profile & Onboarding ---

    suspend fun saveUserProfile(
        displayName: String,
        email: String,
        degreeProgram: String,
        academicYear: String,
        targetCareerRole: String,
        dailyStudyHoursTarget: Double,
        onboardingCompleted: Boolean,
        preferredDarkMode: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        val existingSnap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fsError = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                try {
                    docRef.get(Source.CACHE).await()
                } catch (_: Exception) {
                    null
                }
            } else {
                handleFirestoreError(e, OperationType.GET, docRef.path)
                throw e
            }
        }

        val safeName = displayName.trim().take(80).ifBlank { "Student" }
        val safeEmail = email.trim().take(160)
        val safeDegree = degreeProgram.trim().take(100)
        val safeYear = academicYear.trim().take(40)
        val safeRole = targetCareerRole.trim().take(100)
        val safeHours = dailyStudyHoursTarget.coerceIn(0.5, 12.0)

        try {
            if (existingSnap?.exists() == true) {
                val updates = mapOf(
                    "displayName" to safeName,
                    "degreeProgram" to safeDegree,
                    "academicYear" to safeYear,
                    "targetCareerRole" to safeRole,
                    "dailyStudyHoursTarget" to safeHours,
                    "onboardingCompleted" to onboardingCompleted,
                    "preferredDarkMode" to preferredDarkMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "degreeProgram" to safeDegree,
                    "academicYear" to safeYear,
                    "targetCareerRole" to safeRole,
                    "dailyStudyHoursTarget" to safeHours,
                    "onboardingCompleted" to onboardingCompleted,
                    "preferredDarkMode" to preferredDarkMode,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateThemePreference(preferredDarkMode: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "preferredDarkMode" to preferredDarkMode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Career Goals & Milestones ---

    suspend fun createCareerGoal(
        title: String,
        targetRole: String,
        targetDateEpochDay: Long,
        priority: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("goals").document()
        val safePriority = Priority.fromString(priority).name
        val safeTitle = title.trim().take(120).ifBlank { "Career Goal" }
        val payload = mapOf(
            "userId" to uid,
            "title" to safeTitle,
            "targetRole" to targetRole.trim().take(100),
            "targetDateEpochDay" to targetDateEpochDay.coerceIn(19000L, 40000L),
            "priority" to safePriority,
            "status" to "ACTIVE",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCareerGoal(
        goalId: String,
        relatedMilestones: List<GoalMilestone>,
        relatedTasks: List<StudyTask> = emptyList()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = userDoc(uid)
        val goalRef = userRef.collection("goals").document(goalId)
        try {
            val milestoneRefs = relatedMilestones
                .filter { it.goalId == goalId }
                .map { userRef.collection("milestones").document(it.id) }
            val linkedTaskRefs = relatedTasks
                .filter { it.goalId == goalId }
                .map { userRef.collection("tasks").document(it.id) }

            val batch = db.batch()
            milestoneRefs.take(200).forEach { batch.delete(it) }
            linkedTaskRefs.take(200).forEach { taskRef ->
                batch.update(
                    taskRef,
                    mapOf(
                        "goalId" to "",
                        "milestoneId" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.delete(goalRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, goalRef.path)
            throw e
        }
    }

    suspend fun createMilestone(
        goalId: String,
        title: String,
        description: String,
        orderIndex: Long,
        targetDateEpochDay: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("milestones").document()
        val payload = mapOf(
            "userId" to uid,
            "goalId" to goalId,
            "title" to title.trim().take(140),
            "description" to description.trim().take(400),
            "orderIndex" to orderIndex.coerceIn(0L, 200L),
            "targetDateEpochDay" to targetDateEpochDay.coerceIn(19000L, 40000L),
            "isCompleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleMilestoneCompletion(milestone: GoalMilestone): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("milestones").document(milestone.id)
        try {
            docRef.update(
                mapOf(
                    "isCompleted" to !milestone.isCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Timetable ---

    suspend fun createTimetableBlock(
        title: String,
        dayOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        blockType: String,
        location: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("timetable").document()
        val safeBlockType = BlockType.fromString(blockType).name
        val safeTitle = title.trim().take(100).ifBlank { "Study Block" }
        val payload = mapOf(
            "userId" to uid,
            "title" to safeTitle,
            "dayOfWeek" to dayOfWeek.toLong().coerceIn(1L, 7L),
            "startMinuteOfDay" to startMinuteOfDay.toLong().coerceIn(0L, 1439L),
            "endMinuteOfDay" to endMinuteOfDay.toLong().coerceIn(1L, 1440L),
            "blockType" to safeBlockType,
            "location" to location.trim().take(80),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTimetableBlock(blockId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("timetable").document(blockId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Study Tasks & Recovery ---

    suspend fun createTask(
        goalId: String,
        milestoneId: String,
        title: String,
        notes: String,
        scheduledEpochDay: Long,
        estimatedMinutes: Int,
        priority: String,
        aiReason: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("tasks").document()
        val safePriority = Priority.fromString(priority).name
        val safeTitle = title.trim().take(140).ifBlank { "Study Task" }
        val payload = mapOf(
            "userId" to uid,
            "goalId" to goalId.take(128),
            "milestoneId" to milestoneId.take(128),
            "title" to safeTitle,
            "notes" to notes.trim().take(400),
            "scheduledEpochDay" to scheduledEpochDay.coerceIn(19000L, 40000L),
            "estimatedMinutes" to estimatedMinutes.toLong().coerceIn(5L, 480L),
            "priority" to safePriority,
            "status" to TaskStatus.TODO.name,
            "recoveryCount" to 0L,
            "aiReason" to aiReason.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateTaskStatus(task: StudyTask, newStatus: TaskStatus): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("tasks").document(task.id)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("tasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun applyRecoveryProposals(proposals: List<RecoveryProposal>): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (proposals.isEmpty()) return@runCatching
        val tasksCol = userDoc(uid).collection("tasks")
        try {
            val batch = db.batch()
            proposals.forEach { proposal ->
                val ref = tasksCol.document(proposal.task.id)
                batch.update(
                    ref,
                    mapOf(
                        "scheduledEpochDay" to proposal.proposedEpochDay.coerceIn(19000L, 40000L),
                        "status" to TaskStatus.RECOVERED.name,
                        "recoveryCount" to (proposal.task.recoveryCount + 1L).coerceAtMost(50L),
                        "aiReason" to proposal.reason.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, tasksCol.path)
            throw e
        }
    }

    // --- Batch Apply AI Generated Plan (Goal + Milestones + Tasks Atomically) ---

    suspend fun applyAiStudyPlan(
        goalTitle: String,
        targetRole: String,
        targetDateEpochDay: Long,
        priority: String,
        milestonesWithTasks: List<Pair<AiMilestoneDraft, List<AiTaskDraft>>>,
        baseEpochDay: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val userRef = userDoc(uid)
        val goalRef = userRef.collection("goals").document()
        try {
            val batch = db.batch()
            batch.set(
                goalRef,
                mapOf(
                    "userId" to uid,
                    "title" to goalTitle.trim().take(120).ifBlank { "Career Roadmap" },
                    "targetRole" to targetRole.trim().take(100),
                    "targetDateEpochDay" to targetDateEpochDay.coerceIn(19000L, 40000L),
                    "priority" to Priority.fromString(priority).name,
                    "status" to "ACTIVE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            milestonesWithTasks.forEachIndexed { index, (msDraft, taskDrafts) ->
                val msRef = userRef.collection("milestones").document()
                val msTargetDay = (baseEpochDay + ((index + 1) * 7L)).coerceIn(19000L, 40000L)
                batch.set(
                    msRef,
                    mapOf(
                        "userId" to uid,
                        "goalId" to goalRef.id,
                        "title" to msDraft.title.trim().take(140).ifBlank { "Milestone ${index + 1}" },
                        "description" to msDraft.description.trim().take(400),
                        "orderIndex" to index.toLong(),
                        "targetDateEpochDay" to msTargetDay,
                        "isCompleted" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                taskDrafts.forEach { taskDraft ->
                    val taskRef = userRef.collection("tasks").document()
                    val taskDay = (baseEpochDay + taskDraft.dayOffset.coerceIn(0, 21)).coerceIn(19000L, 40000L)
                    batch.set(
                        taskRef,
                        mapOf(
                            "userId" to uid,
                            "goalId" to goalRef.id,
                            "milestoneId" to msRef.id,
                            "title" to taskDraft.title.trim().take(140).ifBlank { "Focused Study Session" },
                            "notes" to taskDraft.notes.trim().take(400),
                            "scheduledEpochDay" to taskDay,
                            "estimatedMinutes" to taskDraft.estimatedMinutes.toLong().coerceIn(15L, 180L),
                            "priority" to Priority.fromString(taskDraft.priority).name,
                            "status" to TaskStatus.TODO.name,
                            "recoveryCount" to 0L,
                            "aiReason" to taskDraft.aiReason.trim().take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            goalRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, goalRef.path)
            throw e
        }
    }

    // --- Focus Sessions ---

    suspend fun logFocusSession(
        taskId: String,
        taskTitle: String,
        goalId: String,
        plannedMinutes: Int,
        actualMinutes: Int,
        completed: Boolean,
        startedEpochMillis: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("focusSessions").document()
        val payload = mapOf(
            "userId" to uid,
            "taskId" to taskId.take(128),
            "taskTitle" to taskTitle.trim().take(140).ifBlank { "Deep Focus Session" },
            "goalId" to goalId.take(128),
            "plannedMinutes" to plannedMinutes.toLong().coerceIn(1L, 240L),
            "actualMinutes" to actualMinutes.toLong().coerceIn(0L, 240L),
            "completed" to completed,
            "startedEpochMillis" to startedEpochMillis.coerceAtLeast(1700000000000L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- Distraction Blocker Config ---

    suspend fun saveBlockerConfig(
        blockingEnabled: Boolean,
        blockedPackages: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("blockerConfig").document("default")
        val sanitizedPackages = blockedPackages
            .map { it.trim() }
            .filter { it.length in 3..160 }
            .distinct()
            .take(100)

        val existingSnap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fsError = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                try {
                    docRef.get(Source.CACHE).await()
                } catch (_: Exception) {
                    null
                }
            } else {
                handleFirestoreError(e, OperationType.GET, docRef.path)
                throw e
            }
        }

        try {
            if (existingSnap?.exists() == true) {
                docRef.update(
                    mapOf(
                        "blockingEnabled" to blockingEnabled,
                        "blockedPackages" to sanitizedPackages,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "blockingEnabled" to blockingEnabled,
                        "blockedPackages" to sanitizedPackages,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Privacy & Account Data Deletion (Phase 8 Security Compliance) ---

    suspend fun deleteAllUserData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = userDoc(uid)
        val subcollections = listOf("goals", "milestones", "timetable", "tasks", "focusSessions", "blockerConfig")
        try {
            for (sub in subcollections) {
                val snap = userRef.collection(sub).get().await()
                if (!snap.isEmpty) {
                    snap.documents.chunked(400).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { doc -> batch.delete(doc.reference) }
                        batch.commit().await()
                    }
                }
            }
            userRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            throw e
        }
    }
}
