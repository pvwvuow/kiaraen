package com.kiaraen.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kiaraen.app.MainActivity
import com.kiaraen.app.R
import com.kiaraen.app.data.local.AppPrefs
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * یادآور یادگیری — the approved reminder screen backed by a real notification.
 * Strategy: a cheap periodic worker (every ~30 min) checks the persisted
 * settings (enabled? selected weekday? not already reminded today) and posts
 * the reminder. No SCHEDULE_EXACT_ALARM permission needed; ±30 min accuracy
 * is exactly right for a study nudge. TEST payload posts immediately.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "kiara_reminder"
    private const val PERIODIC = "kiara-reminder-periodic"
    private const val TEST = "kiara-reminder-test"
    const val KEY_TEST = "test"
    private const val PREF_LAST_DAY = "remind_last_day"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "یادآور یادگیری",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "یادآوری روزانه برای حفظ کردن واژه‌های نو" },
            )
        }
    }

    /** Keep the periodic checker alive; call after every settings change. */
    fun reschedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES).build(),
        )
    }

    fun postTest(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            TEST,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf(KEY_TEST to true))
                .build(),
        )
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    internal fun post(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 11, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ki_flame)
            .setContentTitle("وقت کیاراست! 🌟")
            .setContentText("۱۰ دقیقه وقت بذار، هدف امروزت رو کامل کن.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).notify(1001, notif)
        }
    }

    internal fun alreadyRemindedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences("kiara_reminder", Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay().toInt()
        if (prefs.getInt(PREF_LAST_DAY, -1) == today) return true
        prefs.edit().putInt(PREF_LAST_DAY, today).apply()
        return false
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val isTest = inputData.getBoolean(ReminderScheduler.KEY_TEST, false)
            val prefs = AppPrefs(ctx)
            val enabled = prefs.remindEnabled.first()
            if (isTest) {
                if (enabled || ReminderScheduler.hasNotificationPermission(ctx)) {
                    ReminderScheduler.post(ctx)
                }
                return Result.success()
            }
            if (!enabled) return Result.success()

            val today = LocalDate.now()
            val days = prefs.remindDays.first()
            val isoName = today.dayOfWeek.getDisplayDay()
            if (isoName !in days) return Result.success()

            // reminder window: settings time .. +2h (periodic check lands inside)
            val at = LocalTime.of(prefs.remindHour.first(), prefs.remindMinute.first())
            val now = LocalDateTime.now().toLocalTime()
            val inWindow = !now.isBefore(at) && Duration.between(at, now).toMinutes() <= 120
            if (!inWindow) return Result.success()

            if (!ReminderScheduler.alreadyRemindedToday(ctx)) ReminderScheduler.post(ctx)
            Result.success()
        } catch (t: Throwable) {
            Result.success() // a reminder failure must never surface to the user
        }
    }
}

private fun DayOfWeek.getDisplayDay(): String = getDisplayName(TextStyle.FULL, Locale.ENGLISH)
