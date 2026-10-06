package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.EpisodeData
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.MediaVersion
import com.example.data.model.SeasonData
import com.example.data.network.api.MovieBoxApiClient
import com.example.data.network.dto.SubjectDto
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context? = null) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize FirebaseFirestore", e)
        null
    }

    private val api = MovieBoxApiClient.service

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _top10Items = MutableStateFlow<List<MediaItem>>(emptyList())
    val top10Items: StateFlow<List<MediaItem>> = _top10Items.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var snapshotListenerRegistration: ListenerRegistration? = null

    // Cache to hold remote fetched media alongside Firestore live entries
    private val apiMediaMap = mutableMapOf<String, MediaItem>()
    private val firestoreMediaMap = mutableMapOf<String, MediaItem>()
    private val top10IdList = mutableListOf<String>()

    init {
        // 1. Instantly populate with the real bundled catalog to ensure zero cold-start delay
        loadBundledCatalog()

        // 2. Start Firebase Firestore sync
        startFirestoreLiveSync()

        // 3. Fetch live feed from api.inmoviebox.com / wefeed-mobile-bff endpoints
        fetchLiveCatalogFromApi()

        // 4. Fetch search suggestions
        fetchSearchSuggestions()
    }

    private fun loadBundledCatalog() {
        if (context == null) return
        try {
            val jsonString = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val listType = Types.newParameterizedType(List::class.java, MediaItem::class.java)
            val adapter = moshi.adapter<List<MediaItem>>(listType)
            val items = adapter.fromJson(jsonString)
            if (!items.isNullOrEmpty()) {
                items.forEach { apiMediaMap[it.id] = it }
                updateCombinedMediaItems()
                Log.d(TAG, "Loaded ${items.size} real media titles from catalog.json")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled catalog.json", e)
        }
    }

    fun fetchLiveCatalogFromApi(forceRefresh: Boolean = false) {
        scope.launch {
            _isLoading.value = true
            try {
                // Fetch Home operating blocks
                val homeResponse = api.getHome("h5.inmoviebox.com")
                if (homeResponse.isSuccessful && homeResponse.body()?.data?.operatingList != null) {
                    val ops = homeResponse.body()!!.data!!.operatingList!!
                    for (op in ops) {
                        val categoryName = op.title ?: "Popular"
                        // Banner Hero Items
                        if (op.type == "BANNER" && op.banner?.items != null) {
                            for (item in op.banner!!.items!!) {
                                val sub = item.subject
                                if (sub != null && !sub.subjectId.isNullOrBlank()) {
                                    val mapped = mapSubjectDtoToMediaItem(
                                        dto = sub,
                                        forcedCategory = "Featured Banner",
                                        isFeatured = true,
                                        bannerOverride = MovieBoxApiClient.resolveImageUrl(item.image?.url)
                                    )
                                    apiMediaMap[mapped.id] = mapped
                                }
                            }
                        }
                        // Operating category subjects
                        if (op.subjects != null) {
                            for (sub in op.subjects!!) {
                                if (!sub.subjectId.isNullOrBlank()) {
                                    val existing = apiMediaMap[sub.subjectId!!]
                                    val mapped = mapSubjectDtoToMediaItem(
                                        dto = sub,
                                        forcedCategory = existing?.category ?: categoryName,
                                        isFeatured = existing?.isFeatured ?: false
                                    )
                                    apiMediaMap[mapped.id] = mapped
                                }
                            }
                        }
                    }
                }

                // Fetch Trending (defines official Top 10 popular ranking)
                val trendingResponse = api.getTrending(page = 1, perPage = 50)
                if (trendingResponse.isSuccessful && trendingResponse.body()?.data?.subjectList != null) {
                    val trendings = trendingResponse.body()!!.data!!.subjectList!!
                    top10IdList.clear()
                    for ((index, sub) in trendings.withIndex()) {
                        if (!sub.subjectId.isNullOrBlank()) {
                            val id = sub.subjectId!!
                            if (index < 10) {
                                top10IdList.add(id)
                            }
                            val existing = apiMediaMap[id]
                            val mapped = mapSubjectDtoToMediaItem(
                                dto = sub,
                                forcedCategory = existing?.category ?: "Trending Now",
                                isFeatured = existing?.isFeatured ?: false,
                                isTop10Rank = index < 10
                            )
                            apiMediaMap[mapped.id] = mapped
                        }
                    }
                }

                // Fetch Tab Operating (Genres & Discoveries)
                try {
                    val tabResponse = api.getTabOperating("h5.inmoviebox.com")
                    if (tabResponse.isSuccessful && tabResponse.body()?.data?.operatingList != null) {
                        for (op in tabResponse.body()!!.data!!.operatingList!!) {
                            val categoryName = op.title ?: "Discover"
                            op.subjects?.forEach { sub ->
                                if (!sub.subjectId.isNullOrBlank()) {
                                    val existing = apiMediaMap[sub.subjectId!!]
                                    val mapped = mapSubjectDtoToMediaItem(
                                        dto = sub,
                                        forcedCategory = existing?.category ?: categoryName,
                                        isFeatured = existing?.isFeatured ?: false
                                    )
                                    apiMediaMap[mapped.id] = mapped
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Optional tab-operating fetch warning: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    updateCombinedMediaItems(shouldRotate = forceRefresh)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch live API catalog: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchSearchSuggestions() {
        scope.launch {
            try {
                val res = api.getSearchSuggestions()
                if (res.isSuccessful && res.body()?.data?.everyoneSearch != null) {
                    val list = res.body()!!.data!!.everyoneSearch!!.mapNotNull { it.title?.trim() }
                    if (list.isNotEmpty()) {
                        _searchSuggestions.value = list
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search suggestions fetch failed: ${e.message}")
            }
        }
    }

    suspend fun fetchSubjectDetail(subjectId: String): MediaItem? = withContext(Dispatchers.IO) {
        try {
            val res = api.getSubjectDetail(subjectId)
            if (res.isSuccessful && res.body()?.data?.subject != null) {
                val data = res.body()!!.data!!
                val existing = apiMediaMap[subjectId]
                val item = mapSubjectDtoToMediaItem(
                    dto = data.subject!!,
                    forcedCategory = existing?.category ?: "Popular",
                    isFeatured = existing?.isFeatured ?: false,
                    stars = data.stars
                )
                // Attach seasons info if available from resource
                val resourceSeasons = data.resource?.seasons
                val seasonsData = if (item.type == MediaType.SERIES && !resourceSeasons.isNullOrEmpty()) {
                    resourceSeasons.map { sRes ->
                        val sNum = sRes.se ?: 1
                        val maxEp = (sRes.maxEp ?: 1).coerceAtMost(30)
                        SeasonData(
                            seasonNumber = sNum,
                            title = "Season $sNum",
                            episodes = (1..maxEp).map { epNum ->
                                EpisodeData(
                                    id = "${subjectId}_s${sNum}_e${epNum}",
                                    episodeNumber = epNum,
                                    title = "Episode $epNum",
                                    overview = item.description,
                                    thumbnailUrl = item.bannerUrl.ifEmpty { item.posterUrl },
                                    durationMinutes = 45,
                                    streamUrl = item.directStreamUrl
                                )
                            }
                        )
                    }
                } else item.seasons

                val updatedItem = item.copy(seasons = seasonsData)
                apiMediaMap[subjectId] = updatedItem
                withContext(Dispatchers.Main) {
                    updateCombinedMediaItems()
                }
                return@withContext updatedItem
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching detail for $subjectId", e)
        }
        return@withContext apiMediaMap[subjectId] ?: firestoreMediaMap[subjectId]
    }

    suspend fun fetchRecommendations(subjectId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = api.getDetailRecommendations(subjectId, page = 1, perPage = 12)
            if (res.isSuccessful && res.body()?.data?.items != null) {
                return@withContext res.body()!!.data!!.items!!.map { dto ->
                    mapSubjectDtoToMediaItem(dto, forcedCategory = "More Like This")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recommendations for $subjectId", e)
        }
        return@withContext emptyList()
    }

    private fun updateCombinedMediaItems(shouldRotate: Boolean = false) {
        val combined = mutableMapOf<String, MediaItem>()
        // API items first
        combined.putAll(apiMediaMap)
        // Firestore items override or supplement
        combined.putAll(firestoreMediaMap)

        var list = combined.values.toList()

        if (shouldRotate && list.size > 20) {
            // Intelligent content rotation on refresh: keep top 10 at peak, rotate remainder
            val topItems = list.filter { it.isTop10 || it.isFeatured }
            val rest = list.filter { !it.isTop10 && !it.isFeatured }.shuffled()
            list = topItems + rest
        }

        _mediaItems.value = list

        // Update dedicated Top 10 list
        val top10s = if (top10IdList.isNotEmpty()) {
            top10IdList.mapNotNull { combined[it] }
        } else {
            list.filter { it.isTop10 || it.isFeatured }.take(10)
        }
        _top10Items.value = top10s.ifEmpty { list.take(10) }
    }

    /**
     * Connects real-time snapshot listener to Firestore collection "media".
     */
    fun startFirestoreLiveSync() {
        if (firestore == null) return

        snapshotListenerRegistration?.remove()

        snapshotListenerRegistration = firestore.collection(COLLECTION_MEDIA)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore media collection listen error: ${error.message}", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    firestoreMediaMap.clear()
                    snapshot.documents.forEach { doc ->
                        mapDocumentToMediaItem(doc)?.let { item ->
                            firestoreMediaMap[item.id] = item
                        }
                    }
                    updateCombinedMediaItems()
                    Log.d(TAG, "Firestore sync: ${firestoreMediaMap.size} custom items")
                }
            }
    }

    fun getMediaById(id: String): MediaItem? {
        return _mediaItems.value.find { it.id == id }
    }

    fun addOrUpdateMedia(item: MediaItem) {
        firestoreMediaMap[item.id] = item
        updateCombinedMediaItems()

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
        }
    }

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

    fun toggleTop10(id: String) {
        val target = _mediaItems.value.find { it.id == id } ?: return
        val newTop10 = !target.isTop10
        val updated = target.copy(isTop10 = newTop10)
        if (firestoreMediaMap.containsKey(id)) {
            firestoreMediaMap[id] = updated
        } else {
            apiMediaMap[id] = updated
        }
        updateCombinedMediaItems()

        firestore?.collection(COLLECTION_MEDIA)
            ?.document(id)
            ?.update("isTop10", newTop10)
    }

    fun deleteMedia(id: String) {
        firestoreMediaMap.remove(id)
        apiMediaMap.remove(id)
        updateCombinedMediaItems()

        firestore?.collection(COLLECTION_MEDIA)
            ?.document(id)
            ?.delete()
    }

    fun refreshCatalog() {
        fetchLiveCatalogFromApi(forceRefresh = true)
        startFirestoreLiveSync()
    }

    fun clearCatalog() {
        firestoreMediaMap.clear()
        updateCombinedMediaItems()

        firestore?.let { db ->
            db.collection(COLLECTION_MEDIA).get().addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { it.reference.delete() }
            }
        }
    }

    companion object {
        private const val TAG = "MediaRepository"
        const val COLLECTION_MEDIA = "media"

        fun mapSubjectDtoToMediaItem(
            dto: SubjectDto,
            forcedCategory: String = "Popular",
            isFeatured: Boolean = false,
            bannerOverride: String? = null,
            stars: List<com.example.data.network.dto.StaffDto>? = null,
            isTop10Rank: Boolean = false
        ): MediaItem {
            val id = dto.subjectId ?: "media_${System.currentTimeMillis()}"
            val isSeries = dto.subjectType == 2
            val type = if (isSeries) MediaType.SERIES else MediaType.MOVIE

            val releaseYear = dto.releaseDate?.take(4)?.toIntOrNull() ?: 2024
            val genres = dto.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: listOf("Drama")
            val ratingScore = dto.imdbRatingValue?.toFloatOrNull() ?: 7.5f
            val matchPercentage = (70 + (ratingScore * 3).toInt()).coerceIn(70, 99)

            val posterUrl = MovieBoxApiClient.resolveImageUrl(dto.cover?.url)
            val trailerCover = MovieBoxApiClient.resolveImageUrl(dto.trailer?.cover?.url)
            val bannerUrl = bannerOverride?.ifEmpty { null } ?: trailerCover.ifEmpty { posterUrl }
            val directStreamUrl = dto.trailer?.videoAddress?.url ?: ""

            val durationText = when {
                dto.duration != null && dto.duration > 0 -> "${(dto.duration / 60)}m"
                isSeries -> "TV Series"
                else -> "2h 5m"
            }

            val castList = (stars ?: dto.staffList)?.mapNotNull { it.name?.trim() }?.filter { it.isNotBlank() } ?: emptyList()

            // Extract language versions from dubs and subtitles
            val availableVersions = mutableListOf<MediaVersion>()
            val languageSet = linkedSetOf<String>()

            // 1. Check official dubs list
            dto.dubs?.forEach { dub ->
                val lanName = dub.lanName ?: "Audio"
                val label = when {
                    lanName.contains("Hindi", ignoreCase = true) -> "Hindi Version"
                    lanName.contains("English", ignoreCase = true) -> "English Version"
                    lanName.contains("Tamil", ignoreCase = true) -> "Tamil Version"
                    lanName.contains("Telugu", ignoreCase = true) -> "Telugu Version"
                    lanName.contains("Malayalam", ignoreCase = true) -> "Malayalam Version"
                    lanName.contains("Spanish", ignoreCase = true) || lanName.contains("Español", ignoreCase = true) -> "Spanish Version"
                    lanName.contains("Original", ignoreCase = true) -> "Original Version"
                    else -> "$lanName Version"
                }
                availableVersions.add(
                    MediaVersion(
                        id = dub.subjectId ?: id,
                        label = label,
                        languageCode = dub.lanCode ?: "en",
                        isDub = dub.type == 0,
                        isOriginal = dub.original == true,
                        streamUrl = directStreamUrl
                    )
                )
                languageSet.add(lanName.replace(" dub", "").replace(" sub", "").trim())
            }

            // 2. Extract from subtitles if no explicit dubs
            if (availableVersions.isEmpty() && !dto.subtitles.isNullOrBlank()) {
                val rawSubs = dto.subtitles!!.split(",").map { it.trim() }.filter { it.isNotBlank() }
                rawSubs.forEach { sub ->
                    languageSet.add(sub)
                }

                // If Indian regional language detected in subtitles or country is India
                if (dto.subtitles!!.contains("हिन्दी") || dto.subtitles!!.contains("Hindi", ignoreCase = true)) {
                    availableVersions.add(MediaVersion(id = id, label = "Hindi Version", languageCode = "hi", isOriginal = false))
                }
                if (dto.subtitles!!.contains("English", ignoreCase = true)) {
                    availableVersions.add(MediaVersion(id = id, label = "English Version", languageCode = "en", isOriginal = true))
                }
                if (dto.subtitles!!.contains("Español", ignoreCase = true) || dto.subtitles!!.contains("Spanish", ignoreCase = true)) {
                    availableVersions.add(MediaVersion(id = id, label = "Spanish Version", languageCode = "es", isOriginal = false))
                }
            }

            // Fallback default version
            if (availableVersions.isEmpty()) {
                val defaultLang = if (dto.countryName.equals("India", ignoreCase = true)) "Hindi Version" else "English Version"
                availableVersions.add(MediaVersion(id = id, label = defaultLang, languageCode = "en", isOriginal = true))
            }

            val seasons = if (isSeries) {
                listOf(
                    SeasonData(
                        seasonNumber = 1,
                        title = "Season 1",
                        episodes = listOf(
                            EpisodeData(
                                id = "${id}_s1_e1",
                                episodeNumber = 1,
                                title = "Episode 1: Pilot",
                                overview = dto.description ?: "Episode 1 of ${dto.title}",
                                thumbnailUrl = bannerUrl.ifEmpty { posterUrl },
                                durationMinutes = 45,
                                streamUrl = directStreamUrl
                            ),
                            EpisodeData(
                                id = "${id}_s1_e2",
                                episodeNumber = 2,
                                title = "Episode 2: The Journey",
                                overview = dto.description ?: "Episode 2 of ${dto.title}",
                                thumbnailUrl = bannerUrl.ifEmpty { posterUrl },
                                durationMinutes = 50,
                                streamUrl = directStreamUrl
                            )
                        )
                    )
                )
            } else emptyList()

            return MediaItem(
                id = id,
                title = dto.title ?: "Untitled",
                description = dto.description?.ifBlank { "Experience ${dto.title}, released in $releaseYear." } ?: "",
                bannerUrl = bannerUrl,
                posterUrl = posterUrl,
                type = type,
                category = forcedCategory,
                genres = genres,
                releaseYear = releaseYear,
                rating = if (dto.countryName == "United States") "TV-MA" else "PG-13",
                matchPercentage = matchPercentage,
                durationText = durationText,
                directStreamUrl = directStreamUrl,
                isFeatured = isFeatured,
                cast = castList,
                seasons = seasons,
                isTop10 = isTop10Rank || isFeatured || forcedCategory.contains("Popular", ignoreCase = true),
                badgeLabel = dto.corner ?: if (isTop10Rank) "TOP 10" else if (isFeatured) "EXCLUSIVE" else null,
                availableLanguages = languageSet.toList().ifEmpty { listOf("English") },
                availableVersions = availableVersions,
                countryName = dto.countryName ?: ""
            )
        }

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
                "seasons" to seasonsMap,
                "countryName" to item.countryName,
                "availableLanguages" to item.availableLanguages
            )
        }

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
            val countryName = doc.getString("countryName") ?: ""
            val languages = (doc.get("availableLanguages") as? List<*>)?.mapNotNull { it?.toString() } ?: listOf("English")

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

            val defaultVersions = listOf(
                MediaVersion(id = id, label = "English Version", languageCode = "en", isOriginal = true),
                MediaVersion(id = id, label = "Hindi Version", languageCode = "hi", isOriginal = false)
            )

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
                badgeLabel = badgeLabel,
                availableLanguages = languages,
                availableVersions = defaultVersions,
                countryName = countryName
            )
        }
    }
}
