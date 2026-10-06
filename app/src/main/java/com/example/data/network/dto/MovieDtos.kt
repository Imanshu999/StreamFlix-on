package com.example.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseApiResponse<T>(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: T? = null
)

@JsonClass(generateAdapter = true)
data class HomeResponseData(
    @Json(name = "operatingList") val operatingList: List<OperatingBlockDto>? = null,
    @Json(name = "platformList") val platformList: List<PlatformDto>? = null
)

@JsonClass(generateAdapter = true)
data class PlatformDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "uploadBy") val uploadBy: String? = null
)

@JsonClass(generateAdapter = true)
data class OperatingBlockDto(
    @Json(name = "type") val type: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "position") val position: Int? = null,
    @Json(name = "subjects") val subjects: List<SubjectDto>? = null,
    @Json(name = "banner") val banner: BannerBlockDto? = null
)

@JsonClass(generateAdapter = true)
data class BannerBlockDto(
    @Json(name = "items") val items: List<BannerItemDto>? = null
)

@JsonClass(generateAdapter = true)
data class BannerItemDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "image") val image: ImageDto? = null,
    @Json(name = "subjectId") val subjectId: String? = null,
    @Json(name = "subjectType") val subjectType: Int? = null,
    @Json(name = "subject") val subject: SubjectDto? = null,
    @Json(name = "detailPath") val detailPath: String? = null
)

@JsonClass(generateAdapter = true)
data class TrendingResponseData(
    @Json(name = "subjectList") val subjectList: List<SubjectDto>? = null
)

@JsonClass(generateAdapter = true)
data class DubDto(
    @Json(name = "subjectId") val subjectId: String? = null,
    @Json(name = "lanName") val lanName: String? = null,
    @Json(name = "lanCode") val lanCode: String? = null,
    @Json(name = "original") val original: Boolean? = null,
    @Json(name = "type") val type: Int? = null,
    @Json(name = "detailPath") val detailPath: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectDto(
    @Json(name = "subjectId") val subjectId: String? = null,
    @Json(name = "subjectType") val subjectType: Int? = null, // 1 = Movie, 2 = Series
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "cover") val cover: ImageDto? = null,
    @Json(name = "countryName") val countryName: String? = null,
    @Json(name = "imdbRatingValue") val imdbRatingValue: String? = null,
    @Json(name = "imdbRatingCount") val imdbRatingCount: Long? = null,
    @Json(name = "subtitles") val subtitles: String? = null,
    @Json(name = "dubs") val dubs: List<DubDto>? = null,
    @Json(name = "hasResource") val hasResource: Boolean? = null,
    @Json(name = "trailer") val trailer: TrailerDto? = null,
    @Json(name = "staffList") val staffList: List<StaffDto>? = null,
    @Json(name = "corner") val corner: String? = null,
    @Json(name = "detailPath") val detailPath: String? = null
)

@JsonClass(generateAdapter = true)
data class ImageDto(
    @Json(name = "url") val url: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class TrailerDto(
    @Json(name = "videoAddress") val videoAddress: VideoAddressDto? = null,
    @Json(name = "cover") val cover: ImageDto? = null
)

@JsonClass(generateAdapter = true)
data class VideoAddressDto(
    @Json(name = "videoId") val videoId: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "duration") val duration: Int? = null
)

@JsonClass(generateAdapter = true)
data class StaffDto(
    @Json(name = "staffId") val staffId: String? = null,
    @Json(name = "staffType") val staffType: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "character") val character: String? = null,
    @Json(name = "avatarUrl") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectDetailResponseData(
    @Json(name = "subject") val subject: SubjectDto? = null,
    @Json(name = "stars") val stars: List<StaffDto>? = null,
    @Json(name = "resource") val resource: SubjectResourceDto? = null
)

@JsonClass(generateAdapter = true)
data class SubjectResourceDto(
    @Json(name = "seasons") val seasons: List<SeasonResourceDto>? = null,
    @Json(name = "source") val source: String? = null
)

@JsonClass(generateAdapter = true)
data class SeasonResourceDto(
    @Json(name = "se") val se: Int? = null,
    @Json(name = "maxEp") val maxEp: Int? = null
)

@JsonClass(generateAdapter = true)
data class DetailRecResponseData(
    @Json(name = "items") val items: List<SubjectDto>? = null
)

@JsonClass(generateAdapter = true)
data class EveryoneSearchData(
    @Json(name = "everyoneSearch") val everyoneSearch: List<SearchSuggestionDto>? = null
)

@JsonClass(generateAdapter = true)
data class SearchSuggestionDto(
    @Json(name = "title") val title: String? = null
)

@JsonClass(generateAdapter = true)
data class PlayInfoResponseData(
    @Json(name = "streams") val streams: List<StreamUrlDto>? = null,
    @Json(name = "hls") val hls: List<StreamUrlDto>? = null,
    @Json(name = "dash") val dash: List<StreamUrlDto>? = null
)

@JsonClass(generateAdapter = true)
data class StreamUrlDto(
    @Json(name = "url") val url: String? = null,
    @Json(name = "resolution") val resolution: Int? = null
)

@JsonClass(generateAdapter = true)
data class CaptionsResponseData(
    @Json(name = "captions") val captions: List<CaptionDto>? = null
)

@JsonClass(generateAdapter = true)
data class CaptionDto(
    @Json(name = "language") val language: String? = null,
    @Json(name = "url") val url: String? = null
)
