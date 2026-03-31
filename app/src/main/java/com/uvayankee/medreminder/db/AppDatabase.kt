package com.uvayankee.medreminder.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromDoseStatus(status: DoseStatus): String = status.name

    @TypeConverter
    fun toDoseStatus(name: String): DoseStatus = DoseStatus.valueOf(name)
}

@Database(
    entities = [
        Prescription::class,
        TimeSchedule::class,
        DoseLog::class,
        Settings::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
