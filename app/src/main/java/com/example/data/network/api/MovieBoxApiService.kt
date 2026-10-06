package com.example.data.network.api

import com.example.data.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface MovieBoxApiService {

    @GET("wefeed-h5api-bff/home")
    suspend fun getHome(
        @Query("host") host: String = "h5.inmoviebox.com"
    ): Response<BaseApiResponse<HomeResponseData>>

    @GET("wefeed-h5api-bff/tab-operating")
    suspend fun getTabOperating(
        @Query("host") host: String = "h5.inmoviebox.com"
    ): Response<BaseApiResponse<HomeResponseData>>

    @GET("wefeed-h5api-bff/subject/trending")
    suspend fun getTrending(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 30
    ): Response<BaseApiResponse<TrendingResponseData>>

    @GET("wefeed-h5api-bff/detail")
    suspend fun getSubjectDetail(
        @Query("subjectId") subjectId: String
    ): Response<BaseApiResponse<SubjectDetailResponseData>>

    @GET("wefeed-h5api-bff/subject/detail-rec")
    suspend fun getDetailRecommendations(
        @Query("subjectId") subjectId: String,
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 12
    ): Response<BaseApiResponse<DetailRecResponseData>>

    @GET("wefeed-h5api-bff/subject/everyone-search")
    suspend fun getSearchSuggestions(): Response<BaseApiResponse<EveryoneSearchData>>

    @GET("wefeed-h5api-bff/media-player/get-domain")
    suspend fun getPlayerDomain(): Response<BaseApiResponse<String>>

    @GET("wefeed-h5api-bff/subject/play")
    suspend fun getPlayInfo(
        @Query("subjectId") subjectId: String,
        @Query("se") season: Int = 1,
        @Query("ep") episode: Int = 1
    ): Response<BaseApiResponse<PlayInfoResponseData>>

    @GET("wefeed-h5api-bff/subject/caption")
    suspend fun getCaptions(
        @Query("subjectId") subjectId: String,
        @Query("id") id: String = subjectId,
        @Query("se") season: Int = 1,
        @Query("ep") episode: Int = 1
    ): Response<BaseApiResponse<CaptionsResponseData>>
}
