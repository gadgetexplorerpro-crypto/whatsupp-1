package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

data class HardwareTelemetry(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val networkType: String = "Wi-Fi",
    val isOnline: Boolean = true,
    val latitude: Double = 37.4220,
    val longitude: Double = -122.0841,
    val accuracy: Double = 5.0,
    val locationLabel: String = "GPS Ready"
)

class DeviceHardwareManager(context: Context) {
    private val appContext = context.applicationContext
    private var audioRecordJob: Job? = null

    init {
        createNotificationChannel()
    }

    fun getStableDeviceId(): String {
        val rawId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        val clean = (rawId ?: "${Build.MANUFACTURER}_${Build.MODEL}").replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        return "dev_${clean.take(24).ifBlank { "android" }}"
    }

    fun getDefaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "Android"
        val model = Build.MODEL ?: "Phone"
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    fun readBatteryAndNetwork(): HardwareTelemetry {
        val batteryIntent = try {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G / LTE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }

        return HardwareTelemetry(
            batteryLevel = pct,
            isCharging = isCharging,
            networkType = netType,
            isOnline = isOnline
        )
    }

    fun registerNetworkReconnectCallback(onNetworkRestored: () -> Unit): ConnectivityManager.NetworkCallback? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkRestored()
            }
        }
        return try {
            cm.registerDefaultNetworkCallback(callback)
            callback
        } catch (_: Exception) {
            null
        }
    }

    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback?) {
        if (callback == null) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try {
            cm?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentGpsLocation(): HardwareTelemetry? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return@withContext null

        return@withContext try {
            val client = LocationServices.getFusedLocationProviderClient(appContext)
            val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val loc = client.getCurrentLocation(priority, CancellationTokenSource().token).await()
                ?: client.lastLocation.await()
            if (loc != null) {
                val label = resolveAddressLabel(loc.latitude, loc.longitude)
                val base = readBatteryAndNetwork()
                base.copy(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracy = loc.accuracy.toDouble().coerceIn(1.0, 9999.0),
                    locationLabel = label
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveAddressLabel(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val list = geocoder.getFromLocation(lat, lng, 1)
            val addr = list?.firstOrNull()
            if (addr != null) {
                listOfNotNull(addr.thoroughfare ?: addr.featureName, addr.locality, addr.adminArea)
                    .joinToString(", ")
                    .ifBlank { "%.4f, %.4f".format(lat, lng) }
            } else {
                "%.4f, %.4f".format(lat, lng)
            }
        } catch (_: Exception) {
            "%.4f, %.4f".format(lat, lng)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveAudioCapture(
        scope: CoroutineScope,
        onAudioMetrics: (dbLevel: Int, pcmChunkBase64: String) -> Unit
    ) {
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return

        stopLiveAudioCapture()
        audioRecordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 8000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1600)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )
            } catch (_: Exception) {
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                return@launch
            }

            val buffer = ShortArray(minBuffer / 2)
            try {
                recorder.startRecording()
                var tick = 0
                while (isActive) {
                    val readCount = recorder.read(buffer, 0, buffer.size)
                    if (readCount > 0) {
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            sum += buffer[i] * buffer[i].toDouble()
                        }
                        val rms = sqrt(sum / readCount)
                        val db = if (rms > 1.0) (20.0 * log10(rms)).toInt().coerceIn(10, 110) else 12
                        val encodedChunk = if (tick % 4 == 0) {
                            val byteBuf = ByteArray(readCount.coerceAtMost(400) * 2)
                            for (i in 0 until (byteBuf.size / 2)) {
                                val s = buffer[i].toInt()
                                byteBuf[i * 2] = (s and 0xFF).toByte()
                                byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }
                            Base64.encodeToString(byteBuf, Base64.NO_WRAP)
                        } else ""
                        onAudioMetrics(db, encodedChunk)
                        tick++
                    }
                    delay(250L)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                recorder.release()
            }
        }
    }

    fun stopLiveAudioCapture() {
        audioRecordJob?.cancel()
        audioRecordJob = null
    }

    fun playIncomingPcmAudio(base64Pcm: String, scope: CoroutineScope) {
        if (base64Pcm.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64Pcm, Base64.NO_WRAP)
                if (bytes.isEmpty()) return@launch
                val sampleRate = 8000
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bytes.size.coerceAtLeast(1600))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(bytes, 0, bytes.size)
                track.play()
                delay(450L)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    fun triggerDeviceRingAlert() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
            ringtone?.play()
        } catch (_: Exception) {
        }
        try {
            val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500L)
            }
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Whatsupp Family Privacy & Connection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Visible notifications when camera, microphone, or GPS connection is active"
            }
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun postPrivacyNotification(title: String, body: String, notificationId: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        try {
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_whatsupp_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val CHANNEL_ID = "whatsupp_privacy_alerts"
    }
}
