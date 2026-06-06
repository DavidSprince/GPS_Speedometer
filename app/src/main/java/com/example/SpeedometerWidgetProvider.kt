package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

class SpeedometerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context, SpeedometerWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val TAG = "WidgetProvider"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.speedometer_widget)

            // Obtain shared preference configs for Range prediction
            val prefs = context.getSharedPreferences("droute_prefs", Context.MODE_PRIVATE)
            val fuelAddedStr = prefs.getString("fuel_quantity", "2.0") ?: "2.0"
            val capacityStr = prefs.getString("fuel_tank_capacity", "5.0") ?: "5.0"
            val mileageStr = prefs.getString("average_mileage", "12.5") ?: "12.5"

            val fuelAdded = fuelAddedStr.toDoubleOrNull() ?: 2.0
            val capacity = capacityStr.toDoubleOrNull() ?: 5.0
            val mileage = mileageStr.toDoubleOrNull() ?: 12.5

            // Check if tracking service is running and fetch dynamic telemetry if so
            var currentSpeed = 0.0
            var distanceRun = 0.0
            var statusText = "READY"
            var statusColor = 0xFF10B981.toInt() // Emerald Green

            try {
                val activeState = TrackingService.tripState.value
                when (activeState.status) {
                    TrackingStatus.TRACKING -> {
                        currentSpeed = activeState.currentSpeedKmh
                        distanceRun = activeState.distanceKm
                        statusText = "RECORDING"
                        statusColor = 0xFFEF4444.toInt() // Crimson Red
                    }
                    TrackingStatus.PAUSED -> {
                        currentSpeed = 0.0
                        distanceRun = activeState.distanceKm
                        statusText = "PAUSED"
                        statusColor = 0xFFF59E0B.toInt() // Amber Orange
                    }
                    TrackingStatus.IDLE -> {
                        currentSpeed = 0.0
                        distanceRun = 0.0
                        statusText = "READY"
                        statusColor = 0xFF10B981.toInt() // Emerald Green
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying TrackingService state: ${e.message}")
            }

            // Estimate range remaining & fuel percentage
            val fuelConsumed = if (mileage > 0.0) distanceRun / mileage else 0.0
            val remainingFuel = (fuelAdded - fuelConsumed).coerceAtLeast(0.0)
            val remainingFuelPercentage = if (capacity > 0.0) (remainingFuel / capacity).coerceIn(0.0, 1.0) else 0.0
            val estimatedRange = remainingFuel * mileage

            // Populate RemoteViews
            views.setTextViewText(R.id.widget_speed_value, String.format("%.1f", currentSpeed))
            views.setTextViewText(R.id.widget_range_value, String.format("%.1f KM", estimatedRange))
            views.setTextViewText(R.id.widget_status, statusText)
            views.setTextColor(R.id.widget_status, statusColor)
            
            views.setProgressBar(R.id.widget_fuel_progress, 100, (remainingFuelPercentage * 100).toInt(), false)
            views.setTextViewText(R.id.widget_fuel_label, String.format("%.0f%%", remainingFuelPercentage * 100))

            // Clicking launcher action to bring up main app dashboard
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.parent_widget_layout, pendingIntent)

            // Perform update
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Static trigger to update widgets globally (called from Service / Activity on state updates)
        fun triggerWidgetUpdates(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisAppWidget = ComponentName(context, SpeedometerWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
                if (appWidgetIds.isEmpty()) return

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed triggerWidgetUpdates: ${e.message}")
            }
        }
    }
}
