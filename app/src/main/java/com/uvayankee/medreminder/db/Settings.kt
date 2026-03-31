package com.uvayankee.medreminder.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val reNotifyIntervalMinutes: Int = 5,
    val isChimeEnabled: Boolean = true
)
