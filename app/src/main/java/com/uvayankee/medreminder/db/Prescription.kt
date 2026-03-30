package com.uvayankee.medreminder.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prescription")
data class Prescription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val schedulePattern: Int = 0, // 0: Days, 1: Months, 2: Weekly
    val interval: Int = 1,        // Every N days/months
    val startDate: Long,          // UTC Milliseconds
    val endDate: Long,            // UTC Milliseconds
    val instructions: String? = null
)
