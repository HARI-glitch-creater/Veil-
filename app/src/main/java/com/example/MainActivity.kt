package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.call.VoiceCallStatus
import com.example.data.BootRoute
import com.example.data.local.CallDirection
import com.example.data.local.ChannelType
import com.example.data.local.MessageEntity
import com.example.data.local.VeilRoomEntity
import com.example.notifications.VeilNotificationHelper
import com.example.ui.VeilViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.VeilDynamicLogo
import com.example.ui.components.VeilObsidianBackdrop
import com.example.ui.screens.ActiveVoiceCallOverlay
import com.example.ui.screens.BackendSettingsScreen
import com.example.ui.screens.CallsTabScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CommunitiesTabScreen
import com.example.ui.screens.ConfirmBindProfileScreen
import com.example.ui.screens.CreateCommunitySheet
import com.example.ui.screens.CreateProfileScreen
import com.example.ui.screens.HostRoomScreen
import com.example.ui.screens.JoinRoomScreen
import com.example.ui.screens.ProfileInspectorScreen
import com.example.ui.screens.ProfilesTabScreen
import com.example.ui.screens.RoomInviteSheet
import com.example.ui.screens.SettingsHubScreen
import com.example.ui.screens.WelcomeRoleScreen
import com.example.ui.theme.CoralError
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassDivider
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.InterFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVeilAccent
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.VeilTheme
import com.example.update.ReleaseUpdateInfo
import kotlinx.coroutines.delay

sealed interface VeilScreen {
    data object BootLoading : VeilScreen
    data object CreateProfile : VeilScreen
    data object ConfirmBind : VeilScreen
    data object RoomGateway : VeilScreen
    data class HostRoom(val returnToSettings: Boolean = false) : VeilScreen
    data class JoinRoom(val returnToSettings: Boolean = false) : VeilScreen
    data object HomeShell : VeilScreen
    data class DirectChat(val peerPublicKey: String) : VeilScreen
    data class CommunityChat(val communityId: String) : VeilScreen
    data class ProfileInspector(val peerPublicKey: String, val returnToChat: Boolean = false) : VeilScreen
    data object SettingsHub : VeilScreen
    data object BackendSettings : VeilScreen
}

enum class HomeTab(val label: String) {
    PROFILES("Profiles"),
    COMMUNITIES("Communities"),
    CALLS("Calls")
}

class MainActivity : ComponentActivity() {

    private val viewModel: VeilViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleCallActionIntent(intent)

