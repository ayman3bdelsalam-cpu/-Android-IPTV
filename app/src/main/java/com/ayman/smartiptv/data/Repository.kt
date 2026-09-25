package com.ayman.smartiptv.data

import com.ayman.smartiptv.data.api.RetrofitClient
import com.ayman.smartiptv.data.model.*
import kotlinx.coroutines.CancellationException

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

class Repository {

    private val api = RetrofitClient.api

    private fun playerApiUrl(host: String) = "${host.trimEnd('/')}/player_api.php"

    suspend fun login(host: String, user: String, pass: String): ApiResult<LoginResponse> {
        return safeCall {
            val resp = api.login(playerApiUrl(host), user, pass)
            when {
                resp.userInfo == null -> ApiResult.Error("استجابة غير صالحة من السيرفر")
                resp.userInfo.auth == 0 -> ApiResult.Error("بيانات الدخول غير صحيحة")
                resp.userInfo.status != null && resp.userInfo.status != "Active" ->
                    ApiResult.Error("الحساب غير مُفعّل (${resp.userInfo.status})")
                else -> ApiResult.Success(resp)
            }
        }
    }

    suspend fun getCategories(host: String, user: String, pass: String, type: ContentType): ApiResult<List<Category>> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.VOD -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        return safeCall {
            ApiResult.Success(api.getCategories(playerApiUrl(host), user, pass, action))
        }
    }

    suspend fun getStreams(
        host: String, user: String, pass: String, type: ContentType, categoryId: String? = null
    ): ApiResult<List<StreamItem>> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_streams"
            ContentType.VOD -> "get_vod_streams"
            ContentType.SERIES -> "get_series"
        }
        return safeCall {
            ApiResult.Success(api.getStreams(playerApiUrl(host), user, pass, action, categoryId))
        }
    }

    suspend fun getSeriesInfo(host: String, user: String, pass: String, seriesId: Int): ApiResult<SeriesInfoResponse> {
        return safeCall {
            val resp = api.getSeriesInfo(playerApiUrl(host), user, pass, seriesId = seriesId)
            if (resp.episodes.isNullOrEmpty()) ApiResult.Error("لا توجد مواسم لهذا المسلسل")
            else ApiResult.Success(resp)
        }
    }

    /**
     * Xtream servers commonly expose the same live stream as both HLS and MPEG-TS.
     * HLS is tried first because it is broadly compatible; the player can fall back
     * to TS automatically if startup fails.
     */
    fun buildLiveUrls(host: String, user: String, pass: String, streamId: Int): List<String> {
        val base = host.trimEnd('/')
        return listOf(
            "$base/live/$user/$pass/$streamId.m3u8",
            "$base/live/$user/$pass/$streamId.ts"
        )
    }

    fun buildVodUrl(host: String, user: String, pass: String, streamId: Int, ext: String) =
        "${host.trimEnd('/')}/movie/$user/$pass/$streamId.$ext"

    fun buildSeriesUrl(host: String, user: String, pass: String, episodeId: String, ext: String) =
        "${host.trimEnd('/')}/series/$user/$pass/$episodeId.$ext"

    private suspend fun <T> safeCall(block: suspend () -> ApiResult<T>): ApiResult<T> {
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: java.net.SocketTimeoutException) {
            ApiResult.Error("انتهت مهلة الاتصال — تحقق من الشبكة")
        } catch (_: java.net.UnknownHostException) {
            ApiResult.Error("تعذر الوصول للسيرفر — تحقق من الرابط")
        } catch (_: com.google.gson.JsonSyntaxException) {
            ApiResult.Error("استجابة غير صالحة من السيرفر")
        } catch (e: Exception) {
            ApiResult.Error("خطأ غير متوقع: ${e.message ?: "غير معروف"}")
        }
    }
}
