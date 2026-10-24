package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ConnectionEvent
import com.example.data.model.FamilyDevice
import com.example.ui.LocalDashboardState
import com.example.ui.UiState
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrivacyAmber
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppTealDark
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DevicesDashboardTab(
    localState: LocalDashboardState,
    devicesState: UiState<List<FamilyDevice>>,
    historyState: UiState<List<ConnectionEvent>>,
    onSwitchRole: (String) -> Unit,
    onUpdateDeviceName: (String) -> Unit,
    onCompletePairing: (String) -> Unit,
    onSelectTargetDevice: (String) -> Unit,
    onUpdateConsent: (Boolean, Boolean, Boolean) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onSendRemoteCommand: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onOpenAccountPrivacy: () -> Unit = {}
) {
    var pairingInput by remember(localState.pairingCode) { mutableStateOf(localState.pairingCode) }
    var deviceNameInput by remember(localState.thisDeviceName) { mutableStateOf(localState.thisDeviceName) }
    var isEditingName by remember { mutableStateOf(false) }

    val devices = (devicesState as? UiState.Success)?.data ?: emptyList()
    val history = (historyState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("devices_dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. This Phone Role & Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppTealDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = WhatsAppEmerald
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = localState.thisDeviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ID: ${localState.thisDeviceId} • ${localState.networkType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = WhatsAppEmerald.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (localState.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    tint = WhatsAppEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${localState.batteryLevel}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WhatsAppEmerald
                                )
                            }
                        }
                    }

                    if (isEditingName) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = deviceNameInput,
                                onValueChange = { deviceNameInput = it },
                                label = { Text("Phone Display Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onUpdateDeviceName(deviceNameInput)
                                    isEditingName = false
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select this phone's role:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Rename Phone",
                                style = MaterialTheme.typography.labelLarge,
                                color = WhatsAppEmerald,
                                modifier = Modifier.clickable { isEditingName = true }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = localState.thisDeviceRole == "CONTROLLER",
                            onClick = { onSwitchRole("CONTROLLER") },
                            label = { Text("Main Device (Controller)") },
                            leadingIcon = {
                                if (localState.thisDeviceRole == "CONTROLLER") {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WhatsAppEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = WhatsAppEmerald
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_controller_chip")
                        )
                        FilterChip(
                            selected = localState.thisDeviceRole == "CONNECTED",
                            onClick = { onSwitchRole("CONNECTED") },
                            label = { Text("Connected Device") },
                            leadingIcon = {
                                if (localState.thisDeviceRole == "CONNECTED") {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WhatsAppEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = WhatsAppEmerald
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_connected_chip")
                        )
                    }
                }
            }
        }

        // 2. Initial Pairing & Permissions Consent Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = WhatsAppEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Device Pairing & Explicit Consent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Sign into the same Google Account on both phones and verify the 6-digit pairing code. Camera and microphone access always display a visible consent indicator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = pairingInput,
                            onValueChange = { if (it.length <= 6) pairingInput = it },
                            label = { Text("6-Digit Pairing Code") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pairing_code_input")
                        )
                        Button(
                            onClick = { onCompletePairing(pairingInput) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppEmerald,
                                contentColor = Color(0xFF042917)
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("pair_device_button")
                        ) {
                            Text("Pair & Sync", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Camera, Mic, GPS & Notification Permissions")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ConsentSwitchRow(
                        title = "Allow Live Camera Viewing (Front & Back)",
                        subtitle = "Shows active green privacy banner when camera is streaming",
                        checked = localState.cameraConsentGranted,
                        onCheckedChange = {
                            onUpdateConsent(it, localState.micConsentGranted, localState.locationConsentGranted)
                        }
                    )
                    ConsentSwitchRow(
                        title = "Allow Live Microphone & Two-Way Voice",
                        subtitle = "Shows active audio indicator when microphone is streaming",
                        checked = localState.micConsentGranted,
                        onCheckedChange = {
                            onUpdateConsent(localState.cameraConsentGranted, it, localState.locationConsentGranted)
                        }
                    )
                    ConsentSwitchRow(
                        title = "Allow Real-Time GPS Location Sharing",
                        subtitle = "Syncs live coordinates and accuracy with paired family phone",
                        checked = localState.locationConsentGranted,
                        onCheckedChange = {
                            onUpdateConsent(localState.cameraConsentGranted, localState.micConsentGranted, it)
                        }
                    )
                    ConsentSwitchRow(
                        title = "Secure Automatic Reconnection When Online",
                        subtitle = "Automatically restores encrypted session when Wi-Fi/5G reconnects",
                        checked = localState.autoReconnect,
                        onCheckedChange = onToggleAutoReconnect
                    )
                }
            }
        }

        // 3. Connected Family Devices List (WhatsApp Chat-List Style)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paired Family Devices (${devices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localState.dtlsFingerprint,
                    style = MaterialTheme.typography.labelSmall,
                    color = WhatsAppEmerald
                )
            }
        }

        items(devices, key = { it.id }) { device ->
            val isThisPhone = device.id == localState.thisDeviceId
            val isSelectedTarget = device.id == localState.selectedTargetDeviceId ||
                (localState.selectedTargetDeviceId.isBlank() && !isThisPhone)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelectedTarget) {
                            Modifier.border(1.5.dp, WhatsAppEmerald, RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectTargetDevice(device.id) }
                    .testTag("device_card_${device.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (device.role == "CONTROLLER") WhatsAppTealDark else WhatsAppEmerald.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (device.role == "CONTROLLER") Color.White else WhatsAppEmerald
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isThisPhone) "${device.deviceName} (This Phone)" else device.deviceName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(if (device.isOnline) OnlineGreen else PrivacyAmber)
                                    )
                                }
                                Text(
                                    text = "${device.role} • Code ${device.pairingCode} • ${device.locationLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    tint = if (device.batteryLevel < 20) PrivacyAmber else WhatsAppEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${device.batteryLevel}%",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = device.networkType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Quick Action Bar for Remote Device Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistActionButton(
                            icon = Icons.Default.CameraAlt,
                            label = if (device.activeCameraFacing != "OFF") "Cam (${device.activeCameraFacing})" else "Live Cam",
                            active = device.activeCameraFacing != "OFF",
                            onClick = {
                                onSelectTargetDevice(device.id)
                                onNavigateToTab(1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AssistActionButton(
                            icon = Icons.Default.Mic,
                            label = if (device.isMicStreaming || device.isTwoWayVoiceActive) "Audio Live" else "Voice/Mic",
                            active = device.isMicStreaming || device.isTwoWayVoiceActive,
                            onClick = {
                                onSelectTargetDevice(device.id)
                                onNavigateToTab(2)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AssistActionButton(
                            icon = Icons.Default.LocationOn,
                            label = "GPS Map",
                            active = device.locationConsentGranted,
                            onClick = {
                                onSelectTargetDevice(device.id)
                                onNavigateToTab(3)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onSelectTargetDevice(device.id)
                                onSendRemoteCommand("RING_DEVICE")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Ring Device",
                                tint = WhatsAppEmerald
                            )
                        }
                    }
                }
            }
        }

        // Account & Data Safety Hub Card
        item {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = WhatsAppEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Account, Privacy & Store URLs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Access Google Play Data Safety URLs (Privacy Policy & Account Deletion) and delete account data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenAccountPrivacy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_manage_account_privacy_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Account & Google Play URLs")
                    }
                }
            }
        }

        // 4. Connection History & Privacy Audit Log
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = WhatsAppEmerald
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connection History & Audit Trail",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(history.take(15), key = { it.id.ifBlank { it.details + it.createdAt } }) { evt ->
            val timeFormatted = remember(evt.createdAt) {
                val date = evt.createdAt?.toDate()
                if (date != null) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date) else "Just now"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${evt.eventType} • ${evt.deviceName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = WhatsAppEmerald
                        )
                        Text(
                            text = evt.details,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = WhatsAppEmerald
            )
        )
    }
}

@Composable
private fun AssistActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (active) WhatsAppEmerald.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) WhatsAppEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) WhatsAppEmerald else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
