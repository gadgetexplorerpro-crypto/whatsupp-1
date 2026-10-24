package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.DarkCard
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrivacyAmber
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppTealDark

/**
 * Privacy Policy and Terms of Service Viewer.
 * Designed for both in-app review and the Login screen.
 * Does NOT display external URLs on the login screen to satisfy the user constraint.
 */
@Composable
fun PrivacyAndTermsDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("privacy_terms_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WhatsAppEmerald.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = WhatsAppEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Whatsupp Legal & Safety",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Transparency & Mutual Family Consent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_legal_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs: Privacy Policy / Terms of Service
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = WhatsAppEmerald
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Privacy Policy",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Terms of Service",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (selectedTab == 0) {
                        PrivacyPolicyContent()
                    } else {
                        TermsOfServiceContent()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_legal_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppEmerald,
                        contentColor = Color(0xFF042917)
                    )
                ) {
                    Text("I Understand & Accept", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicyContent() {
    LegalSection(
        title = "1. Introduction & Family Scope",
        body = "Whatsupp is built exclusively for transparent family device link, baby monitoring, elder assistance, and personal multi-device awareness. Both devices must be signed in with the same Google Account and paired using an explicit 6-digit confirmation code."
    )
    LegalSection(
        title = "2. Device Hardware & Sensor Data",
        body = "• Camera (Front & Back): Used solely for live visual streaming between paired devices. Camera access requires explicit in-app consent toggles and displays a persistent on-screen green shield banner whenever active.\n" +
            "• Microphone & Voice: Real-time audio streaming and two-way voice intercom require microphone permissions and user consent.\n" +
            "• Precise GPS Location: Used solely to calculate proximity distance and render family radar map coordinates. Location is never shared with third parties or advertisers.\n" +
            "• Battery & Telemetry: Battery percentage, charging status, and Wi-Fi/cellular connection type are synchronized to keep family members informed of device health."
    )
    LegalSection(
        title = "3. End-to-End Encryption & Security",
        body = "• WebRTC live streams negotiate encrypted peer-to-peer connections using DTLS-SRTP protocols.\n" +
            "• Local chat logs and media snapshots are stored in an encrypted SQLite vault utilizing AES-256-GCM keys managed by the Android Keystore.\n" +
            "• Firestore cloud database rules enforce strict zero-trust security: only the authenticated Google Account owner can read or write their device documents."
    )
    LegalSection(
        title = "4. Zero Third-Party Tracking",
        body = "Whatsupp contains no advertising SDKs, commercial telemetry brokers, or third-party behavioral trackers. Your live audio, video, location, and chat messages belong strictly to your family account."
    )
    LegalSection(
        title = "5. User Control & Data Deletion Rights",
        body = "You have the right to revoke camera/mic consent at any second, unpair any device, or trigger a full 'Delete Account & Wipe Data' inside the app, which immediately purges all cloud records, signaling sessions, messages, and local encrypted databases."
    )
}

@Composable
private fun TermsOfServiceContent() {
    LegalSection(
        title = "1. Mutual Consent & Permitted Use",
        body = "Whatsupp may only be installed on devices that you own or on devices where the primary user has given full, explicit, and informed consent. Both devices must actively confirm the 6-digit mutual pairing code."
    )
    LegalSection(
        title = "2. Strict Prohibition on Covert Surveillance",
        body = "Using Whatsupp for clandestine, covert, secret, or non-consensual tracking, spying, stalking, or harassment is strictly forbidden and constitutes a material breach of these Terms, as well as a violation of Google Play Developer Program Policies. The app incorporates mandatory on-screen privacy indicators that cannot be concealed."
    )
    LegalSection(
        title = "3. Emergency Services Disclaimer",
        body = "Whatsupp is a peer-to-peer family awareness tool and is NOT an emergency dispatch or replacement for official emergency services (such as 911/112). Do not rely on Whatsupp for life-critical or emergency response."
    )
    LegalSection(
        title = "4. Account Revocation & Termination",
        body = "You may terminate device connections or delete your Whatsupp account at any time through the in-app settings. We reserve the right to suspend any account found violating mutual consent or applicable laws."
    )
}

@Composable
private fun LegalSection(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WhatsAppEmerald
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
        }
    }
}

/**
 * Account & Data Privacy Dialog.
 * Shown inside the app (Dashboard / TopAppBar).
 * Displays:
 * 1. Privacy Policy URL for Google Play Console
 * 2. Delete Account and Delete Data URL for Google Play Console Data Safety
 * 3. Working in-app "Delete Account & Wipe All Data" feature
 * 4. In-app viewer for Privacy & Terms
 */
@Composable
fun AccountAndPrivacyDialog(
    userEmail: String,
    currentUserId: String,
    onDismiss: () -> Unit,
    onDeleteAccountConfirmed: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showFullLegalViewer by remember { mutableStateOf(false) }

    val privacyUrl = stringResource(id = R.string.privacy_policy_url)
    val deleteAccountUrl = stringResource(id = R.string.account_deletion_url)

    if (showFullLegalViewer) {
        PrivacyAndTermsDialog(onDismiss = { showFullLegalViewer = false })
        return
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Account & Wipe All Data?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This action is permanent and cannot be undone. It will immediately execute:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Purge all paired device profiles from Firestore\n" +
                            "• Delete all WebRTC signaling sessions\n" +
                            "• Erase all encrypted family chat records & media metadata\n" +
                            "• Wipe the local AES-256 encrypted SQLite vault database\n" +
                            "• Delete your Google Account association from Whatsupp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isDeleting) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.error,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Wiping all account & cloud data...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        onDeleteAccountConfirmed()
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp)
                .testTag("account_privacy_management_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(WhatsAppEmerald.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = WhatsAppEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Account & Data Privacy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 1. Google Play Console Required URLs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                                tint = WhatsAppEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Play Data Safety URLs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Provide these exact URLs in the Google Play Console for store compliance:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Privacy Policy URL
                        UrlDisplayCard(
                            label = "Privacy Policy URL",
                            subLabel = "For Play Console > App Content > Privacy Policy",
                            url = privacyUrl,
                            context = context
                        )

                        // Delete Account & Delete Data URL
                        UrlDisplayCard(
                            label = "Account & Data Deletion URL",
                            subLabel = "For Play Console > App Content > Data Safety > Delete Account URL",
                            url = deleteAccountUrl,
                            context = context
                        )
                    }
                }

                // 2. In-App Legal Viewer
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
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = WhatsAppEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "In-App Policy & Terms",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Review the full privacy agreement and terms of consensual family use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showFullLegalViewer = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("view_full_legal_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Privacy Policy & Terms")
                        }
                    }
                }

                // 3. Delete Account & Delete All Data
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete Account & Wipe Data",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Permanently deletes all registered devices, signaling logs, encrypted chat messages, and local vaults. Required by Google Play Data Safety policy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("delete_account_and_data_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete Account & All Data",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Close Button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun UrlDisplayCard(
    label: String,
    subLabel: String,
    url: String,
    context: Context
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(label, url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Link", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No web browser found", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
