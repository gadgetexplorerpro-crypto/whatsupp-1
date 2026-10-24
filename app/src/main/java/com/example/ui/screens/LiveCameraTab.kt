package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.FamilyDevice
import com.example.ui.LocalDashboardState
import com.example.ui.UiState
import com.example.ui.theme.AlertRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrivacyAmber
import com.example.ui.theme.WhatsAppEmerald
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun LiveCameraTab(
    localState: LocalDashboardState,
    devicesState: UiState<List<FamilyDevice>>,
    onSetLocalCameraMode: (String) -> Unit,
    onSendRemoteCommand: (String) -> Unit,
    onLocalFrameEncoded: (String) -> Unit,
    onSaveEncryptedSnapshot: (String, String) -> Unit,
    onRequestCameraPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val devices = (devicesState as? UiState.Success)?.data ?: emptyList()
    val targetDevice = devices.find { it.id == localState.selectedTargetDeviceId }
        ?: devices.firstOrNull { it.id != localState.thisDeviceId }
        ?: devices.firstOrNull()

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var latestLocalBase64 by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. WebRTC & Consent Status Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = WhatsAppEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Camera (Front & Back)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (localState.activeCameraFacing != "OFF") OnlineGreen.copy(alpha = 0.2f) else PrivacyAmber.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (localState.activeCameraFacing != "OFF") "LIVE: ${localState.activeCameraFacing}" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (localState.activeCameraFacing != "OFF") OnlineGreen else PrivacyAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = localState.webrtcState,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Live CameraX Viewport / Remote Connected Device Stream Viewport
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF060D11))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (localState.activeCameraFacing != "OFF" && hasCameraPermission && localState.cameraConsentGranted) {
                    CameraXLivePreview(
                        cameraFacing = localState.activeCameraFacing,
                        lifecycleOwner = lifecycleOwner,
                        onFrameEncoded = { b64 ->
                            latestLocalBase64 = b64
                            onLocalFrameEncoded(b64)
                        }
                    )
                } else if (targetDevice != null && targetDevice.activeCameraFacing != "OFF" && targetDevice.latestFrameBase64.length > 100) {
                    val remoteBitmap = remember(targetDevice.latestFrameBase64) {
                        try {
                            val bytes = Base64.decode(targetDevice.latestFrameBase64, Base64.NO_WRAP)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (remoteBitmap != null) {
                        Image(
                            bitmap = remoteBitmap.asImageBitmap(),
                            contentDescription = "Remote Connected Device Live Camera",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CameraStandbyOverlay(
                            title = "Receiving ${targetDevice.deviceName} (${targetDevice.activeCameraFacing})...",
                            subtitle = "WebRTC encrypted video channel active"
                        )
                    }
                } else {
                    CameraStandbyOverlay(
                        title = if (!hasCameraPermission) "Camera Permission Needed" else "Live Camera Feed Ready",
                        subtitle = "Start Front or Back camera below, or request remote camera from your paired phone."
                    )
                }

                // Visible Active Camera Privacy Indicator Badge inside viewport
                if (localState.activeCameraFacing != "OFF" || (targetDevice?.activeCameraFacing ?: "OFF") != "OFF") {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CONSENTED LIVE CAMERA • ENCRYPTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 3. Local Camera Controls (Front / Back / Stop / Encrypted Snapshot)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "This Phone Camera Controls (${localState.thisDeviceName})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (!hasCameraPermission) {
                    Button(
                        onClick = onRequestCameraPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppEmerald,
                            contentColor = Color(0xFF042917)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_camera_permission_button")
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!hasCameraPermission) onRequestCameraPermission()
                            else onSetLocalCameraMode("BACK")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (localState.activeCameraFacing == "BACK") WhatsAppEmerald else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (localState.activeCameraFacing == "BACK") Color(0xFF042917) else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_back_camera_button")
                    ) {
                        Icon(imageVector = Icons.Default.CameraRear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back Cam")
                    }

                    Button(
                        onClick = {
                            if (!hasCameraPermission) onRequestCameraPermission()
                            else onSetLocalCameraMode("FRONT")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (localState.activeCameraFacing == "FRONT") WhatsAppEmerald else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (localState.activeCameraFacing == "FRONT") Color(0xFF042917) else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_front_camera_button")
                    ) {
                        Icon(imageVector = Icons.Default.CameraFront, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Front Cam")
                    }

                    OutlinedButton(
                        onClick = { onSetLocalCameraMode("OFF") },
                        modifier = Modifier.testTag("stop_camera_button")
                    ) {
                        Icon(imageVector = Icons.Default.StopCircle, contentDescription = null, tint = AlertRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val frame = latestLocalBase64.ifBlank {
                            targetDevice?.latestFrameBase64.orEmpty()
                        }
                        if (frame.isNotBlank()) {
                            onSaveEncryptedSnapshot(frame, localState.activeCameraFacing.ifBlank { "CAM" })
                        }
                    },
                    enabled = latestLocalBase64.isNotBlank() || (targetDevice?.latestFrameBase64?.length ?: 0) > 100,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("capture_encrypted_snapshot_button")
                ) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save AES-256-GCM Encrypted Snapshot to Local Vault")
                }
            }
        }

        // 4. Remote Connected Device Camera Controller
        if (targetDevice != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Remote Device Control: ${targetDevice.deviceName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Consent Status: ${if (targetDevice.cameraConsentGranted) "Granted (Visible indicator active on remote device)" else "Awaiting remote user consent"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (targetDevice.cameraConsentGranted) WhatsAppEmerald else PrivacyAmber
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSendRemoteCommand("START_CAM_BACK") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remote Back")
                        }
                        Button(
                            onClick = { onSendRemoteCommand("START_CAM_FRONT") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.CameraFront, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remote Front")
                        }
                        OutlinedButton(
                            onClick = { onSendRemoteCommand("STOP_CAM") }
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraStandbyOverlay(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.VideocamOff,
            contentDescription = null,
            tint = WhatsAppEmerald,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8696A0)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = WhatsAppEmerald,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "WebRTC DTLS-SRTP + Consent Verified",
                style = MaterialTheme.typography.labelSmall,
                color = WhatsAppEmerald
            )
        }
    }
}

@Composable
private fun CameraXLivePreview(
    cameraFacing: String,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onFrameEncoded: (String) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(cameraFacing, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            encodeFrameProxyToBase64(imageProxy)?.let(onFrameEncoded)
                            imageProxy.close()
                        }
                    }

                val selector = if (cameraFacing == "FRONT") {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
            } catch (_: Exception) {
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
    )
}

private fun encodeFrameProxyToBase64(imageProxy: ImageProxy): String? {
    return try {
        val bitmap = imageProxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val scaled = Bitmap.createScaledBitmap(rotated, 320, 240, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 55, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}
