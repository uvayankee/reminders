package com.uvayankee.medreminder.domain.prescription

import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.db.AlarmDao
import com.uvayankee.medreminder.db.Prescription
import com.uvayankee.medreminder.db.TimeSchedule

class SavePrescriptionUseCase(
    private val alarmDao: AlarmDao,
    private val alarmRepository: AlarmRepository
) {
    /**
     * Saves a prescription and its associated time schedules, then refreshes the dose schedule.
     */
    suspend operator fun invoke(prescription: Prescription, times: List<TimeSchedule>) {
        val pId = alarmDao.savePrescriptionWithTimes(prescription, times)
        
        // Refresh alarms and extend chain (force refresh on save)
        alarmRepository.generateUpcomingDosesForPrescription(pId)
        
        // TODO: In Phase 5, this will be handled by a reactive observer
        alarmRepository.reScheduleNextAlarm()
    }
}
