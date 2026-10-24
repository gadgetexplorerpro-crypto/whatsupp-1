# Whatsupp Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Strict User Isolation**: Every document in `/users/{userId}/devices/{deviceId}`, `/users/{userId}/signals/{signalId}`, `/users/{userId}/messages/{messageId}`, and `/users/{userId}/history/{eventId}` belongs strictly to the authenticated Google account (`request.auth != null && request.auth.uid == userId && incoming().userId == request.auth.uid`).
2. **Path Variable Hardening**: Every single-document ID (`userId`, `deviceId`, `signalId`, `messageId`, `eventId`) must match `^[a-zA-Z0-9_\-]+$` with length `<= 128`.
3. **Temporal Integrity**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` objects `<= request.time`. On updates to `devices`, `createdAt` and `userId` are immutable.
4. **Strict Key Allowlists & Size Bounds**: Every `create` and `update` operation enforces `hasOnly(...)` on keys and calls `isValid[Entity](incoming())` to validate field types, enums, numeric bounds, and string lengths.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Device Read**: `unauthDb.collection("users/alice/devices").get()` -> Reject (`request.auth != null`).
2. **Cross-User Device Spying**: Bob (`bob_456`) attempts `get` or `list` on `users/alice_123/devices` -> Reject (`request.auth.uid == userId`).
3. **Spoofed Owner UID on Create**: Alice creates `users/alice_123/devices/dev1` with `userId: "bob_456"` -> Reject (`incoming().userId == request.auth.uid`).
4. **Shadow/Ghost Field Injection**: Alice creates or updates `users/alice_123/devices/dev1` with extra key `isAdmin: true` -> Reject (`keys().hasOnly(...)`).
5. **Invalid Device Role Enum**: Alice writes `role: "HACKER"` instead of `"CONTROLLER"` or `"CONNECTED"` -> Reject (`isValidFamilyDevice`).
6. **Out-of-Bounds Battery Percentage**: Alice writes `batteryLevel: 999` or `-5` -> Reject (`batteryLevel >= 0 && batteryLevel <= 100`).
7. **Out-of-Bounds GPS Coordinates**: Alice writes `latitude: 145.0` or `longitude: -290.0` -> Reject (`latitude >= -90 && latitude <= 90`).
8. **Future Timestamp Injection**: Alice writes `createdAt` in the future (`> request.time`) or as a string/number -> Reject (`createdAt is timestamp && createdAt <= request.time`).
9. **Immutable Field Tampering on Update**: Alice updates `users/alice_123/devices/dev1` and mutates `createdAt` or `userId` -> Reject (`incoming().createdAt == existing().createdAt`).
10. **Append-Only Audit History Tampering**: Alice attempts to `update` or `delete` an audit log in `users/alice_123/history/evt1` -> Reject (`allow update, delete: if false`).
11. **Oversized String Resource Poisoning**: Alice writes a 20,000-character `cipherText` into `messages` -> Reject (`cipherText.size() <= 10000`).
12. **Invalid ID Poisoning**: Alice creates a document with ID containing spaces or special characters -> Reject (`isValidId`).
