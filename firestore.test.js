const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

// --- UNAUTHENTICATED DENIAL ---

test("Unauthenticated user: denied on profiles, memories, voice_commands, and sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      key: "Pref",
      content: "Dark mode",
      category: "Geral",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("voice_commands").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

// --- USER PROFILE ---

test("UserProfile: owner create/update succeeds; cross-user & ghost fields & future timestamps fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Mirror JarvisCloudRepository.upsertUserProfile create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Mirror partial update preserving createdAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Pro",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject cross-user read/write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));

  // Reject listing /users
  await assertFails(aliceDb.collection("users").get());

  // Reject shadow/ghost field injection & createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// --- MEMORIES ---

test("CloudMemory: Kotlin-aligned create, list, update, delete succeed; invalid payloads & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Empty subcollection list succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());

  // Create matching JarvisCloudRepository.addMemory (NO `id` field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      key: "Idioma",
      content: "Responder em português",
      category: "Preferência",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      content: "Responder de forma concisa em português",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra @DocumentId `id` field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_2").set({
      id: "mem_2",
      userId: ALICE_UID,
      key: "Extra",
      content: "Should fail",
      category: "Geral",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_3").set({
      userId: ALICE_UID,
      key: "Future",
      content: "Bad ts",
      category: "Geral",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject cross-user read/list/delete
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").delete());

  // Owner delete succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").delete());
});

// --- VOICE COMMANDS ---

test("CloudVoiceCommand: Kotlin-aligned CRUD succeeds; invalid actionType enum fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("voice_commands").doc("cmd_1").set({
      userId: ALICE_UID,
      triggerPhrase: "modo foco",
      actionType: "OFFLINE_RESPONSE",
      actionPayload: "Modo foco ativado",
      enabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Toggle enabled partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("voice_commands").doc("cmd_1").update({
      enabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid enum rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("voice_commands").doc("cmd_2").set({
      userId: ALICE_UID,
      triggerPhrase: "hack",
      actionType: "INVALID_TYPE",
      actionPayload: "payload",
      enabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("voice_commands").get());
});

// --- SESSIONS ---

test("CloudSession: Kotlin-aligned create/update/list/delete succeed; negative count & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_default").set({
      userId: ALICE_UID,
      title: "Conversa Principal",
      messageCount: 4,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_default").update({
      title: "Conversa Principal",
      lastMessagePreview: "Olá Jarvis!",
      messageCount: 6,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get());

  // Negative messageCount fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_bad").set({
      userId: ALICE_UID,
      title: "Bad",
      messageCount: -1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_default").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_default").delete());
});
