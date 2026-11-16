# Phase 10 — Iteration, Metrics & Scaling Framework: Life Pilot

## 1. Weekly Founder Operating Cadence (Mon–Fri)
| Day | Focus | Concrete Actions |
|---|---|---|
| **Monday** | Telemetry & Health Review | Review crash logs, App Check / Firestore latency, AI generation failure rate, and weekly core metrics dashboard. |
| **Tuesday** | Qualitative User Signals | Read 5–10 student feedback notes / Play Store reviews; tag recurring friction in Onboarding, Timetable entry, or Recovery UX. |
| **Wednesday** | Prioritized Shipping | Select top 1 retention bottleneck + 1 polish fix using Impact × Confidence / Effort; ship focused PR/build. |
| **Thursday** | QA & Regression Gate | Run `EnginesUnitTest`, `LifePilotRepositoryRuleTest`, and `firestore.test.js` emulator tests before internal testing release. |
| **Friday** | Cohort Check & Release | Push weekly update to Closed/Open Track; log changes in `CHANGELOG.md`; prep next week's campus feedback focus. |

---

## 2. Core Startup Metrics (7-Metric Scorecard)
1. **Onboarding Completion Rate:** `% of Google-authenticated users who complete OnboardingScreen`. Target: **>= 80%**.
2. **Time-to-First-Roadmap:** Median minutes from first launch to saving 1 Goal + >= 1 Task (manual or AI Planner). Target: **<= 4 min**.
3. **Day-7 Retention:** `% of activated students returning on Day 7`. Target: **>= 35%**.
4. **Weekly Execution Rate:** Average completed vs. planned weekly tasks per active student. Target: **>= 65%**.
5. **Adaptive Recovery Adoption Rate:** `% of missed-task events resolved via 1-Tap Recovery instead of churn`. Target: **>= 50%**.
6. **Focus Minutes per Active Week:** Median logged Pomodoro/deep-work minutes per weekly active student. Target: **>= 120 min/wk**.
7. **AI Plan Apply Rate:** `% of generated AI Study Plans that users tap "Apply to Roadmap" on`. Target: **>= 70%**.

---

## 3. Feature Expansion Gate (Post-MVP Roadmap)
Only promote items from the Phase 3 Non-Goals backlog when Core Metrics #1–#5 hit healthy thresholds:
- **v1.1:** Focus Mode session notifications + Android Home Screen Glance Widget for today's next task.
- **v1.2:** Semester Timetable ICS/Calendar import & automated morning briefing reminder.
- **v1.3:** Pro Tier monetization (unlimited AI monthly replanning, multi-semester degree GPA + career analytics).
- **v2.0:** Cross-platform iOS client sharing the exact `firebase-blueprint.json` Firestore schema & zero-trust rules.

---

## 4. CPO Decision Rubric
Before adding any new screen or button:
- Does it tighten the core loop (**Goal -> Timetable -> Task -> Focus -> Recovery -> Progress**)?
- Does it keep onboarding under 2 minutes and daily planning under 3 taps?
- Can it be enforced by zero-trust Firestore rules without exposing student data?
