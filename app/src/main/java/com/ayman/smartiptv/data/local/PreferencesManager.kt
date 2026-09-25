package com.ayman.smartiptv.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ayman.smartiptv.data.model.ContentType
import com.ayman.smartiptv.data.model.ResumeEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ayman_iptv_prefs")

class PreferencesManager(private val context: Context) {

    private val gson = Gson()

    companion object {
        val KEY_HOST = stringPreferencesKey("host")
        val KEY_USER = stringPreferencesKey("user")
        val KEY_PASS = stringPreferencesKey("pass")
        val KEY_VIDEO_SCALE = stringPreferencesKey("video_scale")
        val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }

    // ===== Login credentials =====
    // Credentials are encrypted with Android Keystore before they touch DataStore.
    suspend fun saveCredentials(host: String, user: String, pass: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_HOST] = CredentialCipher.encrypt(host)
                prefs[KEY_USER] = CredentialCipher.encrypt(user)
                prefs[KEY_PASS] = CredentialCipher.encrypt(pass)
            }
        } catch (_: Exception) { }
    }

    suspend fun clearCredentials() {
        try {
            // Keep account-scoped favorites/resume history and player preferences.
            context.dataStore.edit { prefs ->
                prefs.remove(KEY_HOST)
                prefs.remove(KEY_USER)
                prefs.remove(KEY_PASS)
            }
        } catch (_: Exception) { }
    }

    suspend fun getSavedHost(): String? = CredentialCipher.decrypt(safeRead(KEY_HOST))
    suspend fun getSavedUser(): String? = CredentialCipher.decrypt(safeRead(KEY_USER))
    suspend fun getSavedPass(): String? = CredentialCipher.decrypt(safeRead(KEY_PASS))

    private suspend fun safeRead(key: Preferences.Key<String>): String? {
        return try {
            context.dataStore.data.map { it[key] }.first()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getVideoScaleMode(): String = safeRead(KEY_VIDEO_SCALE) ?: "ZOOM"

    suspend fun saveVideoScaleMode(mode: String) {
        try {
            context.dataStore.edit { it[KEY_VIDEO_SCALE] = mode }
        } catch (_: Exception) { }
    }

    private fun accountKey(host: String, user: String): String {
        val raw = "$host:$user"
        return raw.hashCode().toString()
    }

    // ===== Favorites =====
    private fun favKey(host: String, user: String, type: ContentType) =
        stringPreferencesKey("favs_${accountKey(host, user)}_${type.name}")

    suspend fun getFavorites(host: String, user: String, type: ContentType): Set<Int> {
        return try {
            val raw = context.dataStore.data.map { it[favKey(host, user, type)] }.first()
            if (raw.isNullOrEmpty()) emptySet()
            else {
                val listType = object : TypeToken<List<Int>>() {}.type
                gson.fromJson<List<Int>>(raw, listType)?.toSet() ?: emptySet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun toggleFavorite(host: String, user: String, type: ContentType, itemId: Int) {
        try {
            val current = getFavorites(host, user, type).toMutableSet()
            if (current.contains(itemId)) current.remove(itemId) else current.add(itemId)
            context.dataStore.edit { prefs ->
                prefs[favKey(host, user, type)] = gson.toJson(current.toList())
            }
        } catch (_: Exception) { }
    }

    // ===== Resume positions =====
    private fun resumeKey(host: String, user: String) =
        stringPreferencesKey("resume_${accountKey(host, user)}")

    suspend fun getResumeMap(host: String, user: String): Map<String, ResumeEntry> {
        return try {
            val raw = context.dataStore.data.map { it[resumeKey(host, user)] }.first()
            if (raw.isNullOrEmpty()) emptyMap()
            else {
                val listType = object : TypeToken<List<ResumeEntry>>() {}.type
                val list: List<ResumeEntry> = gson.fromJson(raw, listType) ?: emptyList()
                val now = System.currentTimeMillis()
                list.filter { now - it.timestamp < THIRTY_DAYS_MS }
                    .associateBy { it.storageKey }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveResumeEntry(host: String, user: String, entry: ResumeEntry) {
        try {
            val current = getResumeMap(host, user).toMutableMap()
            // Treat near-finished content as completed.
            if (entry.durationMs > 0 && entry.positionMs > entry.durationMs - 30_000) {
                current.remove(entry.storageKey)
            } else {
                current[entry.storageKey] = entry
            }
            context.dataStore.edit { prefs ->
                prefs[resumeKey(host, user)] = gson.toJson(current.values.toList())
            }
        } catch (_: Exception) { }
    }
}
