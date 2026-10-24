package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ConnectionEvent
import com.example.data.model.EncryptedChatMessage
import com.example.data.model.FamilyDevice
import com.example.data.model.WebRtcSignal
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.security.LocalCryptoManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WhatsuppRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // 1. Observe all paired/registered devices under the same Google account
    fun observeDevices(userId: String = requireUserId()): Flow<List<FamilyDevice>> = flow {
        val path = "users/$userId/devices"
        emitAll(
            db.collection("users").document(userId).collection("devices")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyDevice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    // 2. Register or initialize a device in Firestore
    suspend fun registerOrSyncDevice(device: FamilyDevice): Result<String> = runCatching {
        val uid = requireUserId()
        val deviceId = device.id.ifBlank { "dev_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = device.copy(userId = uid).toCreateMap()
                docRef.set(payload).await()
            } else {
                val updateMap = mapOf(
                    "deviceName" to device.deviceName.take(80).ifBlank { "Android Phone" },
                    "role" to if (device.role == "CONNECTED") "CONNECTED" else "CONTROLLER",
                    "pairingCode" to device.pairingCode.take(12).padEnd(6, '0'),
                    "isPaired" to device.isPaired,
                    "isOnline" to device.isOnline,
                    "autoReconnect" to device.autoReconnect,
                    "batteryLevel" to device.batteryLevel.coerceIn(0L, 100L),
                    "isCharging" to device.isCharging,
                    "networkType" to device.networkType.take(40).ifBlank { "Wi-Fi" },
                    "latitude" to device.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to device.longitude.coerceIn(-180.0, 180.0),
                    "locationAccuracy" to device.locationAccuracy.coerceIn(0.0, 10000.0),
                    "locationLabel" to device.locationLabel.take(200),
                    "cameraConsentGranted" to device.cameraConsentGranted,
                    "micConsentGranted" to device.micConsentGranted,
                    "locationConsentGranted" to device.locationConsentGranted,
                    "activeCameraFacing" to device.activeCameraFacing,
                    "isMicStreaming" to device.isMicStreaming,
                    "isTwoWayVoiceActive" to device.isTwoWayVoiceActive,
                    "audioLevelDb" to device.audioLevelDb.coerceIn(0L, 120L),
                    "latestFrameBase64" to device.latestFrameBase64.take(350000),
                    "latestAudioBase64" to device.latestAudioBase64.take(200000),
                    "remoteCommand" to device.remoteCommand.take(64).ifBlank { "NONE" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            deviceId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getDeviceById(targetUserId: String, deviceId: String): Result<FamilyDevice?> = runCatching {
        val docRef = db.collection("users").document(targetUserId).collection("devices").document(deviceId)
        try {
            val snap = docRef.get().await()
            snap.toObject(FamilyDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateDeviceFields(deviceId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        try {
            val payload = (updates + ("updatedAt" to FieldValue.serverTimestamp())).filterValues { it != null }
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // 3. WebRTC Signaling (SDP Offer/Answer & ICE Candidates)
    fun observeSignals(userId: String = requireUserId()): Flow<List<WebRtcSignal>> = flow {
        val path = "users/$userId/signals"
        emitAll(
            db.collection("users").document(userId).collection("signals")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WebRtcSignal::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun sendWebRtcSignal(
        senderDeviceId: String,
        targetDeviceId: String,
        type: String,
        sdpOrCandidate: String,
        mediaMode: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val signalId = "sig_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        val signal = WebRtcSignal(
            id = signalId,
            userId = uid,
            senderDeviceId = senderDeviceId,
            targetDeviceId = targetDeviceId,
            type = type,
            sdpOrCandidate = sdpOrCandidate,
            mediaMode = mediaMode,
            encryptionFingerprint = LocalCryptoManager.generateDtlsFingerprint(uid, senderDeviceId)
        )
        try {
            docRef.set(signal.toCreateMap()).await()
            signalId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // 4. Encrypted Family Chat & Media Vault Messages
    fun observeMessages(userId: String = requireUserId()): Flow<List<EncryptedChatMessage>> = flow {
        val path = "users/$userId/messages"
        emitAll(
            db.collection("users").document(userId).collection("messages")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EncryptedChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun sendEncryptedMessage(
        senderDeviceId: String,
        senderName: String,
        senderRole: String,
        messageType: String,
        plainText: String,
        mediaBase64: String = "",
        durationSec: Long = 0L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("messages").document(msgId)
        val cipherText = LocalCryptoManager.encrypt(plainText, uid)
        val message = EncryptedChatMessage(
            id = msgId,
            userId = uid,
            senderDeviceId = senderDeviceId,
            senderName = senderName,
            senderRole = senderRole,
            messageType = messageType,
            cipherText = cipherText,
            plainPreview = plainText.take(500),
            mediaBase64 = mediaBase64,
            durationSec = durationSec,
            isDelivered = true
        )
        try {
            docRef.set(message.toCreateMap()).await()
            msgId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // 5. Connection History & Privacy Consent Audit Log
    fun observeHistory(userId: String = requireUserId()): Flow<List<ConnectionEvent>> = flow {
        val path = "users/$userId/history"
        emitAll(
            db.collection("users").document(userId).collection("history")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConnectionEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun logConnectionEvent(
        deviceId: String,
        deviceName: String,
        eventType: String,
        details: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("history").document(eventId)
        val event = ConnectionEvent(
            id = eventId,
            userId = uid,
            deviceId = deviceId,
            deviceName = deviceName,
            eventType = eventType,
            details = details,
            encryptedFlag = true
        )
        try {
            docRef.set(event.toCreateMap()).await()
            eventId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDevice(deviceId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAllUserData(userId: String = requireUserId()): Result<Unit> = runCatching {
        val userDoc = db.collection("users").document(userId)

        try {
            val devices = userDoc.collection("devices").get().await()
            for (doc in devices.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        try {
            val signals = userDoc.collection("signals").get().await()
            for (doc in signals.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        try {
            val messages = userDoc.collection("messages").get().await()
            for (doc in messages.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }
}
