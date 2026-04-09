package com.uvayankee.medreminder.domain.prescription

import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.db.AlarmDao
import com.uvayankee.medreminder.db.Prescription

class DeletePrescriptionUseCase(
    private val alarmDao: AlarmDao,
    private val alarmRepository: AlarmRepository
) {
    /**
     * Deletes a prescription and its associated data, then refreshes the alarm system.
     */
    suspend operator fun invoke(prescription: Prescription) {
        alarmDao.deletePrescription(prescription)
        
        alarmRepository.refreshNotifications()
    }
}
