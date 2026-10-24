package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.FamilyDevice
import com.example.ui.LocalDashboardState
import com.example.ui.UiState
import com.example.ui.theme.AlertRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrivacyAmber
import com.example.ui.theme.WhatsAppEmerald
import kotlin.math.sin

@Composable
fun LiveAudioVoiceTab(
    localState: LocalDashboardState,
    devicesState: UiState<List<FamilyDevice>>,
    onToggleMicStream: (Boolean) -> Unit,
    onToggleTwoWayVoice: (Boolean) -> Unit,
    onSendRemoteCommand: (String) -> Unit,
    onSendVoiceNote: (Long) -> Unit,
    onRequestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    val hasMicPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val devices = (devicesState as? UiState.Success)?.data ?: emptyList()
    val targetDevice = devices.find { it.id == localState.selectedTargetDeviceId }
        ?: devices.firstOrNull { it.id != localState.thisDeviceId }
        ?: devices.firstOrNull()

    val activeDb = when {
        localState.isMicStreaming || localState.isTwoWayVoiceActive -> localState.localAudioLevelDb
        targetDevice != null && (targetDevice.isMicStreaming || targetDevice.isTwoWayVoiceActive) -> targetDevice.audioLevelDb.toInt()
        else -> 12
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Audio Spectrum & Voice Call Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = WhatsAppEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Audio & Two-Way Voice",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (localState.isMicStreaming || localState.isTwoWayVoiceActive) {
                            OnlineGreen.copy(alpha = 0.2f)
                        } else {
                            PrivacyAmber.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = "${activeDb} dB",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (localState.isMicStreaming || localState.isTwoWayVoiceActive) OnlineGreen else PrivacyAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // 24-Bar Real-Time Audio Waveform Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF071015))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AudioWaveformCanvas(
                        dbLevel = activeDb,
                        isActive = localState.isMicStreaming || localState.isTwoWayVoiceActive ||
                            (targetDevice?.isMicStreaming == true) || (targetDevice?.isTwoWayVoiceActive == true)
                    )
                }

                if (!hasMicPermission) {
                    Button(
                        onClick = onRequestMicPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppEmerald,
                            contentColor = Color(0xFF042917)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_mic_permission_button")
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Microphone Permission", fontWeight = FontWeight.Bold)
                    }
                }

                // Two-Way Voice Intercom Call Button
                Button(
                    onClick = {
                        if (!hasMicPermission) onRequestMicPermission()
                        else onToggleTwoWayVoice(!localState.isTwoWayVoiceActive)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (localState.isTwoWayVoiceActive) AlertRed else WhatsAppEmerald,
                        contentColor = if (localState.isTwoWayVoiceActive) Color.White else Color(0xFF042917)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("two_way_voice_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (localState.isTwoWayVoiceActive) Icons.Default.CallEnd else Icons.Default.Call,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (localState.isTwoWayVoiceActive) {
                            "End Two-Way Family Voice Call"
                        } else {
                            "Start Two-Way Voice Communication"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // One-Way Live Microphone Stream Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!hasMicPermission) onRequestMicPermission()
                            else onToggleMicStream(!localState.isMicStreaming)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_mic_stream_button")
                    ) {
                        Icon(
                            imageVector = if (localState.isMicStreaming) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (localState.isMicStreaming) AlertRed else WhatsAppEmerald
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (localState.isMicStreaming) "Stop Mic Stream" else "Stream This Mic")
                    }

                    OutlinedButton(
                        onClick = { onSendVoiceNote(8L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("send_encrypted_voice_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = WhatsAppEmerald
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Voice Note")
                    }
                }
            }
        }

        // 2. Remote Connected Device Microphone & Intercom Controls
        if (targetDevice != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(WhatsAppEmerald.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = WhatsAppEmerald
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Remote Audio Control: ${targetDevice.deviceName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (targetDevice.micConsentGranted) {
                                    "Remote Mic Consent Granted • Level ${targetDevice.audioLevelDb} dB"
                                } else {
                                    "Awaiting Remote Microphone Consent"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (targetDevice.micConsentGranted) WhatsAppEmerald else PrivacyAmber
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSendRemoteCommand("START_MIC") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Listen Remote Mic")
                        }
                        Button(
                            onClick = { onSendRemoteCommand("START_VOICE") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Remote Intercom")
                        }
                        OutlinedButton(
                            onClick = { onSendRemoteCommand("STOP_MIC") }
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
private fun AudioWaveformCanvas(
    dbLevel: Int,
    isActive: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val barCount = 28
        val spacing = 6.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(4f)
        val normalized = (dbLevel.coerceIn(10, 110) - 10) / 100f

        for (i in 0 until barCount) {
            val waveFactor = if (isActive) {
                (0.25f + 0.75f * kotlin.math.abs(sin((i * 0.45f) + (dbLevel * 0.2f)))) * normalized.coerceAtLeast(0.2f)
            } else {
                0.12f
            }
            val barHeight = (size.height * waveFactor).coerceIn(8f, size.height)
            val x = i * (barWidth + spacing)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = if (isActive) WhatsAppEmerald else Color(0xFF37474F),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
