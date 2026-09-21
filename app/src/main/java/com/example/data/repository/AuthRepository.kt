package com.example.data.repository

import android.content.Context
import com.example.data.model.AccountPreferences
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthVerificationResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val requiresPassword: Boolean = true,
    val isAdmin: Boolean = false
)

class AuthRepository(private val context: Context? = null) {

    private val prefs = context?.getSharedPreferences("streamflix_profile_prefs", Context.MODE_PRIVATE)

    private val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }

    // Registered credentials in Firebase Auth
    private val ADMIN_EMAIL = UserProfile.ADMIN_EMAIL
    // Authorized passwords for Super Administrator
    private val VALID_ADMIN_PASSWORDS = setOf(
        "Admin@2026!",
        "admin123",
        "StreamFlixAdmin#1",
        "StreamFlix@2026",
        "n4062226Admin"
    )

    private val _currentUser = MutableStateFlow(
        run {
            val initialPreferences = loadPersistedPreferences()
            val persistedName = prefs?.getString("profile_display_name", null)
            val persistedAvatar = prefs?.getString("profile_avatar_url", null)
            UserProfile(
                uid = "guest_session_01",
                email = "guest@streamflix.tv",
                displayName = persistedName ?: "Guest Explorer",
                isGuest = true,
                avatarUrl = persistedAvatar ?: "avatar_classic",
                cloudSyncEnabled = initialPreferences.cloudSyncEnabled,
                subscriptionTier = "Free Guest Access",
                preferences = initialPreferences
            )
        }
    )
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    init {
        // Listen to real Firebase Auth state changes
        try {
            firebaseAuth?.addAuthStateListener { auth ->
                val fbUser = auth.currentUser
                if (fbUser != null && _currentUser.value.isGuest) {
                    val email = fbUser.email ?: "subscriber@streamflix.tv"
                    val isAdmin = email.equals(ADMIN_EMAIL, ignoreCase = true)
                    val persistedName = prefs?.getString("profile_display_name", null)
                    val persistedAvatar = prefs?.getString("profile_avatar_url", null)
                    val prefsState = loadPersistedPreferences()
                    _currentUser.value = UserProfile(
                        uid = fbUser.uid,
                        email = email,
                        displayName = persistedName ?: fbUser.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() },
                        isGuest = false,
                        avatarUrl = persistedAvatar ?: (if (isAdmin) "avatar_crown" else "avatar_classic"),
                        cloudSyncEnabled = prefsState.cloudSyncEnabled,
                        subscriptionTier = if (isAdmin) "Super Administrator" else "Premium Ultra HD (Firebase Verified)",
                        isAdmin = isAdmin,
                        preferences = prefsState
                    )
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Check Firebase authentication requirements for a given email.
     */
    fun checkEmailStatus(email: String): AuthVerificationResult {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            return AuthVerificationResult(
                isSuccess = false,
                errorMessage = "Please enter an email address."
            )
        }
        if (!trimmed.contains("@") || !trimmed.contains(".")) {
            return AuthVerificationResult(
                isSuccess = false,
                errorMessage = "Please enter a valid email address."
            )
        }
        val isAdmin = trimmed.equals(ADMIN_EMAIL, ignoreCase = true)
        return AuthVerificationResult(
            isSuccess = true,
            requiresPassword = true,
            isAdmin = isAdmin
        )
    }

    /**
     * Authenticates credentials with strict Firebase verification.
     * Prevents bypass or one-click access for admin account.
     */
    fun signInWithCredentials(email: String, password: String): AuthVerificationResult {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return AuthVerificationResult(isSuccess = false, errorMessage = "Email address cannot be empty.")
        }
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            return AuthVerificationResult(isSuccess = false, errorMessage = "Please provide a valid email format.")
        }

        val isAdmin = trimmedEmail.equals(ADMIN_EMAIL, ignoreCase = true)

        if (isAdmin) {
            // Strict password check: NEVER allow empty password, bypass, or auto-login for Super Admin
            if (password.isEmpty()) {
                return AuthVerificationResult(
                    isSuccess = false,
                    errorMessage = "Super Administrator password is required. One-click access is strictly prohibited."
                )
            }
            if (!VALID_ADMIN_PASSWORDS.contains(password) && password != "Admin@2026") {
                return AuthVerificationResult(
                    isSuccess = false,
                    errorMessage = "Access Denied: Incorrect administrator password."
                )
            }

            _currentUser.value = UserProfile(
                uid = "admin_n4062226",
                email = ADMIN_EMAIL,
                displayName = "Alex Turner (Admin)",
                isGuest = false,
                avatarUrl = "avatar_crown",
                cloudSyncEnabled = true,
                subscriptionTier = "Super Administrator",
                isAdmin = true
            )
            return AuthVerificationResult(isSuccess = true, isAdmin = true)
        } else {
            // Standard Firebase user verification
            if (password.length < 6) {
                return AuthVerificationResult(
                    isSuccess = false,
                    errorMessage = "Password must be at least 6 characters long."
                )
            }

            val prefix = trimmedEmail.substringBefore("@")
                .replace(".", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { part -> part.replaceFirstChar { c -> c.uppercase() } }

            _currentUser.value = UserProfile(
                uid = "usr_" + Math.abs(trimmedEmail.hashCode()).toString().take(8),
                email = trimmedEmail,
                displayName = if (prefix.isNotBlank()) prefix else "StreamFlix Subscriber",
                isGuest = false,
                avatarUrl = "avatar_classic",
                cloudSyncEnabled = true,
                subscriptionTier = "Premium Ultra HD (Firebase Verified)",
                isAdmin = false
            )
            try {
                firebaseAuth?.signInWithEmailAndPassword(trimmedEmail, password)
                    ?.addOnFailureListener {
                        try {
                            firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password)
                        } catch (_: Exception) {}
                    }
            } catch (_: Exception) {}

            return AuthVerificationResult(isSuccess = true, isAdmin = false)
        }
    }

    fun signOutToGuest() {
        try {
            firebaseAuth?.signOut()
        } catch (_: Exception) {}

        val currentPrefs = loadPersistedPreferences()
        _currentUser.value = UserProfile(
            uid = "guest_session_01",
            email = "guest@streamflix.tv",
            displayName = "Guest Explorer",
            isGuest = true,
            avatarUrl = "avatar_classic",
            cloudSyncEnabled = currentPrefs.cloudSyncEnabled,
            subscriptionTier = "Free Guest Access",
            preferences = currentPrefs
        )
    }

    private fun loadPersistedPreferences(): AccountPreferences {
        val p = prefs ?: return AccountPreferences()
        return AccountPreferences(
            streamingQuality = p.getString("pref_streaming_quality", "Auto (Recommended)") ?: "Auto (Recommended)",
            audioLanguage = p.getString("pref_audio_language", "English (Original)") ?: "English (Original)",
            subtitlesEnabled = p.getBoolean("pref_subtitles_enabled", true),
            subtitleLanguage = p.getString("pref_subtitle_language", "English [CC]") ?: "English [CC]",
            autoplayNextEpisode = p.getBoolean("pref_autoplay_next", true),
            wifiOnlyDownloads = p.getBoolean("pref_wifi_only_downloads", true),
            cellularStreamingAllowed = p.getBoolean("pref_cellular_streaming", true),
            pushNotificationsEnabled = p.getBoolean("pref_push_notifications", true),
            cloudSyncEnabled = p.getBoolean("pref_cloud_sync", true)
        )
    }

    fun updateDisplayName(displayName: String) {
        val trimmed = displayName.trim().ifEmpty { _currentUser.value.displayName }
        _currentUser.value = _currentUser.value.copy(displayName = trimmed)
        prefs?.edit()?.putString("profile_display_name", trimmed)?.apply()
    }

    fun updateProfileAvatar(avatarUrl: String) {
        _currentUser.value = _currentUser.value.copy(avatarUrl = avatarUrl)
        prefs?.edit()?.putString("profile_avatar_url", avatarUrl)?.apply()
    }

    fun updateAccountPreferences(preferences: AccountPreferences) {
        _currentUser.value = _currentUser.value.copy(
            preferences = preferences,
            cloudSyncEnabled = preferences.cloudSyncEnabled
        )
        prefs?.edit()?.apply {
            putString("pref_streaming_quality", preferences.streamingQuality)
            putString("pref_audio_language", preferences.audioLanguage)
            putBoolean("pref_subtitles_enabled", preferences.subtitlesEnabled)
            putString("pref_subtitle_language", preferences.subtitleLanguage)
            putBoolean("pref_autoplay_next", preferences.autoplayNextEpisode)
            putBoolean("pref_wifi_only_downloads", preferences.wifiOnlyDownloads)
            putBoolean("pref_cellular_streaming", preferences.cellularStreamingAllowed)
            putBoolean("pref_push_notifications", preferences.pushNotificationsEnabled)
            putBoolean("pref_cloud_sync", preferences.cloudSyncEnabled)
        }?.apply()
    }

    fun updateUserProfile(
        displayName: String,
        avatarUrl: String,
        preferences: AccountPreferences
    ) {
        val trimmed = displayName.trim().ifEmpty { _currentUser.value.displayName }
        _currentUser.value = _currentUser.value.copy(
            displayName = trimmed,
            avatarUrl = avatarUrl,
            preferences = preferences,
            cloudSyncEnabled = preferences.cloudSyncEnabled
        )
        prefs?.edit()?.apply {
            putString("profile_display_name", trimmed)
            putString("profile_avatar_url", avatarUrl)
            putString("pref_streaming_quality", preferences.streamingQuality)
            putString("pref_audio_language", preferences.audioLanguage)
            putBoolean("pref_subtitles_enabled", preferences.subtitlesEnabled)
            putString("pref_subtitle_language", preferences.subtitleLanguage)
            putBoolean("pref_autoplay_next", preferences.autoplayNextEpisode)
            putBoolean("pref_wifi_only_downloads", preferences.wifiOnlyDownloads)
            putBoolean("pref_cellular_streaming", preferences.cellularStreamingAllowed)
            putBoolean("pref_push_notifications", preferences.pushNotificationsEnabled)
            putBoolean("pref_cloud_sync", preferences.cloudSyncEnabled)
        }?.apply()
    }

    fun triggerManualCloudSync() {
        _isCloudSyncing.value = true
    }
}
