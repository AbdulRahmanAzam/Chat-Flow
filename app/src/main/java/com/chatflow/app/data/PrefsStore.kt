package com.chatflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chatflow_prefs")

class PrefsStore(private val context: Context) {
    private val KEY_NICKNAME = stringPreferencesKey("nickname")
    private val KEY_THEME = stringPreferencesKey("theme")

    val nickname: Flow<String> = context.dataStore.data.map { it[KEY_NICKNAME] ?: "" }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "system" }

    suspend fun setNickname(name: String) {
        context.dataStore.edit { it[KEY_NICKNAME] = name }
    }

    suspend fun setTheme(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }
}
