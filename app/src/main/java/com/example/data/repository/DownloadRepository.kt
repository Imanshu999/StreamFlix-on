package com.example.data.repository

import android.content.Context
import android.os.Environment
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.model.EpisodeData
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val context: Context
) {
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    private val httpClient = OkHttpClient()

    suspend fun addMovieDownload(media: MediaItem) {
        download(media.id, media.title, media.posterUrl, media.directStreamUrl, null, null)
    }

    suspend fun addEpisodeDownload(media: MediaItem, episode: EpisodeData) {
        download(
            id = "${media.id}_${episode.id}",
            title = media.title,
            posterUrl = episode.thumbnailUrl.ifEmpty { media.posterUrl },
            streamUrl = episode.streamUrl,
            episodeId = episode.id,
            episodeTitle = "E${episode.episodeNumber}: ${episode.title}"
        )
    }

    private suspend fun download(
        id: String,
        title: String,
        posterUrl: String,
        streamUrl: String,
        episodeId: String?,
        episodeTitle: String?
    ) = withContext(Dispatchers.IO) {
        require(streamUrl.isNotBlank()) { "No downloadable video URL is available." }
        require(!streamUrl.contains(".m3u8", ignoreCase = true)) {
            "This stream is HLS and needs a media download service."
        }

        val existing = downloadDao.getAllDownloadsList().firstOrNull { it.id == id }
        if (existing?.downloadStatus == "COMPLETED") return@withContext

        val request = Request.Builder().url(streamUrl).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("Download failed: HTTP ${response.code}")
        }

        val body = response.body
        if (body == null) {
            response.close()
            throw IllegalStateException("Download failed: empty response.")
        }

        val downloadsDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "StreamFlix"
        )
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]+"), "_").trim('_').ifBlank { "streamflix_media" }
        val extension = guessExtension(response.header("Content-Type"), streamUrl)
        val file = File(downloadsDir, "${safeTitle}_${id.hashCode().toUInt().toString(16)}$extension")

        var downloadedBytes = 0L
        val totalBytes = body.contentLength()
        downloadDao.insert(
            DownloadEntity(
                id = id,
                mediaId = id.substringBefore("_"),
                episodeId = episodeId,
                title = title,
                episodeTitle = episodeTitle,
                posterUrl = posterUrl,
                fileSizeBytes = 0L,
                downloadProgress = 0f,
                downloadStatus = "DOWNLOADING",
                downloadedAt = System.currentTimeMillis(),
                streamUrl = streamUrl
            )
        )

        try {
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastPersistedProgress = 0f
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            if (progress - lastPersistedProgress >= 0.05f) {
                                lastPersistedProgress = progress
                                downloadDao.insert(
                                    DownloadEntity(
                                        id = id,
                                        mediaId = id.substringBefore("_"),
                                        episodeId = episodeId,
                                        title = title,
                                        episodeTitle = episodeTitle,
                                        posterUrl = posterUrl,
                                        fileSizeBytes = downloadedBytes,
                                        downloadProgress = progress,
                                        downloadStatus = "DOWNLOADING",
                                        downloadedAt = System.currentTimeMillis(),
                                        streamUrl = streamUrl
                                    )
                                )
                            }
                        }
                    }
                }
            }

            downloadDao.insert(
                DownloadEntity(
                    id = id,
                    mediaId = id.substringBefore("_"),
                    episodeId = episodeId,
                    title = title,
                    episodeTitle = episodeTitle,
                    posterUrl = posterUrl,
                    fileSizeBytes = downloadedBytes,
                    downloadProgress = 1f,
                    downloadStatus = "COMPLETED",
                    downloadedAt = System.currentTimeMillis(),
                    streamUrl = file.toURI().toString()
                )
            )
        } catch (t: Throwable) {
            file.delete()
            downloadDao.deleteById(id)
            throw t
        } finally {
            body.close()
        }
    }

    private fun guessExtension(contentType: String?, url: String): String {
        val lowerType = contentType.orEmpty().lowercase()
        val lowerUrl = URL(url).path.lowercase()
        return when {
            lowerType.contains("mp4") || lowerUrl.endsWith(".mp4") -> ".mp4"
            lowerType.contains("webm") || lowerUrl.endsWith(".webm") -> ".webm"
            lowerType.contains("mkv") || lowerUrl.endsWith(".mkv") -> ".mkv"
            lowerType.contains("mpeg") || lowerUrl.endsWith(".mpeg") || lowerUrl.endsWith(".mpg") -> ".mpg"
            else -> ".mp4"
        }
    }

    suspend fun removeDownload(id: String) {
        downloadDao.getAllDownloadsList().firstOrNull { it.id == id }?.let { item ->
            if (item.streamUrl.startsWith("file:")) {
                runCatching { File(URL(item.streamUrl).toURI()).delete() }
            }
        }
        downloadDao.deleteById(id)
    }

    suspend fun clearAllDownloads() {
        downloadDao.getAllDownloadsList().forEach { item ->
            if (item.streamUrl.startsWith("file:")) {
                runCatching { File(URL(item.streamUrl).toURI()).delete() }
            }
        }
        downloadDao.deleteAll()
    }

    suspend fun isDownloaded(id: String): Boolean = downloadDao.isDownloaded(id)

    suspend fun repairBrokenDownloadUrls(catalog: List<MediaItem>) {
        val list = downloadDao.getAllDownloadsList()
        for (item in list) {
            if (!item.streamUrl.startsWith("file:") &&
                (item.streamUrl.contains("commondatastorage.googleapis.com") ||
                 item.streamUrl.contains("gtv-videos-bucket"))
            ) {
                val media = catalog.find { it.id == item.mediaId }
                val updatedUrl = if (item.episodeId != null) {
                    media?.seasons?.flatMap { it.episodes }?.find { it.id == item.episodeId }?.streamUrl ?: ""
                } else {
                    media?.directStreamUrl ?: ""
                }
                downloadDao.insert(item.copy(streamUrl = updatedUrl))
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
