package com.uvayankee.medreminder

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.alarm.AlarmScheduler
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [30])
class DoseActionIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var alarmDao: AlarmDao
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var prescriptionRepository: PrescriptionRepository
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: org.robolectric.shadows.ShadowAlarmManager

    @Before
    fun setup() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        alarmDao = db.alarmDao()

        val scheduler = AlarmScheduler(context)
        alarmRepository = AlarmRepository(alarmDao, scheduler)
        prescriptionRepository = PrescriptionRepository(alarmDao, alarmRepository)

        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = Shadows.shadowOf(alarmManager)
        
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
    }

    @After
    fun teardown() {
        db.close()
        stopKoin()
    }

    @Test
    fun `Given a pending dose, when I Take it, then DB status updates AND next alarm schedules`() = runBlocking {
        // GIVEN: A prescription with two doses today
        val now = System.currentTimeMillis()
        val pId = alarmDao.insertPrescription(Prescription(name = "BDD Med", startDate = 0, endDate = Long.MAX_VALUE))
        
        val currentDoseTime = now + 10000 // 10 seconds from now
        val nextDoseTime = now + 3600000 // 1 hour from now

        val currentDoseId = alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = currentDoseTime, reminderTimeMinutes = 0))
        alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = nextDoseTime, reminderTimeMinutes = 60))

        // Ensure alarm is scheduled for the current dose
        alarmRepository.reScheduleNextAlarm()
        var scheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Initial alarm should be scheduled", scheduledAlarm)
        assertEquals("Alarm should be set for current dose time", currentDoseTime, scheduledAlarm!!.triggerAtTime)

        // WHEN: I Take the current dose
        alarmRepository.takeDose(currentDoseId)

        // THEN: The DB status is TAKEN
        val updatedDose = alarmDao.getDoseById(currentDoseId)
        assertEquals(DoseStatus.TAKEN, updatedDose?.status)

        // AND: The next alarm is scheduled for the next dose
        scheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Next alarm should be scheduled", scheduledAlarm)
        assertEquals("Alarm should be set for the next dose time", nextDoseTime, scheduledAlarm!!.triggerAtTime)
    }

    @Test
    fun `Given a pending dose, when I Snooze it, then DB status is SNOOZED AND alarm reschedules`() = runBlocking {
        val now = System.currentTimeMillis()
        val pId = alarmDao.insertPrescription(Prescription(name = "Snooze Med", startDate = 0, endDate = Long.MAX_VALUE))
        
        val currentDoseId = alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = now, reminderTimeMinutes = 0))
        alarmRepository.reScheduleNextAlarm()

        // WHEN: I Snooze it for 15 minutes
        alarmRepository.snoozeDose(currentDoseId, 15)

        // THEN: DB status is SNOOZED
        val updatedDose = alarmDao.getDoseById(currentDoseId)
        assertEquals(DoseStatus.SNOOZED, updatedDose?.status)
        val expectedSnoozeTime = now + (15 * 60 * 1000)
        val diff = Math.abs(updatedDose!!.scheduledTime - expectedSnoozeTime)
        assertTrue("Snooze time difference too large: $diff", diff < 1000)

        // AND: Alarm is rescheduled for the snooze time
        val scheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Snoozed alarm should be scheduled", scheduledAlarm)
        val alarmDiff = Math.abs(scheduledAlarm!!.triggerAtTime - expectedSnoozeTime)
        assertTrue("Alarm time difference too large", alarmDiff < 1000)
    }

    @Test
    fun `Given a pending dose, when I Skip it, then DB status is SKIPPED AND alarm reschedules`() = runBlocking {
        val now = System.currentTimeMillis()
        val pId = alarmDao.insertPrescription(Prescription(name = "Skip Med", startDate = 0, endDate = Long.MAX_VALUE))
        
        val currentDoseTime = now + 10000
        val nextDoseTime = now + 3600000

        val currentDoseId = alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = currentDoseTime, reminderTimeMinutes = 0))
        alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = nextDoseTime, reminderTimeMinutes = 60))
        alarmRepository.reScheduleNextAlarm()

        // WHEN: I Skip it
        alarmRepository.skipDose(currentDoseId)

        // THEN: DB status is SKIPPED
        val updatedDose = alarmDao.getDoseById(currentDoseId)
        assertEquals(DoseStatus.SKIPPED, updatedDose?.status)

        // AND: Alarm is rescheduled for the next dose
        val scheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Next alarm should be scheduled after skip", scheduledAlarm)
        assertEquals("Alarm should be set for the next dose time", nextDoseTime, scheduledAlarm!!.triggerAtTime)
    }
}
