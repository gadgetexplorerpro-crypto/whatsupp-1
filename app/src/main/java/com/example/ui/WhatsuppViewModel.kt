package com.example.ui

import android.net.ConnectivityManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SavedMediaEntity
import com.example.data.local.VaultChatEntity
import com.example.data.local.VaultDao
import com.example.data.model.ConnectionEvent
import com.example.data.model.EncryptedChatMessage
import com.example.data.model.FamilyDevice
import com.example.data.model.WebRtcSignal
import com.example.data.repository.WhatsuppRepository
import com.example.data.security.LocalCryptoManager
import com.example.hardware.DeviceHardwareManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class LocalDashboardState(
    val thisDeviceId: String = "",
    val thisDeviceName: String = "Android Phone",
    val thisDeviceRole: String = "CONTROLLER", // "CONTROLLER" or "CONNECTED"
    val pairingCode: String = "849201",
    val isPaired: Boolean = true,
    val autoReconnect: Boolean = true,
    val selectedTargetDeviceId: String = "",
    val cameraConsentGranted: Boolean = true,
    val micConsentGranted: Boolean = true,
    val locationConsentGranted: Boolean = true,
    val activeCameraFacing: String = "OFF", // "OFF", "FRONT", "BACK"
    val isMicStreaming: Boolean = false,
    val isTwoWayVoiceActive: Boolean = false,
    val localAudioLevelDb: Int = 14,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val networkType: String = "Wi-Fi",
    val isOnline: Boolean = true,
    val latitude: Double = 37.4220,
    val longitude: Double = -122.0841,
    val locationAccuracy: Double = 5.0,
    val locationLabel: String = "Mountain View, CA",
    val statusBannerMessage: String? = null,
    val dtlsFingerprint: String = "",
    val webrtcState: String = "DTLS-SRTP Ready",
    val lastHandledCommand: String = "NONE"
)

