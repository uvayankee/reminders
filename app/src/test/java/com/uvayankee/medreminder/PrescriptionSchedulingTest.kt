package com.uvayankee.medreminder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.alarm.AlarmScheduler
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class PrescriptionSchedulingTest {
    private lateinit var db: AppDatabase
    private lateinit var alarmDao: AlarmDao
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var prescriptionRepository: PrescriptionRepository

    @Before
    fun createDb() {
        stopKoin() // Ensure clean state
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Initialize WorkManager for tests
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        alarmDao = db.alarmDao()
        
        val scheduler = AlarmScheduler(context)
        alarmRepository = AlarmRepository(alarmDao, scheduler)
        prescriptionRepository = PrescriptionRepository(alarmDao, alarmRepository)
    }

    @After
    fun closeDb() {
        db.close()
        stopKoin()
    }

    @Test
    fun testNewPrescriptionSchedulesDoses() = runBlocking {
        val newPrescription = Prescription(
            name = "Test Med",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000
        )
        
        val times = listOf(
            TimeSchedule(prescriptionId = 0, reminderTimeMinutes = 840) // 2:00 PM
        )

        prescriptionRepository.savePrescription(newPrescription, times)

        val allPrescriptions = prescriptionRepository.getAllPrescriptions().first()
        assertEquals("Should have 1 prescription", 1, allPrescriptions.size)
        val savedId = allPrescriptions[0].id
        assertTrue("Saved ID should be greater than 0", savedId > 0)

        val doses = alarmDao.getDosesForDayFlow(0, Long.MAX_VALUE).first()
        assertTrue("DoseLog should not be empty for a new prescription", doses.isNotEmpty())
        assertEquals("Dose should be linked to the saved prescription ID", savedId, doses[0].prescriptionId)
    }

    @Test
    fun testOverdueDoseDoesNotCauseAlarmLoop() = runBlocking {
        val pId = alarmDao.insertPrescription(Prescription(name = "Old Med", startDate = 0, endDate = Long.MAX_VALUE))
        val overdueTime = System.currentTimeMillis() - 3600000 // 1 hour ago
        alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = overdueTime, reminderTimeMinutes = 0))

        alarmRepository.reScheduleNextAlarm()

        val nextFuture = alarmDao.getNextFutureDose(System.currentTimeMillis())
        assertEquals("Should find no future doses", null, nextFuture)
    }

    @Test
    fun testSnoozeReschedulesAlarm() = runBlocking {
        // GIVEN: A dose scheduled for now
        val pId = alarmDao.insertPrescription(Prescription(name = "Snooze Med", startDate = 0, endDate = Long.MAX_VALUE))
        val now = System.currentTimeMillis()
        val doseId = alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = now, reminderTimeMinutes = 0))

        // WHEN: Snoozing for 15 minutes
        alarmRepository.snoozeDose(doseId, 15)

        // THEN: getNextFutureDose should find the snoozed dose at the new time
        val nextFuture = alarmDao.getNextFutureDose(now)
        assertTrue("Should find a future dose after snooze", nextFuture != null)
        assertEquals(DoseStatus.SNOOZED, nextFuture?.status)
        assertTrue("Scheduled time should be in the future", nextFuture!!.scheduledTime > now)
    }

    @Test
    fun testDeletePrescriptionCleansUpDoses() = runBlocking {
        // GIVEN: A prescription with doses
        val p = Prescription(name = "Delete Me", startDate = System.currentTimeMillis(), endDate = Long.MAX_VALUE)
        val times = listOf(TimeSchedule(prescriptionId = 0, reminderTimeMinutes = 840))
        prescriptionRepository.savePrescription(p, times)
        
        val allPrescriptions = prescriptionRepository.getAllPrescriptions().first()
        assertEquals(1, allPrescriptions.size)
        val savedPrescription = allPrescriptions[0]
        
        val dosesBefore = alarmDao.getDosesForDayFlow(0, Long.MAX_VALUE).first()
        assertTrue("Should have doses before deletion", dosesBefore.isNotEmpty())

        // WHEN: Deleting the prescription
        prescriptionRepository.deletePrescription(savedPrescription)

        // THEN: All related data should be gone
        val allPrescriptionsAfter = prescriptionRepository.getAllPrescriptions().first()
        assertEquals("Prescription should be deleted", 0, allPrescriptionsAfter.size)

        val dosesAfter = alarmDao.getDosesForDayFlow(0, Long.MAX_VALUE).first()
        assertTrue("Doses should be deleted by cascade", dosesAfter.isEmpty())
        
        val schedulesAfter = alarmDao.getActiveTimeSchedulesForPrescription(savedPrescription.id)
        assertTrue("Time schedules should be deleted by cascade", schedulesAfter.isEmpty())
    }

    @Test
    fun testEnsureFutureDosesScheduledDoesNotCreateDuplicates() = runBlocking {
        val newPrescription = Prescription(
            name = "Duplicate Med Test",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000
        )
        val times = listOf(
            TimeSchedule(prescriptionId = 0, reminderTimeMinutes = 480) // 8:00 AM
        )

        // Schedule it the first time
        prescriptionRepository.savePrescription(newPrescription, times)

        val allPrescriptions = prescriptionRepository.getAllPrescriptions().first()
        val pId = allPrescriptions[0].id

        val initialDoses = alarmDao.getDosesForDayFlow(0, Long.MAX_VALUE).first()
        val initialSize = initialDoses.size
        assertTrue("Should have some doses generated initially", initialSize > 0)

        // Run ensureFutureDosesScheduled manually, multiple times
        alarmRepository.ensureFutureDosesScheduled(pId, force = false)
        alarmRepository.ensureFutureDosesScheduled(pId, force = false)
        alarmRepository.ensureFutureDosesScheduled(pId, force = false)

        val finalDoses = alarmDao.getDosesForDayFlow(0, Long.MAX_VALUE).first()
        assertEquals("Duplicate doses should not have been created", initialSize, finalDoses.size)
    }
}
