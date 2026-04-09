package com.uvayankee.medreminder.domain.dose

import com.uvayankee.medreminder.db.AlarmDao
import com.uvayankee.medreminder.db.DoseStatus

class TakeDoseUseCase(
    private val alarmDao: AlarmDao
) {
    /**
     * Marks the specified doses as TAKEN.
     */
    suspend operator fun invoke(doseIds: LongArray) {
        doseIds.forEach { id ->
            val dose = alarmDao.getDoseById(id) ?: return@forEach
            alarmDao.updateDoseLog(dose.copy(
                status = DoseStatus.TAKEN,
                actualTime = System.currentTimeMillis()
            ))
        }
    }
}
