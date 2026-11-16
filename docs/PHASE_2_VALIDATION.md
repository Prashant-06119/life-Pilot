# LIFE PILOT — PHASE 2: VALIDATION KIT & GO/NO-GO FRAMEWORK

**Status:** Active — Ready for Founder Execution  
**Phase Objective:** Prove real student demand and willingness-to-pay *before* writing production code.  
**Validation Targets:**
- 30 student interviews (checkpoint after first 10: >= 6 strong pain signals).
- >= 10% landing-page visitor-to-waitlist conversion.
- 3 serious pre-orders, deposits, or committed pilot pledges (where appropriate).
- 15 committed initial pilot users.

> **Anti-Bias Rule:** Never ask *"Would you use an app that does X?"* Students politely say yes to hypothetical AI apps. Ask only about **past behavior, current workarounds, missed goals, and actual money/time spent**.

---

## 1. Student Interview Questionnaire (15–20 Minutes, Past-Behavior Only)

### Screening (1 min)
- Degree, current year/semester, primary smartphone OS (confirm Android usage), and current career/internship goal.

### Past Execution & Academic Collision (8 min)
1. *Walk me through the last week of college—when was the last time your academic coursework collided with your career/placement prep? What happened?*
2. *Think back to the last time you set a career or skill goal (e.g., DSA, portfolio project, exam prep) and fell behind. Which exact day did the plan break, and what did you do next?*
3. *How many hours did you intend to spend on career prep last week versus how many hours did you actually execute? Where did the missing hours go?*
4. *When you miss a planned study block today, how do you reschedule it—if at all?*

### Current Tools & Workarounds (5 min)
5. *Open your phone and show me what apps or notebooks you actually used yesterday to plan your day (Calendar, TickTick, Notion, Forest, MyStudyLife, WhatsApp self-chat, paper).*
6. *Which planner or study app did you download in the last 6 months and stop using? Why did you quit it?*
7. *How much money have you spent in the last 12 months on study apps, courses, test series, or productivity tools?*

### Closing & Commitment Test (3 min)
8. *What is the hardest 10-minute problem in your daily routine that you would pay to make disappear this semester?*
9. *(Only at the end)* Ask for commitment—not compliments: *"We are recruiting 15 students for an early Android pilot next month. Would you like to join the pilot group and test a 5-minute prototype this week?"*

---

## 2. Interview Recording Template

Copy per interview (`INT-01` through `INT-30`):

| Field | Entry |
|---|---|
| **Interview ID / Date** | `INT-__` · `YYYY-MM-DD` |
| **Student Profile** | Year · Major · College Tier · Android Device |
| **Primary Career Goal** | e.g., Summer Internship / Campus Placement / Gate / Portfolio |
| **Last Plan Breakdown Story** | Specific event from past 14 days (verbatim summary) |
| **Current Tool Stack** | Exact apps shown on phone |
| **Previous App Churn** | App abandoned + exact reason |
| **Pain Severity (1–5)** | 1 = Mild annoyance · 5 = Missed deadlines / active stress / paid workaround |
| **Strong Pain Signal? (Y/N)** | Marked `Y` only if student experienced plan breakdown >= 2x/month AND tried fixing it |
| **Verified Quotes** | Exact words only (no paraphrased hype) |
| **Pilot / WTP Signal** | Joined waitlist? Agreed to prototype call? Stated max monthly price? |

---

## 3. Pain-Point Analysis Framework

Score each recurring pain theme across the 30 interviews:

| Pain Theme | Mention Count (/30) | Avg Severity (1–5) | Workaround Exists? (Y/N) | Paid Today? | Priority |
|---|---:|---:|---|---|---|
| Academic vs. career schedule collision | | | | | |
| Overdue task spiral / no recovery | | | | | |
| High timetable setup friction | | | | | |
| Phone distraction during study blocks | | | | | |
| Unclear daily steps for career goal | | | | | |

**Decision Rule:** Only build MVP features targeting pains cited by **>= 50% of interviewees** with average severity **>= 3.5/5**.

---

## 4. Competitor Feedback Collection Framework

Whenever an interviewee mentions **MyStudyLife, Forest, StudySmarter, TickTick, Shovel, Notion, or Google Calendar**, capture:
1. **Trigger to Install:** What specific problem made them try it?
2. **Active Usage Duration:** < 1 week / 1–4 weeks / > 1 month.
3. **Failure Mode:** *Setup too tedious* / *Too rigid when college got busy* / *No career roadmap* / *Too expensive* / *Distracted anyway*.
4. **Switching Trigger:** What would make them switch vs. keep their current free habit?

---

## 5. Landing Page Copy (Waitlist Test)

### Hero Section
- **Eyebrow:** Built for College Students Preparing for Placements & Careers
- **Headline:** Turn Career Goals Into Daily Execution—Even When College Gets Chaotic.
- **Subheadline:** Life Pilot connects your fixed college timetable, career milestones, daily tasks, focus timer, and 1-tap missed-task recovery in one native Android app.
- **Primary CTA:** `Join the Android Early Access Waitlist`
- **Micro-copy:** Free pilot access for the first 100 students · No spam · Android first.

### Problem vs. Solution Strip
- **Why Static Planners Fail:** One surprise lab or late lecture breaks your whole week—and red overdue lists kill your motivation.
- **How Life Pilot Works:**
  1. **Set Your Career Goal:** Break internships or placement prep into clear weekly milestones.
  2. **Fit Around Real Classes:** Keep fixed college lectures and labs locked; fill open gaps automatically.
  3. **Focus & Measure:** Track deep-work sessions directly against milestone progress.
  4. **Recover Without Guilt:** Miss a block? Life Pilot recalculates open slots in one tap.

