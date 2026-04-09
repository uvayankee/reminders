package com.uvayankee.medreminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uvayankee.medreminder.MainActivity
import com.uvayankee.medreminder.db.AlarmDao
import com.uvayankee.medreminder.db.DoseLog
import com.uvayankee.medreminder.db.DoseStatus
import com.uvayankee.medreminder.db.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val alarmDao: AlarmDao by inject()
    private val alarmRepository: AlarmRepository by inject()
    private val alarmScheduler = AlarmScheduler(appContext)

    override suspend fun doWork(): Result {
        Log.d("NotificationWorker", "Processing notifications")
        val now = System.currentTimeMillis()
        val isReNotify = inputData.getBoolean("isReNotify", false)
        val isUpdateOnly = inputData.getBoolean("isUpdateOnly", false)

        // Ensure chain is extended
        alarmRepository.generateFutureDosesForAll()

        // 1. Get overdue doses
        val overdueDoses = alarmDao.getOverdueDoses(now)
        if (overdueDoses.isNotEmpty()) {
            // We alert if it's a fresh alarm or a re-notify nag, but NOT if it's a silent update from user action
            showNotification(overdueDoses, forceAlert = !isUpdateOnly)
            scheduleReNotify()
        } else {
            // No overdue doses, clear notification and stop loop
            cancelNotification()
            alarmScheduler.cancelReNotify()
        }

        // 2. Alarms are now scheduled reactively by DoseLogObserver
        
        return Result.success()
    }

    private suspend fun showNotification(doses: List<DoseLog>, forceAlert: Boolean) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medication_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Medication Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows reminders for medications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Collective Actions
        val doseIds = doses.map { it.id }.toLongArray()
        val medNames = doses.mapNotNull { 
            alarmDao.getPrescriptionByIdImmediate(it.prescriptionId)?.name 
        }.distinct().joinToString(", ")

        val takeIntent = Intent(applicationContext, DoseActionReceiver::class.java).apply {
            action = DoseActionReceiver.ACTION_TAKE
            putExtra("EXTRA_DOSE_IDS", doseIds)
        }
        val takePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            100, // Fixed ID for collective take
            takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent5 = Intent(applicationContext, DoseActionReceiver::class.java).apply {
            action = DoseActionReceiver.ACTION_SNOOZE
            putExtra("EXTRA_DOSE_IDS", doseIds)
            putExtra("EXTRA_SNOOZE_MINUTES", 5)
        }
        val snoozePendingIntent5 = PendingIntent.getBroadcast(
            applicationContext,
            200,
            snoozeIntent5,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent15 = Intent(applicationContext, DoseActionReceiver::class.java).apply {
            action = DoseActionReceiver.ACTION_SNOOZE
            putExtra("EXTRA_DOSE_IDS", doseIds)
            putExtra("EXTRA_SNOOZE_MINUTES", 15)
        }
        val snoozePendingIntent15 = PendingIntent.getBroadcast(
            applicationContext,
            300,
            snoozeIntent15,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.uvayankee.medreminder.R.drawable.ic_stat_notification)
            .setColor(androidx.core.content.ContextCompat.getColor(applicationContext, com.uvayankee.medreminder.R.color.teal_700))
            .setContentTitle("Medication Reminder")
            .setContentText(medNames)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_edit, if (doses.size > 1) "Take All" else "Take", takePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 5m", snoozePendingIntent5)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 15m", snoozePendingIntent15)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(!forceAlert)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private suspend fun scheduleReNotify() {
        val settings = alarmDao.getSettings() ?: Settings()
        val intervalMillis = settings.reNotifyIntervalMinutes.toLong() * 60 * 1000
        val nextTime = System.currentTimeMillis() + intervalMillis

        alarmScheduler.scheduleReNotify(nextTime)
        Log.d("NotificationWorker", "Scheduled re-notify via AlarmManager in ${settings.reNotifyIntervalMinutes} minutes")
    }

    companion object {
        const val NOTIFICATION_ID = 2001
    }
}
