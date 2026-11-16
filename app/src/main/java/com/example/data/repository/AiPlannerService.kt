package com.example.data.repository

import android.util.Log
import com.example.domain.engine.RecoveryProposal
import com.example.domain.model.Priority
import com.example.domain.model.StudyTask
import com.example.domain.model.TimetableBlock
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiMilestoneDraft(
    val title: String,
    val description: String,
    val tasks: List<AiTaskDraft>
)

data class AiTaskDraft(
    val title: String,
    val notes: String,
    val estimatedMinutes: Int,
    val dayOffset: Int,
    val priority: String,
    val aiReason: String
)

data class AiStudyPlanDraft(
    val goalTitle: String,
    val strategySummary: String,
    val milestones: List<AiMilestoneDraft>
)

class AiPlannerService {

    private val studyPlanSchema = Schema.obj(
        properties = mapOf(
            "goalTitle" to Schema.string(description = "Concise career goal title"),
            "strategySummary" to Schema.string(description = "2-sentence execution strategy tailored to student schedule"),
            "milestones" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Milestone title"),
                        "description" to Schema.string(description = "Concrete outcome for this milestone"),
                        "tasks" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "title" to Schema.string(description = "Specific study task title"),
                                    "notes" to Schema.string(description = "Actionable execution hint"),
                                    "estimatedMinutes" to Schema.integer(description = "Duration between 20 and 120 minutes"),
                                    "dayOffset" to Schema.integer(description = "Day offset from today (0 to 13)"),
                                    "priority" to Schema.enumeration(
                                        values = listOf("HIGH", "MEDIUM", "LOW"),
                                        description = "Priority level"
                                    ),
                                    "aiReason" to Schema.string(description = "Brief reason explaining why this priority fits the goal")
                                )
                            ),
                            description = "2 to 3 concrete tasks for this milestone"
                        )
                    )
                ),
                description = "3 ordered career milestones"
            )
        )
    )

    private val recoveryAdviceSchema = Schema.obj(
        properties = mapOf(
            "recommendations" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "taskId" to Schema.string(description = "Exact task ID from input"),
                        "reason" to Schema.string(description = "Empathetic, realistic 1-sentence reason for rescheduling this task")
                    )
                )
            )
        )
    )

    private val studyPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studyPlanSchema
            }
        )
    }

    private val recoveryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recoveryAdviceSchema
            }
        )
    }

    suspend fun generateStudyPlan(
        targetRole: String,
        degreeProgram: String,
        dailyStudyHours: Double,
        horizonWeeks: Int,
        timetableBlocks: List<TimetableBlock>
    ): Result<AiStudyPlanDraft> {
        val dailyCapMinutes = (dailyStudyHours.coerceIn(0.5, 12.0) * 60.0).toInt()
        val classSummary = if (timetableBlocks.isEmpty()) {
            "No fixed college classes logged yet."
        } else {
            timetableBlocks.groupBy { it.dayOfWeek }.entries.sortedBy { it.key }.joinToString("; ") { (dow, list) ->
                "Day $dow: ${list.size} fixed blocks"
            }
        }

        val prompt = """
            You are Life Pilot, an AI study planner for college students.
            Create a realistic, non-overwhelming career execution plan for:
            - Target Career Role: $targetRole
            - Degree Program: $degreeProgram
            - Daily Career Prep Budget: $dailyCapMinutes minutes/day ($dailyStudyHours hrs)
            - Timeline: $horizonWeeks weeks
            - Academic Schedule Density: $classSummary

            Rules:
            - Generate exactly 3 ordered milestones with 2-3 specific tasks each (6-8 tasks total).
            - Keep each task estimatedMinutes between 25 and 120 minutes, never exceeding $dailyCapMinutes total minutes on the same dayOffset.
            - Spread dayOffset across 0..13 realistically around college coursework.
            - Explain priority concisely in aiReason.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                studyPlanModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("AI returned an empty study plan.")
            val root = JSONObject(jsonText)
            val goalTitle = root.optString("goalTitle", "$targetRole Roadmap").trim().ifBlank { "$targetRole Roadmap" }
            val strategySummary = root.optString("strategySummary", "Structured execution plan aligned with your college schedule.")
            val milestonesArr = root.getJSONArray("milestones")
            val milestones = mutableListOf<AiMilestoneDraft>()

            // Deterministic daily budget clamping to enforce Phase 8 AI QA safety
            val assignedMinutesByDay = mutableMapOf<Int, Int>()

            for (i in 0 until milestonesArr.length()) {
                val msObj = milestonesArr.getJSONObject(i)
                val msTitle = msObj.optString("title", "Milestone ${i + 1}").trim()
                val msDesc = msObj.optString("description", "").trim()
                val tasksArr = msObj.optJSONArray("tasks")
                val tasks = mutableListOf<AiTaskDraft>()

                if (tasksArr != null) {
                    for (j in 0 until tasksArr.length()) {
                        val tObj = tasksArr.getJSONObject(j)
                        val estMin = tObj.optInt("estimatedMinutes", 45).coerceIn(20, 120)
                        var offset = tObj.optInt("dayOffset", i * 3 + j).coerceIn(0, 13)
                        // Ensure deterministic daily capacity cap is respected
                        while ((assignedMinutesByDay[offset] ?: 0) + estMin > dailyCapMinutes && offset < 13) {
                            offset++
                        }
                        assignedMinutesByDay[offset] = (assignedMinutesByDay[offset] ?: 0) + estMin

                        val priority = Priority.fromString(tObj.optString("priority", "MEDIUM")).name
                        val safeTaskTitle = tObj.optString("title", "Focused Study Session")
                            .trim()
                            .take(140)
                            .ifBlank { "Focused Study Session" }
                        tasks.add(
                            AiTaskDraft(
                                title = safeTaskTitle,
                                notes = tObj.optString("notes", "").trim().take(400),
                                estimatedMinutes = estMin,
                                dayOffset = offset,
                                priority = priority,
                                aiReason = tObj.optString("aiReason", "Aligned with $targetRole milestone").trim().take(300)
                            )
                        )
                    }
                }
                if (msTitle.isNotBlank()) {
                    milestones.add(AiMilestoneDraft(msTitle.take(140), msDesc.take(400), tasks))
                }
            }

            if (milestones.isEmpty()) {
                throw IllegalStateException("AI did not generate valid milestones. Please try again.")
            }
            Result.success(AiStudyPlanDraft(goalTitle, strategySummary, milestones))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI study plan generation timed out", e)
            Result.failure(Exception("AI planning timed out after 60 seconds. Check connection and retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI study plan generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Unable to generate AI study plan."))
        }
    }

    suspend fun enrichRecoveryReasons(
        proposals: List<RecoveryProposal>
    ): Map<String, String> {
        if (proposals.isEmpty()) return emptyMap()
        val summary = proposals.joinToString("\n") { p ->
            "- taskId=${p.task.id} | title=${p.task.title} | minutes=${p.task.estimatedMinutes} | priority=${p.task.priority} | newDay=${p.proposedDayLabel}"
        }
        val prompt = """
            A college student missed these study tasks and our deterministic scheduler placed them into open timetable gaps:
            $summary
            Write one brief, supportive 1-sentence reason per taskId explaining why this recovery order/day makes sense.
        """.trimIndent()

        return try {
            val response = withTimeout(30_000L) {
                recoveryModel.generateContent(prompt)
            }
            val text = response.text ?: return emptyMap()
            val root = JSONObject(text)
            val arr = root.optJSONArray("recommendations") ?: return emptyMap()
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("taskId")
                    val reason = obj.optString("reason")
                    if (id.isNotBlank() && reason.isNotBlank()) {
                        put(id, reason.take(300))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AI recovery enrichment skipped; falling back to deterministic reasons: ${e.message}")
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "AiPlannerService"
    }
}
