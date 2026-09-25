package com.ayman.smartiptv.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("user_info") val userInfo: UserInfo?
)

data class UserInfo(
    @SerializedName("auth") val auth: Int?,
    @SerializedName("status") val status: String?
)

data class Category(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String
)

// عنصر عام يغطي القنوات الحية وأفلام الـ VOD (الحقول الزائدة تُتجاهل تلقائيًا)
data class StreamItem(
    @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("series_id") val seriesId: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null
) {
    val id: Int get() = streamId ?: seriesId ?: 0
    val displayName: String get() = name ?: title ?: "Unknown"
    val posterUrl: String? get() = streamIcon ?: cover
}

data class SeriesInfoResponse(
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>?
)

data class Episode(
    @SerializedName("id") val id: String,
    @SerializedName("episode_num") val episodeNum: Int?,
    @SerializedName("container_extension") val containerExtension: String? = "mp4"
)

enum class ContentType { LIVE, VOD, SERIES }

// حالة استئناف المشاهدة — تُخزَّن محليًا عبر DataStore
data class ResumeEntry(
    val storageKey: String,
    val name: String,
    val icon: String?,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long,
    val type: ContentType,
    val ext: String,
    val itemId: Int
)
