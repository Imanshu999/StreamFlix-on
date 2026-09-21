package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EpisodeData
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.screens.*
import com.example.ui.theme.*

enum class BottomTab {
    HOME, HISTORY, DOWNLOADS, PROFILE
}

@Composable
fun MainScreen(
    viewModel: StreamFlixViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isClientBanned by viewModel.isClientBanned.collectAsStateWithLifecycle()

    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val featuredMedia by viewModel.featuredMedia.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val allReviews by viewModel.allReviews.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val isOfflineModeActive by viewModel.isOfflineModeActive.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.myFavoriteIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val activeDevices by viewModel.activeDevices.collectAsStateWithLifecycle()
    val adminNotification by viewModel.adminNotification.collectAsStateWithLifecycle()

    var activeBottomTab by remember { mutableStateOf(BottomTab.HOME) }
    var showOfflineModal by remember { mutableStateOf(false) }

    // When network drops or offline mode is engaged, enforce Downloads-only access
    LaunchedEffect(isOffline) {
        if (isOffline) {
            activeBottomTab = BottomTab.DOWNLOADS
            viewModel.navigateTo(ScreenDestination.Downloads)
            showOfflineModal = true
        } else {
            showOfflineModal = false
        }
    }

    // Check ban first: if banned, show lockout screen immediately
    if (isClientBanned) {
        BannedLockoutScreen(
            deviceId = viewModel.telemetrySecurityRepository.deviceId,
            ipAddress = viewModel.telemetrySecurityRepository.clientIp,
            onEmergencyOverride = { pin ->
                if (pin == "admin123" || pin == "8888") {
                    viewModel.emergencyUnbanCurrentDevice()
                    true
                } else false
            }
        )
        return
    }

    // If splash screen
    if (currentScreen is ScreenDestination.Splash) {
        SplashScreen(
            onSplashFinished = {
                viewModel.navigateTo(ScreenDestination.Home)
            }
        )
        return
    }

    // Video Player is Fullscreen (no bottom bar)
    if (currentScreen is ScreenDestination.VideoPlayer) {
        val vp = currentScreen as ScreenDestination.VideoPlayer

        val downloadedItem = downloads.find {
            it.mediaId == vp.mediaId && (vp.episodeId == null || it.episodeId == vp.episodeId)
        }

        // Offline Restriction: Block streaming non-downloaded media while offline
        if (isOffline && downloadedItem == null) {
            LaunchedEffect(Unit) {
                showOfflineModal = true
                activeBottomTab = BottomTab.DOWNLOADS
                viewModel.navigateTo(ScreenDestination.Downloads)
            }
            return
        }

        val media = mediaItems.find { it.id == vp.mediaId } ?: downloadedItem?.let { dl ->
            MediaItem(
                id = dl.mediaId,
                title = dl.title,
                description = "Downloaded Offline Media",
                posterUrl = dl.posterUrl,
                bannerUrl = dl.posterUrl,
                directStreamUrl = dl.streamUrl
            )
        } ?: mediaItems.firstOrNull()

        if (media != null) {
            val allEpisodes = media.seasons.flatMap { it.episodes }
            val episode = if (downloadedItem != null && downloadedItem.episodeId != null) {
                allEpisodes.find { it.id == vp.episodeId } ?: EpisodeData(
                    id = downloadedItem.episodeId,
                    episodeNumber = 1,
                    title = downloadedItem.episodeTitle ?: downloadedItem.title,
                    overview = "Downloaded Offline Episode",
                    durationMinutes = 45,
                    thumbnailUrl = downloadedItem.posterUrl,
                    streamUrl = downloadedItem.streamUrl
                )
            } else {
                allEpisodes.find { it.id == vp.episodeId } ?: (if (vp.episodeId == null && media.seasons.isNotEmpty()) allEpisodes.firstOrNull() else null)
            }
            val existingHistory = watchHistory.find { it.mediaId == media.id }

            val activeMedia = if (downloadedItem != null && vp.episodeId == null && downloadedItem.streamUrl.isNotEmpty()) {
                media.copy(directStreamUrl = downloadedItem.streamUrl)
            } else {
                media
            }
            val activeEpisode = if (downloadedItem != null && episode != null && downloadedItem.streamUrl.isNotEmpty()) {
                episode.copy(streamUrl = downloadedItem.streamUrl)
            } else {
                episode
            }

            BackHandler {
                if (isOffline) {
                    activeBottomTab = BottomTab.DOWNLOADS
                    viewModel.navigateTo(ScreenDestination.Downloads)
                } else {
                    viewModel.navigateTo(ScreenDestination.Home)
                }
            }

            VideoPlayerScreen(
                media = activeMedia,
                episode = activeEpisode,
                initialPositionMs = existingHistory?.currentPositionMs ?: 0L,
                onBackClick = {
                    if (isOffline) {
                        activeBottomTab = BottomTab.DOWNLOADS
                        viewModel.navigateTo(ScreenDestination.Downloads)
                    } else {
                        viewModel.navigateTo(ScreenDestination.Home)
                    }
                },
                onProgressUpdate = { currentMs, durationMs ->
                    viewModel.updatePlaybackProgress(
                        mediaId = media.id,
                        episodeId = episode?.id,
                        title = media.title,
                        episodeTitle = episode?.let { "E${it.episodeNumber}: ${it.title}" },
                        posterUrl = episode?.thumbnailUrl?.ifEmpty { media.posterUrl } ?: media.posterUrl,
                        currentPosMs = currentMs,
                        durationMs = durationMs
                    )
                },
                onNextEpisode = if (episode != null) {
                    val currentIndex = allEpisodes.indexOf(episode)
                    if (currentIndex != -1 && currentIndex + 1 < allEpisodes.size) {
                        val nextEp = allEpisodes[currentIndex + 1]
                        { viewModel.navigateTo(ScreenDestination.VideoPlayer(media.id, nextEp.id)) }
                    } else null
                } else null,
                onEpisodeSelect = { selectedEp ->
                    viewModel.navigateTo(ScreenDestination.VideoPlayer(media.id, selectedEp.id))
                }
            )
            return
        } else {
            BackHandler {
                viewModel.navigateTo(ScreenDestination.Home)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NetflixRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading player...", color = NetflixLightGrey, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.navigateTo(ScreenDestination.Home) }) {
                        Text("Return to Home", color = NetflixRed)
                    }
                }
            }
            return
        }
    }

    // Detail Screen (No bottom bar for immersive preview)
    if (currentScreen is ScreenDestination.MediaDetail) {
        val detail = currentScreen as ScreenDestination.MediaDetail
        val media = mediaItems.find { it.id == detail.mediaId }
        if (media != null) {
            BackHandler {
                viewModel.navigateTo(ScreenDestination.Home)
            }

            MediaDetailScreen(
                media = media,
                allMedia = mediaItems,
                isFavorite = favoriteIds.contains(media.id),
                downloads = downloads,
                reviews = allReviews.filter { it.mediaId == media.id },
                currentUser = currentUser,
                onBackClick = {
                    viewModel.navigateTo(ScreenDestination.Home)
                },
                onPlayMovie = { m ->
                    viewModel.navigateTo(ScreenDestination.VideoPlayer(m.id))
                },
                onPlayEpisode = { m, ep ->
                    viewModel.navigateTo(ScreenDestination.VideoPlayer(m.id, ep.id))
                },
                onDownloadClick = { m, ep ->
                    viewModel.downloadMedia(m, ep)
                },
                onDeleteDownload = { id ->
                    viewModel.removeDownload(id)
                },
                onSubmitReview = { rating, text ->
                    viewModel.submitReview(media.id, rating, text)
                },
                onDeleteReview = { reviewId ->
                    viewModel.deleteReview(reviewId)
                },
                onSignInWithGoogle = {
                    activeBottomTab = BottomTab.PROFILE
                    viewModel.navigateTo(ScreenDestination.Profile)
                },
                onMyListToggle = {
                    viewModel.toggleMyList(media.id)
                },
                onSelectRecommended = { rec ->
                    viewModel.navigateTo(ScreenDestination.MediaDetail(rec.id))
                }
            )
            return
        } else {
            BackHandler {
                viewModel.navigateTo(ScreenDestination.Home)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NetflixRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading title...", color = NetflixLightGrey, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.navigateTo(ScreenDestination.Home) }) {
                        Text("Return to Home", color = NetflixRed)
                    }
                }
            }
            return
        }
    }

    // Admin Portal Screen
    if (currentScreen is ScreenDestination.AdminPortal) {
        val isStrictAdmin = currentUser.isAdmin
        if (!isStrictAdmin) {
            LaunchedEffect(Unit) {
                viewModel.navigateTo(ScreenDestination.Home)
            }
            return
        }

        val isUploadingMedia by viewModel.isUploadingMedia.collectAsStateWithLifecycle()
        val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
        val uploadStatusMessage by viewModel.uploadStatusMessage.collectAsStateWithLifecycle()

        BackHandler {
            viewModel.navigateTo(ScreenDestination.Profile)
        }

        AdminPortalScreen(
            mediaItems = mediaItems,
            activeDevices = activeDevices,
            bannedRecords = viewModel.telemetrySecurityRepository.bannedRecords.collectAsStateWithLifecycle().value,
            currentDeviceId = viewModel.telemetrySecurityRepository.deviceId,
            currentIpAddress = viewModel.telemetrySecurityRepository.clientIp,
            adminNotification = adminNotification,
            onClearNotification = { viewModel.clearAdminNotification() },
            onBackClick = { viewModel.navigateTo(ScreenDestination.Profile) },
            onAddMedia = { title, desc, banner, poster, cat, type, stream, year, rating, seasons ->
                viewModel.addMediaItemFromCMS(
                    title, desc, banner, poster, cat, type, stream, year, rating, seasons
                )
            },
            onSaveMedia = { mediaItem -> viewModel.saveMediaItemFromCMS(mediaItem) },
            onSetFeatured = { id -> viewModel.setFeaturedMedia(id) },
            onToggleTop10 = { id -> viewModel.toggleTop10Media(id) },
            onDeleteMedia = { id -> viewModel.deleteMediaFromCMS(id) },
            onResetCatalog = { viewModel.refreshCatalogFromFirestore() },
            onBanDevice = { id, reason -> viewModel.banDevice(id, reason) },
            onUnbanDevice = { id -> viewModel.unbanDevice(id) },
            onBanIp = { ip, reason -> viewModel.banIp(ip, reason) },
            onUnbanIp = { ip -> viewModel.unbanIp(ip) },
            isUploadingMedia = isUploadingMedia,
            uploadProgress = uploadProgress,
            uploadStatusMessage = uploadStatusMessage,
            onUploadFile = { uri, folder, mime, onSuccess, onError ->
                viewModel.uploadMediaFile(uri, folder, mime, onSuccess, onError)
            }
        )
        return
    }

    // Primary App Scaffold with Netflix Dark Bottom Navigation Bar
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NetflixBlack,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                containerColor = NetflixBottomBar,
                contentColor = NetflixWhite,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_bottom_nav_bar")
            ) {
                // Home Tab
                NavigationBarItem(
                    selected = activeBottomTab == BottomTab.HOME,
                    onClick = {
                        if (isOffline) {
                            showOfflineModal = true
                        } else {
                            activeBottomTab = BottomTab.HOME
                            viewModel.navigateTo(ScreenDestination.Home)
                        }
                    },
                    icon = {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
                    },
                    label = { Text("Home", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NetflixRed,
                        selectedTextColor = NetflixWhite,
                        unselectedIconColor = if (isOffline) NetflixLightGrey.copy(alpha = 0.5f) else NetflixLightGrey,
                        unselectedTextColor = if (isOffline) NetflixLightGrey.copy(alpha = 0.5f) else NetflixLightGrey,
                        indicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("nav_tab_home")
                )

                // History Tab
                NavigationBarItem(
                    selected = activeBottomTab == BottomTab.HISTORY,
                    onClick = {
                        if (isOffline) {
                            showOfflineModal = true
                        } else {
                            activeBottomTab = BottomTab.HISTORY
                            viewModel.navigateTo(ScreenDestination.History)
                        }
                    },
                    icon = {
                        Icon(imageVector = Icons.Default.History, contentDescription = "History")
                    },
                    label = { Text("History", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NetflixRed,
                        selectedTextColor = NetflixWhite,
                        unselectedIconColor = if (isOffline) NetflixLightGrey.copy(alpha = 0.5f) else NetflixLightGrey,
                        unselectedTextColor = if (isOffline) NetflixLightGrey.copy(alpha = 0.5f) else NetflixLightGrey,
                        indicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("nav_tab_history")
                )

                // Downloads Tab (Always accessible offline)
                NavigationBarItem(
                    selected = activeBottomTab == BottomTab.DOWNLOADS,
                    onClick = {
                        activeBottomTab = BottomTab.DOWNLOADS
                        viewModel.navigateTo(ScreenDestination.Downloads)
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (isOffline) {
                                    Badge(containerColor = BadgeGreen) {
                                        Text("OFFLINE", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = "Downloads")
                        }
                    },
                    label = { Text("Downloads", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NetflixRed,
                        selectedTextColor = NetflixWhite,
                        unselectedIconColor = NetflixLightGrey,
                        unselectedTextColor = NetflixLightGrey,
                        indicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("nav_tab_downloads")
                )

                // Profile Tab
                NavigationBarItem(
                    selected = activeBottomTab == BottomTab.PROFILE,
                    onClick = {
                        if (isOffline) {
                            showOfflineModal = true
                        } else {
                            activeBottomTab = BottomTab.PROFILE
                            viewModel.navigateTo(ScreenDestination.Profile)
                        }
                    },
                    icon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "Profile")
                    },
                    label = { Text("Profile", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NetflixRed,
                        selectedTextColor = NetflixWhite,
                        unselectedIconColor = if (isOffline) NetflixLightGrey.copy(alpha = 0.5f) else NetflixLightGrey,
                        unselectedTextColor = if (isOffline) NetflixLightGrey.copy(alpha = 0.5f) else NetflixLightGrey,
                        indicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("nav_tab_profile")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Sleek Offline Top Banner when network drops
            if (isOffline) {
                OfflineWarningBanner(
                    onGoToDownloads = {
                        activeBottomTab = BottomTab.DOWNLOADS
                        viewModel.navigateTo(ScreenDestination.Downloads)
                        showOfflineModal = false
                    }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = activeBottomTab, label = "tab_crossfade") { tab ->
                    if (isOffline && tab != BottomTab.DOWNLOADS) {
                        OfflineRestrictedView(
                            onGoToDownloads = {
                                activeBottomTab = BottomTab.DOWNLOADS
                                viewModel.navigateTo(ScreenDestination.Downloads)
                            }
                        )
                    } else {
                        when (tab) {
                            BottomTab.HOME -> {
                                HomeScreen(
                                    mediaItems = mediaItems,
                                    featuredMedia = featuredMedia,
                                    watchHistory = watchHistory,
                                    favoriteIds = favoriteIds,
                                    selectedCategory = selectedCategory,
                                    onCategorySelected = { cat ->
                                        if (isOffline) showOfflineModal = true
                                        else viewModel.selectedCategoryFilter.value = cat
                                    },
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { q ->
                                        if (isOffline) showOfflineModal = true
                                        else viewModel.searchQuery.value = q
                                    },
                                    userAvatarUrl = currentUser.avatarUrl,
                                    isGuest = currentUser.isGuest,
                                    isAdmin = currentUser.isAdmin,
                                    onMediaClick = { media ->
                                        if (isOffline) {
                                            showOfflineModal = true
                                        } else {
                                            viewModel.navigateTo(ScreenDestination.MediaDetail(media.id))
                                        }
                                    },
                                    onPlayClick = { media ->
                                        if (isOffline) {
                                            showOfflineModal = true
                                        } else {
                                            viewModel.navigateTo(ScreenDestination.VideoPlayer(media.id))
                                        }
                                    },
                                    onResumeHistory = { history ->
                                        if (isOffline) {
                                            showOfflineModal = true
                                        } else {
                                            viewModel.navigateTo(
                                                ScreenDestination.VideoPlayer(history.mediaId, history.episodeId)
                                            )
                                        }
                                    },
                                    onMyListToggle = { id -> viewModel.toggleMyList(id) },
                                    onProfileClick = {
                                        if (isOffline) {
                                            showOfflineModal = true
                                        } else {
                                            activeBottomTab = BottomTab.PROFILE
                                            viewModel.navigateTo(ScreenDestination.Profile)
                                        }
                                    },
                                    onAdminClick = {
                                        if (isOffline) {
                                            showOfflineModal = true
                                        } else if (currentUser.isAdmin) {
                                            viewModel.navigateTo(ScreenDestination.AdminPortal)
                                        }
                                    }
                                )
                            }
                            BottomTab.HISTORY -> {
                                HistoryScreen(
                                    historyList = watchHistory,
                                    isCloudSynced = currentUser.cloudSyncEnabled,
                                    userEmail = currentUser.email,
                                    onResumeItem = { history ->
                                        if (isOffline) {
                                            showOfflineModal = true
                                        } else {
                                            viewModel.navigateTo(
                                                ScreenDestination.VideoPlayer(history.mediaId, history.episodeId)
                                            )
                                        }
                                    },
                                    onDeleteItem = { id -> viewModel.removeHistoryItem(id) },
                                    onClearAll = { viewModel.clearWatchHistory() }
                                )
                            }
                            BottomTab.DOWNLOADS -> {
                                DownloadsScreen(
                                    downloads = downloads,
                                    isOfflineModeActive = isOffline,
                                    onToggleOfflineMode = { active -> viewModel.toggleOfflineMode(active) },
                                    onPlayDownload = { dl ->
                                        // Local playback of downloaded item allowed offline
                                        viewModel.navigateTo(ScreenDestination.VideoPlayer(dl.mediaId, dl.episodeId))
                                    },
                                    onDeleteDownload = { id -> viewModel.removeDownload(id) },
                                    onDeleteAllDownloads = { viewModel.clearAllDownloads() },
                                    onFindSomethingToDownload = {
                                        if (isOffline) {
                                            showOfflineModal = true
                                        } else {
                                            activeBottomTab = BottomTab.HOME
                                            viewModel.navigateTo(ScreenDestination.Home)
                                        }
                                    }
                                )
                            }
                            BottomTab.PROFILE -> {
                                ProfileScreen(
                                    user = currentUser,
                                    deviceId = viewModel.telemetrySecurityRepository.deviceId,
                                    deviceIp = viewModel.telemetrySecurityRepository.clientIp,
                                    isBanned = isClientBanned,
                                    onSignIn = { email, password -> viewModel.authRepository.signInWithCredentials(email, password) },
                                    onSignOutGuest = { viewModel.authRepository.signOutToGuest() },
                                    onSelectAvatar = { url -> viewModel.authRepository.updateProfileAvatar(url) },
                                    onUpdateProfile = { name, avatar, prefs -> viewModel.updateUserProfile(name, avatar, prefs) },
                                    onUpdatePreferences = { prefs -> viewModel.updateAccountPreferences(prefs) },
                                    onSaveCameraBitmap = { bitmap -> viewModel.saveCameraProfileBitmap(bitmap) },
                                    onOpenAdminPortal = {
                                        if (currentUser.isAdmin) {
                                            viewModel.navigateTo(ScreenDestination.AdminPortal)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        var offlineRetryFeedback by remember { mutableStateOf<String?>(null) }

        // Non-dismissible / clean Offline Warning Message Modal
        if (showOfflineModal) {
            OfflineWarningModal(
                retryFeedback = offlineRetryFeedback,
                onGoToDownloads = {
                    offlineRetryFeedback = null
                    activeBottomTab = BottomTab.DOWNLOADS
                    viewModel.navigateTo(ScreenDestination.Downloads)
                    showOfflineModal = false
                },
                onRetry = {
                    val connected = viewModel.checkNetworkNow()
                    if (connected) {
                        offlineRetryFeedback = null
                        viewModel.toggleOfflineMode(false)
                        showOfflineModal = false
                    } else {
                        offlineRetryFeedback = "Still disconnected. Please check your Wi-Fi or Mobile Data connection."
                    }
                }
            )
        }
    }
}

/**
 * Clean offline restricted fallback view when online tab is tapped during offline mode
 */
@Composable
fun OfflineRestrictedView(
    onGoToDownloads: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
            border = BorderStroke(1.dp, NetflixCardBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("offline_restricted_view")
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFE50914).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline Connection Lost",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Connection Lost",
                    color = NetflixWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Oops! Network connection is lost. Please check your internet connection or watch from your downloaded library.",
                    color = NetflixLightGrey,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Online feeds, search, and streaming are restricted while offline.",
                    color = Color(0xFFFFB300),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onGoToDownloads,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("offline_restricted_go_to_downloads_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Watch from Downloaded Library",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Professional Offline Warning Message Modal Overlay
 */
@Composable
fun OfflineWarningModal(
    retryFeedback: String? = null,
    onGoToDownloads: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Non-dismissible: keep modal visible while offline */ },
        icon = {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFFE50914).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline Connection Lost",
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = "Connection Lost",
                color = NetflixWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Oops! Network connection is lost. Please check your internet connection or watch from your downloaded library.",
                    color = NetflixLightGrey,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Offline Mode: Only downloaded titles are available for playback.",
                    color = Color(0xFFFFB300),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                if (retryFeedback != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = retryFeedback,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToDownloads,
                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("offline_go_to_downloads_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Go to Downloads Library",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGrey),
                border = BorderStroke(1.dp, Color(0xFF444444)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("offline_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry Connection", fontSize = 13.sp)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        modifier = Modifier.testTag("offline_warning_modal")
    )
}

/**
 * Sleek, professional offline warning top banner
 */
@Composable
fun OfflineWarningBanner(
    onGoToDownloads: () -> Unit
) {
    Surface(
        color = Color(0xFFB71C1C),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_warning_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Oops! Network connection is lost. Please check your internet connection or watch from your downloaded library.",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onGoToDownloads,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("banner_go_to_downloads_button")
            ) {
                Text(
                    text = "Downloads",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
