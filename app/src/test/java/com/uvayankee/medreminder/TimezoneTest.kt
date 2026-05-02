package com.uvayankee.medreminder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.alarm.AlarmScheduler
import com.uvayankee.medreminder.db.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class TimezoneTest {
    private lateinit var db: AppDatabase
    private lateinit var alarmDao: AlarmDao
    private lateinit var alarmRepository: AlarmRepository
    private var defaultTz: TimeZone? = null

    @Before
    fun createDb() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        alarmDao = db.alarmDao()
        alarmRepository = AlarmRepository(alarmDao, AlarmScheduler(context))
        defaultTz = TimeZone.getDefault()
    }

    @After
    fun closeDb() {
        db.close()
        stopKoin()
        defaultTz?.let { TimeZone.setDefault(it) }
    }

    @Test
    fun testTimezoneChangeAdjustsDoseTime() = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver")) // MDT/MST
        val pId = alarmDao.insertPrescription(Prescription(name = "Med", startDate = 0, endDate = Long.MAX_VALUE))

        // Let's say we set an alarm for 6:30 PM (18 * 60 + 30 = 1110 minutes)
        val timeMinutes = 1110
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 2)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val origTime = cal.timeInMillis

        val doseId = alarmDao.insertDoseLog(DoseLog(
            prescriptionId = pId,
            scheduledTime = origTime,
            reminderTimeMinutes = timeMinutes,
            status = DoseStatus.PENDING
        ))

        // Change to Central Time
        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))

        alarmRepository.adjustAlarmsForTimezoneChange()

        val dose = alarmDao.getDoseById(doseId)!!
        assertNotEquals(origTime, dose.scheduledTime)

        // It should still be 6:30 PM local time in the new timezone
        val newCal = Calendar.getInstance().apply { timeInMillis = dose.scheduledTime }
        assertEquals(18, newCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, newCal.get(Calendar.MINUTE))
        assertEquals(1110, dose.reminderTimeMinutes)
    }
}
