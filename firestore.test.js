const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_123";
const BOB_UID = "bob_student_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid) {
  return {
    userId: uid,
    displayName: "Aarav Sharma",
    email: "aarav@college.edu",
    degreeProgram: "B.Tech Computer Science",
    academicYear: "3rd Year",
    targetCareerRole: "Software Engineer",
    dailyStudyHoursTarget: 3.0,
    onboardingCompleted: true,
    preferredDarkMode: true,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validGoal(uid) {
  return {
    userId: uid,
    title: "Crack SWE Summer Internship",
    targetRole: "Backend Engineer",
    targetDateEpochDay: 20800,
    priority: "HIGH",
    status: "ACTIVE",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validTask(uid, goalId = "goal_1") {
  return {
    userId: uid,
    goalId,
    milestoneId: "ms_1",
    title: "Solve 3 Sliding Window DSA problems",
    notes: "Focus on time complexity",
    scheduledEpochDay: 20773,
    estimatedMinutes: 60,
    priority: "HIGH",
    status: "TODO",
    recoveryCount: 0,
    aiReason: "Core placement pattern",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
});

test("Authenticated owner can create and update own profile, cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyStudyHoursTarget: 4.5,
      updatedAt: nowTs(),
    })
  );

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));
});

test("Shadow update with ghost field and immutable field tamper are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: nowTs(),
    })
  );

  // Identity spoofing / email mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      userId: BOB_UID,
      updatedAt: nowTs(),
    })
  );
});

test("Milestone creation requires existing parent goal (Parent Gate)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const orphanMilestone = {
    userId: ALICE_UID,
    goalId: "missing_goal",
    title: "Orphan Milestone",
    description: "Should fail",
    orderIndex: 0,
    targetDateEpochDay: 20790,
    isCompleted: false,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("milestones").doc("ms_1").set(orphanMilestone)
  );

  // Create parent goal first, then milestone succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("goals").doc("goal_1").set(validGoal(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("milestones")
      .doc("ms_1")
      .set({ ...orphanMilestone, goalId: "goal_1" })
  );
});

test("Timetable validates start < end and valid blockType enum", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("timetable").doc("b1").set({
      userId: ALICE_UID,
      title: "Invalid Block",
      dayOfWeek: 1,
      startMinuteOfDay: 600,
      endMinuteOfDay: 540,
      blockType: "CLASS",
      location: "Room 101",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("timetable").doc("b1").set({
      userId: ALICE_UID,
      title: "Operating Systems Lecture",
      dayOfWeek: 1,
      startMinuteOfDay: 540,
      endMinuteOfDay: 600,
      blockType: "CLASS",
      location: "Room 101",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Tasks enforce schema, list isolation, and reject value poisoning", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set(validTask(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  // Cross-user list denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  // Value poisoning: invalid status enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").update({
      status: "INVALID_STATUS",
      updatedAt: nowTs(),
    })
  );

  // Valid recovery update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").update({
      scheduledEpochDay: 20775,
      status: "RECOVERED",
      recoveryCount: 1,
      updatedAt: nowTs(),
    })
  );
});

test("Focus sessions are append-only (update blocked)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("focusSessions").doc("fs_1");

  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      taskId: "t1",
      taskTitle: "Solve 3 Sliding Window DSA problems",
      goalId: "goal_1",
      plannedMinutes: 25,
      actualMinutes: 25,
      completed: true,
      startedEpochMillis: 1731700000000,
      createdAt: nowTs(),
    })
  );

  await assertFails(sessionRef.update({ actualMinutes: 99 }));
});

test("DistractionBlockerConfig enforces owner isolation, default docId, and bounded list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceConfigRef = aliceDb.collection("users").doc(ALICE_UID).collection("blockerConfig").doc("default");

  await assertSucceeds(
    aliceConfigRef.set({
      userId: ALICE_UID,
      blockingEnabled: true,
      blockedPackages: ["com.instagram.android", "com.google.android.youtube"],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(aliceConfigRef.get());

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("blockerConfig").doc("default").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("blockerConfig").doc("default").update({
      blockingEnabled: false,
      updatedAt: nowTs(),
    })
  );

  // Arbitrary doc ID rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("blockerConfig").doc("custom_id").set({
      userId: ALICE_UID,
      blockingEnabled: true,
      blockedPackages: ["com.instagram.android"],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Over 100 packages rejected
  const tooManyApps = Array.from({ length: 101 }, (_, i) => `com.distraction.app${i}`);
  await assertFails(
    aliceConfigRef.update({
      blockedPackages: tooManyApps,
      updatedAt: nowTs(),
    })
  );

  // Ghost field update rejected
  await assertFails(
    aliceConfigRef.update({
      usageLog: ["com.instagram.android"],
      updatedAt: nowTs(),
    })
  );
});

