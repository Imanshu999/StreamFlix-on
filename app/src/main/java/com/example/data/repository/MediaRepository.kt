package com.example.data.repository

import android.util.Log
import com.example.data.model.EpisodeData
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SeasonData
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaRepository {

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize FirebaseFirestore", e)
        null
    }

    // Initial state is empty; media items are fetched and synchronized in real-time from Firebase Firestore
    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var snapshotListenerRegistration: ListenerRegistration? = null

    init {
        startFirestoreLiveSync()
    }

    /**
     * Connects real-time snapshot listener to Firestore collection "media".
     * Live updates automatically propagate to all observers.
     * Content is strictly retrieved from Firebase; if the collection is empty,
     * the catalog remains empty until uploaded by an admin.
     */
    fun startFirestoreLiveSync() {
        if (firestore == null) {
            _isLoading.value = false
            return
        }

        snapshotListenerRegistration?.remove()
        _isLoading.value = true

        snapshotListenerRegistration = firestore.collection(COLLECTION_MEDIA)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e(TAG, "Firestore media collection listen error: ${error.message}", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val parsed = snapshot.documents.mapNotNull { doc ->
                        mapDocumentToMediaItem(doc)
                    }
                    _mediaItems.value = parsed
                    Log.d(TAG, "Real-time sync: Received ${parsed.size} media items from Firestore")
                }
            }
    }

    fun getMediaById(id: String): MediaItem? {
        return _mediaItems.value.find { it.id == id }
    }

    /**
     * Persists media item to Firebase Firestore live collection.
     */
    fun addOrUpdateMedia(item: MediaItem) {
        // Optimistic local update for instantaneous UI reactivity
        val current = _mediaItems.value.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(0, item)
        }
        if (item.isFeatured) {
            _mediaItems.value = current.map {
                if (it.id == item.id) it else it.copy(isFeatured = false)
            }
        } else {
            _mediaItems.value = current
        }

        // Live write to Firebase Firestore
        firestore?.let { db ->
            val dataMap = mediaItemToMap(item)
            db.collection(COLLECTION_MEDIA)
                .document(item.id)
                .set(dataMap)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully synced '${item.title}' (${item.id}) to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to write '${item.title}' to Firestore", e)
                }

            if (item.isFeatured) {
                current.filter { it.id != item.id && it.isFeatured }.forEach { other ->
                    db.collection(COLLECTION_MEDIA).document(other.id).update("isFeatured", false)
                }
            }
        }
    }

    /**
     * Designates a media item as the top hero featured title in Firestore.
     */
    fun setFeaturedMedia(id: String) {
        val current = _mediaItems.value.map {
            if (it.id == id) it.copy(isFeatured = true) else it.copy(isFeatured = false)
        }
        _mediaItems.value = current

        firestore?.let { db ->
            current.forEach { item ->
                db.collection(COLLECTION_MEDIA)
                    .document(item.id)
                    .update("isFeatured", item.id == id)
            }
        }
    }

    /**
     * Toggles Top 10 status in Firestore.
     */
    fun toggleTop10(id: String) {
        val target = _mediaItems.value.find { it.id == id } ?: return
        val newTop10 = !target.isTop10
        _mediaItems.value = _mediaItems.value.map {
            if (it.id == id) it.copy(isTop10 = newTop10) else it
        }

        firestore?.collection(COLLECTION_MEDIA)
            ?.document(id)
            ?.update("isTop10", newTop10)
    }

    /**
     * Deletes a media item from Firestore and local cache.
     */
    fun deleteMedia(id: String) {
        _mediaItems.value = _mediaItems.value.filter { it.id != id }

        firestore?.collection(COLLECTION_MEDIA)
            ?.document(id)
            ?.delete()
            ?.addOnSuccessListener {
                Log.d(TAG, "Deleted media item '$id' from Firestore")
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete media item '$id' from Firestore", e)
            }
    }

    /**
     * Refreshes the real-time snapshot listener from Firestore.
     */
    fun refreshCatalog() {
        startFirestoreLiveSync()
    }

    /**
     * Deletes all items from Firestore collection.
     */
    fun clearCatalog() {
        val allIds = _mediaItems.value.map { it.id }
        _mediaItems.value = emptyList()

        firestore?.let { db ->
            allIds.forEach { id ->
                db.collection(COLLECTION_MEDIA).document(id).delete()
            }
        }
    }

    companion object {
        private const val TAG = "MediaRepository"
        const val COLLECTION_MEDIA = "media"

        /**
         * Serializes a MediaItem into a Firestore document map.
         */
        fun mediaItemToMap(item: MediaItem): Map<String, Any?> {
            val seasonsMap = item.seasons.map { season ->
                mapOf(
                    "seasonNumber" to season.seasonNumber,
                    "title" to season.title,
                    "episodes" to season.episodes.map { ep ->
                        mapOf(
                            "id" to ep.id,
                            "episodeNumber" to ep.episodeNumber,
                            "title" to ep.title,
                            "overview" to ep.overview,
                            "thumbnailUrl" to ep.thumbnailUrl,
                            "durationMinutes" to ep.durationMinutes,
                            "streamUrl" to ep.streamUrl
                        )
                    }
                )
            }

            return mapOf(
                "id" to item.id,
                "title" to item.title,
                "description" to item.description,
                "bannerUrl" to item.bannerUrl,
                "posterUrl" to item.posterUrl,
                "type" to item.type.name,
                "category" to item.category,
                "genres" to item.genres,
                "releaseYear" to item.releaseYear,
                "rating" to item.rating,
                "matchPercentage" to item.matchPercentage,
                "durationText" to item.durationText,
                "directStreamUrl" to item.directStreamUrl,
                "isFeatured" to item.isFeatured,
                "cast" to item.cast,
                "isTop10" to item.isTop10,
                "badgeLabel" to (item.badgeLabel ?: ""),
                "seasons" to seasonsMap
            )
        }

        /**
         * Safely deserializes a Firestore DocumentSnapshot into a MediaItem.
         */
        fun mapDocumentToMediaItem(doc: DocumentSnapshot): MediaItem? {
            val id = doc.getString("id") ?: doc.id
            val title = doc.getString("title") ?: return null
            val description = doc.getString("description") ?: ""
            val bannerUrl = doc.getString("bannerUrl") ?: ""
            val posterUrl = doc.getString("posterUrl") ?: ""
            val typeStr = doc.getString("type") ?: "MOVIE"
            val type = try {
                MediaType.valueOf(typeStr)
            } catch (_: Exception) {
                MediaType.MOVIE
            }
            val category = doc.getString("category") ?: "Trending Now"
            val genres = (doc.get("genres") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val releaseYear = (doc.getLong("releaseYear") ?: 2024L).toInt()
            val rating = doc.getString("rating") ?: "TV-MA"
            val matchPercentage = (doc.getLong("matchPercentage") ?: 98L).toInt()
            val durationText = doc.getString("durationText") ?: ""
            val directStreamUrl = doc.getString("directStreamUrl") ?: ""
            val isFeatured = doc.getBoolean("isFeatured") ?: false
            val cast = (doc.get("cast") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val isTop10 = doc.getBoolean("isTop10") ?: false
            val badgeLabel = doc.getString("badgeLabel")?.takeIf { it.isNotBlank() }

            val rawSeasons = doc.get("seasons") as? List<*> ?: emptyList<Any>()
            val seasons = rawSeasons.mapNotNull { sObj ->
                val sMap = sObj as? Map<*, *> ?: return@mapNotNull null
                val sNum = (sMap["seasonNumber"] as? Number)?.toInt() ?: 1
                val sTitle = sMap["title"]?.toString() ?: "Season $sNum"
                val rawEpisodes = sMap["episodes"] as? List<*> ?: emptyList<Any>()
                val episodes = rawEpisodes.mapNotNull { epObj ->
                    val epMap = epObj as? Map<*, *> ?: return@mapNotNull null
                    val epId = epMap["id"]?.toString() ?: "${id}_s${sNum}_e${epMap["episodeNumber"]}"
                    val epNum = (epMap["episodeNumber"] as? Number)?.toInt() ?: 1
                    val epTitle = epMap["title"]?.toString() ?: "Episode $epNum"
                    val epOverview = epMap["overview"]?.toString() ?: ""
                    val epThumb = epMap["thumbnailUrl"]?.toString() ?: bannerUrl
                    val epDuration = (epMap["durationMinutes"] as? Number)?.toInt() ?: 45
                    val epStream = epMap["streamUrl"]?.toString() ?: directStreamUrl
                    EpisodeData(
                        id = epId,
                        episodeNumber = epNum,
                        title = epTitle,
                        overview = epOverview,
                        thumbnailUrl = epThumb,
                        durationMinutes = epDuration,
                        streamUrl = epStream
                    )
                }
                SeasonData(
                    seasonNumber = sNum,
                    title = sTitle,
                    episodes = episodes
                )
            }

            return MediaItem(
                id = id,
                title = title,
                description = description,
                bannerUrl = bannerUrl,
                posterUrl = posterUrl,
                type = type,
                category = category,
                genres = genres,
                releaseYear = releaseYear,
                rating = rating,
                matchPercentage = matchPercentage,
                durationText = durationText,
                directStreamUrl = directStreamUrl,
                isFeatured = isFeatured,
                cast = cast,
                seasons = seasons,
                isTop10 = isTop10,
                badgeLabel = badgeLabel
            )
        }
    }
}
