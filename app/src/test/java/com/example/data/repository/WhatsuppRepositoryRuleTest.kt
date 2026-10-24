package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FamilyDevice
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class WhatsuppRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun registerAndObserveDevices_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = WhatsuppRepository(firestore)
        val devId = "dev_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerOrSyncDevice(
                FamilyDevice(
                    id = devId,
                    userId = aliceUid,
                    deviceName = "Main Controller Phone",
                    role = "CONTROLLER",
                    pairingCode = "849201",
                    isPaired = true,
                    batteryLevel = 92L
                )
            )
        }
        assertTrue(createResult.isSuccess)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDevices(aliceUid).first { list -> list.any { it.id == devId } }
        }
        assertTrue(emitted.any { it.id == devId && it.deviceName == "Main Controller Phone" })
    }

    @Test
    fun sendEncryptedMessageAndLogHistory_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = WhatsuppRepository(firestore)

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendEncryptedMessage(
                senderDeviceId = "dev_main",
                senderName = "Main Phone",
                senderRole = "CONTROLLER",
                messageType = "TEXT",
                plainText = "Checking in on Connected Device status"
            )
        }
        assertTrue(msgResult.isSuccess)

        val histResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logConnectionEvent(
                deviceId = "dev_main",
                deviceName = "Main Phone",
                eventType = "PAIRED",
                details = "Both devices paired with user consent."
            )
        }
        assertTrue(histResult.isSuccess)
    }

    @Test
    fun getDeviceById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WhatsuppRepository(firestore)
        val devId = "dev_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.registerOrSyncDevice(
                FamilyDevice(
                    id = devId,
                    userId = aliceUid,
                    deviceName = "Alice Phone",
                    role = "CONTROLLER"
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = WhatsuppRepository(firestore)
        val bobAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getDeviceById(aliceUid, devId)
        }
        assertTrue(bobAttempt.isFailure)
        val fsError = bobAttempt.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    @Test
    fun observeDevices_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = WhatsuppRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeDevices(aliceUid).first()
            }
            fail("Expected exception for unauthenticated user")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
