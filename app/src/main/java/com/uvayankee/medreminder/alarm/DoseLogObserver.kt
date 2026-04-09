package com.uvayankee.medreminder.alarm

import android.util.Log
import com.uvayankee.medreminder.db.AlarmDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DoseLogObserver(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val externalScope: CoroutineScope? = null
) {
    private val scope = externalScope ?: CoroutineScope(Dispatchers.Main.immediate)
    private var observationJob: Job? = null
    private val currentTimeFlow = MutableStateFlow(System.currentTimeMillis())
    
    private val _nextScheduledTime = MutableStateFlow<Long?>(null)
    val nextScheduledTime: StateFlow<Long?> = _nextScheduledTime.asStateFlow()

    /**
     * Starts observing the database for the next future dose and automatically schedules the alarm.
     */
    fun startObserving() {
        observationJob?.cancel()
        observationJob = scope.launch {
            currentTimeFlow.collectLatest { now ->
                alarmDao.getNextFutureDoseFlow(now).collectLatest { nextDose ->
                    Log.i("DoseLogObserver", "Database state changed. Next dose: $nextDose")
                    val time = nextDose?.scheduledTime
                    _nextScheduledTime.value = time
                    
                    if (time != null) {
                        alarmScheduler.scheduleAlarm(time)
                    } else {
                        alarmScheduler.cancelAlarm()
                    }
                }
            }
        }
    }

    /**
     * Manually advances the "now" time for the observer. Useful for tests.
     */
    fun advanceTime(newNow: Long) {
        currentTimeFlow.value = newNow
    }

    fun stopObserving() {
        observationJob?.cancel()
    }
}
