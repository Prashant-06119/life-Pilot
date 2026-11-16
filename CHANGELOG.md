# Life Pilot — Changelog

All notable changes across the 10-milestone Phase 5 MVP build and Phases 6–8 verification.

## [1.0.0-mvp] — Launch Candidate

### Added (10 MVP Milestones)
- **Milestone 1 — Project, Theme & App Check Setup:**
  - Configured unique `applicationId = "com.aistudio.lifepilot.kqxwrm"` and `app_name = "Life Pilot"`.
  - Built Dark Navy & Electric Blue primary theme (`#080E1A`, `#0F172A`, `#3B82F6`, `#22D3EE`) with Light Mode support (`Theme.kt`, `Color.kt`) and bundled Space Grotesk, Plus Jakarta Sans, and JetBrains Mono typography (`Type.kt`).
  - Installed `ThemePreferences` via Jetpack DataStore and `configureAppCheck(this, intent)` in `MainActivity.onCreate()` before UI rendering.
  - Added custom adaptive launcher icon and hero banner artwork (`res/drawable/`, `mipmap-*`).
- **Milestone 2 — Authentication & Zero-Trust Rules:**
  - Integrated Google Sign-In exclusively via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` interactive + `GetGoogleIdOption` silent auto sign-in) in `AuthComponents.kt`.
  - Authored `firebase-blueprint.json` and zero-trust `firestore.rules` enforcing owner UID isolation, schema validation, immutable key protection, parent goal gates, and append-only focus sessions.
- **Milestone 3 — Student Onboarding:**
  - Implemented `OnboardingScreen.kt` capturing student name, degree/major, academic year, target career role, and daily study capacity (0.5h–8.0h), persisted cleanly to `/users/{uid}`.
- **Milestone 4 — Career Goals & Milestones:**
  - Implemented `GoalsScreen.kt` and `LifePilotRepository` CRUD + milestone completion tracking, plus `ProgressCalculator` weighted goal progress (60% milestones, 40% linked tasks).
- **Milestone 5 — Study Timetable & Deterministic Scheduling:**
  - Built weekly timetable management (`PlannerScreen.kt` Timetable tab) with `SchedulingEngine.hasTimetableConflict(...)` overlap detection and daily available study capacity calculation.
- **Milestone 6 — Smart Task Manager:**
  - Built prioritized Smart Tasks (`PlannerScreen.kt` Tasks tab) linked to goals/milestones with status transitions (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `MISSED`, `RECOVERED`).
- **Milestone 7 — Adaptive Task Recovery:**
  - Built deterministic `RecoveryEngine.generateRecoveryPlan(...)` distributing missed/overdue tasks across the next 7 open days without exceeding daily target capacity, surfaced via `RecoverySheet.kt` and optionally enriched with empathetic context via `AiPlannerService.enrichRecoveryReasons(...)`.
- **Milestone 8 — Focus Mode Timer:**
  - Built `FocusScreen.kt` and coroutine-driven timer state in `LifePilotViewModel` (15m/25m/45m/60m presets, Start/Pause/Reset/Complete & Log) writing append-only `FocusSession` logs to Firestore.
- **Milestone 9 — Progress Dashboard:**
  - Built `DashboardScreen.kt` with weekly execution ring, focused minutes, active execution streak, missed-task recovery banner, today's prioritized queue, today's timetable preview, and active career goal rollups.
- **Milestone 10 — AI Study Planner & Settings/Privacy:**
  - Built `AiPlannerSheet.kt` powered by Firebase AI Logic (`gemini-3.8-flash`) with structured JSON output, deterministic daily budget clamping, preview-before-apply workflow, and `SettingsPrivacyDialog.kt` (Dark/Light toggle, Privacy & Terms summary, Sign Out, Delete All Cloud Data).
