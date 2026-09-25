package com.ayman.smartiptv.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.smartiptv.data.ApiResult
import com.ayman.smartiptv.data.Repository
import com.ayman.smartiptv.data.local.PreferencesManager
import com.ayman.smartiptv.data.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

private data class TimedCache<T>(val data: T, val savedAtMs: Long)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = Repository()
    private val prefs = PreferencesManager(application)

    private val cacheTtlMs = 5 * 60 * 1000L
    private val categoriesCache = mutableMapOf<ContentType, TimedCache<List<Category>>>()
    private val streamsCache = mutableMapOf<String, TimedCache<List<StreamItem>>>()
    private val seriesCache = mutableMapOf<Int, TimedCache<SeriesInfoResponse>>()

    private var categoriesJob: Job? = null
    private var streamsJob: Job? = null
    private var seriesJob: Job? = null
    private var searchJob: Job? = null

    var host: String = ""
        private set
    var user: String = ""
        private set
    var pass: String = ""
        private set

    val loginState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val categoriesState = MutableStateFlow<UiState<List<Category>>>(UiState.Idle)
    val streamsState = MutableStateFlow<UiState<List<StreamItem>>>(UiState.Idle)
    val seriesInfoState = MutableStateFlow<UiState<SeriesInfoResponse>>(UiState.Idle)

    val favoritesLive = MutableStateFlow<Set<Int>>(emptySet())
    val favoritesVod = MutableStateFlow<Set<Int>>(emptySet())
    val favoritesSeries = MutableStateFlow<Set<Int>>(emptySet())
    val resumeMap = MutableStateFlow<Map<String, ResumeEntry>>(emptyMap())

    var currentType: ContentType = ContentType.LIVE
    private var searchBaseStreams: List<StreamItem> = emptyList()

    private fun <T> TimedCache<T>?.isFresh(): Boolean =
        this != null && SystemClock.elapsedRealtime() - savedAtMs <= cacheTtlMs

    fun tryAutoLogin(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val h = prefs.getSavedHost()
            val u = prefs.getSavedUser()
            val p = prefs.getSavedPass()
            if (!h.isNullOrEmpty() && !u.isNullOrEmpty() && !p.isNullOrEmpty()) {
                login(h, u, p) { success -> onResult(success) }
            } else {
                onResult(false)
            }
        }
    }

    fun login(hostInput: String, userInput: String, passInput: String, onResult: (Boolean) -> Unit) {
        var h = hostInput.trim()
        if (h.isEmpty()) {
            loginState.value = UiState.Error("الرجاء إدخال رابط السيرفر")
            onResult(false)
            return
        }
        if (!h.startsWith("http", ignoreCase = true)) h = "http://$h"
        h = h.trimEnd('/')
        val u = userInput.trim()
        val p = passInput.trim()
        if (u.isEmpty() || p.isEmpty()) {
            loginState.value = UiState.Error("الرجاء إدخال اسم المستخدم وكلمة المرور")
            onResult(false)
            return
        }

        loginState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = repo.login(h, u, p)) {
                is ApiResult.Success -> {
                    host = h
                    user = u
                    pass = p
                    prefs.saveCredentials(h, u, p)
                    clearNetworkCaches()
                    loadAllFavorites()
                    loadResumeMap()
                    loginState.value = UiState.Success(Unit)
                    onResult(true)
                }
                is ApiResult.Error -> {
                    loginState.value = UiState.Error(result.message)
                    onResult(false)
                }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.clearCredentials()
            host = ""
            user = ""
            pass = ""
            clearNetworkCaches()
            loginState.value = UiState.Idle
            categoriesState.value = UiState.Idle
            streamsState.value = UiState.Idle
            seriesInfoState.value = UiState.Idle
            onDone()
        }
    }

    private fun clearNetworkCaches() {
        categoriesCache.clear()
        streamsCache.clear()
        seriesCache.clear()
        searchBaseStreams = emptyList()
    }

    private suspend fun loadAllFavorites() {
        favoritesLive.value = prefs.getFavorites(host, user, ContentType.LIVE)
        favoritesVod.value = prefs.getFavorites(host, user, ContentType.VOD)
        favoritesSeries.value = prefs.getFavorites(host, user, ContentType.SERIES)
    }

    private suspend fun loadResumeMap() {
        resumeMap.value = prefs.getResumeMap(host, user)
    }

    fun toggleFavorite(type: ContentType, itemId: Int) {
        viewModelScope.launch {
            prefs.toggleFavorite(host, user, type, itemId)
            when (type) {
                ContentType.LIVE -> favoritesLive.value = prefs.getFavorites(host, user, type)
                ContentType.VOD -> favoritesVod.value = prefs.getFavorites(host, user, type)
                ContentType.SERIES -> favoritesSeries.value = prefs.getFavorites(host, user, type)
            }
        }
    }

    fun isFavorite(type: ContentType, itemId: Int): Boolean = when (type) {
        ContentType.LIVE -> favoritesLive.value.contains(itemId)
        ContentType.VOD -> favoritesVod.value.contains(itemId)
        ContentType.SERIES -> favoritesSeries.value.contains(itemId)
    }

    fun loadCategories(type: ContentType, forceRefresh: Boolean = false) {
        currentType = type
        val cached = categoriesCache[type]
        if (!forceRefresh && cached.isFresh()) {
            categoriesState.value = UiState.Success(cached!!.data)
            return
        }

        categoriesJob?.cancel()
        categoriesState.value = UiState.Loading
        categoriesJob = viewModelScope.launch {
            when (val result = repo.getCategories(host, user, pass, type)) {
                is ApiResult.Success -> {
                    categoriesCache[type] = TimedCache(result.data, SystemClock.elapsedRealtime())
                    categoriesState.value = UiState.Success(result.data)
                }
                is ApiResult.Error -> categoriesState.value = UiState.Error(result.message)
            }
        }
    }

    fun loadStreams(categoryId: String?, onlyFavorites: Boolean = false, forceRefresh: Boolean = false) {
        searchJob?.cancel()
        val key = "${currentType.name}:${categoryId ?: "ALL"}"
        val cached = streamsCache[key]
        if (!forceRefresh && cached.isFresh()) {
            publishStreams(cached!!.data, onlyFavorites)
            return
        }

        streamsJob?.cancel()
        streamsState.value = UiState.Loading
        streamsJob = viewModelScope.launch {
            when (val result = repo.getStreams(host, user, pass, currentType, categoryId)) {
                is ApiResult.Success -> {
                    streamsCache[key] = TimedCache(result.data, SystemClock.elapsedRealtime())
                    publishStreams(result.data, onlyFavorites)
                }
                is ApiResult.Error -> streamsState.value = UiState.Error(result.message)
            }
        }
    }

    private fun publishStreams(raw: List<StreamItem>, onlyFavorites: Boolean) {
        val filtered = if (onlyFavorites) {
            val favSet = when (currentType) {
                ContentType.LIVE -> favoritesLive.value
                ContentType.VOD -> favoritesVod.value
                ContentType.SERIES -> favoritesSeries.value
            }
            raw.filter { favSet.contains(it.id) }
        } else raw

        searchBaseStreams = filtered
        streamsState.value = UiState.Success(filtered)
    }

    fun searchStreams(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) searchBaseStreams
            else searchBaseStreams.filter { it.displayName.lowercase().contains(q) }
            streamsState.value = UiState.Success(filtered)
        }
    }

    fun loadSeriesInfo(seriesId: Int, forceRefresh: Boolean = false) {
        val cached = seriesCache[seriesId]
        if (!forceRefresh && cached.isFresh()) {
            seriesInfoState.value = UiState.Success(cached!!.data)
            return
        }

        seriesJob?.cancel()
        seriesInfoState.value = UiState.Loading
        seriesJob = viewModelScope.launch {
            when (val result = repo.getSeriesInfo(host, user, pass, seriesId)) {
                is ApiResult.Success -> {
                    seriesCache[seriesId] = TimedCache(result.data, SystemClock.elapsedRealtime())
                    seriesInfoState.value = UiState.Success(result.data)
                }
                is ApiResult.Error -> seriesInfoState.value = UiState.Error(result.message)
            }
        }
    }

    fun buildPlaybackUrls(type: ContentType, item: StreamItem): List<String> = when (type) {
        ContentType.LIVE -> repo.buildLiveUrls(host, user, pass, item.id)
        ContentType.VOD -> listOf(repo.buildVodUrl(host, user, pass, item.id, item.containerExtension ?: "mp4"))
        ContentType.SERIES -> item.containerExtension?.let {
            listOf(repo.buildSeriesUrl(host, user, pass, item.id.toString(), it))
        } ?: emptyList()
    }

    fun buildEpisodeUrl(episode: Episode): String =
        repo.buildSeriesUrl(host, user, pass, episode.id, episode.containerExtension ?: "mp4")

    fun saveResume(entry: ResumeEntry) {
        viewModelScope.launch {
            prefs.saveResumeEntry(host, user, entry)
            resumeMap.value = prefs.getResumeMap(host, user)
        }
    }

    fun getResumePosition(storageKey: String): Long =
        resumeMap.value[storageKey]?.positionMs ?: 0L

    suspend fun getSavedVideoScaleMode(): String = prefs.getVideoScaleMode()

    fun saveVideoScaleMode(mode: String) {
        viewModelScope.launch { prefs.saveVideoScaleMode(mode) }
    }
}
