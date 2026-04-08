package com.uvayankee.medreminder.domain.dose

import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.db.AlarmDao
import com.uvayankee.medreminder.db.DoseStatus
import com.uvayankee.medreminder.db.Settings

class SnoozeDoseUseCase(
    private val alarmDao: AlarmDao,
    private val alarmRepository: AlarmRepository
) {
    /**
     * Reschedules the specified doses to a later time and updates the next alarm.
     * If [minutes] is null, the default re-notify interval from settings is used.
     */
    suspend operator fun invoke(doseIds: LongArray, minutes: Int? = null) {
        val settings = alarmDao.getSettings() ?: Settings()
        val snoozeMinutes = minutes ?: settings.reNotifyIntervalMinutes
        val newTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)

        doseIds.forEach { id ->
            val dose = alarmDao.getDoseById(id) ?: return@forEach
            alarmDao.updateDoseLog(dose.copy(
                status = DoseStatus.SNOOZED,
                scheduledTime = newTime
            ))
        }
        // TODO: In Phase 5, this will be handled by a reactive observer
        alarmRepository.reScheduleNextAlarm()
    }
}
