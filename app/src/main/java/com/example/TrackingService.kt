package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.example.data.Trip
import com.example.data.TripDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class TrackingStatus {
    IDLE, TRACKING, PAUSED
}

data class PathPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toLatLng() = LatLng(latitude, longitude)
}

data class TripState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val currentSpeedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val durationSeconds: Long = 0L,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val pathPoints: List<PathPoint> = emptyList(),
    val startTimeMs: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0.0f,
    val altitudeMeters: Double = 0.0,
    val bearingDegrees: Float = 0.0f
)

class TrackingService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var timerJob: Job? = null

    // Track active performance metrics
    private var lastLocation: Location? = null
    private var lastSpeedCheckTime = 0L
    private var isThrottled = false

    companion object {
        private const val NOTIFICATION_ID = 1010
        private const val CHANNEL_ID = "droute_tracking_channel"
        private const val TAG = "TrackingService"

        // Actions
        const val ACTION_START_OR_RESUME = "ACTION_START_OR_RESUME"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"

        private val _tripState = MutableStateFlow(TripState())
        val tripState: StateFlow<TripState> = _tripState.asStateFlow()

        fun resetState() {
            _tripState.update { TripState() }
        }

        fun resetActiveTrip() {
            _tripState.update { state ->
                state.copy(
                    distanceKm = 0.0,
                    durationSeconds = 0,
                    avgSpeedKmh = 0.0,
                    maxSpeedKmh = 0.0,
                    pathPoints = emptyList()
                )
            }
        }

        fun calculateHaversineDistance(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return earthRadiusKm * c
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        serviceScope.launch {
            tripState.collect {
                SpeedometerWidgetProvider.triggerWidgetUpdates(this@TrackingService)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME -> {
                    startOrResumeTracking()
                }
                ACTION_PAUSE -> {
                    pauseTracking()
                }
                ACTION_STOP -> {
                    stopTracking()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startOrResumeTracking() {
        if (_tripState.value.status == TrackingStatus.TRACKING) return

        val wasPaused = _tripState.value.status == TrackingStatus.PAUSED
        _tripState.update { 
            it.copy(
                status = TrackingStatus.TRACKING,
                startTimeMs = if (wasPaused) it.startTimeMs else System.currentTimeMillis()
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                buildNotification(), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        startLocationUpdates()
        startTimerTicker()
        Log.d(TAG, "Tracking Started/Resumed")
    }

    private fun pauseTracking() {
        if (_tripState.value.status != TrackingStatus.TRACKING) return

        _tripState.update { it.copy(status = TrackingStatus.PAUSED, currentSpeedKmh = 0.0) }
        stopTimerTicker()
        removeLocationUpdates()
        updateNotification()
        Log.d(TAG, "Tracking Paused")
    }

    private fun stopTracking() {
        stopTimerTicker()
        removeLocationUpdates()
        
        val state = _tripState.value
        if (state.status != TrackingStatus.IDLE) {
            val prefs = getSharedPreferences("droute_prefs", Context.MODE_PRIVATE)
            val mileageStr = prefs.getString("average_mileage", "12.5") ?: "12.5"
            val mileage = mileageStr.toDoubleOrNull() ?: 12.5
            val computedFuelUsed = if (mileage > 0.0) state.distanceKm / mileage else 0.0

            // 1. Save trip statistics to Database
            if (state.distanceKm > 0.005 || state.durationSeconds > 2) {
                val tripRecord = Trip(
                    date = System.currentTimeMillis(),
                    duration = state.durationSeconds,
                    distanceTravelled = state.distanceKm,
                    avgSpeed = state.avgSpeedKmh,
                    maxSpeed = state.maxSpeedKmh,
                    fuelUsed = computedFuelUsed
                )
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val db = TripDatabase.getDatabase(applicationContext)
                        db.tripDao.insertTrip(tripRecord)
                        Log.d(TAG, "Trip auto-saved successfully in TrackingService")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save trip to Room: $e")
                    }
                }
            }

            // 2. Deduct fuel consumed from SharedPreferences
            val fuelQtyStr = prefs.getString("fuel_quantity", "2.0") ?: "2.0"
            val fuelQty = fuelQtyStr.toDoubleOrNull() ?: 2.0
            val remaining = if (fuelQty > computedFuelUsed) fuelQty - computedFuelUsed else 0.0
            prefs.edit().putString("fuel_quantity", String.format(java.util.Locale.US, "%.2f", remaining)).apply()
        }

        _tripState.update { TripState() }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Tracking Stopped")
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                if (_tripState.value.status == TrackingStatus.TRACKING) {
                    _tripState.update { state ->
                        val newDuration = state.durationSeconds + 1
                        val newAvgSpeed = if (newDuration > 0) {
                            (state.distanceKm / (newDuration / 3600.0))
                        } else {
                            0.0
                        }
                        state.copy(
                            durationSeconds = newDuration,
                            avgSpeedKmh = newAvgSpeed
                        )
                    }
                    updateNotification()
                }
            }
        }
    }

    private fun stopTimerTicker() {
        timerJob?.cancel()
        timerJob = null
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(intervalMs: Long = 2000L) {
        removeLocationUpdates()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(1000L))
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                if (_tripState.value.status != TrackingStatus.TRACKING) return

                for (location in result.locations) {
                    handleNewLocation(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                mainLooper
            )
            isThrottled = (intervalMs > 5000L)
            Log.d(TAG, "Location updates active (interval: ${intervalMs}ms, throttled: $isThrottled)")
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Missing GPS permissions to request location updates: $unlikely")
        }
    }

    private fun removeLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        lastLocation = null
    }

    private fun handleNewLocation(location: Location) {
        val speedKmh = if (location.hasSpeed()) {
            location.speed * 3.6 // Metres per second to km/h
        } else {
            // estimate speed if speed not available using Haversine formula
            val distKm = lastLocation?.let { prev ->
                calculateHaversineDistance(prev.latitude, prev.longitude, location.latitude, location.longitude)
            } ?: 0.0
            val timeSec = (location.time - (lastLocation?.time ?: location.time)) / 1000.0
            if (timeSec > 0) (distKm * 1000.0 / timeSec) * 3.6 else 0.0
        }

        // Apply battery-saving checks
        if (speedKmh > 0.5) {
            lastSpeedCheckTime = System.currentTimeMillis()
            if (isThrottled) {
                // Return to normal high frequency active tracking
                startLocationUpdates(2000L)
            }
        } else {
            val idleTime = System.currentTimeMillis() - lastSpeedCheckTime
            if (idleTime > 120_000L && !isThrottled) {
                // Throttle GPS location query intervals to 30s to conserve battery
                Log.d(TAG, "Vehicle speed is 0 for > 2 mins. Throttling GPS updates to 30 seconds.")
                startLocationUpdates(30000L)
            }
        }

        var speedToDisplay = speedKmh
        if (speedToDisplay < 1.0) speedToDisplay = 0.0 // noise filter for static standing still

        val distanceDelta = lastLocation?.let { prevLoc ->
            // filter inaccurate jumps
            if (location.accuracy < 50f) {
                calculateHaversineDistance(
                    prevLoc.latitude,
                    prevLoc.longitude,
                    location.latitude,
                    location.longitude
                )
            } else {
                0.0
            }
        } ?: 0.0

        val newPoint = PathPoint(location.latitude, location.longitude)

        _tripState.update { state ->
            val updatedPoints = state.pathPoints + newPoint
            val newDistance = state.distanceKm + distanceDelta
            val newMax = if (speedToDisplay > state.maxSpeedKmh) speedToDisplay else state.maxSpeedKmh
            
            state.copy(
                currentSpeedKmh = speedToDisplay,
                distanceKm = newDistance,
                maxSpeedKmh = newMax,
                pathPoints = updatedPoints,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                altitudeMeters = location.altitude,
                bearingDegrees = location.bearing
            )
        }

        lastLocation = location
    }

    private fun updateNotification() {
        if (_tripState.value.status == TrackingStatus.IDLE) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = _tripState.value
        val speedText = String.format("%.1f km/h", state.currentSpeedKmh)
        val distText = String.format("%.2f km", state.distanceKm)
        val durationFormatted = formatSeconds(state.durationSeconds)

        val textContent = "Speed: $speedText | Distance: $distText | Time: $durationFormatted"

        // Open app when notification clicked
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification custom button intents
        val pauseIntent = Intent(this, TrackingService::class.java).apply { action = ACTION_PAUSE }
        val resumeIntent = Intent(this, TrackingService::class.java).apply { action = ACTION_START_OR_RESUME }
        val stopIntent = Intent(this, TrackingService::class.java).apply { action = ACTION_STOP }

        val pPause = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
        val pResume = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE)
        val pStop = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("D_Route HUD Tracking")
            .setContentText(textContent)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (state.status == TrackingStatus.TRACKING) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pPause)
        } else if (state.status == TrackingStatus.PAUSED) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", pResume)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Trip", pStop)

        return builder.build()
    }

    private fun formatSeconds(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "D_Route GPS Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for background Speedometer HUD tracking"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopTimerTicker()
        removeLocationUpdates()
        serviceJob.cancel()
        super.onDestroy()
    }
}