        setContent {
            val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
            VeilTheme(accentColor = accentColor) {
                VeilAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallActionIntent(intent)
    }

    private fun handleCallActionIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            VeilNotificationHelper.ACTION_ACCEPT_CALL -> {
                viewModel.voiceCallController.acceptIncomingCall(
                    roomIdOverride = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_ID),
                    roomCodeOverride = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_CODE),
                    roomNameOverride = intent.getStringExtra(VeilNotificationHelper.EXTRA_ROOM_NAME),
                    callerPublicKeyOverride = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_PUBLIC_KEY),
                    callerUsernameOverride = intent.getStringExtra(VeilNotificationHelper.EXTRA_CALLER_USERNAME),
                    offerSdpOverride = intent.getStringExtra(VeilNotificationHelper.EXTRA_OFFER_SDP)
                )
            }
            VeilNotificationHelper.ACTION_DECLINE_CALL -> {
                viewModel.voiceCallController.declineIncomingCall()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VeilAppRoot(viewModel: VeilViewModel) {
    val context = LocalContext.current
    val accent = LocalVeilAccent.current

    val bootRoute by viewModel.bootRoute.collectAsStateWithLifecycle()
    val onboardingDraft by viewModel.onboardingDraft.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val isRoomCreator by viewModel.isActiveRoomCreator.collectAsStateWithLifecycle()
    val roomMembers by viewModel.activeRoomMembers.collectAsStateWithLifecycle()
    val communities by viewModel.activeRoomCommunities.collectAsStateWithLifecycle()
    val callHistory by viewModel.activeRoomCallHistory.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val activeCallState by viewModel.activeCallState.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf<VeilScreen>(VeilScreen.BootLoading) }
    var splashElapsed by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(HomeTab.PROFILES) }
    var isBindingProfile by remember { mutableStateOf(false) }

    // Home UI transient overlays
    var showInviteSheet by remember { mutableStateOf(false) }
    var showRoomSwitcherSheet by remember { mutableStateOf(false) }
    var showCreateCommunitySheet by remember { mutableStateOf(false) }
    var preselectedPeerForCommunity by remember { mutableStateOf<String?>(null) }

    // Runtime permissions for Notifications & Voice Calls
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val callAudioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.postBanner("Microphone permission is required for encrypted voice calls.")
        }
    }

    // Hold Dark Glass Splash Screen for ~1.2s before routing into BootRoute
    LaunchedEffect(Unit) {
        delay(1200L)
        splashElapsed = true
    }

    // Boot state machine transition (only auto-drives initial BootLoading state)
    LaunchedEffect(bootRoute, splashElapsed) {
        if (currentScreen == VeilScreen.BootLoading && bootRoute != null && splashElapsed) {
            currentScreen = when (bootRoute!!) {
                BootRoute.CREATE_PROFILE -> VeilScreen.CreateProfile
                BootRoute.ROOM_GATEWAY -> VeilScreen.RoomGateway
                BootRoute.HOME -> VeilScreen.HomeShell
                BootRoute.REINSTALL_RESTORED -> if (rooms.isNotEmpty()) VeilScreen.HomeShell else VeilScreen.RoomGateway
            }
        }
    }

    // Request POST_NOTIFICATIONS on Android 13+ when entering HomeShell
    LaunchedEffect(currentScreen) {
        if (currentScreen == VeilScreen.HomeShell && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Auto-dismiss transient status banner
    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(3600L)
            viewModel.clearBanner()
        }
    }

    fun startVoiceCallWithPermissionCheck(peerKey: String, peerName: String, peerPfp: String) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.startVoiceCallByKey(peerKey, peerName, peerPfp)
        } else {
            callAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openStoredMediaExternal(message: MessageEntity) {
        val uri = viewModel.repository.mediaStorage.getFileProviderUri(message.localMediaPath)
        if (uri == null) {
            viewModel.postBanner("Encrypted media file is not cached locally yet.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, message.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, message.fileName.ifBlank { "Open File" }))
        }.onFailure {
            viewModel.postBanner("No app available to open this file type.")
        }
    }

    VeilObsidianBackdrop {
        when (val screen = currentScreen) {
            VeilScreen.BootLoading -> {
                VeilSplashScreen(accentColor = accent.primary)
            }

            VeilScreen.CreateProfile -> {
                CreateProfileScreen(
                    draft = onboardingDraft,
                    onDraftChange = { viewModel.updateOnboardingDraft(it) },
                    onCreateCameraTarget = { viewModel.createCameraCaptureTarget() },
                    onCompressAvatarUri = { uri, cb -> viewModel.compressAndStoreAvatar(uri, cb) },
                    onContinueToConfirm = { currentScreen = VeilScreen.ConfirmBind }
                )
            }

            VeilScreen.ConfirmBind -> {
                val id = remember { viewModel.getLocalIdentity() }
                val deviceShort = remember { viewModel.getDeviceBindingId().take(16).uppercase() }
                ConfirmBindProfileScreen(
                    draft = onboardingDraft,
                    publicKeyFingerprint = id.shortFingerprint,
                    deviceBindingShortId = deviceShort,
                    isBinding = isBindingProfile,
                    onEditProfile = { currentScreen = VeilScreen.CreateProfile },
                    onConfirmAndBind = {
                        isBindingProfile = true
                        viewModel.confirmAndBindProfile { res ->
                            isBindingProfile = false
                            res.onSuccess {
                                currentScreen = if (rooms.isNotEmpty()) VeilScreen.HomeShell else VeilScreen.RoomGateway
                            }
                        }
                    }
                )
            }

            VeilScreen.RoomGateway -> {
                WelcomeRoleScreen(
                    username = userProfile?.username.orEmpty(),
                    canGoBack = rooms.isNotEmpty(),
                    onBack = { currentScreen = VeilScreen.HomeShell },
                    onSelectHost = { currentScreen = VeilScreen.HostRoom(returnToSettings = false) },
                    onSelectJoin = { currentScreen = VeilScreen.JoinRoom(returnToSettings = false) }
                )
            }

            is VeilScreen.HostRoom -> {
                HostRoomScreen(
                    viewModel = viewModel,
                    onBack = {
                        currentScreen = if (screen.returnToSettings) {
                            VeilScreen.SettingsHub
                        } else if (rooms.isNotEmpty()) {
                            VeilScreen.HomeShell
                        } else {
                            VeilScreen.RoomGateway
                        }
                    },
                    onEnterRoom = {
                        activeTab = HomeTab.PROFILES
                        currentScreen = VeilScreen.HomeShell
                    }
                )
            }

            is VeilScreen.JoinRoom -> {
                JoinRoomScreen(
                    viewModel = viewModel,
                    onBack = {
                        currentScreen = if (screen.returnToSettings) {
                            VeilScreen.SettingsHub
                        } else if (rooms.isNotEmpty()) {
                            VeilScreen.HomeShell
                        } else {
                            VeilScreen.RoomGateway
                        }
                    },
                    onJoinedRoom = {
                        activeTab = HomeTab.PROFILES
                        currentScreen = VeilScreen.HomeShell
                    }
                )
            }

            VeilScreen.HomeShell -> {
                val currentRoom = activeRoom
                if (currentRoom == null) {
                    LaunchedEffect(rooms) {
                        if (rooms.isEmpty()) currentScreen = VeilScreen.RoomGateway
                        else viewModel.switchActiveRoom(rooms.first().id)
                    }
                } else {
                    HomeShellScreen(
                        activeRoom = currentRoom,
                        activeTab = activeTab,
                        members = roomMembers,
                        communities = communities,
                        callHistory = callHistory,
                        isRoomCreator = isRoomCreator,
                        onSelectTab = { tab ->
                            activeTab = tab
                            if (tab == HomeTab.CALLS) {
                                viewModel.markMissedCallsSeen(currentRoom.id)
                            }
                        },
                        onOpenRoomInvite = { showInviteSheet = true },
                        onOpenSwitchRoom = { showRoomSwitcherSheet = true },
                        onOpenSettings = { currentScreen = VeilScreen.SettingsHub },
                        onOpenDirectChat = { member ->
                            viewModel.clearDirectUnread(currentRoom.id, member.publicKey)
                            currentScreen = VeilScreen.DirectChat(member.publicKey)
                        },
                        onInspectProfile = { member ->
                            currentScreen = VeilScreen.ProfileInspector(member.publicKey, returnToChat = false)
                        },
                        onOpenCreateCommunity = { preselectedMember ->
                            preselectedPeerForCommunity = preselectedMember?.publicKey
                            showCreateCommunitySheet = true
                        },
                        onToggleMute = { member ->
                            viewModel.toggleMuteMember(currentRoom.id, member.publicKey, !member.isMuted)
                        },
                        onToggleBlock = { member ->
                            viewModel.toggleBlockMember(currentRoom.id, member.publicKey, !member.isBlocked)
                        },
                        onReportMember = { member, reason, notes ->
                            viewModel.reportMember(currentRoom.id, member.publicKey, reason, notes)
                        },
                        onRemoveMemberFromRoom = { member ->
                            viewModel.removeMemberFromRoom(currentRoom.id, member)
                        },
                        onOpenCommunityChat = { comm ->
                            viewModel.clearCommunityUnread(comm.id)
                            currentScreen = VeilScreen.CommunityChat(comm.id)
                        },
                        onPlaceVoiceCall = { peerKey, peerName, peerPfp ->
                            startVoiceCallWithPermissionCheck(peerKey, peerName, peerPfp)
                        }
                    )
                }
            }

            is VeilScreen.DirectChat -> {
                val currentRoom = activeRoom
                if (currentRoom != null) {
                    val peer by viewModel.observeMember(currentRoom.id, screen.peerPublicKey)
                        .collectAsStateWithLifecycle(initialValue = roomMembers.firstOrNull { it.publicKey == screen.peerPublicKey })
                    val messages by viewModel.observeMessages(currentRoom.id, ChannelType.DIRECT, screen.peerPublicKey)
                        .collectAsStateWithLifecycle(initialValue = emptyList())

                    LaunchedEffect(messages.size) {
                        viewModel.clearDirectUnread(currentRoom.id, screen.peerPublicKey)
                    }

                    ChatScreen(
                        roomName = currentRoom.roomName,
                        channelType = ChannelType.DIRECT,
                        directPeer = peer,
                        community = null,
                        messages = messages,
                        recordingState = recordingState,
                        playbackState = playbackState,
                        onBack = { currentScreen = VeilScreen.HomeShell },
                        onInspectPeer = {
                            currentScreen = VeilScreen.ProfileInspector(screen.peerPublicKey, returnToChat = true)
                        },
                        onStartVoiceCall = {
                            val p = peer
                            if (p != null) {
                                startVoiceCallWithPermissionCheck(p.publicKey, p.username, p.localPfpPath)
                            }
                        },
                        onSendText = { txt, maxViews ->
                            viewModel.sendText(currentRoom.id, ChannelType.DIRECT, screen.peerPublicKey, txt, maxViews)
                        },
                        onSendMediaUri = { uri, type, maxViews ->
                            viewModel.sendMediaUri(currentRoom.id, ChannelType.DIRECT, screen.peerPublicKey, uri, type, maxViews)
                        },
                        onCreateCameraTarget = { viewModel.createCameraCaptureTarget() },
                        onSendCapturedPhoto = { file, maxViews ->
                            viewModel.sendCapturedPhoto(currentRoom.id, ChannelType.DIRECT, screen.peerPublicKey, file, maxViews)
                        },
                        onSendLocation = { lat, lng, label, maxViews ->
                            viewModel.sendLocation(currentRoom.id, ChannelType.DIRECT, screen.peerPublicKey, lat, lng, label, maxViews)
                        },
                        onStartVoiceRecord = { viewModel.startVoiceRecording() },
                        onStopSendVoiceRecord = { maxViews ->
                            viewModel.stopAndSendVoiceNote(currentRoom.id, ChannelType.DIRECT, screen.peerPublicKey, maxViews)
                        },
                        onCancelVoiceRecord = { viewModel.cancelVoiceRecording() },
                        onToggleVoicePlay = { id, path -> viewModel.voiceManager.togglePlayback(id, path) },
                        onSeekVoice = { id, frac -> viewModel.voiceManager.seekPlayback(id, frac) },
                        onOpenStoredMedia = ::openStoredMediaExternal,
                        onConsumeEphemeralView = { msgId -> viewModel.consumeEphemeralView(msgId) },
                        onPostStatus = { viewModel.postBanner(it) }
                    )
                }
            }

            is VeilScreen.CommunityChat -> {
                val currentRoom = activeRoom
                if (currentRoom != null) {
                    val comm by viewModel.observeCommunity(screen.communityId)
                        .collectAsStateWithLifecycle(initialValue = communities.firstOrNull { it.id == screen.communityId })
                    val messages by viewModel.observeMessages(currentRoom.id, ChannelType.COMMUNITY, screen.communityId)
                        .collectAsStateWithLifecycle(initialValue = emptyList())

                    LaunchedEffect(messages.size) {
                        viewModel.clearCommunityUnread(screen.communityId)
                    }

                    ChatScreen(
                        roomName = currentRoom.roomName,
                        channelType = ChannelType.COMMUNITY,
                        directPeer = null,
                        community = comm,
                        messages = messages,
                        recordingState = recordingState,
                        playbackState = playbackState,
                        onBack = { currentScreen = VeilScreen.HomeShell },
                        onInspectPeer = null,
                        onStartVoiceCall = null,
                        onSendText = { txt, maxViews ->
                            viewModel.sendText(currentRoom.id, ChannelType.COMMUNITY, screen.communityId, txt, maxViews)
                        },
                        onSendMediaUri = { uri, type, maxViews ->
                            viewModel.sendMediaUri(currentRoom.id, ChannelType.COMMUNITY, screen.communityId, uri, type, maxViews)
                        },
                        onCreateCameraTarget = { viewModel.createCameraCaptureTarget() },
                        onSendCapturedPhoto = { file, maxViews ->
                            viewModel.sendCapturedPhoto(currentRoom.id, ChannelType.COMMUNITY, screen.communityId, file, maxViews)
                        },
                        onSendLocation = { lat, lng, label, maxViews ->
                            viewModel.sendLocation(currentRoom.id, ChannelType.COMMUNITY, screen.communityId, lat, lng, label, maxViews)
                        },
                        onStartVoiceRecord = { viewModel.startVoiceRecording() },
                        onStopSendVoiceRecord = { maxViews ->
                            viewModel.stopAndSendVoiceNote(currentRoom.id, ChannelType.COMMUNITY, screen.communityId, maxViews)
                        },
                        onCancelVoiceRecord = { viewModel.cancelVoiceRecording() },
                        onToggleVoicePlay = { id, path -> viewModel.voiceManager.togglePlayback(id, path) },
                        onSeekVoice = { id, frac -> viewModel.voiceManager.seekPlayback(id, frac) },
                        onOpenStoredMedia = ::openStoredMediaExternal,
                        onConsumeEphemeralView = { msgId -> viewModel.consumeEphemeralView(msgId) },
                        onPostStatus = { viewModel.postBanner(it) }
                    )
                }
            }

            is VeilScreen.ProfileInspector -> {
                val currentRoom = activeRoom
                if (currentRoom != null) {
                    val member by viewModel.observeMember(currentRoom.id, screen.peerPublicKey)
                        .collectAsStateWithLifecycle(initialValue = roomMembers.firstOrNull { it.publicKey == screen.peerPublicKey })
                    val target = member
                    if (target != null) {
                        val groups = remember(target.safetyNumber) {
                            viewModel.repository.crypto.formatSafetyNumberGroups(target.safetyNumber)
                        }
                        ProfileInspectorScreen(
                            roomName = currentRoom.roomName,
                            member = target,
                            safetyNumberGroups = groups,
                            isRoomCreator = isRoomCreator,
                            onBack = {
                                currentScreen = if (screen.returnToChat) {
                                    VeilScreen.DirectChat(target.publicKey)
                                } else {
                                    VeilScreen.HomeShell
                                }
                            },
                            onMessage = {
                                currentScreen = VeilScreen.DirectChat(target.publicKey)
                            },
                            onVoiceCall = {
                                startVoiceCallWithPermissionCheck(target.publicKey, target.username, target.localPfpPath)
                            },
                            onToggleMute = {
                                viewModel.toggleMuteMember(currentRoom.id, target.publicKey, !target.isMuted)
                            },
                            onToggleBlock = {
                                viewModel.toggleBlockMember(currentRoom.id, target.publicKey, !target.isBlocked)
                            },
                            onToggleVerified = { verified ->
                                viewModel.setMemberVerified(currentRoom.id, target.publicKey, verified)
                            },
                            onReport = { reason, notes ->
                                viewModel.reportMember(currentRoom.id, target.publicKey, reason, notes)
                            },
                            onRemoveFromRoom = {
                                viewModel.removeMemberFromRoom(currentRoom.id, target) { res ->
                                    if (res.isSuccess) {
                                        currentScreen = VeilScreen.HomeShell
                                    }
                                }
                            }
                        )
                    }
                }
            }

            VeilScreen.SettingsHub -> {
                val id = remember { viewModel.getLocalIdentity() }
                val idUri = remember(id) { viewModel.repository.crypto.buildIdentityUri(id) }
                SettingsHubScreen(
                    userProfile = userProfile,
                    publicKeyFingerprint = id.shortFingerprint,
                    identityUri = idUri,
                    rooms = rooms,
                    activeRoomId = activeRoom?.id,
                    accentColor = accentColor,
                    isCheckingUpdate = isCheckingUpdate,
                    onBack = { currentScreen = VeilScreen.HomeShell },
                    onCreateCameraTarget = { viewModel.createCameraCaptureTarget() },
                    onCompressAvatarUri = { uri, cb -> viewModel.compressAndStoreAvatar(uri, cb) },
                    onSaveProfile = { user, motto, gender, pfp ->
                        viewModel.updateProfileAndSync(user, motto, gender, pfp) { }
                    },
                    onSwitchRoom = { roomId ->
                        viewModel.switchActiveRoom(roomId)
                        currentScreen = VeilScreen.HomeShell
                    },
                    onDeleteRoom = { roomId -> viewModel.deleteRoom(roomId) },
                    onHostNewRoom = { currentScreen = VeilScreen.HostRoom(returnToSettings = true) },
                    onJoinNewRoom = { currentScreen = VeilScreen.JoinRoom(returnToSettings = true) },
                    onSelectAccentColor = { viewModel.setAccentColor(it) },
                    onOpenBackendSettings = { currentScreen = VeilScreen.BackendSettings },
                    onCheckForUpdates = { viewModel.checkForUpdates(silent = false) },
                    onPostStatus = { viewModel.postBanner(it) }
                )
            }

            VeilScreen.BackendSettings -> {
                BackendSettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = VeilScreen.SettingsHub }
                )
            }
        }

        // Room 16-Character Veil Code Invite Sheet
        val roomForInvite = activeRoom
        if (showInviteSheet && roomForInvite != null) {
            RoomInviteSheet(
                room = roomForInvite,
                onDismiss = { showInviteSheet = false },
                onCopied = { viewModel.postBanner(it) }
            )
        }

        // Quick Room Switcher Bottom Sheet
        if (showRoomSwitcherSheet) {
            RoomSwitcherSheet(
                rooms = rooms,
                activeRoomId = activeRoom?.id,
                onDismiss = { showRoomSwitcherSheet = false },
                onSelectRoom = { roomId ->
                    showRoomSwitcherSheet = false
                    viewModel.switchActiveRoom(roomId)
                },
                onHostRoom = {
                    showRoomSwitcherSheet = false
                    currentScreen = VeilScreen.HostRoom(returnToSettings = false)
                },
                onJoinRoom = {
                    showRoomSwitcherSheet = false
                    currentScreen = VeilScreen.JoinRoom(returnToSettings = false)
                }
            )
        }

        // Create Private Community Sheet (Hard Minimum 3 Profiles Total)
        val roomForComm = activeRoom
        if (showCreateCommunitySheet && roomForComm != null) {
            CreateCommunitySheet(
                roomMembers = roomMembers,
                preselectedPeerPublicKey = preselectedPeerForCommunity,
                onDismiss = {
                    showCreateCommunitySheet = false
                    preselectedPeerForCommunity = null
                },
                onCreate = { name, desc, selectedKeys ->
                    viewModel.createPrivateCommunity(roomForComm.id, name, desc, selectedKeys) { res ->
                        res.onSuccess { created ->
                            showCreateCommunitySheet = false
                            preselectedPeerForCommunity = null
                            activeTab = HomeTab.COMMUNITIES
                            currentScreen = VeilScreen.CommunityChat(created.id)
                        }
                    }
                }
            )
        }

        // Active Voice-Only Call Full-Screen Overlay
        if (activeCallState.status != VoiceCallStatus.IDLE) {
            ActiveVoiceCallOverlay(
                callState = activeCallState,
                onToggleMute = { viewModel.voiceCallController.toggleMicMute() },
                onToggleSpeaker = { viewModel.voiceCallController.toggleSpeaker() },
                onEndCall = { viewModel.voiceCallController.endActiveCall() },
                onAcceptIncoming = { viewModel.voiceCallController.acceptIncomingCall() },
                onDeclineIncoming = { viewModel.voiceCallController.declineIncomingCall() }
            )
        }

        // Dark Glass In-App GitHub Release Update Dialog
        val update = availableUpdate
        if (update != null) {
            UpdateAvailableDialog(
                info = update,
                onLater = { viewModel.dismissUpdateDialog() },
                onUpdateNow = {
                    viewModel.dismissUpdateDialog()
                    viewModel.startApkUpdateDownload(update)
                }
            )
        }

        // Floating Status Banner
        AnimatedVisibility(
            visible = statusBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 84.dp)
        ) {
            GlassCard(
                cornerRadius = 16.dp,
                containerColor = GlassSurfaceElevated,
                borderTopColor = accent.highlight.copy(alpha = 0.45f)
            ) {
                Text(
                    text = statusBanner.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(color = SnowPrimary)
                )
            }
        }
    }
}

