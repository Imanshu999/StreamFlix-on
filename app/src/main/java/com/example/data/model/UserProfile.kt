package com.example.data.model

data class AccountPreferences(
    val streamingQuality: String = "Auto (Recommended)",
    val audioLanguage: String = "English (Original)",
    val subtitlesEnabled: Boolean = true,
    val subtitleLanguage: String = "English [CC]",
    val autoplayNextEpisode: Boolean = true,
    val wifiOnlyDownloads: Boolean = true,
    val cellularStreamingAllowed: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = true
)

data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val isGuest: Boolean = true,
    val avatarUrl: String = "avatar_classic",
    val cloudSyncEnabled: Boolean = false,
    val subscriptionTier: String = "Premium 4K HDR",
    val isAdmin: Boolean = !isGuest && email.trim().equals(ADMIN_EMAIL, ignoreCase = true),
    val preferences: AccountPreferences = AccountPreferences()
) {
    companion object {
        const val ADMIN_EMAIL = "n4062226@gmail.com"
    }
}
