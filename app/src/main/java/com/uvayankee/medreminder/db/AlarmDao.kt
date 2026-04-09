package com.uvayankee.medreminder.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: Prescription): Long

    @Update
    suspend fun updatePrescription(prescription: Prescription)

    @Delete
    suspend fun deletePrescription(prescription: Prescription)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeSchedule(timeSchedule: TimeSchedule): Long

    @Query("DELETE FROM time_schedule WHERE prescriptionId = :prescriptionId")
    suspend fun deleteTimeSchedulesForPrescription(prescriptionId: Long)

    @Query("DELETE FROM dose_log WHERE prescriptionId = :prescriptionId AND status = 'PENDING'")
    suspend fun clearPendingDosesForPrescription(prescriptionId: Long)

    @Query("SELECT * FROM prescription ORDER BY name ASC")
    fun getAllPrescriptionsFlow(): Flow<List<Prescription>>

    @Query("SELECT * FROM prescription WHERE id = :id")
    suspend fun getPrescriptionById(id: Long): Prescription?

    @Query("SELECT * FROM prescription WHERE isEnabled = 1")
    suspend fun getActivePrescriptions(): List<Prescription>

    @Query("SELECT * FROM time_schedule WHERE prescriptionId = :prescriptionId AND isAlarmEnabled = 1")
    suspend fun getActiveTimeSchedulesForPrescription(prescriptionId: Long): List<TimeSchedule>

    @Transaction
    suspend fun savePrescriptionWithTimes(prescription: Prescription, times: List<TimeSchedule>): Long {
        val pId = if (prescription.id == 0L) {
            insertPrescription(prescription)
        } else {
            updatePrescription(prescription)
            deleteTimeSchedulesForPrescription(prescription.id)
            prescription.id
        }
        
        times.forEach { 
            insertTimeSchedule(it.copy(prescriptionId = pId))
        }
        return pId
    }

    @Query("SELECT * FROM dose_log WHERE (status = 'PENDING' OR status = 'SNOOZED') AND scheduledTime > :now ORDER BY scheduledTime ASC LIMIT 1")
    suspend fun getNextFutureDose(now: Long): DoseLog?

    @Query("SELECT * FROM dose_log WHERE (status = 'PENDING' OR status = 'SNOOZED') AND scheduledTime > :now ORDER BY scheduledTime ASC LIMIT 1")
    fun getNextFutureDoseFlow(now: Long): Flow<DoseLog?>

    @Query("SELECT * FROM dose_log WHERE (status = 'PENDING' OR status = 'SNOOZED') ORDER BY scheduledTime ASC LIMIT 1")
    suspend fun getNextPendingDose(): DoseLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(doseLog: DoseLog): Long

    @Update
    suspend fun updateDoseLog(doseLog: DoseLog)

    @Query("SELECT * FROM dose_log WHERE (status = 'PENDING' OR status = 'SNOOZED') AND scheduledTime <= :now")
    suspend fun getOverdueDoses(now: Long): List<DoseLog>

    @Query("SELECT * FROM dose_log WHERE scheduledTime >= :startOfDay AND scheduledTime <= :endOfDay ORDER BY scheduledTime ASC")
    fun getDosesForDayFlow(startOfDay: Long, endOfDay: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_log WHERE id = :id")
    suspend fun getDoseById(id: Long): DoseLog?

    @Query("SELECT * FROM dose_log WHERE prescriptionId = :prescriptionId AND scheduledTime = :scheduledTime")
    suspend fun getDoseByPrescriptionAndScheduledTime(prescriptionId: Long, scheduledTime: Long): DoseLog?

    @Query("SELECT * FROM prescription WHERE id = :id")
    suspend fun getPrescriptionByIdImmediate(id: Long): Prescription?

    @Query("SELECT MAX(scheduledTime) FROM dose_log WHERE prescriptionId = :prescriptionId")
    suspend fun getLastScheduledDoseTime(prescriptionId: Long): Long?

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: Settings)

    @Transaction
    @Query("SELECT * FROM dose_log WHERE status = 'PENDING' OR status = 'SNOOZED' ORDER BY scheduledTime ASC")
    fun getPendingDosesFlow(): Flow<List<DoseLog>>
}
