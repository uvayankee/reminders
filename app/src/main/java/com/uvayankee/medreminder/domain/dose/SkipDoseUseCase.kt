package com.uvayankee.medreminder.domain.dose

import com.uvayankee.medreminder.db.AlarmDao
import com.uvayankee.medreminder.db.DoseStatus

class SkipDoseUseCase(
    private val alarmDao: AlarmDao
) {
    /**
     * Marks the specified doses as SKIPPED.
     */
    suspend operator fun invoke(doseIds: LongArray) {
        doseIds.forEach { id ->
            val dose = alarmDao.getDoseById(id) ?: return@forEach
            alarmDao.updateDoseLog(dose.copy(
                status = DoseStatus.SKIPPED,
                actualTime = System.currentTimeMillis()
            ))
        }
    }
}
