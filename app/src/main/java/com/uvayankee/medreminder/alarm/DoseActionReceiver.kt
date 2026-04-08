package com.uvayankee.medreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.uvayankee.medreminder.domain.dose.SnoozeDoseUseCase
import com.uvayankee.medreminder.domain.dose.TakeDoseUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DoseActionReceiver : BroadcastReceiver(), KoinComponent {
    private val takeDoseUseCase: TakeDoseUseCase by inject()
    private val snoozeDoseUseCase: SnoozeDoseUseCase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val doseId = intent.getLongExtra("EXTRA_DOSE_ID", -1)
        val doseIds = intent.getLongArrayExtra("EXTRA_DOSE_IDS")
        val minutes = intent.getIntExtra("EXTRA_SNOOZE_MINUTES", -1)
        
        if (doseId == -1L && doseIds == null) return
        val ids = doseIds ?: longArrayOf(doseId)

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            when (action) {
                ACTION_TAKE -> takeDoseUseCase(ids)
                ACTION_SNOOZE -> {
                    val snoozeMinutes = if (minutes != -1) minutes else null
                    snoozeDoseUseCase(ids, snoozeMinutes)
                }
            }
            
            // Refresh notification state silently
            val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(androidx.work.Data.Builder().putBoolean("isUpdateOnly", true).build())
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)

        }
    }

    companion object {
        const val ACTION_TAKE = "com.uvayankee.medreminder.ACTION_TAKE"
        const val ACTION_SNOOZE = "com.uvayankee.medreminder.ACTION_SNOOZE"
    }
}
