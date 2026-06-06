package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val date: Long, // timestamp
    val duration: Long, // in seconds
    val distanceTravelled: Double, // in km
    val avgSpeed: Double, // in km/h
    val maxSpeed: Double, // in km/h
    val fuelUsed: Double // in Liters
)
