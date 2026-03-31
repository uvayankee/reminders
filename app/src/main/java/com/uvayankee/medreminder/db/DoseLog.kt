package com.uvayankee.medreminder.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DoseStatus {
    PENDING, TAKEN, SKIPPED, SNOOZED
}

@Entity(
    tableName = "dose_log",
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
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prescriptionId: Long,
    val scheduledTime: Long, // UTC Milliseconds
    val actualTime: Long? = null,
    val status: DoseStatus = DoseStatus.PENDING,
    val reminderTimeMinutes: Int, // The time of day this was scheduled for
    val dosage: Float = 1.0f
)
