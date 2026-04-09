package com.uvayankee.medreminder.alarm

import android.util.Log
import com.uvayankee.medreminder.db.AlarmDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DoseLogObserver(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val scope = CoroutineScope(dispatcher)
    private var observationJob: Job? = null
    private val currentTimeFlow = MutableStateFlow(System.currentTimeMillis())
    
    // For test synchronization
    private var nextScheduleSignal = CompletableDeferred<Unit>()

    /**
     * Starts observing the database for the next future dose and automatically schedules the alarm.
     */
    fun startObserving() {
        observationJob?.cancel()
        observationJob = scope.launch {
            currentTimeFlow.collectLatest { now ->
                alarmDao.getNextFutureDoseFlow(now).collectLatest { nextDose ->
                    Log.i("DoseLogObserver", "Database state changed. Next dose: $nextDose")
                    if (nextDose != null) {
                        alarmScheduler.scheduleAlarm(nextDose.scheduledTime)
                    } else {
                        alarmScheduler.cancelAlarm()
                    }
                    
                    // Signal for tests
                    val signal = nextScheduleSignal
                    nextScheduleSignal = CompletableDeferred()
                    signal.complete(Unit)
                }
            }
        }
    }

    suspend fun waitForNextSchedule() {
        nextScheduleSignal.await()
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
