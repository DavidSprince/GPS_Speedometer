package com.example.utils

import com.example.data.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CSVExportUtility {
    fun convertToCSV(trips: List<Trip>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csv = StringBuilder()
        
        // CSV Header
        csv.append("Trip ID,Date,Duration (Seconds),Duration Formatted,Distance (km),Avg Speed (km/h),Max Speed (km/h),Fuel Used (L)\n")
        
        for (trip in trips) {
            val formattedDate = dateFormat.format(Date(trip.date))
            val formattedDuration = formatDuration(trip.duration)
            csv.append("${trip.id},")
               .append("\"$formattedDate\",")
               .append("${trip.duration},")
               .append("\"$formattedDuration\",")
               .append(String.format(Locale.US, "%.2f", trip.distanceTravelled)).append(",")
               .append(String.format(Locale.US, "%.2f", trip.avgSpeed)).append(",")
               .append(String.format(Locale.US, "%.2f", trip.maxSpeed)).append(",")
               .append(String.format(Locale.US, "%.2f", trip.fuelUsed)).append("\n")
        }
        return csv.toString()
    }

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
