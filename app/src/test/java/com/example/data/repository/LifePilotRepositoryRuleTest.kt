package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.core.DateUtils
import com.example.domain.engine.RecoveryProposal
import com.example.domain.model.StudyTask
import com.example.domain.model.TaskStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifePilotRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun profileGoalMilestoneTaskAndFocusSession_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = LifePilotRepository(firestore)
        val today = DateUtils.todayEpochDay()

        val profileRes = withTimeout(TIMEOUT_MS) {
            repository.saveUserProfile(
                displayName = "Aarav",
                email = ALICE_EMAIL,
                degreeProgram = "B.Tech CS",
                academicYear = "3rd Year",
                targetCareerRole = "Software Engineer",
                dailyStudyHoursTarget = 3.5,
                onboardingCompleted = true,
                preferredDarkMode = true
            )
        }
        assertTrue("Profile save failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)

        val goalId = withTimeout(TIMEOUT_MS) {
            repository.createCareerGoal(
                title = "Placement Readiness",
                targetRole = "SWE Intern",
                targetDateEpochDay = today + 60,
                priority = "HIGH"
            ).getOrThrow()
        }
        assertTrue(goalId.isNotBlank())

        val milestoneId = withTimeout(TIMEOUT_MS) {
            repository.createMilestone(
                goalId = goalId,
                title = "Master Arrays & Trees",
                description = "Solve 40 curated problems",
                orderIndex = 0L,
                targetDateEpochDay = today + 14
            ).getOrThrow()
        }
        assertTrue(milestoneId.isNotBlank())

        val blockRes = withTimeout(TIMEOUT_MS) {
            repository.createTimetableBlock(
                title = "Data Structures Lecture",
                dayOfWeek = 1,
                startMinuteOfDay = 540,
                endMinuteOfDay = 600,
                blockType = "CLASS",
                location = "LH-2"
            )
        }
        assertTrue("Timetable save failed: ${blockRes.exceptionOrNull()}", blockRes.isSuccess)

        val taskId = withTimeout(TIMEOUT_MS) {
            repository.createTask(
                goalId = goalId,
                milestoneId = milestoneId,
                title = "Binary Tree Traversal Practice",
                notes = "BFS & DFS",
                scheduledEpochDay = today - 1,
                estimatedMinutes = 45,
                priority = "HIGH"
            ).getOrThrow()
        }

        val observedTasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(uid).first { list -> list.any { it.id == taskId } }
        }
        val createdTask = observedTasks.first { it.id == taskId }
        assertEquals(TaskStatus.TODO.name, createdTask.status)

        // Apply recovery proposal
        val recoverRes = withTimeout(TIMEOUT_MS) {
            repository.applyRecoveryProposals(
                listOf(
                    RecoveryProposal(
                        task = createdTask,
                        proposedEpochDay = today,
                        proposedDayLabel = "Today",
                        reason = "Fits open gap today",
                        fitsWithinDailyCap = true
                    )
                )
            )
        }
        assertTrue("Recovery failed: ${recoverRes.exceptionOrNull()}", recoverRes.isSuccess)

        // Log focus session
        val sessionRes = withTimeout(TIMEOUT_MS) {
            repository.logFocusSession(
                taskId = taskId,
                taskTitle = createdTask.title,
                goalId = goalId,
                plannedMinutes = 25,
                actualMinutes = 25,
                completed = true,
                startedEpochMillis = System.currentTimeMillis()
            )
        }
        assertTrue("Focus session failed: ${sessionRes.exceptionOrNull()}", sessionRes.isSuccess)
    }

    @Test
    fun observeTasks_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = LifePilotRepository(firestore)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.createTask(
                goalId = "",
                milestoneId = "",
                title = "Alice Private Prep",
                notes = "",
                scheduledEpochDay = DateUtils.todayEpochDay(),
                estimatedMinutes = 30,
                priority = "MEDIUM"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = LifePilotRepository(firestore)
        val thrown = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeTasks(aliceUid).first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected PERMISSION_DENIED for cross-user read", thrown)
        val fsError = thrown!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
    }

    @Test
    fun observeTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = LifePilotRepository(firestore)

        val thrown = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTasks("some_uid_123").first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected PERMISSION_DENIED when unauthenticated", thrown)
        val fsError = thrown!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
    }

    @Test
    fun createMilestone_withoutParentGoal_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = LifePilotRepository(firestore)
        val res = withTimeout(TIMEOUT_MS) {
            repository.createMilestone(
                goalId = "nonexistent_goal_999",
                title = "Orphan Milestone",
                description = "Blocked by parent gate",
                orderIndex = 0L,
                targetDateEpochDay = DateUtils.todayEpochDay() + 7
            )
        }
        assertTrue(res.isFailure)
        val fsError = res.exceptionOrNull()!!.unwrapFirestoreException()
        assertNotNull(fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_lp@test.com"
        const val BOB_EMAIL = "bob_lp@test.com"
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
