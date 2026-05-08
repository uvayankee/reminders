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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class TimezoneJumpTest {
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
    fun testTimezoneJumpOverAlarm() = runBlocking {
        // Start in America/Denver (MT)
        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
        val pId = alarmDao.insertPrescription(Prescription(name = "Med", startDate = 0, endDate = Long.MAX_VALUE))

        // Alarm at 2:00 PM (14 * 60 = 840 minutes)
        val timeMinutes = 840
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 2)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
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

        // Timezone changes to America/Chicago (CT) at 1:30 PM MT (which is 2:30 PM CT)
        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))

        alarmRepository.adjustAlarmsForTimezoneChange()

        val dose = alarmDao.getDoseById(doseId)!!

        // The scheduled time should still represent 2:00 PM local time in CT
        val newCal = Calendar.getInstance().apply { timeInMillis = dose.scheduledTime }
        assertEquals(14, newCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, newCal.get(Calendar.MINUTE))
        assertEquals(840, dose.reminderTimeMinutes)

        // Now, we simulate "now" being 2:30 PM CT
        val nowCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 2)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.timeInMillis

        // Since scheduledTime is 2:00 PM CT and now is 2:30 PM CT, it should be considered overdue
        val overdueDoses = alarmDao.getOverdueDoses(now)
        assertTrue(overdueDoses.any { it.id == doseId })
    }
}
