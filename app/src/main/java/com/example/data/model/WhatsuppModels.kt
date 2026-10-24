package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FamilyDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val role: String = "CONTROLLER", // "CONTROLLER" or "CONNECTED"
    val pairingCode: String = "842910",
    val isPaired: Boolean = false,
    val isOnline: Boolean = true,
    val autoReconnect: Boolean = true,
    val batteryLevel: Long = 100L,
    val isCharging: Boolean = false,
    val networkType: String = "Wi-Fi",
    val latitude: Double = 37.4220,
    val longitude: Double = -122.0841,
    val locationAccuracy: Double = 5.0,
    val locationLabel: String = "Mountain View, CA",
    val cameraConsentGranted: Boolean = false,
    val micConsentGranted: Boolean = false,
    val locationConsentGranted: Boolean = false,
    val activeCameraFacing: String = "OFF", // "OFF", "FRONT", "BACK"
    val isMicStreaming: Boolean = false,
    val isTwoWayVoiceActive: Boolean = false,
    val audioLevelDb: Long = 0L,
    val latestFrameBase64: String = "",
    val latestAudioBase64: String = "",
    val remoteCommand: String = "NONE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "deviceName" to deviceName.take(80).ifBlank { "Android Phone" },
        "role" to if (role == "CONNECTED") "CONNECTED" else "CONTROLLER",
        "pairingCode" to pairingCode.take(12).padEnd(6, '0'),
        "isPaired" to isPaired,
        "isOnline" to isOnline,
        "autoReconnect" to autoReconnect,
        "batteryLevel" to batteryLevel.coerceIn(0L, 100L),
        "isCharging" to isCharging,
        "networkType" to networkType.take(40).ifBlank { "Wi-Fi" },
        "latitude" to latitude.coerceIn(-90.0, 90.0),
        "longitude" to longitude.coerceIn(-180.0, 180.0),
        "locationAccuracy" to locationAccuracy.coerceIn(0.0, 10000.0),
        "locationLabel" to locationLabel.take(200),
        "cameraConsentGranted" to cameraConsentGranted,
        "micConsentGranted" to micConsentGranted,
        "locationConsentGranted" to locationConsentGranted,
        "activeCameraFacing" to if (activeCameraFacing in listOf("OFF", "FRONT", "BACK")) activeCameraFacing else "OFF",
        "isMicStreaming" to isMicStreaming,
        "isTwoWayVoiceActive" to isTwoWayVoiceActive,
        "audioLevelDb" to audioLevelDb.coerceIn(0L, 120L),
        "latestFrameBase64" to latestFrameBase64.take(350000),
        "latestAudioBase64" to latestAudioBase64.take(200000),
        "remoteCommand" to remoteCommand.take(64).ifBlank { "NONE" },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class WebRtcSignal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderDeviceId: String = "",
    val targetDeviceId: String = "",
    val type: String = "OFFER", // "OFFER", "ANSWER", "ICE_CANDIDATE", "BYE", "PING"
    val sdpOrCandidate: String = "",
    val mediaMode: String = "CAMERA_BACK", // "CAMERA_FRONT", "CAMERA_BACK", "AUDIO_STREAM", "TWO_WAY_VOICE"
    val encryptionFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "senderDeviceId" to senderDeviceId.take(128),
        "targetDeviceId" to targetDeviceId.take(128),
        "type" to if (type in listOf("OFFER", "ANSWER", "ICE_CANDIDATE", "BYE", "PING")) type else "PING",
        "sdpOrCandidate" to sdpOrCandidate.take(60000).ifBlank { "v=0" },
        "mediaMode" to if (mediaMode in listOf("CAMERA_FRONT", "CAMERA_BACK", "AUDIO_STREAM", "TWO_WAY_VOICE")) mediaMode else "CAMERA_BACK",
        "encryptionFingerprint" to encryptionFingerprint.take(256).ifBlank { "sha-256 AA:BB:CC:DD" },
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class EncryptedChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderDeviceId: String = "",
    val senderName: String = "",
    val senderRole: String = "CONTROLLER",
    val messageType: String = "TEXT", // "TEXT", "VOICE_NOTE", "SNAPSHOT", "LOCATION_PIN", "SYSTEM_ALERT"
    val cipherText: String = "",
    val plainPreview: String = "",
    val mediaBase64: String = "",
    val durationSec: Long = 0L,
    val isDelivered: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "senderDeviceId" to senderDeviceId.take(128),
        "senderName" to senderName.take(80).ifBlank { "Family Phone" },
        "senderRole" to if (senderRole == "CONNECTED") "CONNECTED" else "CONTROLLER",
        "messageType" to if (messageType in listOf("TEXT", "VOICE_NOTE", "SNAPSHOT", "LOCATION_PIN", "SYSTEM_ALERT")) messageType else "TEXT",
        "cipherText" to cipherText.take(10000).ifBlank { "ENC" },
        "plainPreview" to plainPreview.take(500).ifBlank { "Encrypted message" },
        "mediaBase64" to mediaBase64.take(300000),
        "durationSec" to durationSec.coerceIn(0L, 600L),
        "isDelivered" to isDelivered,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ConnectionEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val eventType: String = "CONNECTED",
    val details: String = "",
    val encryptedFlag: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "deviceId" to deviceId.take(128),
        "deviceName" to deviceName.take(80).ifBlank { "Family Device" },
        "eventType" to if (eventType in listOf(
                "PAIRED", "CONNECTED", "RECONNECTED", "CAMERA_SESSION",
                "MIC_SESSION", "VOICE_CALL", "LOCATION_SYNC", "CONSENT_CHANGED", "BATTERY_ALERT"
            )
        ) eventType else "CONNECTED",
        "details" to details.take(500).ifBlank { "Device event logged" },
        "encryptedFlag" to encryptedFlag,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
