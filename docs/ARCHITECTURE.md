# LIFE PILOT — SYSTEM ARCHITECTURE & DATA DESIGN (v1.0)

**Phase:** Phase 4 — System Architecture  
**Stack:** Native Android (Kotlin + Jetpack Compose + Material 3) · Firebase Auth (Google Sign-In via Credential Manager) · Cloud Firestore (Named Database) · Firebase AI Logic (`gemini-3.8-flash`) · Firebase App Check  
**Pattern:** Modular Monolith (MVVM + Pure Kotlin Domain Engines + User-Isolated Firestore Repositories)

---

## 1. High-Level System Architecture

```
+-------------------------------------------------------------------------+
|                    COMPOSE PRESENTATION LAYER (M3)                      |
|  AuthGate -> Onboarding -> MainScaffold (NavBar / NavRail)              |
|  [Dashboard]   [Roadmap/Goals]   [Planner: Timetable+Tasks]   [Focus]   |
|  Modal Flows: [AI Study Planner Sheet]  [Adaptive Recovery Sheet]       |
+------------------------------------+------------------------------------+
                                     | StateFlow<UiState<T>>
                                     v
+-------------------------------------------------------------------------+
|                       VIEWMODEL LAYER (MVVM)                            |
|  Auth/ProfileVM · DashboardVM · GoalsVM · PlannerVM · FocusVM · AiPlanVM|
+------------+-----------------------+-----------------------+------------+
             |                       |                       |
             v                       v                       v
+------------------------+ +-------------------+ +------------------------+
|  DETERMINISTIC ENGINES | |  REPOSITORIES     | |  FIREBASE AI SERVICE   |
|  • SchedulingEngine    | |  • UserProfileRepo| |  • Lazy GenerativeModel|
|  • RecoveryEngine      | |  • GoalRepo       | |  • Schema.obj JSON     |
|  • ProgressCalculator  | |  • TimetableRepo  | |  • 60s Coroutine       |
|  (100% Unit-Testable)  | |  • TaskRepo       | |    Timeout + Errors    |
+------------------------+ |  • FocusRepo      | +-----------+------------+
                           +---------+---------+             |
                                     |                       |
                                     v                       v
+-------------------------------------------------------------------------+
|                 FIREBASE CLOUD (USER-ISOLATED + APP CHECK)              |
|  • Google Auth (Credential Manager) -> Request Auth Context             |
|  • Named Firestore DB (`R.string.firestore_database_id`)                |
|  • Hardened Zero-Trust Rules (`/users/{userId}/...`)                    |
|  • Firebase AI Logic (`gemini-3.8-flash`) + Debug/reCAPTCHA App Check   |
+-------------------------------------------------------------------------+
```

---

## 2. Firestore Database Schema (User-Isolated Subcollections)

All user records are strictly isolated beneath `/users/{userId}`:

1. **`/users/{userId}` (`UserProfile`)**
   - `userId: String`, `displayName: String`, `email: String`, `degreeProgram: String`, `academicYear: String`, `targetCareerRole: String`, `dailyStudyHoursTarget: Double` (0.5–8.0), `onboardingCompleted: Boolean`, `createdAt: Timestamp`, `updatedAt: Timestamp`.
2. **`/users/{userId}/goals/{goalId}` (`CareerGoal`)**
   - `userId: String`, `title: String`, `targetRole: String`, `targetDateEpochDay: Long`, `priority: String` (`HIGH` | `MEDIUM` | `LOW`), `status: String` (`ACTIVE` | `COMPLETED` | `ARCHIVED`), `createdAt: Timestamp`, `updatedAt: Timestamp`.
3. **`/users/{userId}/milestones/{milestoneId}` (`GoalMilestone`)**
   - `userId: String`, `goalId: String`, `title: String`, `description: String`, `orderIndex: Long`, `targetDateEpochDay: Long`, `isCompleted: Boolean`, `createdAt: Timestamp`, `updatedAt: Timestamp`.
4. **`/users/{userId}/timetable/{blockId}` (`TimetableBlock`)**
   - `userId: String`, `title: String`, `dayOfWeek: Long` (1=Mon..7=Sun), `startMinuteOfDay: Long` (0..1439), `endMinuteOfDay: Long` (1..1440), `blockType: String` (`CLASS` | `LAB` | `EXAM` | `STUDY`), `location: String`, `createdAt: Timestamp`, `updatedAt: Timestamp`.
