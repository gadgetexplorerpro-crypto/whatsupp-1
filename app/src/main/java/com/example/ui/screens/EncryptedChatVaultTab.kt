package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SavedMediaEntity
import com.example.data.model.EncryptedChatMessage
import com.example.data.security.LocalCryptoManager
import com.example.ui.LocalDashboardState
import com.example.ui.UiState
import com.example.ui.theme.WhatsAppEmerald
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EncryptedChatVaultTab(
    localState: LocalDashboardState,
    messagesState: UiState<List<EncryptedChatMessage>>,
    savedVaultMedia: List<SavedMediaEntity>,
    currentUserId: String,
    onSendTextMessage: (String) -> Unit,
    onSendVoiceNote: (Long) -> Unit,
    onShareLocationPin: () -> Unit,
    onDeleteSavedMedia: (String) -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. WhatsApp E2E Encryption Notice Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = WhatsAppEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Messages & saved media are encrypted locally with AES-256-GCM in Room Vault.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Local Encrypted Saved Media Vault Strip (if any snapshots/clips saved)
        if (savedVaultMedia.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EnhancedEncryption,
                        contentDescription = null,
                        tint = WhatsAppEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Local Encrypted Media Vault (${savedVaultMedia.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = WhatsAppEmerald
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(savedVaultMedia, key = { it.id }) { media ->
                        val decryptedCaption = remember(media.encryptedCaption, currentUserId) {
                            LocalCryptoManager.decrypt(media.encryptedCaption, currentUserId)
                        }
                        val decryptedBitmap = remember(media.encryptedBase64, currentUserId) {
                            try {
                                val b64 = LocalCryptoManager.decrypt(media.encryptedBase64, currentUserId)
                                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        Card(
                            modifier = Modifier.width(150.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (decryptedBitmap != null) {
                                    Image(
                                        bitmap = decryptedBitmap.asImageBitmap(),
                                        contentDescription = decryptedCaption,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(78.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(78.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0A181F)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Photo,
                                            contentDescription = null,
                                            tint = WhatsAppEmerald
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = decryptedCaption.take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onDeleteSavedMedia(media.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Vault Media",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. WhatsApp-Style Chat Thread
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("encrypted_chat_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EnhancedEncryption,
                                contentDescription = null,
                                tint = WhatsAppEmerald,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Encrypted Family Chat & Media Log",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Send an encrypted message, voice note, or live GPS pin between your Main Device and Connected Device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id.ifBlank { it.cipherText + it.createdAt } }) { msg ->
                val isFromMe = msg.senderDeviceId == localState.thisDeviceId
                val decryptedText = remember(msg.cipherText, currentUserId) {
                    val dec = LocalCryptoManager.decrypt(msg.cipherText, currentUserId)
                    if (dec.isNotBlank() && dec != msg.cipherText) dec else msg.plainPreview
                }
                val timeStr = remember(msg.createdAt) {
                    val d = msg.createdAt?.toDate()
                    if (d != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(d) else "Now"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 310.dp),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isFromMe) 16.dp else 4.dp,
                            bottomEnd = if (isFromMe) 4.dp else 16.dp
                        ),
                        color = if (isFromMe) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${msg.senderName} (${msg.senderRole})",
                                style = MaterialTheme.typography.labelSmall,
                                color = WhatsAppEmerald,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (msg.messageType == "VOICE_NOTE") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = WhatsAppEmerald
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = decryptedText,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            } else {
                                Text(
                                    text = decryptedText,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    modifier = Modifier.size(14.dp),
                                    tint = WhatsAppEmerald
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. WhatsApp-Style Composer Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onShareLocationPin,
                    modifier = Modifier.testTag("chat_share_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Share GPS Pin",
                        tint = WhatsAppEmerald
                    )
                }

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("Encrypted family message...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                IconButton(
                    onClick = { onSendVoiceNote(5L) },
                    modifier = Modifier.testTag("chat_voice_note_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Send Encrypted Voice Note",
                        tint = WhatsAppEmerald
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = WhatsAppEmerald,
                    modifier = Modifier.size(46.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank()) {
                                onSendTextMessage(messageInput)
                                messageInput = ""
                            }
                        },
                        modifier = Modifier.testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color(0xFF042917)
                        )
                    }
                }
            }
        }
    }
}
