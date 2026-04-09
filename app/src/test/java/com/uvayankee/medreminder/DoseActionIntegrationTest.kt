package com.uvayankee.medreminder

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.alarm.AlarmScheduler
import com.uvayankee.medreminder.alarm.DoseLogObserver
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.db.*
import com.uvayankee.medreminder.domain.dose.SkipDoseUseCase
import com.uvayankee.medreminder.domain.dose.SnoozeDoseUseCase
import com.uvayankee.medreminder.domain.dose.TakeDoseUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DoseActionIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var alarmDao: AlarmDao
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var prescriptionRepository: PrescriptionRepository
    private lateinit var takeDoseUseCase: TakeDoseUseCase
    private lateinit var snoozeDoseUseCase: SnoozeDoseUseCase
    private lateinit var skipDoseUseCase: SkipDoseUseCase
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: org.robolectric.shadows.ShadowAlarmManager
    private val testScope = CoroutineScope(Dispatchers.Default)

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
        
        takeDoseUseCase = TakeDoseUseCase(alarmDao)
        snoozeDoseUseCase = SnoozeDoseUseCase(alarmDao)
        skipDoseUseCase = SkipDoseUseCase(alarmDao)

        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
    }

    @After
    fun teardown() {
        testScope.cancel()
        db.close()
        stopKoin()
    }

    private suspend fun DoseLogObserver.awaitSchedule(expected: Long?, tag: String) {
        println("Waiting for schedule [$tag]: expected=$expected")
        withTimeout(10000) {
            events.filter { it == expected }.first()
        }
        println("Reached schedule [$tag]: $expected")
    }

    @Test
    fun `Given a pending dose, when I Take it, then DB status updates AND next alarm schedules reactively`() = runBlocking {
        val observer = DoseLogObserver(alarmDao, AlarmScheduler(ApplicationProvider.getApplicationContext()), testScope)
        observer.startObserving()
        
        val now = (System.currentTimeMillis() / 1000) * 1000
        observer.advanceTime(now)
        observer.awaitSchedule(null, "Initial idle")
        
        val pId = alarmDao.insertPrescription(Prescription(name = "BDD Med", startDate = 0, endDate = Long.MAX_VALUE))
        val currentDoseTime = now + 10000
        val nextDoseTime = now + 3600000

        alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = nextDoseTime, reminderTimeMinutes = 60))
        alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = currentDoseTime, reminderTimeMinutes = 0))
        
        observer.awaitSchedule(currentDoseTime, "Insert doses")

        var scheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Initial alarm should be scheduled", scheduledAlarm)
        assertEquals("Alarm should be set for current dose time", currentDoseTime, scheduledAlarm!!.triggerAtTime)

        // WHEN: I Take the current dose
        val currentDose = alarmDao.getDoseByPrescriptionAndScheduledTime(pId, currentDoseTime)
        assertNotNull("Dose should exist in DB before taking", currentDose)
        takeDoseUseCase(longArrayOf(currentDose!!.id))
        
        observer.awaitSchedule(nextDoseTime, "Take dose")

        // THEN: The DB status is TAKEN
        val updatedDose = alarmDao.getDoseByPrescriptionAndScheduledTime(pId, currentDoseTime)
        assertEquals(DoseStatus.TAKEN, updatedDose?.status)

        // AND: The next alarm is scheduled reactively
        scheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Next alarm should be scheduled", scheduledAlarm)
        assertEquals("Alarm should be set for the next dose time", nextDoseTime, scheduledAlarm!!.triggerAtTime)
    }

    @Test
    fun `Given a pending dose, when I Snooze it, then DB status is SNOOZED AND alarm reschedules reactively`() = runBlocking {
        val observer = DoseLogObserver(alarmDao, AlarmScheduler(ApplicationProvider.getApplicationContext()), testScope)
        observer.startObserving()

        val now = (System.currentTimeMillis() / 1000) * 1000
        observer.advanceTime(now)
        observer.awaitSchedule(null, "Initial idle")
        
        val pId = alarmDao.insertPrescription(Prescription(name = "Snooze Med", startDate = 0, endDate = Long.MAX_VALUE))
        val currentDoseTime = now + 10000
        val currentDoseId = alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = currentDoseTime, reminderTimeMinutes = 0))
        
        observer.awaitSchedule(currentDoseTime, "Insert dose")

        // WHEN: I Snooze it for 15 minutes
        snoozeDoseUseCase(longArrayOf(currentDoseId), 15)
        
        val updatedDose = alarmDao.getDoseById(currentDoseId)
        val expectedSnoozeTime = updatedDose!!.scheduledTime
        observer.awaitSchedule(expectedSnoozeTime, "Snooze dose")

        // THEN: DB status is SNOOZED
        assertEquals(DoseStatus.SNOOZED, alarmDao.getDoseById(currentDoseId)?.status)
        
        // AND: Alarm is rescheduled reactively
        val nextAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Snoozed alarm should be scheduled", nextAlarm)
        assertEquals("Alarm should be set for the snooze time", expectedSnoozeTime, nextAlarm!!.triggerAtTime)
    }

    @Test
    fun `Given a pending dose, when I Skip it, then DB status is SKIPPED AND alarm reschedules reactively`() = runBlocking {
        val observer = DoseLogObserver(alarmDao, AlarmScheduler(ApplicationProvider.getApplicationContext()), testScope)
        observer.startObserving()

        val now = (System.currentTimeMillis() / 1000) * 1000
        observer.advanceTime(now)
        observer.awaitSchedule(null, "Initial idle")
        
        val pId = alarmDao.insertPrescription(Prescription(name = "Skip Med", startDate = 0, endDate = Long.MAX_VALUE))
        val currentDoseTime = now + 10000
        val nextDoseTime = now + 3600000

        alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = nextDoseTime, reminderTimeMinutes = 60))
        val currentDoseId = alarmDao.insertDoseLog(DoseLog(prescriptionId = pId, scheduledTime = currentDoseTime, reminderTimeMinutes = 0))
        
        observer.awaitSchedule(currentDoseTime, "Insert doses")

        // WHEN: I Skip it
        skipDoseUseCase(longArrayOf(currentDoseId))
        observer.awaitSchedule(nextDoseTime, "Skip dose")

        // THEN: DB status is SKIPPED
        assertEquals(DoseStatus.SKIPPED, alarmDao.getDoseById(currentDoseId)?.status)

        // AND: Alarm is rescheduled reactively
        val nextAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Next alarm should be scheduled after skip", nextAlarm)
        assertEquals("Alarm should be set for the next dose time", nextDoseTime, nextAlarm!!.triggerAtTime)
    }
}
