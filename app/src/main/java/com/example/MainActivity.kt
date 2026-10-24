package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ui.screens.AccountAndPrivacyDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalVaultDatabase
import com.example.data.repository.WhatsuppRepository
import com.example.hardware.DeviceHardwareManager
import com.example.ui.WhatsuppViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.DevicesDashboardTab
import com.example.ui.screens.EncryptedChatVaultTab
import com.example.ui.screens.LiveAudioVoiceTab
import com.example.ui.screens.LiveCameraTab
import com.example.ui.screens.LiveGpsMapTab
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrivacyAmber
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppTealDark
import com.example.ui.theme.WhatsuppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhatsuppTheme {
                AppNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val authFlow = remember(auth) { auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        WhatsuppMainScreen(
            currentUserId = user.uid,
            userEmail = user.email ?: "Family Account"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsuppMainScreen(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: WhatsuppViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val vaultDao = LocalVaultDatabase.getInstance(app).vaultDao()
                val hardware = DeviceHardwareManager(app)
                WhatsuppViewModel(
                    repository = WhatsuppRepository(db),
                    vaultDao = vaultDao,
                    hardwareManager = hardware,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val localState by viewModel.localState.collectAsStateWithLifecycle()
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val savedVaultMedia by viewModel.savedVaultMedia.collectAsStateWithLifecycle()
    // Observe WebRTC signals continuously
    viewModel.signalsState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAccountPrivacyDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.refreshGpsLocation()
        }
    }

    val requestAllPermissions = {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionsLauncher.launch(perms)
    }

    val isHardwareStreamingActive = localState.activeCameraFacing != "OFF" ||
        localState.isMicStreaming ||
        localState.isTwoWayVoiceActive

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WhatsAppTealDark,
                        titleContentColor = Color.White
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_whatsupp_logo),
                                contentDescription = "Whatsupp Logo",
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Whatsupp",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${localState.thisDeviceRole} • $userEmail",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB2DFDB)
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (localState.isOnline) OnlineGreen.copy(alpha = 0.2f) else PrivacyAmber.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (localState.isOnline) OnlineGreen else PrivacyAmber)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${localState.batteryLevel}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = { showAccountPrivacyDialog = true },
                            modifier = Modifier.testTag("account_privacy_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Account & Data Privacy",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                signOut(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {},
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = Color.White
                            )
                        }
                    }
                )

                if (showAccountPrivacyDialog) {
                    AccountAndPrivacyDialog(
                        userEmail = userEmail,
                        currentUserId = currentUserId,
                        onDismiss = { showAccountPrivacyDialog = false },
                        onDeleteAccountConfirmed = {
                            viewModel.deleteAccountAndAllData(
                                onComplete = {
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {
                                            showAccountPrivacyDialog = false
                                        },
                                        scope = scope
                                    )
                                }
                            )
                        }
                    )
                }

                // Mandatory Visible Hardware Privacy Indicator Banner when Camera or Mic is active
                AnimatedVisibility(visible = isHardwareStreamingActive) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF004D40)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = OnlineGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buildString {
                                        append("PRIVACY INDICATOR: ")
                                        if (localState.activeCameraFacing != "OFF") append("Camera (${localState.activeCameraFacing}) ")
                                        if (localState.isMicStreaming) append("Live Mic ")
                                        if (localState.isTwoWayVoiceActive) append("2-Way Voice ")
                                        append("Active")
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Stop All",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrivacyAmber,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
                                        viewModel.setCameraMode("OFF")
                                        viewModel.toggleTwoWayVoice(false)
                                        viewModel.toggleMicStreaming(false)
                                    }
                            )
                        }
                    }
                }

                // Status notification banner
                AnimatedVisibility(visible = localState.statusBannerMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = localState.statusBannerMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissBanner() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val items = listOf(
                    Triple(0, "Devices", Icons.Default.Devices),
                    Triple(1, "Live Cam", Icons.Default.CameraAlt),
                    Triple(2, "Audio/Voice", Icons.Default.Mic),
                    Triple(3, "GPS Map", Icons.Default.LocationOn),
                    Triple(4, "Vault Chat", Icons.AutoMirrored.Filled.Chat)
                )
                items.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WhatsAppEmerald,
                            selectedTextColor = WhatsAppEmerald,
                            indicatorColor = WhatsAppEmerald.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DevicesDashboardTab(
                    localState = localState,
                    devicesState = devicesState,
                    historyState = historyState,
                    onSwitchRole = viewModel::switchThisDeviceRole,
                    onUpdateDeviceName = viewModel::updateDeviceName,
                    onCompletePairing = viewModel::completeDevicePairing,
                    onSelectTargetDevice = viewModel::selectTargetDevice,
                    onUpdateConsent = viewModel::updatePrivacyConsent,
                    onToggleAutoReconnect = viewModel::toggleAutoReconnect,
                    onSendRemoteCommand = viewModel::sendRemoteCommandToTarget,
                    onRequestPermissions = requestAllPermissions,
                    onNavigateToTab = { selectedTab = it },
                    onOpenAccountPrivacy = { showAccountPrivacyDialog = true }
                )
                1 -> LiveCameraTab(
                    localState = localState,
                    devicesState = devicesState,
                    onSetLocalCameraMode = viewModel::setCameraMode,
                    onSendRemoteCommand = viewModel::sendRemoteCommandToTarget,
                    onLocalFrameEncoded = viewModel::onLocalCameraFrameEncoded,
                    onSaveEncryptedSnapshot = viewModel::saveEncryptedSnapshot,
                    onRequestCameraPermission = requestAllPermissions
                )
                2 -> LiveAudioVoiceTab(
                    localState = localState,
                    devicesState = devicesState,
                    onToggleMicStream = viewModel::toggleMicStreaming,
                    onToggleTwoWayVoice = viewModel::toggleTwoWayVoice,
                    onSendRemoteCommand = viewModel::sendRemoteCommandToTarget,
                    onSendVoiceNote = viewModel::sendVoiceNoteMessage,
                    onRequestMicPermission = requestAllPermissions
                )
                3 -> LiveGpsMapTab(
                    localState = localState,
                    devicesState = devicesState,
                    onRefreshLocalGps = viewModel::refreshGpsLocation,
                    onPingRemoteLocation = { viewModel.sendRemoteCommandToTarget("PING_LOCATION") },
                    onShareLocationToChat = viewModel::shareCurrentLocationPinToChat,
                    calculateDistanceMeters = viewModel::calculateDistanceMeters,
                    onRequestLocationPermission = requestAllPermissions
                )
                4 -> EncryptedChatVaultTab(
                    localState = localState,
                    messagesState = messagesState,
                    savedVaultMedia = savedVaultMedia,
                    currentUserId = currentUserId,
                    onSendTextMessage = viewModel::sendTextChatMessage,
                    onSendVoiceNote = viewModel::sendVoiceNoteMessage,
                    onShareLocationPin = viewModel::shareCurrentLocationPinToChat,
                    onDeleteSavedMedia = viewModel::deleteSavedMediaItem
                )
            }
        }
    }
}
