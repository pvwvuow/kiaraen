package com.kiaraen.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.kiaraDataStore by preferencesDataStore(name = "kiara_prefs")

/**
 * Tiny key-value settings. Guest mode remembers "ادامه بدون حساب" so the user
 * lands straight in the app on the next launch instead of the auth gate.
 */
class AppPrefs(private val context: Context) {

    private val store get() = context.kiaraDataStore

    val guestMode: Flow<Boolean> get() = store.data.map { it[KEY_GUEST] ?: false }

    suspend fun isGuest(): Boolean = guestMode.first()

    suspend fun setGuestMode(on: Boolean) {
        store.edit { it[KEY_GUEST] = on }
    }

    /** Daily word goal (هدف روزانه) — shown in the «من» tab. */
    val dailyGoal: Flow<Int> get() = store.data.map { it[KEY_GOAL] ?: 15 }

    suspend fun setDailyGoal(n: Int) {
        store.edit { it[KEY_GOAL] = n }
    }

    private companion object {
        val KEY_GUEST = booleanPreferencesKey("guest_mode")
        val KEY_GOAL = intPreferencesKey("daily_goal")
    }
}
