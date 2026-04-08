package com.uvayankee.medreminder.alarm

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
}