@Composable
private fun VeilSplashScreen(accentColor: Color) {
    val logoScale = remember { Animatable(0.84f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B0E))
            .testTag("veil_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Subtle obsidian radial glow matching active user accent
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            accentColor.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .alpha(contentAlpha.value)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .scale(logoScale.value)
                    .size(148.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0xFF0F1014))
                    .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(36.dp)),
                contentAlignment = Alignment.Center
            ) {
                VeilDynamicLogo(
                    accentColor = accentColor,
                    modifier = Modifier.size(134.dp),
                    contentDescription = "Veil"
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Veil",
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = (-0.5).sp,
                color = SnowPrimary,
                modifier = Modifier.testTag("splash_title")
            )

            Text(
                text = stringResource(R.string.splash_motto),
                fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = SlateSecondary,
                modifier = Modifier.testTag("splash_motto")
            )
        }
    }
}

@Composable
private fun HomeShellScreen(
    activeRoom: VeilRoomEntity,
    activeTab: HomeTab,
    members: List<com.example.data.local.RoomMemberEntity>,
    communities: List<com.example.data.local.CommunityEntity>,
    callHistory: List<com.example.data.local.CallHistoryEntity>,
    isRoomCreator: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onOpenRoomInvite: () -> Unit,
    onOpenSwitchRoom: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDirectChat: (com.example.data.local.RoomMemberEntity) -> Unit,
    onInspectProfile: (com.example.data.local.RoomMemberEntity) -> Unit,
    onOpenCreateCommunity: (com.example.data.local.RoomMemberEntity?) -> Unit,
    onToggleMute: (com.example.data.local.RoomMemberEntity) -> Unit,
    onToggleBlock: (com.example.data.local.RoomMemberEntity) -> Unit,
    onReportMember: (com.example.data.local.RoomMemberEntity, String, String) -> Unit,
    onRemoveMemberFromRoom: (com.example.data.local.RoomMemberEntity) -> Unit,
    onOpenCommunityChat: (com.example.data.local.CommunityEntity) -> Unit,
    onPlaceVoiceCall: (String, String, String) -> Unit
) {
    val accent = LocalVeilAccent.current
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    val profilesUnread = remember(members) { members.sumOf { it.unreadCount } }
    val communitiesHasUnread = remember(communities) { communities.any { it.unreadCount > 0 } }
    val missedCallsCount = remember(callHistory) {
        callHistory.count { it.direction == CallDirection.MISSED && !it.isSeen }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val useRail = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = GlassSurfaceElevated.copy(alpha = 0.85f),
                    contentColor = SnowPrimary,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HomeTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onSelectTab(tab) },
                            icon = {
                                HomeTabIconWithBadge(
                                    tab = tab,
                                    selected = selected,
                                    profilesUnread = profilesUnread,
                                    communitiesHasUnread = communitiesHasUnread,
                                    missedCallsCount = missedCallsCount
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = accent.highlight,
                                selectedTextColor = accent.highlight,
                                indicatorColor = accent.container,
                                unselectedIconColor = SlateSecondary,
                                unselectedTextColor = SlateSecondary
                            )
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // WhatsApp-Inspired Dark Glass Top Bar:
                // Left: Stacked Veil (22.sp Bold Space Grotesk) + Lock & Room Name (12.sp Medium Inter)
                // Right: Search + MoreVert (Room Code & QR Invite, Switch Room, Settings)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianBg.copy(alpha = 0.92f))
                        .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isSearchOpen) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(GlassSurfaceSubtle)
                                .border(1.dp, GlassBorderTop, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = SlateSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search ${activeTab.label.lowercase()}...",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = SnowPrimary),
                                    cursorBrush = SolidColor(accent.highlight),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("home_inline_search_input")
                                )
                            }
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    isSearchOpen = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close search",
                                    tint = SlateSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // Two-Line Stacked Header
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onOpenSwitchRoom),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Veil",
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = SnowPrimary,
                                maxLines = 1,
                                modifier = Modifier.testTag("home_header_brand")
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = accent.highlight,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = activeRoom.roomName,
                                    fontFamily = InterFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = SlateSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("home_header_room_name")
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isSearchOpen = true },
                                modifier = Modifier.testTag("home_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search active room",
                                    tint = SnowPrimary
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.testTag("home_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "More options",
                                        tint = SnowPrimary
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    containerColor = GlassSurfaceElevated
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Room Veil Code", color = SnowPrimary) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Key, contentDescription = null, tint = accent.highlight)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenRoomInvite()
                                        },
                                        modifier = Modifier.testTag("menu_room_veil_code")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Switch Room", color = SnowPrimary) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = SnowPrimary)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSwitchRoom()
                                        },
                                        modifier = Modifier.testTag("menu_switch_room")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = SnowPrimary) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Settings, contentDescription = null, tint = SnowPrimary)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSettings()
                                        },
                                        modifier = Modifier.testTag("menu_settings")
                                    )
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassDivider))

                // Active Tab Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeTab) {
                        HomeTab.PROFILES -> ProfilesTabScreen(
                            roomName = activeRoom.roomName,
                            members = members,
                            searchQuery = searchQuery,
                            isRoomCreator = isRoomCreator,
                            onOpenDirectChat = onOpenDirectChat,
                            onInspectProfile = onInspectProfile,
                            onCreateCommunityWithPeer = onOpenCreateCommunity,
                            onToggleMute = onToggleMute,
                            onToggleBlock = onToggleBlock,
                            onReportMember = onReportMember,
                            onRemoveMemberFromRoom = onRemoveMemberFromRoom,
                            onShareRoomInvite = onOpenRoomInvite
                        )

                        HomeTab.COMMUNITIES -> CommunitiesTabScreen(
                            communities = communities,
                            searchQuery = searchQuery,
                            onOpenCommunity = onOpenCommunityChat,
                            onOpenCreateCommunity = { onOpenCreateCommunity(null) }
                        )

                        HomeTab.CALLS -> CallsTabScreen(
                            callHistory = callHistory,
                            searchQuery = searchQuery,
                            onPlaceVoiceCall = onPlaceVoiceCall
                        )
                    }
                }

                // Bottom Navigation Bar (Strictly 3 Tabs: Profiles, Communities, Calls — NO Updates Tab)
                if (!useRail) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassDivider))
                    NavigationBar(
                        containerColor = GlassSurfaceElevated.copy(alpha = 0.92f),
                        contentColor = SnowPrimary,
                        tonalElevation = 0.dp
                    ) {
                        HomeTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onSelectTab(tab) },
                                icon = {
                                    HomeTabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        profilesUnread = profilesUnread,
                                        communitiesHasUnread = communitiesHasUnread,
                                        missedCallsCount = missedCallsCount
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent.highlight,
                                    selectedTextColor = accent.highlight,
                                    indicatorColor = accent.container,
                                    unselectedIconColor = SlateSecondary,
                                    unselectedTextColor = SlateSecondary
                                ),
                                modifier = Modifier.testTag("bottom_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabIconWithBadge(
    tab: HomeTab,
    selected: Boolean,
    profilesUnread: Int,
    communitiesHasUnread: Boolean,
    missedCallsCount: Int
) {
    val accent = LocalVeilAccent.current
    val icon = when (tab) {
        HomeTab.PROFILES -> if (selected) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline
        HomeTab.COMMUNITIES -> if (selected) Icons.Rounded.Groups else Icons.Outlined.Groups
        HomeTab.CALLS -> if (selected) Icons.Rounded.Call else Icons.Outlined.Call
    }

    BadgedBox(
        badge = {
            when (tab) {
                HomeTab.PROFILES -> if (profilesUnread > 0) {
                    Badge(containerColor = accent.primary, contentColor = ObsidianDeep) {
                        Text(if (profilesUnread > 99) "99+" else profilesUnread.toString())
                    }
                }
                HomeTab.COMMUNITIES -> if (communitiesHasUnread) {
                    Badge(containerColor = accent.primary)
                }
                HomeTab.CALLS -> if (missedCallsCount > 0) {
                    Badge(containerColor = CoralError, contentColor = ObsidianDeep) {
                        Text(missedCallsCount.toString())
                    }
                }
            }
        }
    ) {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSwitcherSheet(
    rooms: List<VeilRoomEntity>,
    activeRoomId: String?,
    onDismiss: () -> Unit,
    onSelectRoom: (String) -> Unit,
    onHostRoom: () -> Unit,
    onJoinRoom: () -> Unit
) {
    val accent = LocalVeilAccent.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassSurfaceElevated,
        contentColor = SnowPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Switch Veil Room",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = SnowPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            rooms.forEach { room ->
                val active = room.id == activeRoomId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) accent.container else GlassSurfaceSubtle)
                        .border(1.dp, if (active) accent.highlight else GlassBorderTop, RoundedCornerShape(14.dp))
                        .clickable { onSelectRoom(room.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("switch_room_item_${room.veilCode}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (active) accent.highlight else SlateSecondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = room.roomName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = SnowPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = room.veilCode.chunked(4).joinToString(" - "),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                color = SlateSecondary
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassPrimaryButton(
                    text = "Host Room",
                    icon = Icons.Rounded.Add,
                    onClick = onHostRoom,
                    modifier = Modifier.weight(1f),
                    tag = "switcher_host_room_button"
                )
                GlassSecondaryButton(
                    text = "Join Room",
                    icon = Icons.Rounded.Login,
                    accentColor = accent.highlight,
                    onClick = onJoinRoom,
                    modifier = Modifier.weight(1f),
                    tag = "switcher_join_room_button"
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: ReleaseUpdateInfo,
    onLater: () -> Unit,
    onUpdateNow: () -> Unit
) {
    val accent = LocalVeilAccent.current
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = GlassSurfaceElevated,
        titleContentColor = SnowPrimary,
        textContentColor = SlateSecondary,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = accent.highlight,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "Veil ${info.versionTag} Available",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A new release of Veil is ready to download directly from GitHub Releases.",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateSecondary)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurfaceSubtle)
                        .padding(10.dp)
                ) {
                    Text(
                        text = info.releaseNotes.take(400),
                        style = MaterialTheme.typography.bodySmall.copy(color = SnowPrimary)
                    )
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                text = "Update Now",
                onClick = onUpdateNow,
                tag = "update_dialog_confirm"
            )
        },
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("update_dialog_later")) {
                Text("Later", color = SlateSecondary)
            }
        }
    )
}
