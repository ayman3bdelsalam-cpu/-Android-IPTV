package com.ayman.smartiptv.data.api

import com.ayman.smartiptv.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface XtreamApi {

    // تسجيل الدخول / التحقق من الحساب
    @GET
    suspend fun login(
        @Url url: String,
        @Query("username") user: String,
        @Query("password") pass: String
    ): LoginResponse

    @GET
    suspend fun getCategories(
        @Url url: String,
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String
    ): List<Category>

    @GET
    suspend fun getStreams(
        @Url url: String,
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String,
        @Query("category_id") categoryId: String? = null
    ): List<StreamItem>

    @GET
    suspend fun getSeriesInfo(
        @Url url: String,
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): SeriesInfoResponse
}
