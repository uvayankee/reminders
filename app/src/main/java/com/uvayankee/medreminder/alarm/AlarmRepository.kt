package com.uvayankee.medreminder.alarm

import android.util.Log
import com.uvayankee.medreminder.db.*
import java.util.Calendar
import java.util.TimeZone

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler
) {
    suspend fun scheduleInitialAlarms() {
        if (alarmDao.getActivePrescriptions().isEmpty()) {
            setupTestData()
        }
        // Alarms are now scheduled reactively by DoseLogObserver
    }

    fun refreshNotifications() {
        alarmScheduler.triggerImmediateNotificationUpdate()
    }

    suspend fun generateUpcomingDosesForPrescription(pId: Long) {
        Log.i("AlarmRepository", "generateUpcomingDosesForPrescription: pId=$pId")
        alarmDao.clearPendingDosesForPrescription(pId)
        ensureFutureDosesScheduled(pId, force = true)
    }

    suspend fun generateFutureDosesForAll() {
        val prescriptions = alarmDao.getActivePrescriptions()
        Log.i("AlarmRepository", "generateFutureDosesForAll: Found ${prescriptions.size} active prescriptions")
        prescriptions.forEach { 
            ensureFutureDosesScheduled(it.id, force = false)
        }
    }

    suspend fun ensureFutureDosesScheduled(pId: Long, force: Boolean = false) {
        val prescription = alarmDao.getPrescriptionById(pId) ?: return
        val times = alarmDao.getActiveTimeSchedulesForPrescription(pId)
        val lastScheduled = if (force) null else alarmDao.getLastScheduledDoseTime(pId)
        val startFrom = lastScheduled ?: (prescription.startDate - 86400000)
        
        val horizon = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
        
        Log.i("AlarmRepository", "ensureFutureDosesScheduled: pId=$pId, startFrom=$startFrom, horizon=$horizon, force=$force")
        
        if (startFrom < horizon) {
            val startCal = Calendar.getInstance().apply { 
                timeInMillis = startFrom
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val endCal = Calendar.getInstance().apply { 
                timeInMillis = horizon
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
            }
            
            val currentCal = Calendar.getInstance().apply { timeInMillis = startCal.timeInMillis }
            while (currentCal.before(endCal)) {
                for (time in times) {
                    scheduleDoseIfMissing(pId, time.reminderTimeMinutes, currentCal, time.dosage)
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    private suspend fun setupTestData() {
        val pId = alarmDao.insertPrescription(Prescription(
            name = "MVP Test Med",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
        ))

        alarmDao.insertTimeSchedule(TimeSchedule(prescriptionId = pId, reminderTimeMinutes = 390, dosage = 1.0f))
        alarmDao.insertTimeSchedule(TimeSchedule(prescriptionId = pId, reminderTimeMinutes = 840, dosage = 2.0f))
        alarmDao.insertTimeSchedule(TimeSchedule(prescriptionId = pId, reminderTimeMinutes = 1320, dosage = 1.0f))

        alarmDao.insertSettings(Settings(reNotifyIntervalMinutes = 5))
        
        generateUpcomingDosesForPrescription(pId)
    }

    private suspend fun scheduleDoseIfMissing(pId: Long, timeMinutes: Int, date: Calendar, dosage: Float) {
        val scheduledTime = Calendar.getInstance().apply {
            timeInMillis = date.timeInMillis
            set(Calendar.HOUR_OF_DAY, timeMinutes / 60)
            set(Calendar.MINUTE, timeMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (scheduledTime >= todayStart) {
            val existingDose = alarmDao.getDoseByPrescriptionAndScheduledTime(pId, scheduledTime)
            if (existingDose == null) {
                Log.i("AlarmRepository", "scheduleDoseIfMissing: Attempting insert for pId=$pId, time=$scheduledTime")
                alarmDao.insertDoseLog(DoseLog(
                    prescriptionId = pId,
                    scheduledTime = scheduledTime,
                    reminderTimeMinutes = timeMinutes,
                    dosage = dosage
                ))
            } else {
                Log.i("AlarmRepository", "scheduleDoseIfMissing: Dose already exists for pId=$pId, time=$scheduledTime")
            }
        }
    }
}
