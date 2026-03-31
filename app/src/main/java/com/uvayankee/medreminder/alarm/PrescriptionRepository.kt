package com.uvayankee.medreminder.alarm

import android.util.Log
import com.uvayankee.medreminder.db.*
import kotlinx.coroutines.flow.Flow

class PrescriptionRepository(
    private val alarmDao: AlarmDao,
    private val alarmRepository: AlarmRepository
) {
    fun getAllPrescriptions(): Flow<List<Prescription>> = alarmDao.getAllPrescriptionsFlow()

    suspend fun getPrescriptionById(id: Long): Prescription? = alarmDao.getPrescriptionById(id)

    suspend fun getTimesForPrescription(id: Long): List<TimeSchedule> = 
        alarmDao.getActiveTimeSchedulesForPrescription(id)

    fun getDosesForDay(start: Long, end: Long): Flow<List<DoseLog>> =
        alarmDao.getDosesForDayFlow(start, end)

    suspend fun getPrescriptionByIdImmediate(id: Long): Prescription? =
        alarmDao.getPrescriptionByIdImmediate(id)

    suspend fun savePrescription(prescription: Prescription, times: List<TimeSchedule>) {
        val pId = alarmDao.savePrescriptionWithTimes(prescription, times)
        Log.i("PrescriptionRepository", "savePrescription: Saved with pId=$pId, timesCount=${times.size}")
        // Refresh alarms and extend chain (force refresh on save)
        alarmRepository.generateUpcomingDosesForPrescription(pId)
        alarmRepository.reScheduleNextAlarm()
    }

    suspend fun deletePrescription(prescription: Prescription) {
        alarmDao.deletePrescription(prescription)
        alarmRepository.reScheduleNextAlarm()
        alarmRepository.refreshNotifications()
    }
}
