package com.uvayankee.medreminder.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "time_schedule",
    foreignKeys = [
        ForeignKey(
            entity = Prescription::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("prescriptionId")]
)
data class TimeSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prescriptionId: Long,
    val reminderTimeMinutes: Int, // Minutes from midnight (e.g., 480 = 8:00 AM)
    val dosage: Float = 1.0f,
    val isAlarmEnabled: Boolean = true,
    val dayOfWeek: Int = 0        // 0: Every day, 1-7: Specific day
)
