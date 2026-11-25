package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.VeilViewModel
import com.example.ui.components.VeilObsidianBackdrop
import com.example.ui.screens.BackendSettingsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HostRoomScreen
import com.example.ui.screens.IdentityScreen
import com.example.ui.screens.JoinRoomScreen
import com.example.ui.screens.RoomsListScreen
import com.example.ui.screens.WelcomeRoleScreen
import com.example.ui.theme.GlacierMint
import com.example.ui.theme.GlacierMintContainer
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassDivider
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import com.example.ui.theme.VeilTheme
import kotlinx.coroutines.delay

private enum class MainTab {
    ROOMS,
    BACKEND,
    IDENTITY
}

private sealed interface AppScreen {
    data object WelcomeRole : AppScreen
    data object HostRoom : AppScreen
    data object JoinRoom : AppScreen
    data object MainShell : AppScreen
    data class Chat(val roomId: String) : AppScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            VeilTheme {
                VeilApp()
            }
        }
    }
}

@Composable
private fun VeilApp(viewModel: VeilViewModel = viewModel()) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    var hasDecidedInitialRoute by rememberSaveable { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.WelcomeRole) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.ROOMS) }
    var selectedDesktopRoomId by rememberSaveable { mutableStateOf<String?>(null) }

    // On first load, if the user already has Veil Rooms saved locally, go straight to Rooms.
    // Otherwise start on the Welcome Host-or-Join entry screen as requested.
    LaunchedEffect(rooms) {
        if (!hasDecidedInitialRoute) {
            delay(120)
            if (rooms.isNotEmpty()) {
                currentScreen = AppScreen.MainShell
                selectedDesktopRoomId = rooms.first().id
            } else {
                currentScreen = AppScreen.WelcomeRole
            }
            hasDecidedInitialRoute = true
        }
    }

    // Auto-dismiss glass toast banner
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2800)
            viewModel.consumeToast()
        }
    }

    VeilObsidianBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val screen = currentScreen) {
                is AppScreen.WelcomeRole -> {
                    if (rooms.isNotEmpty()) {
                        BackHandler { currentScreen = AppScreen.MainShell }
                    }
                    WelcomeRoleScreen(
                        canGoBack = rooms.isNotEmpty(),
                        onBack = { currentScreen = AppScreen.MainShell },
                        onSelectHost = { currentScreen = AppScreen.HostRoom },
                        onSelectJoin = { currentScreen = AppScreen.JoinRoom }
                    )
                }

                is AppScreen.HostRoom -> {
                    BackHandler {
                        viewModel.clearHostOutcome()
                        viewModel.clearConnectionTest()
                        currentScreen = if (rooms.isNotEmpty()) AppScreen.MainShell else AppScreen.WelcomeRole
                    }
                    HostRoomScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (rooms.isNotEmpty()) AppScreen.MainShell else AppScreen.WelcomeRole
                        },
                        onEnterRoom = { roomId ->
                            selectedDesktopRoomId = roomId
                            selectedTab = MainTab.ROOMS
                            currentScreen = AppScreen.Chat(roomId)
                        }
                    )
                }

                is AppScreen.JoinRoom -> {
                    BackHandler {
                        currentScreen = if (rooms.isNotEmpty()) AppScreen.MainShell else AppScreen.WelcomeRole
                    }
                    JoinRoomScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (rooms.isNotEmpty()) AppScreen.MainShell else AppScreen.WelcomeRole
                        },
                        onJoinedRoom = { roomId ->
                            selectedDesktopRoomId = roomId
                            selectedTab = MainTab.ROOMS
                            currentScreen = AppScreen.Chat(roomId)
                        }
                    )
                }

                is AppScreen.Chat -> {
                    BackHandler { currentScreen = AppScreen.MainShell }
                    ChatScreen(
                        roomId = screen.roomId,
                        viewModel = viewModel,
                        showBackButton = true,
                        onBack = { currentScreen = AppScreen.MainShell }
                    )
                }

                is AppScreen.MainShell -> {
                    AdaptiveMainShell(
                        viewModel = viewModel,
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        selectedDesktopRoomId = selectedDesktopRoomId,
                        onSelectRoom = { roomId, isExpanded ->
                            selectedDesktopRoomId = roomId
                            if (!isExpanded) {
                                currentScreen = AppScreen.Chat(roomId)
                            }
                        },
                        onOpenRoleChooser = { currentScreen = AppScreen.WelcomeRole },
                        onHostNewRoom = { currentScreen = AppScreen.HostRoom },
                        onJoinNewRoom = { currentScreen = AppScreen.JoinRoom }
                    )
                }
            }

            // Minimalist Frosted Glass Toast Overlay
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 14.dp, start = 20.dp, end = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurfaceElevated)
                        .border(1.dp, GlacierMint.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = toastMessage.orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(color = SnowPrimary)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveMainShell(
    viewModel: VeilViewModel,
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    selectedDesktopRoomId: String?,
    onSelectRoom: (String, Boolean) -> Unit,
    onOpenRoleChooser: () -> Unit,
    onHostNewRoom: () -> Unit,
    onJoinNewRoom: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 800.dp

        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Adaptive Glass Navigation Rail for Tablets / Foldables
                GlassNavigationRail(
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(GlassDivider)
                )

                when (selectedTab) {
                    MainTab.ROOMS -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            RoomsListScreen(
                                viewModel = viewModel,
                                selectedRoomId = selectedDesktopRoomId,
                                onOpenRoom = { onSelectRoom(it, true) },
                                onHostNewRoom = onHostNewRoom,
                                onJoinNewRoom = onJoinNewRoom,
                                onOpenRoleChooser = onOpenRoleChooser,
                                modifier = Modifier
                                    .width(360.dp)
                                    .fillMaxHeight()
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(GlassDivider)
                            )
                            if (selectedDesktopRoomId != null) {
                                ChatScreen(
                                    roomId = selectedDesktopRoomId,
                                    viewModel = viewModel,
                                    showBackButton = false,
                                    onBack = {},
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = SlateMuted,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Select a Veil Room",
                                            style = MaterialTheme.typography.titleMedium.copy(color = SlateSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    MainTab.BACKEND -> BackendSettingsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                    MainTab.IDENTITY -> IdentityScreen(
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // Compact Phone Layout with Floating Glass Pill Navigation Bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                when (selectedTab) {
                    MainTab.ROOMS -> RoomsListScreen(
                        viewModel = viewModel,
                        selectedRoomId = null,
                        onOpenRoom = { onSelectRoom(it, false) },
                        onHostNewRoom = onHostNewRoom,
                        onJoinNewRoom = onJoinNewRoom,
                        onOpenRoleChooser = onOpenRoleChooser,
                        modifier = Modifier.fillMaxSize()
                    )
                    MainTab.BACKEND -> BackendSettingsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    MainTab.IDENTITY -> IdentityScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                FloatingGlassBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingGlassBottomBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(GlassSurfaceElevated)
            .border(1.dp, GlassBorderTop, RoundedCornerShape(26.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassNavItem(
            label = stringResource(R.string.nav_rooms),
            icon = if (selectedTab == MainTab.ROOMS) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline,
            selected = selectedTab == MainTab.ROOMS,
            testTag = "nav_tab_rooms",
            onClick = { onSelectTab(MainTab.ROOMS) },
            modifier = Modifier.weight(1f)
        )
        GlassNavItem(
            label = stringResource(R.string.nav_backend),
            icon = if (selectedTab == MainTab.BACKEND) Icons.Rounded.Storage else Icons.Outlined.Storage,
            selected = selectedTab == MainTab.BACKEND,
            testTag = "nav_tab_backend",
            onClick = { onSelectTab(MainTab.BACKEND) },
            modifier = Modifier.weight(1f)
        )
        GlassNavItem(
            label = stringResource(R.string.nav_identity),
            icon = if (selectedTab == MainTab.IDENTITY) Icons.Rounded.Fingerprint else Icons.Outlined.Fingerprint,
            selected = selectedTab == MainTab.IDENTITY,
            testTag = "nav_tab_identity",
            onClick = { onSelectTab(MainTab.IDENTITY) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GlassNavigationRail(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .padding(vertical = 20.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassNavItem(
            label = stringResource(R.string.nav_rooms),
            icon = if (selectedTab == MainTab.ROOMS) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline,
            selected = selectedTab == MainTab.ROOMS,
            testTag = "nav_rail_rooms",
            onClick = { onSelectTab(MainTab.ROOMS) },
            modifier = Modifier.fillMaxWidth()
        )
        GlassNavItem(
            label = stringResource(R.string.nav_backend),
            icon = if (selectedTab == MainTab.BACKEND) Icons.Rounded.Storage else Icons.Outlined.Storage,
            selected = selectedTab == MainTab.BACKEND,
            testTag = "nav_rail_backend",
            onClick = { onSelectTab(MainTab.BACKEND) },
            modifier = Modifier.fillMaxWidth()
        )
        GlassNavItem(
            label = stringResource(R.string.nav_identity),
            icon = if (selectedTab == MainTab.IDENTITY) Icons.Rounded.Fingerprint else Icons.Outlined.Fingerprint,
            selected = selectedTab == MainTab.IDENTITY,
            testTag = "nav_rail_identity",
            onClick = { onSelectTab(MainTab.IDENTITY) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GlassNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) GlacierMintContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) GlacierMint else SlateMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) GlacierMint else SlateMuted
            )
        )
    }
}
