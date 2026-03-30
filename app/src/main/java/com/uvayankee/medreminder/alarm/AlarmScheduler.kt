package com.uvayankee.medreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(timeMillis: Long) {
        setAlarm(timeMillis, ALARM_ID)
        Log.d("AlarmScheduler", "Scheduled main alarm for $timeMillis")
    }

    fun scheduleReNotify(timeMillis: Long) {
        setAlarm(timeMillis, RENOTIFY_ID)
        Log.d("AlarmScheduler", "Scheduled re-notify alarm for $timeMillis")
    }

    private fun setAlarm(timeMillis: Long, requestCode: Int) {
        Log.d("AlarmScheduler", "setAlarm called for $timeMillis, requestCode=$requestCode")
        if (timeMillis <= System.currentTimeMillis()) {
            Log.w("AlarmScheduler", "Attempted to schedule alarm in the past ($timeMillis). Skipping.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("AlarmScheduler", "Missing SCHEDULE_EXACT_ALARM permission")
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ALARM_TIME", timeMillis)
            putExtra("EXTRA_IS_RE_NOTIFY", requestCode == RENOTIFY_ID)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeMillis,
            pendingIntent
        )
    }

    fun cancelAlarm() {
        cancel(ALARM_ID)
    }

    fun cancelReNotify() {
        cancel(RENOTIFY_ID)
    }

    private fun cancel(requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val ALARM_ID = 1001
        const val RENOTIFY_ID = 1002
    }
}
