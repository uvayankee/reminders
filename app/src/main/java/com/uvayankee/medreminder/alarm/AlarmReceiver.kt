package com.uvayankee.medreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmTime = intent.getLongExtra("EXTRA_ALARM_TIME", 0)
        val isReNotify = intent.getBooleanExtra("EXTRA_IS_RE_NOTIFY", false)
        Log.d("AlarmReceiver", "Received alarm trigger for $alarmTime (isReNotify: $isReNotify)")

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(androidx.work.Data.Builder().putBoolean("isReNotify", isReNotify).build())
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
