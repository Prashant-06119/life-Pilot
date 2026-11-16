# Phase 8 — Launch Preparation, QA & Security Audit: Life Pilot

## 1. Automated Verification Results
- **Android Build (`compile_applet`):** PASSED (`com.aistudio.lifepilot.kqxwrm`, `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`).
- **Domain Engine & App Resource Unit Tests (`EnginesUnitTest` + `ExampleRobolectricTest`):**
  - `schedulingEngine_detectsOverlapAndFindsOpenGaps` — **PASSED**
  - `recoveryEngine_reschedulesMissedTasksWithinDailyCap` — **PASSED**
  - `progressCalculator_computesGoalAndDashboardMetrics` — **PASSED**
  - `ExampleRobolectricTest.read string from context` (`"Life Pilot"`) — **PASSED**
- **Tier 1 Firestore Rules Emulator Suite (`node --test firestore.test.js`):** **7 / 7 PASSED**
  - Unauthenticated access blocked across all collections.
  - Owner profile read/write & cross-user isolation enforced.
  - Shadow updates with ghost fields & immutable field tampering rejected.
  - Milestone Parent Gate (`exists(...)` on parent goal) enforced.
  - Timetable `startMinuteOfDay < endMinuteOfDay` & enum validation enforced.
  - Task schema, list isolation, status enum validation, and recovery updates enforced.
  - Focus sessions append-only (`update` blocked) enforced.
- **Tier 2 Kotlin Repository & Rules Suite (`LifePilotRepositoryRuleTest`):** **4 / 4 PASSED**
  - Authenticated owner end-to-end CRUD (`profile`, `goal`, `milestone`, `task`, `focusSession`) succeeded.
  - Unauthenticated `observeTasks` rejected with `PERMISSION_DENIED`.
  - Cross-user `observeTasks` rejected with `PERMISSION_DENIED`.
  - Milestone creation without existing parent goal rejected with `PERMISSION_DENIED`.
- **Cloud Rules Deployment (`DeployRules`):** Deployed to Firebase project `gen-lang-client-0655113029`.

---

## 2. Security & Auth Invariant Audit
- **Google Sign-In Only:** Verified `0` usages of `signInAnonymously()` or `signInWithEmailAndPassword()` in `app/src/main/`. Auth uses Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + separate `GetGoogleIdOption` auto-sign-in) exclusively.
- **Firestore Database Targeting:** Verified `0` parameterless `FirebaseFirestore.getInstance()` or `Firebase.firestore` calls; every instance resolves `R.string.firestore_database_id` (`ai-studio-android-lifepilo-22407d43-e959-4dca-97bc-e02a3ddf6408`).
- **App Check Wiring:** `configureAppCheck(this, intent)` runs in `MainActivity.onCreate()` immediately after `super.onCreate()` and before UI or ViewModel initialization (Debug provider in debug builds, reCAPTCHA provider in release builds).
- **UID Isolation:** Authenticated ViewModel is keyed by `key(activeUser.uid)` inside `LifePilotApp.kt`, preventing state leakage across sign-out or account switches.

---

## 3. AI QA & Reliability Guardrails
- **Model:** `gemini-3.8-flash` via Firebase AI Logic (`Firebase.ai.generativeModel`), requiring zero embedded API keys.
- **Structured JSON Output:** Schema-constrained responses (`studyPlanSchema`, `recoveryAdviceSchema`) with `withTimeout` guards (60s for study plan generation, 30s for recovery reason enrichment).
- **Deterministic Capacity Clamping:** `AiPlannerService` clamps daily assigned minutes so generated plans never overload a single day past the student's configured `dailyStudyHoursTarget`.
- **Preview-Before-Commit:** Users inspect generated milestones and tasks inside `AiPlannerSheet` before tapping **Apply to Roadmap**.
- **Deterministic Fallback:** Recovery works 100% deterministically via `RecoveryEngine` even if AI enrichment is unavailable or offline.

---

## 4. Google Play Policy & Data Safety Checklist
- **Least-Privilege Permissions:** `AndroidManifest.xml` declares only `INTERNET` and `ACCESS_NETWORK_STATE`. No dangerous storage, location, camera, or background permissions are requested.
- **App Metadata:** Short, policy-compliant title (`Life Pilot`, 10 chars) without buzzwords, emojis, or ALL CAPS. Custom adaptive launcher icon configured across all densities.
- **In-App Data Control & Deletion (`SettingsPrivacyDialog.kt`):**
  - Displays clear Privacy & Terms summary directly in-app.
  - Supports **Sign Out** (clearing Credential Manager state + Firebase Auth sign-out).
  - Supports **Delete All Cloud Data** (`repository.deleteAllUserData()`), purging subcollections (`tasks`, `milestones`, `goals`, `timetable`, `focusSessions`) and `/users/{uid}` in Cloud Firestore.
- **Data Safety Disclosure Alignment:** Data collected (Name, Email from Google Sign-In; student goals, schedule, tasks, and focus logs) is encrypted in transit, tied strictly to the user's Google UID, never sold, and deletable in-app.
