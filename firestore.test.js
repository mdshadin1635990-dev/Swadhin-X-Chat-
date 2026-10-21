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
const ADMIN_UID = "admin_owner";

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

// --- TESTS ---

test("Unauthenticated user: cannot read users, channels, or direct_chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("channels").get());
  await assertFails(unauthDb.collection("direct_chats").get());
});

test("Authenticated user: can create own profile but cannot escalate role to admin", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com" }).firestore();
  
  // Normal profile creation succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@test.com",
      displayName: "Alice",
      role: "user",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Self-assigning admin role fails
  await assertFails(
    aliceDb.collection("users").doc("mallory").set({
      userId: "mallory",
      email: "mallory@test.com",
      displayName: "Mallory",
      role: "admin",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: can create and read channels", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com" }).firestore();
  
  await assertSucceeds(
    aliceDb.collection("channels").doc("global_general").set({
      id: "global_general",
      name: "Global Lounge",
      description: "Welcome to international chat!",
      topic: "General",
      isAnnouncementOnly: false,
      createdBy: ALICE_UID,
      messageCount: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("channels").doc("global_general").get());
});

test("Authenticated user: can post in channel and cannot spoof senderId", async () => {
  // Seed channel
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("channels").doc("tech_chat").set({
      id: "tech_chat",
      name: "Tech Chat",
      description: "Talk tech",
      isAnnouncementOnly: false,
      createdBy: "admin",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  
  // Valid message
  await assertSucceeds(
    aliceDb.collection("channels").doc("tech_chat").collection("messages").doc("msg_1").set({
      id: "msg_1",
      channelId: "tech_chat",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hello world!",
      messageType: "text",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Spoofed senderId fails
  await assertFails(
    aliceDb.collection("channels").doc("tech_chat").collection("messages").doc("msg_2").set({
      id: "msg_2",
      channelId: "tech_chat",
      senderId: BOB_UID, // Spoofed!
      senderName: "Bob",
      text: "I am Bob!",
      messageType: "text",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Direct chats: non-participants cannot read", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("direct_chats").doc("alice_bob").set({
      id: "alice_bob",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(charlieDb.collection("direct_chats").doc("alice_bob").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("direct_chats").doc("alice_bob").get());
});
