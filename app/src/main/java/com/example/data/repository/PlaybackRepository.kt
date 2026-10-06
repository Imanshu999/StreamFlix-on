package com.example.data.repository

import android.util.Log
import com.example.data.network.api.MovieBoxApiClient
import com.example.data.network.dto.CaptionDto
import com.example.data.network.dto.PlayInfoResponseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaybackRepository {

    private val api = MovieBoxApiClient.service
    private var cachedDomain: String? = null

    suspend fun getStreamingDomain(): String = withContext(Dispatchers.IO) {
        cachedDomain?.let { return@withContext it }
        try {
            val response = api.getPlayerDomain()
            if (response.isSuccessful && response.body()?.data != null) {
                val domain = response.body()!!.data!!
                cachedDomain = domain
                return@withContext domain
            }
        } catch (e: Exception) {
            Log.e("PlaybackRepository", "Failed to retrieve streaming domain", e)
        }
        "https://mzfi.me/"
    }

    suspend fun getPlayInfo(
        subjectId: String,
        season: Int = 1,
        episode: Int = 1
    ): PlayInfoResponseData? = withContext(Dispatchers.IO) {
        try {
            val response = api.getPlayInfo(subjectId, season, episode)
            if (response.isSuccessful) {
                return@withContext response.body()?.data
            }
        } catch (e: Exception) {
            Log.e("PlaybackRepository", "Error getting play info for $subjectId", e)
        }
        null
    }

    suspend fun getCaptions(
        subjectId: String,
        season: Int = 1,
        episode: Int = 1
    ): List<CaptionDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCaptions(subjectId = subjectId, id = subjectId, season = season, episode = episode)
            if (response.isSuccessful) {
                return@withContext response.body()?.data?.captions ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("PlaybackRepository", "Error getting captions for $subjectId", e)
        }
        emptyList()
    }
}
