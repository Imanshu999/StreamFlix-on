package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

class IptvRepository(private val context: Context? = null) {

    private val _channels = MutableStateFlow<List<LiveChannel>>(emptyList())
    val channels: StateFlow<List<LiveChannel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"
        private const val TAG = "IptvRepository"
    }

    suspend fun loadChannels(playlistUrl: String = DEFAULT_PLAYLIST_URL, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (!forceRefresh && _channels.value.isNotEmpty()) {
            return@withContext
        }
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val request = Request.Builder()
                .url(playlistUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                _errorMessage.value = "Failed to fetch live channels (${response.code})"
                _isLoading.value = false
                return@withContext
            }

            val m3uContent = response.body!!.string()
            val parsed = parseM3u(m3uContent)
            if (parsed.isEmpty()) {
                _errorMessage.value = "No valid channels found in playlist."
            } else {
                _channels.value = parsed
                val cats = parsed.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                _categories.value = listOf("All") + cats
                Log.d(TAG, "Loaded ${parsed.size} live TV channels across ${cats.size} categories")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading M3U playlist", e)
            _errorMessage.value = e.localizedMessage ?: "Network error fetching channels"
        } finally {
            _isLoading.value = false
        }
    }

    private fun parseM3u(content: String): List<LiveChannel> {
        val list = mutableListOf<LiveChannel>()
        val reader = BufferedReader(StringReader(content))
        var line: String? = reader.readLine()

        var currentName = ""
        var currentLogo = ""
        var currentCategory = "General"
        var currentId = ""
        var currentResolution = ""

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                // Parse attributes
                val tvgId = extractAttribute(trimmed, "tvg-id")
                val tvgLogo = extractAttribute(trimmed, "tvg-logo")
                val groupTitle = extractAttribute(trimmed, "group-title")

                // Channel name is after the last comma
                val commaIndex = trimmed.lastIndexOf(',')
                val name = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Live Channel"
                }

                currentId = tvgId.ifEmpty { "ch_${list.size + 1}" }
                currentLogo = tvgLogo
                currentCategory = groupTitle.ifEmpty { "General" }

                // Check resolution in name like (1080p), (720p), (576p)
                val resRegex = "\\(([0-9]{3,4}p)\\)".toRegex()
                val match = resRegex.find(name)
                currentResolution = match?.groupValues?.get(1) ?: "HD"
                currentName = name
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                // This is the stream URL line
                val streamUrl = trimmed
                if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
                    list.add(
                        LiveChannel(
                            id = currentId,
                            name = currentName.ifEmpty { "Live TV Channel" },
                            logoUrl = currentLogo,
                            category = currentCategory,
                            streamUrl = streamUrl,
                            resolution = currentResolution,
                            country = "India",
                            language = "Hindi / Regional"
                        )
                    )
                }
                // Reset temporary fields
                currentName = ""
                currentLogo = ""
                currentCategory = "General"
                currentId = ""
                currentResolution = ""
            }
            line = reader.readLine()
        }
        return list
    }

    private fun extractAttribute(line: String, attributeName: String): String {
        val pattern = "$attributeName=\"([^\"]*)\"".toRegex()
        val match = pattern.find(line)
        return match?.groupValues?.get(1) ?: ""
    }
}
