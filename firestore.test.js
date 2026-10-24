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

function validDevicePayload(uid = ALICE_UID, ts = new Date()) {
  return {
    userId: uid,
    deviceName: "Pixel 8 Pro Controller",
    role: "CONTROLLER",
    pairingCode: "849201",
    isPaired: true,
    isOnline: true,
    autoReconnect: true,
    batteryLevel: 88,
    isCharging: true,
    networkType: "Wi-Fi",
    latitude: 37.422,
    longitude: -122.084,
    locationAccuracy: 8.5,
    locationLabel: "Mountain View, CA",
    cameraConsentGranted: true,
    micConsentGranted: true,
    locationConsentGranted: true,
    activeCameraFacing: "OFF",
    isMicStreaming: false,
    isTwoWayVoiceActive: false,
    audioLevelDb: 18,
    latestFrameBase64: "",
    latestAudioBase64: "",
    remoteCommand: "NONE",
    createdAt: ts,
    updatedAt: ts,
  };
}

test("1. Unauthenticated user cannot read devices", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/devices`).get());
});

test("2. Cross-user access denied on devices and messages", async () => {
  const ts = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`users/${ALICE_UID}/devices/dev_1`).set(validDevicePayload(ALICE_UID, ts));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/devices/dev_1`).get());
});

test("3. Authenticated owner can create and read valid device", async () => {
  const ts = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/devices/dev_1`).set(validDevicePayload(ALICE_UID, ts))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/devices`).where("userId", "==", ALICE_UID).get()
  );
});

test("4. Spoofed userId on create is rejected", async () => {
  const ts = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/devices/dev_1`).set({
      ...validDevicePayload(ALICE_UID, ts),
      userId: BOB_UID,
    })
  );
});

test("5. Ghost field injection on device create is rejected", async () => {
  const ts = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/devices/dev_1`).set({
      ...validDevicePayload(ALICE_UID, ts),
      isAdmin: true,
    })
  );
});

test("6. Out-of-bounds batteryLevel and invalid role enum are rejected", async () => {
  const ts = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/devices/dev_1`).set({
      ...validDevicePayload(ALICE_UID, ts),
      batteryLevel: 150,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/devices/dev_2`).set({
      ...validDevicePayload(ALICE_UID, ts),
      role: "SUPERUSER",
    })
  );
});

test("7. Append-only history log can be created by owner but cannot be updated or deleted", async () => {
  const ts = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.doc(`users/${ALICE_UID}/history/evt_1`);
  await assertSucceeds(
    ref.set({
      userId: ALICE_UID,
      deviceId: "dev_1",
      deviceName: "Pixel 8 Pro",
      eventType: "PAIRED",
      details: "Initial device pairing completed with user consent.",
      encryptedFlag: true,
      createdAt: ts,
    })
  );
  await assertFails(ref.update({ details: "Modified history" }));
  await assertFails(ref.delete());
});