### Waitlist Form Fields (Keep <= 3 Fields for >= 10% Conversion)
1. Email address
2. Current college year & target career role
3. Optional: *"What is your #1 daily planning struggle?"*

---

## 6. Waitlist Strategy

- **Target:** >= **10% visitor-to-signup conversion** across at least 150–200 targeted student visits (yielding 15–20+ qualified waitlist leads).
- **Zero-Budget Distribution Channels for Test Traffic:**
  1. Share in 3–5 relevant college WhatsApp/Discord groups and student placement communities (value-first post asking for feedback, not spam).
  2. Direct message 30 interviewed students + 2nd-degree referrals.
  3. Short 30-second demo reel/post on LinkedIn/Reddit feedback threads (`r/Btechtards`, `r/IndianAcademia`, `r/ProductivityApps` where allowed).
- **Instrumentation:** Track unique visitors, form starts, completed signups, and referral source via UTM tags.

---

## 7. Clickable Prototype Testing Script (10 Minutes)

Show a lightweight 5-screen clickable wireframe (**Onboarding -> Career Goal -> Timetable + Tasks -> Focus Timer -> Missed-Task Recovery**). Give tasks, **do not explain buttons**:

1. **Task 1 (Goal Clarity):** *"You want to prepare for a software internship in 4 months. Show me how you'd set that up here."* -> Observe hesitation or confusion.
2. **Task 2 (Academic Collision):** *"You have college labs 2–5 PM on Tuesdays. How would you check when you can study today?"* -> Observe timetable legibility.
3. **Task 3 (Recovery Test — Core Wedge):** *"Imagine you missed yesterday's 2-hour coding block because of an assignment. What do you expect this 'Recover Plan' button to do?"* -> Observe trust in AI/deterministic rescheduling.
4. **Post-Test Debrief:** *"Which screen felt unnecessary? Which screen would you actually open every morning?"*

---

## 8. Willingness-to-Pay (WTP) Experiment

Avoid vague *"How much would you pay?"* questions. Run a **Van Westendorp + Fake-Door Tier Check** after the prototype walkthrough:

1. **Anchored Pricing Cards (Test Both INR & USD Segments as Applicable):**
   - **Free Core:** Timetable, tasks, focus timer, 1 career goal.
   - **Pilot Pro (Hypothesis):** ₹99/mo (~$1.99–$3.99/mo global student tier) — Unlimited AI study plans, adaptive missed-task recovery, full analytics.
2. **Commitment Question:** *"If Pilot Pro launches next month at ₹99/month (or $3.99/month), would you:*
   - *A) Stick strictly to Free*
   - *B) Try Free first, unlikely to pay*
   - *C) Pre-commit ₹99 / $3 deposit today for 3 months of Pro + priority onboarding?"*
3. **Target Signal:** At least **3 serious pre-order/deposit commitments** or **>= 20% selecting paid tier interest** among active pain-signal students.

---

## 9. Why Students Might Reject Life Pilot (Pre-Mortem Risks)

Do **not** assume positive demand. Actively test these five rejection hypotheses during interviews:
1. **"Google Calendar + WhatsApp is free and good enough":** Switching cost outweighs perceived benefit if onboarding takes > 3 minutes.
2. **Manual Timetable Fatigue:** Students hate typing 25 weekly classes manually—if setup feels like homework, Day-1 retention collapses.
3. **AI Distrust / Unrealistic Plans:** If AI suggests 4 hours of DSA on an exam eve, students lose trust immediately.
4. **Low Student Willingness-to-Pay:** Indian/global students expect productivity tools to be completely free (or bundled with college/YouTube resources).
5. **Notification Blindness & Guilt:** Students ignore reminder spam when stressed; recovery must feel supportive, not nagging.

---

## 10. Go / No-Go Decision Framework & Validation Report Template

### Go / No-Go Thresholds

| Metric | Green Light (GO to Phase 3) | Yellow Light (PIVOT Scope/Wedge) | Red Light (STOP / Rethink) |
|---|---|---|---|
| **Strong Pain Signals (First 10)** | >= 6 / 10 | 3–5 / 10 | <= 2 / 10 |
| **Total Interviews Completed** | 30 | 15–29 | < 15 |
| **Landing Page Conversion** | >= 10% | 5%–9% | < 5% |
| **Committed Pilot Users** | >= 15 | 8–14 | < 8 |
| **WTP / Pre-Commitments** | >= 3 deposits or strong WTP | Free demand only (adjust monetization) | Zero urgency to switch |

### Validation Report Template *(Fill After Interviews & Waitlist)*

```markdown
# LIFE PILOT — VALIDATION REPORT

## 1. Summary Decision: [ GO | PIVOT | NO-GO ]
- Rationale in 3 sentences: ...

## 2. Verified Evidence (Observed Facts Only)
- Interviews completed: __ / 30
- Strong pain signals in first 10: __ / 10 (Total across 30: __ / 30)
- Landing page visitors: __ | Waitlist signups: __ | Conversion: __%
- Prototype task completion rate: __%
- Committed pilot users: __ / 15 | Paid pre-orders/deposits: __ / 3
- Top 3 verbatim pain quotes: ...
- Top 3 observed reasons students rejected or hesitated: ...

## 3. Assumptions Still Unproven
- [ ] Day-7 retention after onboarding
- [ ] Real acceptance rate of AI recovery proposals
- [ ] Long-term free-to-paid conversion

## 4. Product Decisions for Phase 3 (PRD v1)
- Features validated to KEEP in MVP: ...
- Features to CUT or SIMPLIFY based on feedback: ...
- Monetization hypothesis update: ...
```
