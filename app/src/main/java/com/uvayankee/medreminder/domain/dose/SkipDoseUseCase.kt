package com.uvayankee.medreminder.domain.dose

import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.db.AlarmDao
import com.uvayankee.medreminder.db.DoseStatus

class SkipDoseUseCase(
    private val alarmDao: AlarmDao,
    private val alarmRepository: AlarmRepository
) {
    /**
     * Marks the specified doses as SKIPPED and updates the next alarm.
     */
    suspend operator fun invoke(doseIds: LongArray) {
        doseIds.forEach { id ->
            val dose = alarmDao.getDoseById(id) ?: return@forEach
            alarmDao.updateDoseLog(dose.copy(
                status = DoseStatus.SKIPPED,
                actualTime = System.currentTimeMillis()
            ))
        }
        // TODO: In Phase 5, this will be handled by a reactive observer
        alarmRepository.reScheduleNextAlarm()
    }
}
