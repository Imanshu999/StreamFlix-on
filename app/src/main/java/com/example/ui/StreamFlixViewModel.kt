package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.StreamFlixDatabase
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.EpisodeData
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SeasonData
import com.example.data.model.UserDeviceTelemetry
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.BookmarkRepository
import com.example.data.repository.DownloadRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.ReviewRepository
import com.example.data.repository.TelemetrySecurityRepository
import com.example.data.repository.WatchHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed class ScreenDestination {
    object Splash : ScreenDestination()
    object Home : ScreenDestination()
    data class MediaDetail(val mediaId: String) : ScreenDestination()
    data class VideoPlayer(val mediaId: String, val episodeId: String? = null) : ScreenDestination()
    object History : ScreenDestination()
    object Downloads : ScreenDestination()
    object Profile : ScreenDestination()
    object AdminPortal : ScreenDestination()
}

class StreamFlixViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val db = StreamFlixDatabase.getDatabase(application)
    val mediaRepository = MediaRepository()
    val telemetrySecurityRepository = TelemetrySecurityRepository(application)
    val watchHistoryRepository = WatchHistoryRepository(db.watchHistoryDao())
    val downloadRepository = DownloadRepository(db.downloadDao())
    val reviewRepository = ReviewRepository(db.reviewDao())
    val bookmarkRepository = BookmarkRepository(db.bookmarkDao())
    val authRepository = AuthRepository(application)
    val firebaseStorageRepository = com.example.data.repository.FirebaseStorageRepository(application)

    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia: StateFlow<Boolean> = _isUploadingMedia.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _uploadStatusMessage = MutableStateFlow<String?>(null)
    val uploadStatusMessage: StateFlow<String?> = _uploadStatusMessage.asStateFlow()

    // Real-Time Network Connection Listener
    val networkMonitor = com.example.data.network.NetworkConnectivityMonitor(application)
    val isNetworkConnected: StateFlow<Boolean> = networkMonitor.isConnected
        .stateIn(viewModelScope, SharingStarted.Eagerly, networkMonitor.checkCurrentConnectivity())

    // Offline Mode Simulation & Real-time combined offline state
    val isOfflineModeActive = MutableStateFlow(false)

    val isOffline: StateFlow<Boolean> = combine(
        isNetworkConnected,
        isOfflineModeActive
    ) { connected, forcedOffline ->
        !connected || forcedOffline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Navigation & Screen State with SavedStateHandle restoration across process death
    private val _currentScreen = MutableStateFlow<ScreenDestination>(
        restoreScreenDestination() ?: ScreenDestination.Splash
    )
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    // Filter & Search with SavedStateHandle persistence
    val searchQuery = MutableStateFlow(savedStateHandle.get<String>(KEY_SEARCH_QUERY) ?: "")
    val selectedCategoryFilter = MutableStateFlow(savedStateHandle.get<String>(KEY_SELECTED_CATEGORY) ?: "All")

    // State flows from repositories
    val mediaItems: StateFlow<List<MediaItem>> = mediaRepository.mediaItems

    val featuredMedia: StateFlow<MediaItem?> = mediaRepository.mediaItems
        .combine(searchQuery) { items, _ ->
            items.firstOrNull { it.isFeatured } ?: items.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = watchHistoryRepository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = downloadRepository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReviews: StateFlow<List<com.example.data.local.ReviewEntity>> = reviewRepository.allReviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Persistent Bookmarks ("My List") backed by Room and synced with Firestore
    val myFavoriteIds: StateFlow<Set<String>> = bookmarkRepository.bookmarkedMediaIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            reviewRepository.purgeSeedReviews()
        }

        // Persist filter changes across configuration changes
        viewModelScope.launch {
            searchQuery.collect { query ->
                savedStateHandle[KEY_SEARCH_QUERY] = query
            }
        }

        viewModelScope.launch {
            selectedCategoryFilter.collect { cat ->
                savedStateHandle[KEY_SELECTED_CATEGORY] = cat
            }
        }

        // Sync bookmarks from Firestore whenever authenticated user changes
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (!user.isGuest && user.uid.isNotBlank()) {
                    bookmarkRepository.syncBookmarksFromFirestore(user.uid)
                }
            }
        }

        // Real-Time Offline Guardian: when connection drops or offline mode engages,
        // automatically enforce strict offline boundary and redirect to Downloads
        viewModelScope.launch {
            isOffline.collect { offline ->
                if (offline) {
                    val current = _currentScreen.value
                    if (current is ScreenDestination.Home ||
                        current is ScreenDestination.MediaDetail ||
                        current is ScreenDestination.AdminPortal ||
                        current is ScreenDestination.History ||
                        current is ScreenDestination.Profile
                    ) {
                        navigateTo(ScreenDestination.Downloads)
                    } else if (current is ScreenDestination.VideoPlayer) {
                        val isDownloaded = downloads.value.any { it.mediaId == current.mediaId }
                        if (!isDownloaded) {
                            navigateTo(ScreenDestination.Downloads)
                        }
                    }
                }
            }
        }
    }

    val currentUser: StateFlow<UserProfile> = authRepository.currentUser
    val isClientBanned: StateFlow<Boolean> = telemetrySecurityRepository.isCurrentClientBanned
    val activeDevices: StateFlow<List<UserDeviceTelemetry>> = telemetrySecurityRepository.activeDevices

    // Admin State
    private val _isAdminAuthenticated = MutableStateFlow(false)
    val isAdminAuthenticated: StateFlow<Boolean> = _isAdminAuthenticated.asStateFlow()

    private val _adminNotification = MutableStateFlow<String?>(null)
    val adminNotification: StateFlow<String?> = _adminNotification.asStateFlow()

    fun navigateTo(screen: ScreenDestination) {
        // Strict Admin Gate: Block non-admin users from navigating to AdminPortal
        if (screen is ScreenDestination.AdminPortal && !isAuthorizedAdmin()) {
            _currentScreen.value = ScreenDestination.Home
            persistScreenState(ScreenDestination.Home)
            return
        }

        // Strict Offline Restrictions: Block online-only destinations and redirect directly to Downloads
        if (isOffline.value) {
            when (screen) {
                is ScreenDestination.Home,
                is ScreenDestination.MediaDetail,
                is ScreenDestination.AdminPortal,
                is ScreenDestination.History,
                is ScreenDestination.Profile -> {
                    _currentScreen.value = ScreenDestination.Downloads
                    persistScreenState(ScreenDestination.Downloads)
                    return
                }
                is ScreenDestination.VideoPlayer -> {
                    val isDownloaded = downloads.value.any { it.mediaId == screen.mediaId }
                    if (!isDownloaded) {
                        _currentScreen.value = ScreenDestination.Downloads
                        persistScreenState(ScreenDestination.Downloads)
                        return
                    }
                }
                is ScreenDestination.Downloads,
                is ScreenDestination.Splash -> {
                    _currentScreen.value = screen
                    persistScreenState(screen)
                    return
                }
            }
        }
        _currentScreen.value = screen
        persistScreenState(screen)
    }

    private fun persistScreenState(screen: ScreenDestination) {
        when (screen) {
            is ScreenDestination.Splash -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "Splash"
            }
            is ScreenDestination.Home -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "Home"
                savedStateHandle[KEY_SCREEN_MEDIA_ID] = null
                savedStateHandle[KEY_SCREEN_EPISODE_ID] = null
            }
            is ScreenDestination.MediaDetail -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "MediaDetail"
                savedStateHandle[KEY_SCREEN_MEDIA_ID] = screen.mediaId
                savedStateHandle[KEY_SCREEN_EPISODE_ID] = null
            }
            is ScreenDestination.VideoPlayer -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "VideoPlayer"
                savedStateHandle[KEY_SCREEN_MEDIA_ID] = screen.mediaId
                savedStateHandle[KEY_SCREEN_EPISODE_ID] = screen.episodeId
            }
            is ScreenDestination.History -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "History"
            }
            is ScreenDestination.Downloads -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "Downloads"
            }
            is ScreenDestination.Profile -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "Profile"
            }
            is ScreenDestination.AdminPortal -> {
                savedStateHandle[KEY_CURRENT_SCREEN] = "AdminPortal"
            }
        }
    }

    private fun restoreScreenDestination(): ScreenDestination? {
        val name = savedStateHandle.get<String>(KEY_CURRENT_SCREEN) ?: return null
        val mediaId = savedStateHandle.get<String>(KEY_SCREEN_MEDIA_ID)
        val episodeId = savedStateHandle.get<String>(KEY_SCREEN_EPISODE_ID)
        return when (name) {
            "Home" -> ScreenDestination.Home
            "MediaDetail" -> if (mediaId != null) ScreenDestination.MediaDetail(mediaId) else ScreenDestination.Home
            "VideoPlayer" -> if (mediaId != null) ScreenDestination.VideoPlayer(mediaId, episodeId) else ScreenDestination.Home
            "History" -> ScreenDestination.History
            "Downloads" -> ScreenDestination.Downloads
            "Profile" -> ScreenDestination.Profile
            "AdminPortal" -> if (isAuthorizedAdmin()) ScreenDestination.AdminPortal else ScreenDestination.Home
            else -> null
        }
    }

    // Bookmarks / My List: Room persistence + Firestore sync
    fun toggleMyList(mediaId: String) {
        viewModelScope.launch {
            bookmarkRepository.toggleBookmark(mediaId, currentUser.value.uid)
        }
    }

    fun isFavorite(mediaId: String): Boolean {
        return myFavoriteIds.value.contains(mediaId)
    }

    // Playback Tracking: Room persistence + Firestore sync
    fun updatePlaybackProgress(
        mediaId: String,
        episodeId: String?,
        title: String,
        episodeTitle: String?,
        posterUrl: String,
        currentPosMs: Long,
        durationMs: Long
    ) {
        viewModelScope.launch {
            watchHistoryRepository.savePlaybackPosition(
                mediaId = mediaId,
                episodeId = episodeId,
                title = title,
                episodeTitle = episodeTitle,
                posterUrl = posterUrl,
                currentPositionMs = currentPosMs,
                durationMs = durationMs,
                userId = currentUser.value.uid
            )
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            watchHistoryRepository.clearHistory(currentUser.value.uid)
        }
    }

    fun removeHistoryItem(id: String) {
        viewModelScope.launch {
            watchHistoryRepository.removeHistoryItem(id, currentUser.value.uid)
        }
    }

    // Downloads
    fun downloadMedia(media: MediaItem, episode: EpisodeData? = null) {
        viewModelScope.launch {
            if (episode != null) {
                downloadRepository.addEpisodeDownload(media, episode)
            } else {
                downloadRepository.addMovieDownload(media)
            }
        }
    }

    fun removeDownload(id: String) {
        viewModelScope.launch {
            downloadRepository.removeDownload(id)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            downloadRepository.clearAllDownloads()
        }
    }

    // User Ratings & Reviews
    fun submitReview(mediaId: String, rating: Int, reviewText: String) {
        val user = currentUser.value
        val email = if (user.isGuest) "guest@streamflix.tv" else user.email
        val name = if (user.isGuest) "Guest Reviewer" else user.displayName
        val avatar = user.avatarUrl

        viewModelScope.launch {
            reviewRepository.addReview(
                mediaId = mediaId,
                userEmail = email,
                userName = name,
                userAvatarUrl = avatar,
                rating = rating,
                reviewText = reviewText,
                isVerifiedUser = !user.isGuest
            )
        }
    }

    fun deleteReview(reviewId: String) {
        viewModelScope.launch {
            reviewRepository.deleteReview(reviewId)
        }
    }

    fun toggleOfflineMode(active: Boolean) {
        isOfflineModeActive.value = active
        if (active) {
            navigateTo(ScreenDestination.Downloads)
        }
    }

    fun checkNetworkNow(): Boolean {
        return networkMonitor.checkCurrentConnectivity()
    }

    // Admin Authentication & Actions
    fun authenticateAdmin(pin: String): Boolean {
        if (currentUser.value.isAdmin || pin == "admin123" || pin == "8888") {
            _isAdminAuthenticated.value = true
            _adminNotification.value = "Admin Authenticated Successfully"
            return true
        }
        return false
    }

    fun logoutAdmin() {
        _isAdminAuthenticated.value = false
    }

    fun clearAdminNotification() {
        _adminNotification.value = null
    }

    // CMS Add Item
    fun addMediaItemFromCMS(
        title: String,
        description: String,
        bannerUrl: String,
        posterUrl: String,
        category: String,
        type: MediaType,
        streamUrl: String,
        releaseYear: Int,
        rating: String,
        seasonCount: Int
    ) {
        val id = "cms_" + UUID.randomUUID().toString().take(8)
        val seasons = if (type == MediaType.SERIES) {
            (1..seasonCount.coerceAtLeast(1)).map { sNum ->
                SeasonData(
                    seasonNumber = sNum,
                    title = "Season $sNum",
                    episodes = (1..3).map { epNum ->
                        EpisodeData(
                            id = "${id}_s${sNum}_e${epNum}",
                            episodeNumber = epNum,
                            title = "Episode $epNum: Prelude",
                            overview = "Official episode stream broadcast directly configured via StreamFlix CMS.",
                            thumbnailUrl = bannerUrl.ifEmpty { posterUrl },
                            durationMinutes = 45,
                            streamUrl = streamUrl
                        )
                    }
                )
            }
        } else {
            emptyList()
        }

        val newItem = MediaItem(
            id = id,
            title = title,
            description = description,
            bannerUrl = bannerUrl.ifEmpty { "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=1200&q=80" },
            posterUrl = posterUrl.ifEmpty { "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80" },
            type = type,
            category = category,
            genres = listOf("Trending", "Original"),
            releaseYear = releaseYear,
            rating = rating,
            matchPercentage = 98,
            durationText = if (type == MediaType.SERIES) "$seasonCount Seasons" else "1h 55m",
            directStreamUrl = streamUrl,
            isFeatured = false,
            badgeLabel = "CMS ADDED",
            seasons = seasons
        )

        mediaRepository.addOrUpdateMedia(newItem)
        _adminNotification.value = "Published '$title' to catalog instantly!"
    }

    fun saveMediaItemFromCMS(mediaItem: MediaItem) {
        mediaRepository.addOrUpdateMedia(mediaItem)
        _adminNotification.value = "Saved '${mediaItem.title}' to live catalog!"
    }

    fun setFeaturedMedia(id: String) {
        mediaRepository.setFeaturedMedia(id)
        val title = mediaRepository.getMediaById(id)?.title ?: "Item"
        _adminNotification.value = "⭐ Set '$title' as the Top Featured Hero Title!"
    }

    fun toggleTop10Media(id: String) {
        val current = mediaRepository.getMediaById(id) ?: return
        val willBeTop10 = !current.isTop10
        mediaRepository.toggleTop10(id)
        _adminNotification.value = if (willBeTop10) {
            "🏆 Marked '${current.title}' as TOP 10!"
        } else {
            "Removed '${current.title}' from TOP 10."
        }
    }

    fun isAuthorizedAdmin(): Boolean {
        return currentUser.value.isAdmin
    }

    fun deleteMediaFromCMS(id: String) {
        mediaRepository.deleteMedia(id)
        _adminNotification.value = "Item removed from catalog"
    }

    fun refreshCatalogFromFirestore() {
        mediaRepository.refreshCatalog()
        _adminNotification.value = "Synced with live Firebase Firestore catalog"
    }

    fun clearCatalogFromFirestore() {
        mediaRepository.clearCatalog()
        _adminNotification.value = "Cleared all items from Firestore"
    }

    // Security Ban & Unban Actions
    fun banDevice(deviceId: String, reason: String = "Policy violation / Malicious activity") {
        telemetrySecurityRepository.banDevice(deviceId, reason)
        _adminNotification.value = "Device $deviceId is now BANNED"
    }

    fun unbanDevice(deviceId: String) {
        telemetrySecurityRepository.unbanDevice(deviceId)
        _adminNotification.value = "Device $deviceId unbanned"
    }

    fun banIp(ip: String, reason: String = "Suspicious traffic") {
        telemetrySecurityRepository.banIp(ip, reason)
        _adminNotification.value = "IP $ip is now BANNED"
    }

    fun unbanIp(ip: String) {
        telemetrySecurityRepository.unbanIp(ip)
        _adminNotification.value = "IP $ip unbanned"
    }

    fun uploadMediaFile(
        uri: android.net.Uri,
        folder: String,
        mimeType: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isUploadingMedia.value = true
            _uploadProgress.value = 0f
            _uploadStatusMessage.value = "Uploading to Firebase Cloud Storage..."
            val result = firebaseStorageRepository.uploadFile(
                uri = uri,
                folder = folder,
                mimeTypeHint = mimeType,
                onProgress = { progress ->
                    _uploadProgress.value = progress
                    _uploadStatusMessage.value = "Uploading to Firebase Cloud Storage: ${(progress * 100).toInt()}%"
                }
            )
            _isUploadingMedia.value = false
            result.onSuccess { url ->
                _uploadStatusMessage.value = null
                _adminNotification.value = "File uploaded to Firebase Cloud Storage!"
                onSuccess(url)
            }.onFailure { err ->
                _uploadStatusMessage.value = null
                val errText = err.localizedMessage ?: "Failed to upload file to Firebase Cloud Storage"
                _adminNotification.value = "Upload error: $errText"
                onError(errText)
            }
        }
    }

    fun emergencyUnbanCurrentDevice() {
        telemetrySecurityRepository.emergencyUnbanCurrentDevice()
        _adminNotification.value = "Current Device Restrictions Lifted"
    }

    // Profile Management & Camera Photo Persistence
    fun saveCameraProfileBitmap(bitmap: android.graphics.Bitmap): String {
        return try {
            val app = getApplication<Application>()
            val dir = java.io.File(app.filesDir, "profile_photos")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "profile_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            val path = file.absolutePath
            authRepository.updateProfileAvatar(path)
            path
        } catch (e: Exception) {
            ""
        }
    }

    fun updateUserProfile(
        displayName: String,
        avatarUrl: String,
        preferences: com.example.data.model.AccountPreferences
    ) {
        authRepository.updateUserProfile(displayName, avatarUrl, preferences)
    }

    fun updateDisplayName(name: String) {
        authRepository.updateDisplayName(name)
    }

    fun updateProfileAvatar(avatarUrl: String) {
        authRepository.updateProfileAvatar(avatarUrl)
    }

    fun updateAccountPreferences(preferences: com.example.data.model.AccountPreferences) {
        authRepository.updateAccountPreferences(preferences)
    }

    companion object {
        private const val KEY_CURRENT_SCREEN = "streamflix_saved_current_screen"
        private const val KEY_SCREEN_MEDIA_ID = "streamflix_saved_screen_media_id"
        private const val KEY_SCREEN_EPISODE_ID = "streamflix_saved_screen_episode_id"
        private const val KEY_SEARCH_QUERY = "streamflix_saved_search_query"
        private const val KEY_SELECTED_CATEGORY = "streamflix_saved_selected_category"
    }
}
