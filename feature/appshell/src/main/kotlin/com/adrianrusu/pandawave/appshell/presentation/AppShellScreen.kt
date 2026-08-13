package com.adrianrusu.pandawave.appshell.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.adrianrusu.pandawave.appshell.domain.AppShellIntent
import com.adrianrusu.pandawave.appshell.domain.AppShellState
import com.adrianrusu.pandawave.appshell.navigation.HomeDestination
import com.adrianrusu.pandawave.appshell.navigation.LibraryDestination
import com.adrianrusu.pandawave.appshell.navigation.LoginDestination
import com.adrianrusu.pandawave.appshell.navigation.NowPlayingDestination
import com.adrianrusu.pandawave.appshell.navigation.PandaWaveDestination
import com.adrianrusu.pandawave.appshell.navigation.PandaWaveNavigator
import com.adrianrusu.pandawave.appshell.navigation.PreferencesDestination
import com.adrianrusu.pandawave.appshell.navigation.ProfileDestination
import com.adrianrusu.pandawave.appshell.navigation.RegisterDestination
import com.adrianrusu.pandawave.appshell.navigation.SearchDestination
import com.adrianrusu.pandawave.appshell.navigation.navigationId
import com.adrianrusu.pandawave.appshell.navigation.primaryDestinations
import com.adrianrusu.pandawave.appshell.navigation.selectedRailDestination
import com.adrianrusu.pandawave.appshell.navigation.shouldShowMiniPlayer
import com.adrianrusu.pandawave.core.designsystem.R as DesignSystemR
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.appContentPadding
import com.adrianrusu.pandawave.core.ui.icons.PandaWaveIcons
import com.adrianrusu.pandawave.core.ui.miniplayer.BambooMiniPlayer
import com.adrianrusu.pandawave.core.ui.navigation.BambooNavigationItemModel
import com.adrianrusu.pandawave.core.ui.navigation.BambooNavigationRail
import com.adrianrusu.pandawave.feature.appshell.R
import com.adrianrusu.pandawave.feature.home.HomeRoute
import com.adrianrusu.pandawave.feature.auth.LoginRoute
import com.adrianrusu.pandawave.feature.auth.RegisterRoute
import com.adrianrusu.pandawave.feature.auth.domain.LogoutPhase
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAccountUi
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthNotice
import com.adrianrusu.pandawave.feature.auth.presentation.ProfileAuthViewModel
import com.adrianrusu.pandawave.feature.library.LibraryRoute
import com.adrianrusu.pandawave.feature.nowplaying.NowPlayingRoute
import com.adrianrusu.pandawave.feature.profile.ProfileRoute
import com.adrianrusu.pandawave.feature.profile.ProfileUiAccount
import com.adrianrusu.pandawave.feature.profile.presentation.ProfileViewModel
import com.adrianrusu.pandawave.feature.search.SearchRoute
import com.adrianrusu.pandawave.feature.settings.SettingsRoute
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppShellScreen(
    state: AppShellState,
    interactiveAccountActionsAllowed: Boolean,
    onIntent: (AppShellIntent) -> Unit,
    onMoveTaskToBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(HomeDestination)
    val navigator = remember(backStack) { PandaWaveNavigator(backStack) }
    val currentDestination = navigator.currentDestination
    var ambientVisible by remember { mutableStateOf(false) }
    val chrome = resolveAppShellChrome(
        currentDestination = currentDestination,
        ambientVisible = ambientVisible
    )

    BackHandler(enabled = navigator.isAtRoot, onBack = onMoveTaskToBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (chrome.showMiniPlayer) {
                BambooMiniPlayer(
                    modifier = Modifier
                        .focusRestorer()
                        .focusGroup()
                        .testTag("mini-player-zone"),
                    state = state.miniPlayer,
                    controlsEnabled = state.canDispatchEngineCommands,
                    onClick = navigator::openNowPlaying,
                    onSkipPreviousClick = {
                        onIntent(AppShellIntent.SkipPrevious)
                    },
                    onPlayPauseClick = {
                        onIntent(AppShellIntent.TogglePlayback)
                    },
                    onSkipNextClick = {
                        onIntent(AppShellIntent.SkipNext)
                    }
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val navigationItems = primaryDestinations.map { destination ->
                BambooNavigationItemModel(
                    id = destination.navigationId,
                    label = destination.localizedLabel(),
                    icon = destination.icon,
                    selected = destination == currentDestination.selectedRailDestination,
                    showLabel = true
                )
            }
            if (chrome.showNavigationRail) {
                BambooNavigationRail(
                    items = navigationItems,
                    logo = painterResource(DesignSystemR.drawable.pandawave_ic_logo),
                    logoContentDescription = stringResource(
                        R.string.pandawave_navigation_open_now_playing
                    ),
                    onLogoClick = navigator::openNowPlaying,
                    onItemClick = { destinationId ->
                        primaryDestinations
                            .firstOrNull { it.navigationId == destinationId }
                            ?.let(navigator::selectPrimary)
                    },
                    bottomItemId = ProfileDestination.navigationId
                )
            }
            AppShellContent(
                backStack = backStack,
                navigator = navigator,
                applyContentPadding = chrome.applyContentPadding,
                onAmbientVisibilityChanged = { visible ->
                    ambientVisible = visible && currentDestination == NowPlayingDestination
                },
                interactiveAccountActionsAllowed = interactiveAccountActionsAllowed
            )
        }
    }
}

@Composable
private fun PandaWaveDestination.localizedLabel(): String = when (this) {
    HomeDestination -> stringResource(R.string.pandawave_navigation_home)
    LibraryDestination -> stringResource(R.string.pandawave_navigation_library)
    SearchDestination -> stringResource(R.string.pandawave_navigation_search)
    ProfileDestination -> stringResource(R.string.pandawave_navigation_profile)
    NowPlayingDestination -> stringResource(R.string.pandawave_navigation_now_playing)
    PreferencesDestination -> stringResource(R.string.pandawave_navigation_settings)
    LoginDestination -> stringResource(R.string.pandawave_auth_navigation_login)
    RegisterDestination -> stringResource(R.string.pandawave_auth_navigation_register)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppShellContent(
    backStack: List<androidx.navigation3.runtime.NavKey>,
    navigator: PandaWaveNavigator,
    applyContentPadding: Boolean,
    onAmbientVisibilityChanged: (Boolean) -> Unit,
    interactiveAccountActionsAllowed: Boolean
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer()
            .focusGroup()
            .testTag("destination-content-zone")
            .then(
                if (applyContentPadding) {
                    Modifier.padding(tokens.layout.appContentPadding)
                } else {
                    Modifier
                }
            )
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<HomeDestination> {
                    HomeRoute(modifier = Modifier.fillMaxSize())
                }
                entry<LibraryDestination> {
                    LibraryRoute(modifier = Modifier.fillMaxSize())
                }
                entry<SearchDestination> {
                    SearchRoute(modifier = Modifier.fillMaxSize())
                }
                entry<ProfileDestination> {
                    val profileViewModel: ProfileAuthViewModel = hiltViewModel()
                    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
                    val authAvailable by profileViewModel.isAvailable.collectAsStateWithLifecycle()
                    val canopyProfileViewModel: ProfileViewModel = hiltViewModel()
                    val canopyProfileState by canopyProfileViewModel.state.collectAsStateWithLifecycle()
                    val accountSessionsState by canopyProfileViewModel.accountSessionsState.collectAsStateWithLifecycle()
                    var logoutWarning by remember { mutableStateOf<String?>(null) }
                    val remoteWarning = stringResource(R.string.pandawave_logout_remote_warning)
                    val failedWarning = stringResource(R.string.pandawave_logout_failed_warning)
                    LaunchedEffect(profileViewModel) {
                        profileViewModel.effects.collect { effect ->
                            logoutWarning = when (effect) {
                                ProfileAuthNotice.REMOTE_LOGOUT_UNCONFIRMED -> remoteWarning
                                ProfileAuthNotice.LOGOUT_FAILED -> failedWarning
                                else -> null
                            }
                            delay(LOGOUT_WARNING_DURATION_MILLIS)
                            logoutWarning = null
                        }
                    }
                    ProfileRoute(
                        modifier = Modifier.fillMaxSize(),
                        account = profileState.account.toProfileUiAccount(),
                        accountActionsEnabled = interactiveAccountActionsAllowed && authAvailable,
                        logoutInProgress = profileState.logoutPhase != LogoutPhase.IDLE,
                        logoutWarning = logoutWarning,
                        onLoginClick = navigator::openLogin,
                        onRegisterClick = navigator::openRegister,
                        onLogoutClick = profileViewModel::logout,
                        onSettingsClick = navigator::openPreferences,
                        profileState = canopyProfileState,
                        onRefreshProfile = canopyProfileViewModel::refresh,
                        onUpsertProfile = canopyProfileViewModel::upsert,
                        onUpdateProfileDisplayName = canopyProfileViewModel::updateDisplayName,
                        onDeleteProfile = canopyProfileViewModel::delete,
                        accountSessionsState = accountSessionsState,
                        onRefreshAccountSessions = canopyProfileViewModel::refreshAccountSessions,
                        onLoadNextDeviceSessionsPage = canopyProfileViewModel::loadNextDeviceSessionsPage,
                        onRevokeDeviceSession = canopyProfileViewModel::revokeDeviceSession,
                        onDeleteAccount = canopyProfileViewModel::deleteAccount
                    )
                }
                entry<LoginDestination> {
                    LoginRoute(
                        modifier = Modifier.fillMaxSize(),
                        interactiveAllowed = interactiveAccountActionsAllowed,
                        onClose = {
                            if (navigator.currentDestination == LoginDestination) navigator.pop()
                        }
                    )
                }
                entry<RegisterDestination> {
                    RegisterRoute(
                        modifier = Modifier.fillMaxSize(),
                        interactiveAllowed = interactiveAccountActionsAllowed,
                        onClose = {
                            if (navigator.currentDestination == RegisterDestination) navigator.pop()
                        }
                    )
                }
                entry<PreferencesDestination> {
                    SettingsRoute(modifier = Modifier.fillMaxSize())
                }
                entry<NowPlayingDestination> {
                    NowPlayingRoute(
                        modifier = Modifier.fillMaxSize(),
                        onAmbientVisibilityChanged = onAmbientVisibilityChanged,
                        onLibraryClick = {
                            navigator.selectPrimary(LibraryDestination)
                        }
                    )
                }
            }
        )
    }
}

private val PandaWaveDestination.icon: ImageVector
    get() = when (this) {
        HomeDestination -> PandaWaveIcons.Home
        LibraryDestination -> PandaWaveIcons.Library
        SearchDestination -> PandaWaveIcons.Search
        ProfileDestination -> PandaWaveIcons.Profile
        NowPlayingDestination -> PandaWaveIcons.NowPlaying
        PreferencesDestination -> PandaWaveIcons.Settings
        LoginDestination, RegisterDestination -> PandaWaveIcons.Profile
    }

private fun ProfileAccountUi.toProfileUiAccount(): ProfileUiAccount = when (this) {
    ProfileAccountUi.Anonymous -> ProfileUiAccount.Anonymous
    is ProfileAccountUi.Authenticated -> ProfileUiAccount.Authenticated(
        email = email,
        accountStatus = accountStatus,
        deviceLabel = deviceLabel,
        sessionCreatedAtEpochMillis = sessionCreatedAtEpochMillis,
        sessionLastActiveAtEpochMillis = sessionLastActiveAtEpochMillis
    )
}

private const val LOGOUT_WARNING_DURATION_MILLIS = 5_000L