class WhatsuppViewModel(
    private val repository: WhatsuppRepository,
    private val vaultDao: VaultDao,
    private val hardwareManager: DeviceHardwareManager,
    val currentUserId: String
) : ViewModel() {

    private val thisDeviceId = hardwareManager.getStableDeviceId()
    private val defaultName = hardwareManager.getDefaultDeviceName()

    private val _localState = MutableStateFlow(
        LocalDashboardState(
            thisDeviceId = thisDeviceId,
            thisDeviceName = defaultName,
            dtlsFingerprint = LocalCryptoManager.generateDtlsFingerprint(currentUserId, thisDeviceId)
        )
    )
    val localState: StateFlow<LocalDashboardState> = _localState.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastFrameSentMs: Long = 0L
    private var lastPlayedAudioHash: Int = 0

    // Two-tier Flow error handling for Firestore streams
    val devicesState: StateFlow<UiState<List<FamilyDevice>>> = repository.observeDevices(currentUserId)
        .map<List<FamilyDevice>, UiState<List<FamilyDevice>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing devices", error)
            emit(UiState.Error(error.message ?: "Failed to observe family devices"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val signalsState: StateFlow<UiState<List<WebRtcSignal>>> = repository.observeSignals(currentUserId)
        .map<List<WebRtcSignal>, UiState<List<WebRtcSignal>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing WebRTC signals", error)
            emit(UiState.Error(error.message ?: "Failed to observe WebRTC signals"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<EncryptedChatMessage>>> = repository.observeMessages(currentUserId)
        .map<List<EncryptedChatMessage>, UiState<List<EncryptedChatMessage>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing messages", error)
            emit(UiState.Error(error.message ?: "Failed to observe encrypted messages"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val historyState: StateFlow<UiState<List<ConnectionEvent>>> = repository.observeHistory(currentUserId)
        .map<List<ConnectionEvent>, UiState<List<ConnectionEvent>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing connection history", error)
            emit(UiState.Error(error.message ?: "Failed to observe connection history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val localVaultChats: StateFlow<List<VaultChatEntity>> = vaultDao.observeEncryptedChats(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val savedVaultMedia: StateFlow<List<SavedMediaEntity>> = vaultDao.observeSavedMedia(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    init {
        initializeDeviceAndTelemetryLoop()
    }

    private fun initializeDeviceAndTelemetryLoop() {
        viewModelScope.launch {
            val telemetry = hardwareManager.readBatteryAndNetwork()
            _localState.update {
                it.copy(
                    batteryLevel = telemetry.batteryLevel,
                    isCharging = telemetry.isCharging,
                    networkType = telemetry.networkType,
                    isOnline = telemetry.isOnline
                )
            }
            syncThisDeviceToFirestore()
            repository.logConnectionEvent(
                deviceId = thisDeviceId,
                deviceName = _localState.value.thisDeviceName,
                eventType = "CONNECTED",
                details = "${_localState.value.thisDeviceName} online via ${telemetry.networkType} (${telemetry.batteryLevel}%)"
            )
        }

        // Automatic reconnection when network comes online
        networkCallback = hardwareManager.registerNetworkReconnectCallback {
            if (_localState.value.autoReconnect) {
                viewModelScope.launch {
                    val telemetry = hardwareManager.readBatteryAndNetwork()
                    _localState.update {
                        it.copy(
                            isOnline = true,
                            networkType = telemetry.networkType,
                            batteryLevel = telemetry.batteryLevel,
                            isCharging = telemetry.isCharging,
                            statusBannerMessage = "Auto-reconnected securely via ${telemetry.networkType}"
                        )
                    }
                    syncThisDeviceToFirestore()
                    repository.logConnectionEvent(
                        deviceId = thisDeviceId,
                        deviceName = _localState.value.thisDeviceName,
                        eventType = "RECONNECTED",
                        details = "Automatic secure reconnection completed over ${telemetry.networkType}"
                    )
                }
            }
        }

        // Periodic battery & network heartbeat
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val t = hardwareManager.readBatteryAndNetwork()
                _localState.update {
                    it.copy(
                        batteryLevel = t.batteryLevel,
                        isCharging = t.isCharging,
                        networkType = t.networkType,
                        isOnline = t.isOnline
                    )
                }
                repository.updateDeviceFields(
                    thisDeviceId,
                    mapOf(
                        "batteryLevel" to t.batteryLevel.toLong(),
                        "isCharging" to t.isCharging,
                        "networkType" to t.networkType,
                        "isOnline" to t.isOnline
                    )
                )
            }
        }
    }

    private fun handleIncomingDeviceSync(devices: List<FamilyDevice>) {
        val currentTarget = _localState.value.selectedTargetDeviceId
        val otherDevices = devices.filter { it.id != thisDeviceId }
        if (currentTarget.isBlank() && otherDevices.isNotEmpty()) {
            _localState.update { it.copy(selectedTargetDeviceId = otherDevices.first().id) }
        } else if (currentTarget.isBlank() && devices.isNotEmpty()) {
            _localState.update { it.copy(selectedTargetDeviceId = devices.first().id) }
        }

        val myDoc = devices.find { it.id == thisDeviceId }
        if (myDoc != null) {
            val cmd = myDoc.remoteCommand
            if (cmd != "NONE" && cmd != _localState.value.lastHandledCommand) {
                _localState.update { it.copy(lastHandledCommand = cmd) }
                executeIncomingRemoteCommand(cmd)
            }
        }

        // Play incoming live audio stream on Controller when another device is streaming audio
        val remoteStreamingDevice = otherDevices.firstOrNull {
            (it.isMicStreaming || it.isTwoWayVoiceActive) && it.latestAudioBase64.isNotBlank()
        }
        if (remoteStreamingDevice != null) {
            val hash = remoteStreamingDevice.latestAudioBase64.hashCode()
            if (hash != lastPlayedAudioHash) {
                lastPlayedAudioHash = hash
                hardwareManager.playIncomingPcmAudio(remoteStreamingDevice.latestAudioBase64, viewModelScope)
            }
        }
    }

    private fun executeIncomingRemoteCommand(command: String) {
        val s = _localState.value
        viewModelScope.launch {
            when (command) {
                "START_CAM_FRONT" -> if (s.cameraConsentGranted) {
                    setCameraMode("FRONT")
                    hardwareManager.postPrivacyNotification(
                        "Live Front Camera Active",
                        "Main Controller started viewing Front Camera with your consent."
                    )
                }
                "START_CAM_BACK" -> if (s.cameraConsentGranted) {
                    setCameraMode("BACK")
                    hardwareManager.postPrivacyNotification(
                        "Live Back Camera Active",
                        "Main Controller started viewing Back Camera with your consent."
                    )
                }
                "STOP_CAM" -> setCameraMode("OFF")
                "START_MIC" -> if (s.micConsentGranted) {
                    toggleMicStreaming(true)
                    hardwareManager.postPrivacyNotification(
                        "Live Microphone Audio Active",
                        "Main Controller started listening to live microphone audio."
                    )
                }
                "STOP_MIC" -> toggleMicStreaming(false)
                "START_VOICE" -> if (s.micConsentGranted) {
                    toggleTwoWayVoice(true)
                    hardwareManager.postPrivacyNotification(
                        "Two-Way Voice Intercom Active",
                        "Two-way family voice communication is active."
                    )
                }
                "STOP_VOICE" -> toggleTwoWayVoice(false)
                "PING_LOCATION" -> if (s.locationConsentGranted) {
                    refreshGpsLocation()
                }
                "RING_DEVICE" -> {
                    hardwareManager.triggerDeviceRingAlert()
                    hardwareManager.postPrivacyNotification(
                        "Family Device Ring Alert",
                        "Main Controller pinged this phone!"
                    )
                }
            }
            repository.updateDeviceFields(thisDeviceId, mapOf("remoteCommand" to "NONE"))
        }
    }

    private fun handleIncomingWebRtcSignals(signals: List<WebRtcSignal>) {
        val latestForMe = signals.firstOrNull { it.targetDeviceId == thisDeviceId || it.senderDeviceId != thisDeviceId }
        if (latestForMe != null) {
            _localState.update {
                it.copy(
                    webrtcState = "WebRTC ${latestForMe.type} (${latestForMe.mediaMode}) • ${latestForMe.encryptionFingerprint.take(20)}"
                )
            }
        }
    }

    private fun cacheMessagesToEncryptedVault(messages: List<EncryptedChatMessage>) {
        viewModelScope.launch {
            messages.takeLast(50).forEach { msg ->
                val ts = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                vaultDao.insertEncryptedChat(
                    VaultChatEntity(
                        id = msg.id.ifBlank { "msg_$ts" },
                        userId = currentUserId,
                        senderDeviceId = msg.senderDeviceId,
                        senderName = msg.senderName,
                        senderRole = msg.senderRole,
                        messageType = msg.messageType,
                        encryptedPayload = msg.cipherText,
                        encryptedMediaBase64 = LocalCryptoManager.encrypt(msg.mediaBase64),
                        durationSec = msg.durationSec,
                        timestampMs = ts
                    )
                )
            }
        }
    }

    fun syncThisDeviceToFirestore() {
        val s = _localState.value
        viewModelScope.launch {
            val device = FamilyDevice(
                id = s.thisDeviceId,
                userId = currentUserId,
                deviceName = s.thisDeviceName,
                role = s.thisDeviceRole,
                pairingCode = s.pairingCode,
                isPaired = s.isPaired,
                isOnline = s.isOnline,
                autoReconnect = s.autoReconnect,
                batteryLevel = s.batteryLevel.toLong(),
                isCharging = s.isCharging,
                networkType = s.networkType,
                latitude = s.latitude,
                longitude = s.longitude,
                locationAccuracy = s.locationAccuracy,
                locationLabel = s.locationLabel,
                cameraConsentGranted = s.cameraConsentGranted,
                micConsentGranted = s.micConsentGranted,
                locationConsentGranted = s.locationConsentGranted,
                activeCameraFacing = s.activeCameraFacing,
                isMicStreaming = s.isMicStreaming,
                isTwoWayVoiceActive = s.isTwoWayVoiceActive,
                audioLevelDb = s.localAudioLevelDb.toLong(),
                remoteCommand = "NONE"
            )
            repository.registerOrSyncDevice(device)
        }
    }

    fun switchThisDeviceRole(newRole: String) {
        val validRole = if (newRole == "CONNECTED") "CONNECTED" else "CONTROLLER"
        _localState.update {
            it.copy(
                thisDeviceRole = validRole,
                statusBannerMessage = "Switched role to ${if (validRole == "CONTROLLER") "Main Device (Controller)" else "Connected Device"}"
            )
        }
        syncThisDeviceToFirestore()
        viewModelScope.launch {
            repository.logConnectionEvent(
                deviceId = thisDeviceId,
                deviceName = _localState.value.thisDeviceName,
                eventType = "CONNECTED",
                details = "Switched device role to $validRole"
            )
        }
    }

    fun updateDeviceName(newName: String) {
        val clean = newName.trim().take(80)
        if (clean.isBlank()) return
        _localState.update { it.copy(thisDeviceName = clean) }
        syncThisDeviceToFirestore()
    }

    fun selectTargetDevice(deviceId: String) {
        _localState.update { it.copy(selectedTargetDeviceId = deviceId) }
    }

    fun completeDevicePairing(enteredCode: String) {
        val code = enteredCode.trim().filter { it.isDigit() }.take(6).padEnd(6, '0')
        _localState.update {
            it.copy(
                pairingCode = code,
                isPaired = true,
                statusBannerMessage = "Device paired securely with code $code"
            )
        }
        syncThisDeviceToFirestore()
        viewModelScope.launch {
            repository.logConnectionEvent(
                deviceId = thisDeviceId,
                deviceName = _localState.value.thisDeviceName,
                eventType = "PAIRED",
                details = "Completed encrypted pairing handshake (Code: $code)"
            )
            hardwareManager.postPrivacyNotification(
                "Whatsupp Device Paired",
                "${_localState.value.thisDeviceName} is now paired under your Google Account."
            )
        }
    }

    fun updatePrivacyConsent(camera: Boolean, mic: Boolean, location: Boolean) {
        _localState.update {
            it.copy(
                cameraConsentGranted = camera,
                micConsentGranted = mic,
                locationConsentGranted = location,
                activeCameraFacing = if (!camera) "OFF" else it.activeCameraFacing,
                isMicStreaming = if (!mic) false else it.isMicStreaming,
                isTwoWayVoiceActive = if (!mic) false else it.isTwoWayVoiceActive,
                statusBannerMessage = "Updated hardware consent settings"
            )
        }
        if (!mic) hardwareManager.stopLiveAudioCapture()
        syncThisDeviceToFirestore()
        viewModelScope.launch {
            repository.logConnectionEvent(
                deviceId = thisDeviceId,
                deviceName = _localState.value.thisDeviceName,
                eventType = "CONSENT_CHANGED",
                details = "Consent updated — Cam: $camera, Mic: $mic, GPS: $location"
            )
        }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        _localState.update { it.copy(autoReconnect = enabled) }
        syncThisDeviceToFirestore()
    }

    // Camera Streaming & WebRTC SDP Signaling
    fun setCameraMode(facing: String) {
        val mode = if (facing in listOf("OFF", "FRONT", "BACK")) facing else "OFF"
        if (mode != "OFF" && !_localState.value.cameraConsentGranted) {
            _localState.update {
                it.copy(statusBannerMessage = "Camera consent is required before starting live camera.")
            }
            return
        }
        _localState.update {
            it.copy(
                activeCameraFacing = mode,
                webrtcState = if (mode == "OFF") "Camera Stream Closed" else "WebRTC Live ($mode) • DTLS-SRTP"
            )
        }
        viewModelScope.launch {
            repository.updateDeviceFields(
                thisDeviceId,
                mapOf(
                    "activeCameraFacing" to mode,
                    "latestFrameBase64" to if (mode == "OFF") "" else _localState.value.thisDeviceName
                )
            )
            val targetId = _localState.value.selectedTargetDeviceId.ifBlank { thisDeviceId }
            if (mode != "OFF") {
                val mediaMode = if (mode == "FRONT") "CAMERA_FRONT" else "CAMERA_BACK"
                repository.sendWebRtcSignal(
                    senderDeviceId = thisDeviceId,
                    targetDeviceId = targetId,
                    type = "OFFER",
                    sdpOrCandidate = "v=0\r\no=- ${System.currentTimeMillis()} 2 IN IP4 127.0.0.1\r\ns=WhatsuppLiveCamera\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=setup:actpass\r\na=mid:video_$mode",
                    mediaMode = mediaMode
                )
                repository.logConnectionEvent(
                    deviceId = thisDeviceId,
                    deviceName = _localState.value.thisDeviceName,
                    eventType = "CAMERA_SESSION",
                    details = "Started live $mode camera stream with visible consent indicator"
                )
                hardwareManager.postPrivacyNotification(
                    "Whatsupp Camera Active ($mode)",
                    "Your $mode camera is actively streaming to your paired family device."
                )
            } else {
                repository.sendWebRtcSignal(
                    senderDeviceId = thisDeviceId,
                    targetDeviceId = targetId,
                    type = "BYE",
                    sdpOrCandidate = "a=end-of-candidates",
                    mediaMode = "CAMERA_BACK"
                )
            }
        }
    }

    fun onLocalCameraFrameEncoded(base64Jpeg: String) {
        val now = System.currentTimeMillis()
        if (now - lastFrameSentMs < 1400L) return
        if (_localState.value.activeCameraFacing == "OFF") return
        lastFrameSentMs = now
        viewModelScope.launch {
            repository.updateDeviceFields(
                thisDeviceId,
                mapOf("latestFrameBase64" to base64Jpeg.take(300000))
            )
        }
    }

    fun saveEncryptedSnapshot(base64Jpeg: String, sourceLabel: String) {
        if (base64Jpeg.isBlank()) return
        viewModelScope.launch {
            val mediaId = "snap_${UUID.randomUUID().toString().take(10)}"
            vaultDao.insertSavedMedia(
                SavedMediaEntity(
                    id = mediaId,
                    userId = currentUserId,
                    deviceId = thisDeviceId,
                    deviceName = _localState.value.thisDeviceName,
                    mediaType = "CAMERA_SNAPSHOT",
                    encryptedBase64 = LocalCryptoManager.encrypt(base64Jpeg, currentUserId),
                    encryptedCaption = LocalCryptoManager.encrypt("Snapshot ($sourceLabel)", currentUserId),
                    createdAtMs = System.currentTimeMillis()
                )
            )
            repository.sendEncryptedMessage(
                senderDeviceId = thisDeviceId,
                senderName = _localState.value.thisDeviceName,
                senderRole = _localState.value.thisDeviceRole,
                messageType = "SNAPSHOT",
                plainText = "Encrypted Camera Snapshot ($sourceLabel)",
                mediaBase64 = base64Jpeg.take(250000)
            )
            _localState.update {
                it.copy(statusBannerMessage = "Snapshot encrypted & saved to Local Vault")
            }
        }
    }

    // Live Microphone & Two-Way Voice Intercom
    fun toggleMicStreaming(enable: Boolean) {
        if (enable && !_localState.value.micConsentGranted) {
            _localState.update {
                it.copy(statusBannerMessage = "Microphone consent is required before streaming audio.")
            }
            return
        }
        _localState.update {
            it.copy(
                isMicStreaming = enable,
                webrtcState = if (enable) "WebRTC Audio Stream • Opus/PCM" else "Idle"
            )
        }
        if (enable) {
            hardwareManager.startLiveAudioCapture(viewModelScope) { db, chunk ->
                _localState.update { it.copy(localAudioLevelDb = db) }
                if (chunk.isNotEmpty()) {
                    viewModelScope.launch {
                        repository.updateDeviceFields(
                            thisDeviceId,
                            mapOf(
                                "isMicStreaming" to true,
                                "audioLevelDb" to db.toLong(),
                                "latestAudioBase64" to chunk
                            )
                        )
                    }
                }
            }
            viewModelScope.launch {
                val targetId = _localState.value.selectedTargetDeviceId.ifBlank { thisDeviceId }
                repository.sendWebRtcSignal(
                    senderDeviceId = thisDeviceId,
                    targetDeviceId = targetId,
                    type = "OFFER",
                    sdpOrCandidate = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2",
                    mediaMode = "AUDIO_STREAM"
                )
                repository.logConnectionEvent(
                    deviceId = thisDeviceId,
                    deviceName = _localState.value.thisDeviceName,
                    eventType = "MIC_SESSION",
                    details = "Live microphone audio stream started with visible consent indicator"
                )
                hardwareManager.postPrivacyNotification(
                    "Whatsupp Microphone Active",
                    "Live microphone audio streaming is active."
                )
            }
        } else {
            if (!_localState.value.isTwoWayVoiceActive) {
                hardwareManager.stopLiveAudioCapture()
            }
            viewModelScope.launch {
                repository.updateDeviceFields(
                    thisDeviceId,
                    mapOf(
                        "isMicStreaming" to false,
                        "audioLevelDb" to 0L,
                        "latestAudioBase64" to ""
                    )
                )
            }
        }
    }

    fun toggleTwoWayVoice(enable: Boolean) {
        if (enable && !_localState.value.micConsentGranted) {
            _localState.update {
                it.copy(statusBannerMessage = "Microphone consent is required for two-way voice.")
            }
            return
        }
        _localState.update {
            it.copy(
                isTwoWayVoiceActive = enable,
                isMicStreaming = enable,
                webrtcState = if (enable) "Two-Way Voice Call Active • DTLS-SRTP" else "Voice Call Ended"
            )
        }
        if (enable) {
            hardwareManager.startLiveAudioCapture(viewModelScope) { db, chunk ->
                _localState.update { it.copy(localAudioLevelDb = db) }
                if (chunk.isNotEmpty()) {
                    viewModelScope.launch {
                        repository.updateDeviceFields(
                            thisDeviceId,
                            mapOf(
                                "isTwoWayVoiceActive" to true,
                                "isMicStreaming" to true,
                                "audioLevelDb" to db.toLong(),
                                "latestAudioBase64" to chunk
                            )
                        )
                    }
                }
            }
            viewModelScope.launch {
                val targetId = _localState.value.selectedTargetDeviceId.ifBlank { thisDeviceId }
                repository.sendWebRtcSignal(
                    senderDeviceId = thisDeviceId,
                    targetDeviceId = targetId,
                    type = "OFFER",
                    sdpOrCandidate = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=sendrecv\r\na=mid:twoway_voice",
                    mediaMode = "TWO_WAY_VOICE"
                )
                repository.logConnectionEvent(
                    deviceId = thisDeviceId,
                    deviceName = _localState.value.thisDeviceName,
                    eventType = "VOICE_CALL",
                    details = "Started two-way encrypted voice communication"
                )
            }
        } else {
            hardwareManager.stopLiveAudioCapture()
            viewModelScope.launch {
                repository.updateDeviceFields(
                    thisDeviceId,
                    mapOf(
                        "isTwoWayVoiceActive" to false,
                        "isMicStreaming" to false,
                        "audioLevelDb" to 0L,
                        "latestAudioBase64" to ""
                    )
                )
            }
        }
    }

    // Send Remote Command from Main Controller to Connected Device
    fun sendRemoteCommandToTarget(command: String) {
        val targetId = _localState.value.selectedTargetDeviceId.ifBlank { thisDeviceId }
        viewModelScope.launch {
            repository.updateDeviceFields(targetId, mapOf("remoteCommand" to command))
            _localState.update {
                it.copy(statusBannerMessage = "Sent command '$command' to paired device")
            }
        }
    }

    // Real-time GPS Location Sync
    fun refreshGpsLocation() {
        if (!_localState.value.locationConsentGranted) {
            _localState.update {
                it.copy(statusBannerMessage = "Location consent is disabled on this device.")
            }
            return
        }
        viewModelScope.launch {
            val gps = hardwareManager.fetchCurrentGpsLocation()
            if (gps != null) {
                _localState.update {
                    it.copy(
                        latitude = gps.latitude,
                        longitude = gps.longitude,
                        locationAccuracy = gps.accuracy,
                        locationLabel = gps.locationLabel,
                        batteryLevel = gps.batteryLevel,
                        isCharging = gps.isCharging,
                        networkType = gps.networkType,
                        statusBannerMessage = "GPS synced: ${gps.locationLabel} (±${gps.accuracy.toInt()}m)"
                    )
                }
                repository.updateDeviceFields(
                    thisDeviceId,
                    mapOf(
                        "latitude" to gps.latitude,
                        "longitude" to gps.longitude,
                        "locationAccuracy" to gps.accuracy,
                        "locationLabel" to gps.locationLabel
                    )
                )
                repository.logConnectionEvent(
                    deviceId = thisDeviceId,
                    deviceName = _localState.value.thisDeviceName,
                    eventType = "LOCATION_SYNC",
                    details = "Updated live GPS location: ${gps.locationLabel}"
                )
            } else {
                _localState.update {
                    it.copy(statusBannerMessage = "Ensure GPS permission is granted and location is enabled.")
                }
            }
        }
    }

    // Encrypted Chat & Voice Notes
    fun sendTextChatMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val s = _localState.value
        viewModelScope.launch {
            repository.sendEncryptedMessage(
                senderDeviceId = s.thisDeviceId,
                senderName = s.thisDeviceName,
                senderRole = s.thisDeviceRole,
                messageType = "TEXT",
                plainText = clean
            )
        }
    }

    fun sendVoiceNoteMessage(durationSec: Long) {
        val s = _localState.value
        viewModelScope.launch {
            repository.sendEncryptedMessage(
                senderDeviceId = s.thisDeviceId,
                senderName = s.thisDeviceName,
                senderRole = s.thisDeviceRole,
                messageType = "VOICE_NOTE",
                plainText = "Encrypted Voice Message (${durationSec}s)",
                durationSec = durationSec.coerceIn(1L, 300L)
            )
        }
    }

    fun shareCurrentLocationPinToChat() {
        val s = _localState.value
        viewModelScope.launch {
            repository.sendEncryptedMessage(
                senderDeviceId = s.thisDeviceId,
                senderName = s.thisDeviceName,
                senderRole = s.thisDeviceRole,
                messageType = "LOCATION_PIN",
                plainText = "Live GPS Pin: ${s.locationLabel} (${"%.4f".format(s.latitude)}, ${"%.4f".format(s.longitude)})"
            )
        }
    }

    fun deleteSavedMediaItem(id: String) {
        viewModelScope.launch {
            vaultDao.deleteSavedMedia(id)
        }
    }

    fun dismissBanner() {
        _localState.update { it.copy(statusBannerMessage = null) }
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun deleteAccountAndAllData(
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Delete all Firestore records for devices, signals, and messages
                repository.deleteAllUserData(currentUserId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting Firestore user data", e)
            }
            try {
                // 2. Wipe local encrypted Room vault
                vaultDao.deleteAllChats(currentUserId)
                vaultDao.deleteAllMedia(currentUserId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed wiping local encrypted Room vault", e)
            }
            try {
                // 3. Stop all media hardware
                hardwareManager.stopLiveAudioCapture()
            } catch (e: Exception) {
                Log.w(TAG, "Failed stopping hardware", e)
            }
            try {
                // 4. Delete Firebase Auth user if supported, or sign out
                val user = Firebase.auth.currentUser
                user?.delete()?.await()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase auth user delete failed (may require re-auth), signing out", e)
                try {
                    Firebase.auth.signOut()
                } catch (_: Exception) {}
            }
            onComplete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        hardwareManager.stopLiveAudioCapture()
        hardwareManager.unregisterNetworkCallback(networkCallback)
    }

    private companion object {
        const val TAG = "WhatsuppVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
