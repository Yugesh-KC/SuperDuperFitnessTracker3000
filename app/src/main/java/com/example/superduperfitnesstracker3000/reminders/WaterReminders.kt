package com.example.superduperfitnesstracker3000.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.superduperfitnesstracker3000.MainActivity
import com.example.superduperfitnesstracker3000.R
import com.example.superduperfitnesstracker3000.data.FitnessDatabase
import com.example.superduperfitnesstracker3000.data.ReminderSettings
import com.example.superduperfitnesstracker3000.data.SettingsRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Water reminders: one alarm at a time is scheduled for the next slot
 * (start, start + interval, … up to end, every day). When it fires we show a
 * notification — skipped if today's goal is already reached — and schedule the next one.
 */
object WaterReminders {
    const val QUICK_ADD_ML = 250
    private const val CHANNEL_ID = "water_reminders"
    private const val NOTIFICATION_ID = 1001
    private const val REQ_ALARM = 1
    private const val REQ_ADD = 2
    private const val REQ_OPEN = 3

    fun schedule(context: Context) {
        val settings = SettingsRepository.get(context).current.reminders
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarmManager.cancel(pending)
        if (!settings.enabled) return
        val next = nextSlot(LocalDateTime.now(), settings)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            // Without the "Alarms & reminders" permission: deliver within 10 minutes of the slot.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60_000L, pending)
        }
    }

    /** Exact timing needs the user-granted "Alarms & reminders" permission on Android 12+. */
    fun canScheduleExact(context: Context) =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** The next reminder time strictly after [now]. */
    fun nextSlot(now: LocalDateTime, s: ReminderSettings): LocalDateTime {
        val interval = s.intervalMinutes.coerceAtLeast(15)
        val end = if (s.endMinute > s.startMinute) s.endMinute else s.startMinute
        for (dayOffset in 0L..1L) {
            val day = now.toLocalDate().plusDays(dayOffset)
            var minute = s.startMinute
            while (minute <= end) {
                val t = day.atStartOfDay().plusMinutes(minute.toLong())
                if (t.isAfter(now)) return t
                minute += interval
            }
        }
        return now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(s.startMinute.toLong())
    }

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, test: Boolean = false) {
        if (!canNotify(context)) return
        val target = SettingsRepository.get(context).current.profile.effectiveWaterTargetMl
        val drunk = FitnessDatabase.get(context).waterTotal(LocalDate.now())
        if (!test && drunk >= target) return
        ensureChannel(context)

        val addIntent = PendingIntent.getBroadcast(
            context, REQ_ADD,
            Intent(context, WaterActionReceiver::class.java).setAction(ACTION_ADD_WATER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TAB, "Water"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val left = (target - drunk).coerceAtLeast(0)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setColor(0xFF3B82F6.toInt())
            .setContentTitle("Time for some water 💧")
            .setContentText("$drunk / $target ml today — $left ml to go")
            .setProgress(target, drunk.coerceAtMost(target), false)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "+$QUICK_ADD_ML ml", addIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Water reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders to drink water during the day"
                }
            )
        }
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQ_ALARM,
        Intent(context, WaterActionReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_REMIND = "com.example.superduperfitnesstracker3000.WATER_REMIND"
    const val ACTION_ADD_WATER = "com.example.superduperfitnesstracker3000.WATER_ADD"
}

/** Handles the alarm firing and the "+250 ml" notification button. */
class WaterActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                when (intent.action) {
                    WaterReminders.ACTION_REMIND -> {
                        WaterReminders.showReminder(context)
                        WaterReminders.schedule(context)
                    }
                    WaterReminders.ACTION_ADD_WATER -> {
                        FitnessDatabase.get(context).insertWater(LocalDate.now(), WaterReminders.QUICK_ADD_ML)
                        WaterReminders.dismiss(context)
                    }
                }
            } finally {
                result.finish()
            }
        }.start()
    }
}

/** Alarms are cleared on reboot, app update and clock changes; put the next one back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterReminders.schedule(context)
    }
}
