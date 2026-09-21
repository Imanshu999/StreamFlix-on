package com.example.data.model

enum class MediaType {
    MOVIE,
    SERIES
}

data class EpisodeData(
    val id: String = "",
    val episodeNumber: Int = 1,
    val title: String = "",
    val overview: String = "",
    val thumbnailUrl: String = "",
    val durationMinutes: Int = 0,
    val streamUrl: String = ""
)

data class SeasonData(
    val seasonNumber: Int = 1,
    val title: String = "",
    val episodes: List<EpisodeData> = emptyList()
)

data class MediaItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val bannerUrl: String = "",
    val posterUrl: String = "",
    val type: MediaType = MediaType.MOVIE,
    val category: String = "Trending Now",
    val genres: List<String> = emptyList(),
    val releaseYear: Int = 2024,
    val rating: String = "TV-MA",
    val matchPercentage: Int = 98,
    val durationText: String = "",
    val directStreamUrl: String = "",
    val isFeatured: Boolean = false,
    val cast: List<String> = emptyList(),
    val seasons: List<SeasonData> = emptyList(),
    val isTop10: Boolean = false,
    val badgeLabel: String? = null
)
