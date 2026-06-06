package com.example.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.TrackingService
import com.example.TrackingStatus
import com.example.TripState
import com.example.data.Trip
import com.example.data.TripRepository
import com.example.ui.theme.DashboardTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpeedometerViewModel(
    private val context: Context,
    private val repository: TripRepository
) : ViewModel() {

    private val prefs = context.getSharedPreferences("droute_prefs", Context.MODE_PRIVATE)

    // Themes
    private val _selectedTheme = MutableStateFlow(
        DashboardTheme.values().find { it.name == prefs.getString("selected_theme", DashboardTheme.CYBER_NEON.name) } ?: DashboardTheme.CYBER_NEON
    )
    val selectedTheme: StateFlow<DashboardTheme> = _selectedTheme.asStateFlow()

    // Database Logs
    val allTrips: StateFlow<List<Trip>> = repository.allTrips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Trip State from Background Service
    val activeTripState: StateFlow<TripState> = TrackingService.tripState

    // Manual Range Calculator States
    val fuelQuantityInput = MutableStateFlow(prefs.getString("fuel_quantity", "2.0") ?: "2.0") // Liters added
    val fuelTankCapacityInput = MutableStateFlow(prefs.getString("fuel_tank_capacity", "5.0") ?: "5.0") // Liters tank capacity
    val averageMileageInput = MutableStateFlow(prefs.getString("average_mileage", "12.5") ?: "12.5") // km/L
    val odometerOffsetInput = MutableStateFlow(prefs.getString("odometer_offset_km", "0.0") ?: "0.0") // Odometer offset calibration in km
    val speedLimitInput = MutableStateFlow(prefs.getString("speed_limit", "100") ?: "100") // Speed limit threshold

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "fuel_quantity" -> {
                val newVal = sharedPreferences.getString(key, "2.0") ?: "2.0"
                if (fuelQuantityInput.value != newVal) {
                    fuelQuantityInput.value = newVal
                }
            }
            "fuel_tank_capacity" -> {
                val newVal = sharedPreferences.getString(key, "5.0") ?: "5.0"
                if (fuelTankCapacityInput.value != newVal) {
                    fuelTankCapacityInput.value = newVal
                }
            }
            "average_mileage" -> {
                val newVal = sharedPreferences.getString(key, "12.5") ?: "12.5"
                if (averageMileageInput.value != newVal) {
                    averageMileageInput.value = newVal
                }
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        viewModelScope.launch {
            fuelQuantityInput.collect {
                if (prefs.getString("fuel_quantity", "2.0") != it) {
                    prefs.edit().putString("fuel_quantity", it).apply()
                    com.example.SpeedometerWidgetProvider.triggerWidgetUpdates(context)
                }
            }
        }
        viewModelScope.launch {
            fuelTankCapacityInput.collect {
                if (prefs.getString("fuel_tank_capacity", "5.0") != it) {
                    prefs.edit().putString("fuel_tank_capacity", it).apply()
                    com.example.SpeedometerWidgetProvider.triggerWidgetUpdates(context)
                }
            }
        }
        viewModelScope.launch {
            averageMileageInput.collect {
                if (prefs.getString("average_mileage", "12.5") != it) {
                    prefs.edit().putString("average_mileage", it).apply()
                    com.example.SpeedometerWidgetProvider.triggerWidgetUpdates(context)
                }
            }
        }
        viewModelScope.launch {
            odometerOffsetInput.collect {
                if (prefs.getString("odometer_offset_km", "0.0") != it) {
                    prefs.edit().putString("odometer_offset_km", it).apply()
                }
            }
        }
        viewModelScope.launch {
            speedLimitInput.collect {
                if (prefs.getString("speed_limit", "100") != it) {
                    prefs.edit().putString("speed_limit", it).apply()
                }
            }
        }
    }

    // Live state-driven lifetime complete odometer system
    val lifetimeOdometer: StateFlow<Double> = combine(
        allTrips,
        activeTripState,
        odometerOffsetInput
    ) { trips, activeState, offsetStr ->
        val historicalTotal = trips.sumOf { it.distanceTravelled }
        val offset = offsetStr.toDoubleOrNull() ?: 0.0
        offset + historicalTotal + activeState.distanceKm
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Live remaining fuel in liters based on active trip distance
    val remainingFuel: StateFlow<Double> = combine(
        fuelQuantityInput,
        averageMileageInput,
        activeTripState
    ) { fuelAddedStr, mileageStr, state ->
        val fuelAdded = fuelAddedStr.toDoubleOrNull() ?: 2.0
        val mileage = mileageStr.toDoubleOrNull() ?: 12.5
        val fuelConsumed = if (mileage > 0.0) state.distanceKm / mileage else 0.0
        val remaining = fuelAdded - fuelConsumed
        if (remaining > 0.0) remaining else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2.0)

    // Live fuel ratio out of total tank capacity
    val remainingFuelPercentage: StateFlow<Double> = combine(
        remainingFuel,
        fuelTankCapacityInput
    ) { remaining, capacityStr ->
        val capacity = capacityStr.toDoubleOrNull() ?: 5.0
        if (capacity > 0.0) (remaining / capacity).coerceIn(0.0, 1.0) else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.4)

    // Live Estimated Range remaining (remaining fuel * mileage)
    val estimatedRangeRemaining: StateFlow<Double> = combine(
        remainingFuel,
        averageMileageInput
    ) { remaining, mileageStr ->
        val mileage = mileageStr.toDoubleOrNull() ?: 12.5
        remaining * mileage
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun selectTheme(theme: DashboardTheme) {
        _selectedTheme.value = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun resetFuelGauge() {
        fuelQuantityInput.value = "2.0"
        fuelTankCapacityInput.value = "5.0"
        averageMileageInput.value = "12.5"
    }

    // Trip controls
    fun startTrip(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_OR_RESUME
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun pauseTrip(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_PAUSE
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun endTrip(context: Context) {
        val currentTrip = activeTripState.value
        if (currentTrip.status != TrackingStatus.IDLE) {
            val stopIntent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP
            }
            androidx.core.content.ContextCompat.startForegroundService(context, stopIntent)
        }
    }

    fun deleteTrip(id: Long) {
        viewModelScope.launch {
            repository.deleteTripById(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.deleteAllTrips()
        }
    }

    fun resetOdometer() {
        odometerOffsetInput.value = "0.0"
        clearAllLogs()
    }

    fun resetActiveTrip() {
        TrackingService.resetActiveTrip()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}

// ViewModel factory helper
class SpeedometerViewModelFactory(
    private val context: android.content.Context,
    private val repository: TripRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeedometerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeedometerViewModel(context.applicationContext, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
