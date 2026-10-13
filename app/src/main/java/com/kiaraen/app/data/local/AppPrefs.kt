package com.kiaraen.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.kiaraDataStore by preferencesDataStore(name = "kiara_prefs")

/**
 * Tiny key-value settings. Guest mode remembers "ادامه بدون حساب" so the user
 * lands straight in the app on the next launch instead of the auth gate.
 *
 * v0.4.0 additions mirror the approved prototype screens: ویرایش پروفایل
 * (name/avatar)، یادآور (time/days/smart)، تنظیمات (sounds/speed/meaning)،
 * و «مجموعه‌های من» (marked collection ids).
 */
class AppPrefs(private val context: Context) {

    private val store get() = context.kiaraDataStore

    val guestMode: Flow<Boolean> get() = store.data.map { it[KEY_GUEST] ?: false }

    suspend fun isGuest(): Boolean = guestMode.first()

    suspend fun setGuestMode(on: Boolean) {
        store.edit { it[KEY_GUEST] = on }
    }

    /** Daily word goal (هدف روزانه) — shown in the «من» tab + Goal screen. */
    val dailyGoal: Flow<Int> get() = store.data.map { it[KEY_GOAL] ?: 15 }

    suspend fun setDailyGoal(n: Int) {
        store.edit { it[KEY_GOAL] = n }
    }

    // ── ویرایش پروفایل ──────────────────────────────────────────────────────
    val displayName: Flow<String> get() = store.data.map { it[KEY_NAME] ?: "" }
    val avatarIndex: Flow<Int> get() = store.data.map { it[KEY_AVATAR] ?: 0 }

    suspend fun setDisplayName(name: String) {
        store.edit { it[KEY_NAME] = name.trim() }
    }

    suspend fun setAvatarIndex(i: Int) {
        store.edit { it[KEY_AVATAR] = i.coerceIn(0, 2) }
    }

    // ── یادآور یادگیری ──────────────────────────────────────────────────────
    val remindEnabled: Flow<Boolean> get() = store.data.map { it[KEY_REMIND_ON] ?: false }
    val remindHour: Flow<Int> get() = store.data.map { it[KEY_REMIND_H] ?: 20 }
    val remindMinute: Flow<Int> get() = store.data.map { it[KEY_REMIND_M] ?: 0 }
    /** Selected weekdays as ISO names (MONDAY..SUNDAY). */
    val remindDays: Flow<Set<String>> get() = store.data.map { it[KEY_REMIND_DAYS] ?: DEFAULT_DAYS }
    val remindSmart: Flow<Boolean> get() = store.data.map { it[KEY_REMIND_SMART] ?: false }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int, days: Set<String>, smart: Boolean) {
        store.edit {
            it[KEY_REMIND_ON] = enabled
            it[KEY_REMIND_H] = hour.coerceIn(0, 23)
            it[KEY_REMIND_M] = minute.coerceIn(0, 59)
            it[KEY_REMIND_DAYS] = days
            it[KEY_REMIND_SMART] = smart
        }
    }

    // ── تنظیمات ─────────────────────────────────────────────────────────────
    val soundsOn: Flow<Boolean> get() = store.data.map { it[KEY_SOUNDS] ?: true }
    val speechRate: Flow<String> get() = store.data.map { it[KEY_RATE] ?: "normal" } // slow|normal|fast
    val showMeaningFirst: Flow<Boolean> get() = store.data.map { it[KEY_MEANING_FIRST] ?: false }

    suspend fun setSoundsOn(on: Boolean) = store.edit { it[KEY_SOUNDS] = on }
    suspend fun setSpeechRate(rate: String) = store.edit { it[KEY_RATE] = rate }
    suspend fun setShowMeaningFirst(on: Boolean) = store.edit { it[KEY_MEANING_FIRST] = on }

    // ── مجموعه‌های من (marked in «همه مجموعه‌ها») ───────────────────────────
    val markedCollections: Flow<Set<String>> get() = store.data.map { it[KEY_MARKED] ?: emptySet() }

    suspend fun toggleMarkedCollection(id: Long): Boolean {
        var added = false
        store.edit {
            val cur = it[KEY_MARKED] ?: emptySet()
            val key = id.toString()
            added = key !in cur
            it[KEY_MARKED] = if (added) cur + key else cur - key
        }
        return added
    }

    // ── تم تاریک (WebView UI) — persisted so cold start paints the right chrome ──
    suspend fun isWebDark(): Boolean = store.data.first().let { it[KEY_WEB_DARK] ?: false }

    suspend fun setWebDark(on: Boolean) {
        store.edit { it[KEY_WEB_DARK] = on }
    }

    private companion object {
        val KEY_GUEST = booleanPreferencesKey("guest_mode")
        val KEY_GOAL = intPreferencesKey("daily_goal")
        val KEY_NAME = stringPreferencesKey("display_name")
        val KEY_AVATAR = intPreferencesKey("avatar_index")
        val KEY_REMIND_ON = booleanPreferencesKey("remind_enabled")
        val KEY_REMIND_H = intPreferencesKey("remind_hour")
        val KEY_REMIND_M = intPreferencesKey("remind_minute")
        val KEY_REMIND_DAYS = stringSetPreferencesKey("remind_days")
        val KEY_REMIND_SMART = booleanPreferencesKey("remind_smart")
        val KEY_SOUNDS = booleanPreferencesKey("sounds_on")
        val KEY_RATE = stringPreferencesKey("speech_rate")
        val KEY_MEANING_FIRST = booleanPreferencesKey("meaning_first")
        val KEY_MARKED = stringSetPreferencesKey("marked_collections")
        val KEY_WEB_DARK = booleanPreferencesKey("web_dark")

        val DEFAULT_DAYS = java.time.DayOfWeek.entries.map { it.name }.toSet()
    }
}
