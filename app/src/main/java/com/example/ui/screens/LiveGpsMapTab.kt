package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ShareLocation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyDevice
import com.example.ui.LocalDashboardState
import com.example.ui.UiState
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrivacyAmber
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppTealDark

@Composable
fun LiveGpsMapTab(
    localState: LocalDashboardState,
    devicesState: UiState<List<FamilyDevice>>,
    onRefreshLocalGps: () -> Unit,
    onPingRemoteLocation: () -> Unit,
    onShareLocationToChat: () -> Unit,
    calculateDistanceMeters: (Double, Double, Double, Double) -> Double,
    onRequestLocationPermission: () -> Unit
) {
    val context = LocalContext.current
    val devices = (devicesState as? UiState.Success)?.data ?: emptyList()
    val targetDevice = devices.find { it.id == localState.selectedTargetDeviceId }
        ?: devices.firstOrNull { it.id != localState.thisDeviceId }
        ?: devices.firstOrNull()

    val distanceMeters = if (targetDevice != null) {
        calculateDistanceMeters(
            localState.latitude,
            localState.longitude,
            targetDevice.latitude,
            targetDevice.longitude
        )
    } else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Interactive Family GPS Map Canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF08181E))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FamilyGpsRadarCanvas(
                    localState = localState,
                    devices = devices
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "LIVE FAMILY GPS RADAR & GRID",
                            style = MaterialTheme.typography.labelSmall,
                            color = WhatsAppEmerald
                        )
                        Text(
                            text = "${localState.locationLabel} (±${localState.locationAccuracy.toInt()}m)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (targetDevice != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = PrivacyAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${targetDevice.deviceName}: ${"%.4f".format(targetDevice.latitude)}, ${"%.4f".format(targetDevice.longitude)} • Distance: ${
                                    if (distanceMeters < 1000) "${distanceMeters.toInt()} m" else "%.2f km".format(distanceMeters / 1000.0)
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 2. GPS Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onRequestLocationPermission()
                    onRefreshLocalGps()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhatsAppEmerald,
                    contentColor = Color(0xFF042917)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sync_gps_button")
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sync My GPS", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onPingRemoteLocation,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ping_remote_gps_button")
            ) {
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ping Remote GPS")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShareLocationToChat,
                modifier = Modifier
                    .weight(1f)
                    .testTag("share_location_pin_button")
            ) {
                Icon(imageVector = Icons.Default.ShareLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Pin to Vault")
            }

            OutlinedButton(
                onClick = {
                    val lat = targetDevice?.latitude ?: localState.latitude
                    val lng = targetDevice?.longitude ?: localState.longitude
                    val label = Uri.encode(targetDevice?.deviceName ?: localState.thisDeviceName)
                    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_google_maps_button")
            ) {
                Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Google Maps")
            }
        }

        // 3. Device Coordinates Summary Cards
        devices.forEach { dev ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(WhatsAppTealDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = WhatsAppEmerald
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = dev.deviceName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${dev.locationLabel} • Lat ${"%.5f".format(dev.latitude)}, Lng ${"%.5f".format(dev.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "±${dev.locationAccuracy.toInt()}m",
                        style = MaterialTheme.typography.labelLarge,
                        color = WhatsAppEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyGpsRadarCanvas(
    localState: LocalDashboardState,
    devices: List<FamilyDevice>
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridColor = Color(0xFF13313D)
        val step = size.width / 8f
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
            y += step
        }

        val center = Offset(size.width / 2f, size.height / 2f)
        // Geofence safe zone rings
        drawCircle(
            color = WhatsAppEmerald.copy(alpha = 0.25f),
            radius = size.minDimension * 0.38f,
            center = center,
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        )
        drawCircle(
            color = WhatsAppEmerald.copy(alpha = 0.14f),
            radius = size.minDimension * 0.22f,
            center = center
        )

        // Plot This Phone at Center
        drawCircle(
            color = OnlineGreen.copy(alpha = 0.3f),
            radius = 28f,
            center = center
        )
        drawCircle(
            color = OnlineGreen,
            radius = 12f,
            center = center
        )

        // Plot Paired Family Devices relative to center
        devices.filter { it.id != localState.thisDeviceId }.forEachIndexed { idx, dev ->
            val dLat = ((dev.latitude - localState.latitude) * 8000f).toFloat().coerceIn(-120f, 120f)
            val dLon = ((dev.longitude - localState.longitude) * 8000f).toFloat().coerceIn(-120f, 120f)
            val offsetX = if (dLon == 0f) (idx + 1) * 58f else dLon
            val offsetY = if (dLat == 0f) -(idx + 1) * 42f else -dLat
            val targetPos = Offset(
                (center.x + offsetX).coerceIn(40f, size.width - 40f),
                (center.y + offsetY).coerceIn(40f, size.height - 40f)
            )
            // Connecting line between Controller and Connected Device
            drawLine(
                color = WhatsAppEmerald.copy(alpha = 0.65f),
                start = center,
                end = targetPos,
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
            drawCircle(
                color = PrivacyAmber.copy(alpha = 0.3f),
                radius = 26f,
                center = targetPos
            )
            drawCircle(
                color = PrivacyAmber,
                radius = 11f,
                center = targetPos
            )
        }
    }
}
