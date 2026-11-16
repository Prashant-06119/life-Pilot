# LIFE PILOT — PRODUCT REQUIREMENTS DOCUMENT (PRD v1.0)

**Phase:** Phase 3 — Product Definition  
**Positioning:** "Life Pilot – The AI app that turns career goals into daily execution."  
**Target Audience:** College students preparing for internships, campus placements, and early careers.  
**Platform:** Native Android (Kotlin + Jetpack Compose + Material 3).  
**Status:** Active Master Specification for MVP Build (Phases 5–8).

---

## 1. Product Vision & Objectives
- **Vision:** Close the gap between a college student's long-term career ambition and their chaotic day-to-day academic schedule through one unified execution loop (`PLAN -> FOCUS -> EXECUTE -> RECOVER -> ACHIEVE`).
- **Core User Job:** *"When I'm juggling college classes, exams, and career prep, help me turn my career goal into a realistic daily plan, focus on the right task now, see measurable progress, and automatically recover when my schedule breaks."*
- **Primary Product Objectives:**
  1. Onboard a student from zero to an active career goal + timetable + daily tasks in under 3 minutes.
  2. Link every task and focus session upward to a Career Goal Milestone.
  3. Eliminate the "overdue spiral" through 1-tap deterministic + AI task recovery.

---

## 2. Target Personas
1. **Aarav (Final-Year Placement Sprinter):** 21-year-old CS/Engineering undergrad balancing 75% attendance, lab deadlines, and placement prep (DSA, projects, core subjects).
2. **Maya (Multi-Track Career Builder):** 19-year-old undergrad balancing semester classes and skill/portfolio milestones without burning out before exams.

---

## 3. Strict MVP Feature List & MoSCoW Prioritization

### Must-Have (Strict 9-Feature MVP Scope)
1. **Authentication:** Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption`) + silent startup check (`GetGoogleIdOption`) + explicit Sign Out.
2. **Student Onboarding:** Guided setup collecting student name/degree/semester, target career track, target timeline, and daily career-prep capacity (hours/day).
3. **Career Goals & Milestones:** Create, edit, and delete career goals; manage ordered milestones with target dates, completion state, and automatic progress rollups.
4. **Study Timetable:** Weekly Monday–Sunday schedule supporting fixed academic blocks (College Class, Lab, Exam/Other) and scheduled Career Study blocks with overlap validation.
5. **Smart Task Manager:** Create, prioritize (`HIGH`, `MEDIUM`, `LOW`), schedule, complete, mark missed, and delete tasks linked to a career goal/milestone with estimated duration.
6. **Basic Task Recovery:** Detect overdue/missed tasks and propose non-overlapping recovery slots across upcoming days using the deterministic Recovery Engine (with optional Gemini reasoning).
7. **Focus Timer:** Interactive deep-work countdown timer (25m Pomodoro, 50m Deep Work, custom presets) bound to a specific task, logging completed focus sessions and actual minutes into Firestore.
8. **Progress Dashboard:** Unified home command center showing today's priority tasks, upcoming timetable blocks, weekly goal execution rate, focus minutes, active streak, and quick recovery/AI triggers.
9. **Basic AI Planning:** Gemini (`gemini-3.8-flash` via Firebase AI Logic) generating structured career milestone plans, priority explanations, and recovery suggestions—strictly validated by deterministic schedule checks before saving.

### Out of Scope (Explicitly Excluded from MVP v1)
- iOS application
- Advanced system-wide app blocking / Accessibility overlays
- College administration portal
- Social networking / leaderboards / chat
- Complex course/mentor marketplace
- Open-ended multi-year AI career coaching

---

## 4. Functional Requirements & User Stories

| ID | Feature | User Story | Acceptance Criteria |
|---|---|---|---|
| **FR-1** | Auth | As a student, I want 1-tap Google Sign-In so my goals and timetable sync safely. | Auth gate blocks Firestore until signed in; Google Sign-In button works cleanly; Sign Out clears credential state. |
| **FR-2** | Onboarding | As a new user, I want fast setup so I see a personalized plan immediately. | Captures degree/year, target career role, daily prep hours (0.5–6h), and persists `onboardingCompleted = true`. |
| **FR-3** | Career Goals | As a student, I want to break a career goal into milestones. | CRUD on goals and milestones; progress % updates automatically from milestone/task completion. |
| **FR-4** | Timetable | As a student, I want to block fixed college classes so study tasks never collide. | Day selector (Mon–Sun), block creation with start/end time, overlap prevention, visual distinction between Fixed Class vs. Study Block. |
| **FR-5** | Smart Tasks | As a student, I want priority-ordered daily tasks linked to my career milestones. | Filter by Today / Upcoming / Completed / Missed; mark Done or Missed; deterministic priority sorting. |
| **FR-6** | Task Recovery | As a student, when I miss tasks, I want 1-tap realistic rescheduling. | Lists missed/overdue tasks; computes open non-overlapping slots respecting daily hour limits; 1-tap "Accept Recovery" updates task dates and marks `RECOVERED`. |
| **FR-7** | Focus Timer | As a student, I want a distraction-free timer tied to my task. | Start/Pause/Reset/Complete controls; persists `FocusSession` on completion and increments task/dashboard focus minutes. |
| **FR-8** | Dashboard | As a student, I want one glance at today's execution and weekly progress. | Displays execution %, total focus minutes, today's next block/task, missed-task recovery banner when count > 0, and goal progress cards. |
| **FR-9** | AI Study Planner | As a student, I want Gemini to generate a structured study plan for my goal. | Generates structured JSON milestones/tasks within 60s timeout; deterministic engine validates durations/dates; user previews and taps "Apply to Plan". |

---

## 5. Non-Functional Requirements & UI States
- **Visual Identity:** Premium modern AI SaaS aesthetic with **Dark Navy & Electric Blue** primary palette + **Light Mode** companion.
- **UI States:** Every async or collection-backed view implements explicit **Loading**, **Empty**, and **Error** (with Retry) states.
- **Security & Privacy:** Strict user-scoped Firestore isolation (`/users/{uid}/...`), App Check attestation, zero hardcoded API keys, and full in-app Privacy Policy & Account Deletion control.
- **Accessibility & Responsiveness:** Edge-to-edge layout with safe `WindowInsets`, >= 48dp touch targets, adaptive NavigationBar / NavigationRail for wide screens, and complete `testTag` / `contentDescription` coverage.

---

## 6. Success Metrics & Pricing Hypothesis *(Post-Launch Validation)*
- **Activation Rate:** >= 60% of signed-in users complete onboarding + create >= 1 career goal & 3 tasks on Day 1.
- **Weekly Goal Execution Rate:** Completed planned tasks / total planned tasks.
- **Recovery Acceptance Rate:** % of recovery proposals accepted vs. dismissed.
- **Week-4 Retention Target:** >= 25% active weekly execution.
- **Pricing Hypothesis:** Free core execution loop; Pilot Pro tier hypothesis (to be validated post-PMF, disabled until core product & billing compliance are verified).