5. **`/users/{userId}/tasks/{taskId}` (`StudyTask`)**
   - `userId: String`, `goalId: String`, `milestoneId: String`, `title: String`, `notes: String`, `scheduledEpochDay: Long`, `estimatedMinutes: Long`, `priority: String` (`HIGH` | `MEDIUM` | `LOW`), `status: String` (`TODO` | `IN_PROGRESS` | `COMPLETED` | `MISSED` | `RECOVERED`), `recoveryCount: Long`, `aiReason: String`, `createdAt: Timestamp`, `updatedAt: Timestamp`.
6. **`/users/{userId}/focusSessions/{sessionId}` (`FocusSession`)**
   - `userId: String`, `taskId: String`, `taskTitle: String`, `goalId: String`, `plannedMinutes: Long`, `actualMinutes: Long`, `completed: Boolean`, `startedEpochMillis: Long`, `createdAt: Timestamp`.

---

## 3. Deterministic Scheduling & Recovery Engine Contracts

### `SchedulingEngine` (Pure Kotlin)
- **Timetable Collision Guard:** Rejects any new `TimetableBlock` overlapping an existing block on the same `dayOfWeek`.
- **Daily Capacity Calculation:** For any `dayOfWeek` / `epochDay`, computes `availableStudyMinutes = (dailyStudyHoursTarget * 60) - scheduledTaskMinutes`, bounded by open gaps outside fixed `CLASS`/`LAB`/`EXAM` blocks.
- **Task Priority Ranking:** Orders daily tasks by `(status urgency -> priority weight -> overdue age -> milestone order)`.

### `RecoveryEngine` (Pure Kotlin)
- Identifies all tasks with `status == MISSED` or `(status == TODO && scheduledEpochDay < todayEpochDay)`.
- Scans the next 7 days (`today..today+6`), calculates remaining daily study capacity per day after accounting for fixed academic blocks and already-scheduled tasks, and assigns each missed task to the earliest feasible day without exceeding daily capacity limits.
- Produces a previewable `List<RecoveryProposal>` (`task`, `proposedEpochDay`, `reason`, `fitsCapacity: Boolean`) so the student can inspect and apply in one tap.

---

## 4. AI Service Architecture (Firebase AI Logic + Deterministic Validation)
- **Model:** `gemini-3.8-flash` via `Firebase.ai.generativeModel(...)` initialized lazily (`by lazy`).
- **App Check Wiring:** `configureAppCheck(this, intent)` called as the very first setup step in `MainActivity.onCreate()` before `setContent`, passing `intent` for debug token extraction and using `.commit()` synchronously.
- **Structured JSON Output:** Uses `Schema.obj(...)` + `responseMimeType = "application/json"` for:
  1. **Career Study Plan Generation:** Inputs = target role, timeline weeks, daily study hours, existing milestones -> Outputs = structured milestones + prioritized tasks with estimated minutes and priority rationale.
  2. **AI Recovery Advice:** Inputs = missed tasks + next 7 days' open capacity -> Outputs = prioritized recovery rationale validated and constrained by `RecoveryEngine`.
- **Resilience:** `withTimeout(60_000L)`, cooperative `CancellationException` rethrow, explicit `UiState.Error` surfacing, and deterministic engine clamping (e.g., capping task duration 15–180m and total daily load within target capacity).

---

## 5. Security, Auth & Rules Verification Strategy
- **Auth Invariant:** `AppNavigation` gates all feature screens behind `Firebase.auth` user state. Google Sign-In uses `CredentialManager` (`GetSignInWithGoogleOption` for interactive button, `GetGoogleIdOption` for silent startup check). Zero anonymous or email/password auth in `app/src/main/`.
- **Zero-Trust `firestore.rules`:** Default deny catch-all, `isSignedIn()`, `request.auth.uid == userId`, `isValidId()`, strict `isValid[Entity](incoming())` field type/length/enum checks, immutable `createdAt` and `userId`, and server timestamp upper bounds (`<= request.time`).
- **Two-Tier Rules Testing (Mandatory Before `DeployRules`):**
  1. Tier 1: `firestore.test.js` via `@firebase/rules-unit-testing` against local Firestore Emulator (port `8085`).
  2. Tier 2: Kotlin Robolectric `*RuleTest.kt` extending `FirestoreEmulatorTestBase` testing actual Repository methods against Auth (`9099`) & Firestore (`8085`) emulators.
