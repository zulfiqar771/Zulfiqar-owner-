const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const OWNER_UID = "owner_uid_789";
const OWNER_EMAIL = "fqore2012@gmail.com";

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

test("Unauthenticated users cannot read or write profiles, messages, or appConfig", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("appConfig").doc("settings").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      darkTheme: true,
      suspended: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Regular user can create own non-suspended profile and update own preferences, but cannot escalate privilege", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@gmail.com",
    email_verified: true,
  }).firestore();
  const past = new Date(Date.now() - 5000);

  // Regular user cannot create profile pre-suspended or with ownerNote
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      darkTheme: true,
      suspended: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Valid regular user profile create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@gmail.com",
      darkTheme: true,
      suspended: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Regular user can toggle own darkTheme
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      darkTheme: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Regular user cannot modify suspended or ownerNote or list all users
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      suspended: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      ownerNote: "Forged",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.collection("users").get());
});

test("Email spoofing test: unverified owner email claim is strictly rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      darkTheme: true,
      suspended: false,
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });

  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofDb.collection("users").get());
  await assertFails(spoofDb.collection("users").doc(BOB_UID).get());
  await assertFails(spoofDb.collection("users").doc(BOB_UID).collection("messages").get());
  await assertFails(
    spoofDb.collection("appConfig").doc("settings").set({
      ownerEmail: OWNER_EMAIL,
      announcement: "Spoof",
      ownerDirective: "",
      dailyImageLimit: 8,
      maintenanceMode: false,
      maintenanceMessage: "",
      updatedBy: OWNER_EMAIL,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Verified owner (fqore2012@gmail.com) can list users, inspect/delete messages, suspend users, and manage appConfig", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      darkTheme: true,
      suspended: false,
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
    await adminDb.collection("users").doc(BOB_UID).collection("messages").doc("msg_1").set({
      userId: BOB_UID,
      role: "USER",
      text: "مرحبا",
      generatedImage: false,
      createdAt: new Date(Date.now() - 4000),
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  // Owner can list all users & inspect any user's messages
  await assertSucceeds(ownerDb.collection("users").orderBy("updatedAt", "desc").get());
  await assertSucceeds(
    ownerDb.collection("users").doc(BOB_UID).collection("messages").orderBy("createdAt", "asc").get()
  );

  // Owner can suspend/unsuspend and add an ownerNote
  await assertSucceeds(
    ownerDb.collection("users").doc(BOB_UID).update({
      suspended: true,
      ownerNote: "موقوف مؤقتاً",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner can delete any user's message
  await assertSucceeds(
    ownerDb.collection("users").doc(BOB_UID).collection("messages").doc("msg_1").delete()
  );

  // Owner can write global appConfig/settings
  await assertSucceeds(
    ownerDb.collection("appConfig").doc("settings").set({
      ownerEmail: OWNER_EMAIL,
      announcement: "أهلاً بالجميع في التحديث الجديد ✨",
      ownerDirective: "تحدث باللهجة العراقية اللطيفة دائماً",
      dailyImageLimit: 12,
      maintenanceMode: false,
      maintenanceMessage: "التطبيق تحت الصيانة",
      updatedBy: OWNER_EMAIL,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Regular user can read appConfig/settings (single doc get) but cannot write or list
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(bobDb.collection("appConfig").doc("settings").get());
  await assertFails(bobDb.collection("appConfig").get());
  await assertFails(
    bobDb.collection("appConfig").doc("settings").update({
      dailyImageLimit: 50,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Regular user cannot read another user's profile or messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      darkTheme: true,
      suspended: false,
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
    await adminDb.collection("users").doc(BOB_UID).collection("messages").doc("msg_bob").set({
      userId: BOB_UID,
      role: "USER",
      text: "خاص",
      generatedImage: false,
      createdAt: new Date(Date.now() - 4000),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@gmail.com",
    email_verified: true,
  }).firestore();

  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("messages").get());
});
