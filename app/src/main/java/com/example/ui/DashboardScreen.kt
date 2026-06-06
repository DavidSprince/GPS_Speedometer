package com.example.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.PathPoint
import com.example.TrackingStatus
import com.example.TripState
import com.example.data.Trip
import android.app.Activity
import android.util.Log
import com.example.ui.theme.*
import com.example.utils.CSVExportUtility
import com.example.utils.UnityAdsManager
import com.example.viewmodel.SpeedometerViewModel
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Bundle
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: SpeedometerViewModel) {
    val context = LocalContext.current
    
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val allTrips by viewModel.allTrips.collectAsState()
    val activeState by viewModel.activeTripState.collectAsState()
    val fuelInput by viewModel.fuelQuantityInput.collectAsState()
    val mileageInput by viewModel.averageMileageInput.collectAsState()
    val rangeRemaining by viewModel.estimatedRangeRemaining.collectAsState()
    val remainingFuel by viewModel.remainingFuel.collectAsState()
    val fuelPercent by viewModel.remainingFuelPercentage.collectAsState()
    val tankCapacityStr by viewModel.fuelTankCapacityInput.collectAsState()
    val lifetimeOdometer by viewModel.lifetimeOdometer.collectAsState()
    val speedLimitStr by viewModel.speedLimitInput.collectAsState()

    var selectedTab by remember { mutableStateOf(CockpitTab.DASH) }

    // Trigger rewarded ad whenever the user switches to the LOGS tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == CockpitTab.LOGS) {
            val activity = context as? Activity
            if (activity != null) {
                UnityAdsManager.showRewardedAd(
                    activity = activity,
                    onComplete = {
                        Log.d("DashboardScreen", "Unity Rewarded Ad complete. User rewarded!")
                    },
                    onFailure = {
                        Log.w("DashboardScreen", "Unity Rewarded Ad failed/not loaded yet.")
                    }
                )
            }
        }
    }

    // 1. Initial / manual trigger for Sweep Animation
    var triggerSweep by remember { mutableStateOf(true) }
    val animatedSweepValue = remember { Animatable(0f) }

    LaunchedEffect(triggerSweep, activeState.status) {
        if (triggerSweep || (activeState.status == TrackingStatus.TRACKING && activeState.distanceKm == 0.0)) {
            animatedSweepValue.animateTo(
                targetValue = 240f,
                animationSpec = tween(750, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
            )
            animatedSweepValue.animateTo(
                targetValue = 0f,
                animationSpec = tween(750, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
            )
            triggerSweep = false
        }
    }

    // Determine speed to show (sweep takes priority if animation is active, else actual speed)
    val displaySpeed = if (animatedSweepValue.value > 0.05f) {
        animatedSweepValue.value.toDouble()
    } else {
        activeState.currentSpeedKmh
    }

    val speedLimitValue = speedLimitStr.toDoubleOrNull() ?: 100.0
    val isOverSpeedLimit = displaySpeed > speedLimitValue

    // Speed Danger pulse glow animation (enabled when speed exceeds configurable speed limit)
    val infiniteTransition = rememberInfiniteTransition(label = "DangerGlow")
    val edgeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = if (isOverSpeedLimit) 0.61f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DangerEdgeAlpha"
    )

    // Live clock for cockpit status bar
    var currentTime by remember { mutableStateOf("14:22:00") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            currentTime = sdf.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // Storage access framework launcher to export logs as CSV
    val csvDataToSave = remember { mutableStateOf("") }
    val documentCreatorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream: OutputStream ->
                    outputStream.write(csvDataToSave.value.toByteArray())
                    outputStream.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DRouteTheme(theme = selectedTheme) {
        val isLight = selectedTheme == DashboardTheme.DAY_LIGHT
        val tankCapacity = tankCapacityStr.toDoubleOrNull() ?: 5.0
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isLight) Color(0xFFF4F6F9) else Color.Black)
                .drawBehind {
                    if (edgeAlpha > 0.01f) {
                        drawRect(
                            color = SpeedDangerRed.copy(alpha = edgeAlpha),
                            style = Stroke(width = 16.dp.toPx())
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    // Header Telemetry Status Bar
                    TelemetryStatusBar(activeState = activeState, currentTime = currentTime, isLight = isLight)

                    // Secondary Header Logo block / Theme Selection Toggles
                    HeaderRow(
                        selectedTheme = selectedTheme,
                        onThemeSelected = { viewModel.selectTheme(it) },
                        onManualSweepClick = { triggerSweep = true }
                    )

                    when (selectedTab) {
                        CockpitTab.DASH -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Main Speedometer Cockpit Gauge
                                item {
                                    SpeedometerGaugeCard(
                                        speed = displaySpeed,
                                        tripState = activeState,
                                        theme = selectedTheme,
                                        remainingFuel = remainingFuel,
                                        fuelPercent = fuelPercent,
                                        fuelCapacity = tankCapacity,
                                        isLight = isLight,
                                        rangeRemaining = rangeRemaining,
                                        mileageInput = mileageInput,
                                        lifetimeOdometer = lifetimeOdometer,
                                        speedLimitInput = speedLimitStr,
                                        isOverSpeedLimit = isOverSpeedLimit,
                                        onResetTrip = { viewModel.resetActiveTrip() }
                                    )
                                }

                                // Tactile Session Console Controller Buttons
                                item {
                                    TripControlCard(
                                        activeStatus = activeState.status,
                                        onStart = { viewModel.startTrip(context) },
                                        onPause = { viewModel.pauseTrip(context) },
                                        onEnd = { viewModel.endTrip(context) },
                                        isLight = isLight
                                    )
                                }
                            }
                        }

                        CockpitTab.RANGE -> {
                            RangeTabScreen(
                                fuelInput = fuelInput,
                                mileageInput = mileageInput,
                                fuelCapacityInput = tankCapacityStr,
                                estimatedRange = rangeRemaining,
                                onFuelChanged = { viewModel.fuelQuantityInput.value = it },
                                onMileageChanged = { viewModel.averageMileageInput.value = it },
                                onFuelCapacityChanged = { viewModel.fuelTankCapacityInput.value = it },
                                onReset = { viewModel.resetFuelGauge() },
                                isLight = isLight,
                                remainingFuel = remainingFuel,
                                fuelPercent = fuelPercent,
                                fuelCapacity = tankCapacity,
                                theme = selectedTheme
                            )
                        }

                        CockpitTab.HUD -> {
                            HudTabScreen(
                                activeState = activeState,
                                theme = selectedTheme,
                                isLight = isLight,
                                viewModel = viewModel,
                                context = context
                            )
                        }

                        CockpitTab.LOGS -> {
                            LogBookTabScreen(
                                allTrips = allTrips,
                                activeState = activeState,
                                fuelInput = fuelInput,
                                mileageInput = mileageInput,
                                rangeRemaining = rangeRemaining,
                                selectedTheme = selectedTheme,
                                csvDataToSave = csvDataToSave,
                                documentCreatorLauncher = documentCreatorLauncher,
                                viewModel = viewModel
                            )
                        }

                        CockpitTab.SYSTEM -> {
                            SystemsTabScreen(
                                activeState = activeState,
                                viewModel = viewModel,
                                theme = selectedTheme,
                                isLight = isLight
                            )
                        }
                    }
                }

                // Sticky Bottom Navigation Menu (High density status style)
                CockpitBottomNavigation(
                    selectedTheme = selectedTheme,
                    currentTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    logCount = allTrips.size
                )
            }
        }
    }
}

@Composable
fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var batteryLevel by remember { mutableStateOf(100) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level * 100 / scale.toFloat()).toInt()
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val currentBatteryIntent = context.registerReceiver(receiver, filter)
        currentBatteryIntent?.let { intent ->
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                batteryLevel = (level * 100 / scale.toFloat()).toInt()
            }
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }
    return batteryLevel
}

@Composable
fun TelemetryStatusBar(activeState: TripState, currentTime: String, isLight: Boolean) {
    val activeColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarPulseAlpha"
    )
    val batteryPct = rememberBatteryLevel()
    val batteryColor = if (batteryPct <= 20) Color(0xFFEF4444) else if (isLight) Color(0xFF475569) else Color.Gray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        activeColor.copy(alpha = pulseAlpha), 
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp, 
                        activeColor, 
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            Text(
                text = "D_ROUTE SYSTEM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = activeColor,
                letterSpacing = 1.5.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GPS: ${if (activeState.status == TrackingStatus.TRACKING) "FIXED" else "STANDBY"}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (activeState.status == TrackingStatus.TRACKING) SpeedEcoGreen else if (isLight) Color(0xFF475569) else Color.Gray
            )
            Text(
                text = "BAT: $batteryPct%",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = batteryColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = currentTime,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun HeaderRow(
    selectedTheme: DashboardTheme,
    onThemeSelected: (DashboardTheme) -> Unit,
    onManualSweepClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "COCKPIT CONSOLE",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.Gray.copy(alpha = 0.8f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onManualSweepClick,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                    .testTag("sweep_test_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Test Gauge Sweep",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }

            ThemeToggleBubble(
                tag = "CYBER",
                isSelected = selectedTheme == DashboardTheme.CYBER_NEON,
                accentColor = ElectricCyan,
                onClick = { onThemeSelected(DashboardTheme.CYBER_NEON) }
            )
            ThemeToggleBubble(
                tag = "GOLD",
                isSelected = selectedTheme == DashboardTheme.CARBON_GOLD,
                accentColor = GoldAccent,
                onClick = { onThemeSelected(DashboardTheme.CARBON_GOLD) }
            )
            ThemeToggleBubble(
                tag = "MATRIX",
                isSelected = selectedTheme == DashboardTheme.MATRIX_GREEN,
                accentColor = MatrixGreenAccent,
                onClick = { onThemeSelected(DashboardTheme.MATRIX_GREEN) }
            )
            ThemeToggleBubble(
                tag = "DAY",
                isSelected = selectedTheme == DashboardTheme.DAY_LIGHT,
                accentColor = Color(0xFFFF5A36),
                onClick = { onThemeSelected(DashboardTheme.DAY_LIGHT) }
            )
        }
    }
}

@Composable
fun ThemeToggleBubble(
    tag: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) accentColor else Color.Gray
        )
    }
}

@Composable
fun ClassicOdometer(
    distanceKm: Double,
    isLight: Boolean,
    modifier: Modifier = Modifier
) {
    val totalTenths = (distanceKm * 10.0 + 0.5).toInt().coerceAtLeast(0)
    val fullStr = String.format(java.util.Locale.US, "%06d", totalTenths)
    val digits = fullStr.takeLast(6)

    Row(
        modifier = modifier
            .background(
                if (isLight) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                RoundedCornerShape(6.dp)
            )
            .border(
                BorderStroke(1.dp, if (isLight) Color(0xFFCBD5E1) else Color(0xFF00FAF2).copy(alpha = 0.3f)),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        digits.forEachIndexed { index, char ->
            val isTenth = index == digits.lastIndex
            
            if (isTenth) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .size(3.dp)
                        .background(if (isLight) Color(0xFF0F172A) else Color(0xFF00FAF2), CircleShape)
                        .align(Alignment.Bottom)
                        .padding(bottom = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(13.dp)
                    .height(20.dp)
                    .background(
                        brush = if (isTenth) {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFE2E8F0), Color.White, Color(0xFFCBD5E1))
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF334155), Color(0xFF0F172A), Color(0xFF1E293B))
                            )
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
                    .border(
                        BorderStroke(0.5.dp, if (isLight) Color(0x33000000) else Color(0x33FFFFFF)),
                        shape = RoundedCornerShape(2.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char.toString(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isTenth) Color(0xFFDC2626) else Color.White
                )
            }
        }
    }
}

@Composable
fun TripMeterButton(
    tripKm: Double,
    isLight: Boolean,
    accentColor: Color,
    onResetTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    var holdProgress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            val startTime = System.currentTimeMillis()
            while (isPressed) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed / 5000f).coerceIn(0f, 1f)
                holdProgress = progress
                if (progress >= 1f) {
                    onResetTrip()
                    holdProgress = 0f
                    break
                }
                delay(50)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .width(96.dp)
            .background(
                if (isLight) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                RoundedCornerShape(6.dp)
            )
            .border(
                BorderStroke(1.5.dp, if (isPressed) accentColor else (if (isLight) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.15f))),
                RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .testTag("trip_meter_button"),
        contentAlignment = Alignment.Center
    ) {
        if (isPressed && holdProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(holdProgress)
                    .background(accentColor.copy(alpha = 0.35f))
                    .align(Alignment.CenterStart)
            )
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)
        ) {
            Text(
                text = "TRIP:",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = String.format(java.util.Locale.US, "%.1f", tripKm),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) accentColor else (if (isLight) Color(0xFF0F172A) else Color.White)
            )
        }
        
        if (isPressed) {
            Text(
                text = String.format(java.util.Locale.US, "%.0f", 5.0 * (1f - holdProgress)),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun SpeedometerGaugeCard(
    speed: Double,
    tripState: TripState,
    theme: DashboardTheme,
    remainingFuel: Double,
    fuelPercent: Double,
    fuelCapacity: Double,
    isLight: Boolean,
    rangeRemaining: Double,
    mileageInput: String,
    lifetimeOdometer: Double = 0.0,
    speedLimitInput: String = "100",
    isOverSpeedLimit: Boolean = false,
    onResetTrip: () -> Unit = {}
) {
    val darkBg = if (isLight) Color(0xFFF4F6F9) else Color(0xFF04060C) // Rich neutral dark isolated minimal background
    val primaryColor = if (isLight) Color(0xFF0F172A) else Color(0xFF00FAF2)
    val safeGreen = if (isLight) Color(0xFF16A34A) else Color(0xFF00FF66) // Vivid green for safety
    val warningRed = Color(0xFFFF3B30) // Brilliant red for over limit
    val speedometerColor = if (isOverSpeedLimit) warningRed else safeGreen
    val activeIntervalColor = speedometerColor

    val infiniteTransition = rememberInfiniteTransition(label = "fuel_gauge_wave_opt")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fuel_gauge_offset"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speedometer_gauge_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color = if (isLight) Color(0xFFE2E8F0) else Color(0xFF00FAF2).copy(alpha = 0.20f)),
        colors = CardDefaults.cardColors(
            containerColor = darkBg
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Custom Gauge Canvas Row (combines speedometer and fuel gauge side-by-side)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speedometer Area Layout
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(165.dp)
                                .testTag("analog_gauge_canvas")
                        ) {
                        val width = size.width
                        val height = size.height
                        val center = Offset(width / 2, height / 2)
                        val radius = (size.minDimension / 2.3f)

                        // --- STATS / DECORATIVE OUTER RINGS (HUD Futuristic Vector Aesthetic) ---
                        // 1. Subtle Outer Dashed Ring 1
                        drawArc(
                            color = if (isLight) Color(0xFFE2E8F0) else speedometerColor.copy(alpha = 0.12f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - (radius + 12.dp.toPx()), center.y - (radius + 12.dp.toPx())),
                            size = Size((radius + 12.dp.toPx()) * 2, (radius + 12.dp.toPx()) * 2),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 8.dp.toPx()), 0f)
                            )
                        )

                        // 2. Subtle Outer Solid Ring 2
                        drawArc(
                            color = if (isLight) Color(0xFFE2E8F0) else speedometerColor.copy(alpha = 0.08f),
                            startAngle = 130f,
                            sweepAngle = 280f,
                            useCenter = false,
                            topLeft = Offset(center.x - (radius + 6.dp.toPx()), center.y - (radius + 6.dp.toPx())),
                            size = Size((radius + 6.dp.toPx()) * 2, (radius + 6.dp.toPx()) * 2),
                            style = Stroke(width = 0.75f.dp.toPx())
                        )

                        // 3. Subtle Inner Dashed Ring 3
                        drawArc(
                            color = if (isLight) Color(0xFFE2E8F0) else speedometerColor.copy(alpha = 0.10f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - (radius - 12.dp.toPx()), center.y - (radius - 12.dp.toPx())),
                            size = Size((radius - 12.dp.toPx()) * 2, (radius - 12.dp.toPx()) * 2),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f)
                            )
                        )

                        // 4. Base Dim Cyan Track Trace
                        drawArc(
                            color = if (isLight) Color(0xFFE2E8F0) else speedometerColor.copy(alpha = 0.08f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // --- DYNAMIC VISUAL THRESHOLD INDICATORS ---
                        val speedLimitVal = speedLimitInput.toDoubleOrNull() ?: 100.0
                        val limitProgress = (speedLimitVal / 240.0).coerceIn(0.0, 1.0).toFloat()
                        val limitAngle = 135f + limitProgress * 270f
                        val remainingSweep = (1.0f - limitProgress) * 270f

                        // A. Translucent danger / redline warning zone from threshold to 240 km/h
                        if (remainingSweep > 0.1f) {
                            drawArc(
                                color = warningRed.copy(alpha = 0.18f),
                                startAngle = limitAngle,
                                sweepAngle = remainingSweep,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(
                                    width = 8.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }

                        // B. Physical dynamic tick notch at the exact limit angle boundary
                        val radLimit = Math.toRadians(limitAngle.toDouble())
                        val markerOuterRadius = radius + 6.dp.toPx()
                        val markerInnerRadius = radius - 4.dp.toPx()
                        
                        val markerStartX = center.x + (markerInnerRadius * Math.cos(radLimit)).toFloat()
                        val markerStartY = center.y + (markerInnerRadius * Math.sin(radLimit)).toFloat()
                        val markerEndX = center.x + (markerOuterRadius * Math.cos(radLimit)).toFloat()
                        val markerEndY = center.y + (markerOuterRadius * Math.sin(radLimit)).toFloat()
                        
                        // Radiant glow underlay
                        drawLine(
                            color = warningRed.copy(alpha = 0.40f),
                            start = Offset(markerStartX, markerStartY),
                            end = Offset(markerEndX, markerEndY),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Precise core marker
                        drawLine(
                            color = warningRed,
                            start = Offset(markerStartX, markerStartY),
                            end = Offset(markerEndX, markerEndY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 5. Active Glowing Semi-circular Progress Segment
                        val sweepProgress = (speed / 240.0).coerceIn(0.0, 1.0).toFloat()
                        val targetSweepAngle = sweepProgress * 270f

                        // Outer glowing diffuse shadow arc
                        drawArc(
                            color = speedometerColor.copy(alpha = 0.25f),
                            startAngle = 135f,
                            sweepAngle = targetSweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Intense core active arc
                        drawArc(
                            color = speedometerColor,
                            startAngle = 135f,
                            sweepAngle = targetSweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // 6. Glowing Cyan Ticks (Every 10 km/h up to 240)
                        for (i in 0..240 step 10) {
                            val angleRatio = i / 240f
                            val currentAngle = 135f + (angleRatio * 270f)
                            val angleRad = Math.toRadians(currentAngle.toDouble())

                            val isMajor = i % 40 == 0
                            val tickLen = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                            val startLen = radius - tickLen
                            val endLen = radius
                            
                            val startTickX = center.x + (startLen * Math.cos(angleRad)).toFloat()
                            val startTickY = center.y + (startLen * Math.sin(angleRad)).toFloat()
                            
                            val endTickX = center.x + (endLen * Math.cos(angleRad)).toFloat()
                            val endTickY = center.y + (endLen * Math.sin(angleRad)).toFloat()

                            val isActive = speed >= i
                            val tickColor = if (isActive) {
                                speedometerColor
                            } else {
                                if (isLight) Color(0xFFE2E8F0) else speedometerColor.copy(alpha = 0.15f)
                            }

                            // Glowing tick shadow effect
                            if (isActive) {
                                drawLine(
                                    color = speedometerColor.copy(alpha = 0.3f),
                                    start = Offset(startTickX, startTickY),
                                    end = Offset(endTickX, endTickY),
                                    strokeWidth = if (isMajor) 4.dp.toPx() else 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            drawLine(
                                color = tickColor,
                                start = Offset(startTickX, startTickY),
                                end = Offset(endTickX, endTickY),
                                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Major Text Labels
                            if (isMajor) {
                                val textRadius = radius - 18.dp.toPx()
                                val textX = center.x + (textRadius * Math.cos(angleRad)).toFloat()
                                val textY = center.y + (textRadius * Math.sin(angleRad)).toFloat()

                                drawContext.canvas.nativeCanvas.drawText(
                                    i.toString(),
                                    textX,
                                    textY + 3.dp.toPx(),
                                    android.graphics.Paint().apply {
                                        color = if (isActive) {
                                            speedometerColor.toArgb()
                                        } else {
                                            if (isLight) Color(0xFFE2E8F0).toArgb() else speedometerColor.copy(alpha = 0.35f).toArgb()
                                        }
                                        textSize = 7.5f.sp.toPx()
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                        isAntiAlias = true
                                    }
                                )
                            }
                        }

                        // Glowing cursor point representing active telemetry head
                        val cursorAngle = 135f + targetSweepAngle
                        val cursorRad = Math.toRadians(cursorAngle.toDouble())
                        val cursorPoint = Offset(
                            center.x + (radius * Math.cos(cursorRad)).toFloat(),
                            center.y + (radius * Math.sin(cursorRad)).toFloat()
                        )

                        // Sleek glowing analog needle pointing to current speed representation
                        val needleLength = radius * 0.85f
                        val needleEndX = center.x + (needleLength * Math.cos(cursorRad)).toFloat()
                        val needleEndY = center.y + (needleLength * Math.sin(cursorRad)).toFloat()
                        
                        // Draw needle line
                        drawLine(
                            color = speedometerColor,
                            start = center,
                            end = Offset(needleEndX, needleEndY),
                            strokeWidth = 2.2f.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Center hub cap for the needle
                        drawCircle(
                            color = speedometerColor,
                            radius = 6.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = if (isLight) Color.White else Color(0xFF04060C),
                            radius = 3.dp.toPx(),
                            center = center
                        )

                        drawCircle(
                            color = speedometerColor,
                            radius = 4.dp.toPx(),
                            center = cursorPoint
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = cursorPoint
                        )
                    }

                    // Centered Digital display showing glowing cyan telemetry digits
                    Column(
                        modifier = Modifier.padding(top = 90.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = String.format("%.0f", speed),
                            style = DigitalSpeedometerFont.copy(
                                color = speedometerColor,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Black,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = speedometerColor.copy(alpha = 0.65f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 14f
                                )
                            )
                        )
                        Text(
                            text = "KM/H",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLight) Color(0xFF475569) else speedometerColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }

                // Odometer and Trip Meter side-by-side below the Speedometer dial
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    ClassicOdometer(
                        distanceKm = lifetimeOdometer,
                        isLight = isLight,
                        modifier = Modifier.testTag("classic_odometer_display")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TripMeterButton(
                        tripKm = tripState.distanceKm,
                        isLight = isLight,
                        accentColor = speedometerColor,
                        onResetTrip = onResetTrip
                    )
                }
            }

                // Upgraded custom hardware-style fuel gauge column
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FUEL GAUGE",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLight) Color(0xFF475569) else Color(0xFF00FAF2).copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(115.dp)
                                .testTag("analog_fuel_gauge_canvas")
                        ) {
                            val width = size.width
                            val height = size.height
                            val center = Offset(width / 2, height / 2)
                            val radius = (size.minDimension / 2.3f)

                            // Outer dashed design ring for fuel gauge
                            drawArc(
                                color = if (isLight) Color(0xFFE2E8F0) else Color(0xFF00FAF2).copy(alpha = 0.08f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Fuel sweep progress
                            val sweepProgress = fuelPercent.toFloat().coerceIn(0f, 1f)
                            val targetSweepAngle = sweepProgress * 270f

                            // Glowing cyan / warning red theme based on fuel level
                            val fuelColor = if (sweepProgress <= 0.15f) {
                                Color(0xFFEF4444) // Danger Red
                            } else if (sweepProgress <= 0.3f) {
                                Color(0xFFF59E0B) // Warning Amber
                            } else {
                                if (isLight) Color(0xFF059669) else Color(0xFF00FAF2) // Glowing Cyan or Safe Green
                            }

                            // Draw active glowing arc segment
                            drawArc(
                                color = fuelColor,
                                startAngle = 135f,
                                sweepAngle = targetSweepAngle,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Inner fluid-depth cyber tank design
                            val innerRadius = radius * 0.45f

                            drawCircle(
                                color = if (isLight) Color(0xFFE2E8F0).copy(alpha = 0.35f) else Color(0xFF00FAF2).copy(alpha = 0.03f),
                                radius = innerRadius,
                                center = center
                            )
                            drawCircle(
                                color = if (isLight) Color(0xFFE2E8F0) else Color(0xFF00FAF2).copy(alpha = 0.10f),
                                radius = innerRadius,
                                center = center,
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // Clip wave liquid layout to the inner circular area
                            val circlePath = Path().apply {
                                addOval(Rect(center, innerRadius))
                            }

                            clipPath(circlePath) {
                                drawCircle(
                                    color = fuelColor.copy(alpha = 0.04f),
                                    radius = innerRadius,
                                    center = center
                                )

                                val waveHeight = 3.dp.toPx()
                                val currentY = center.y + innerRadius - (innerRadius * 2 * sweepProgress)

                                val wavePath = Path().apply {
                                    val startX = center.x - innerRadius
                                    val endX = center.x + innerRadius
                                    val startY = center.y + innerRadius

                                    moveTo(startX, startY)
                                    for (x in (startX.toInt())..(endX.toInt()) step 2) {
                                        val relativeX = x - startX
                                        val sineValue = Math.sin((relativeX / innerRadius * Math.PI) + waveOffset.toDouble()).toFloat()
                                        val y = currentY + sineValue * waveHeight
                                        lineTo(x.toFloat(), y)
                                    }
                                    lineTo(endX, startY)
                                    close()
                                }
                                drawPath(path = wavePath, color = fuelColor.copy(alpha = 0.30f))

                                val secondWaveOffset = waveOffset + Math.PI.toFloat() * 0.65f
                                val secondWavePath = Path().apply {
                                    val startX = center.x - innerRadius
                                    val endX = center.x + innerRadius
                                    val startY = center.y + innerRadius

                                    moveTo(startX, startY)
                                    for (x in (startX.toInt())..(endX.toInt()) step 2) {
                                        val relativeX = x - startX
                                        val sineValue = Math.sin((relativeX / innerRadius * Math.PI) - secondWaveOffset.toDouble()).toFloat()
                                        val y = (currentY + 1.dp.toPx()) + sineValue * (waveHeight * 0.7f)
                                        lineTo(x.toFloat(), y)
                                    }
                                    lineTo(endX, startY)
                                    close()
                                }
                                drawPath(path = secondWavePath, color = fuelColor.copy(alpha = 0.15f))
                            }

                            // 3. Draw key ticks (E, 1/2, F) in cyber aesthetics
                            val angles = listOf(135f, 270f, 45f)
                            val labels = listOf("E", "1/2", "F")
                            for (idx in angles.indices) {
                                val currentAngle = angles[idx]
                                val angleRad = Math.toRadians(currentAngle.toDouble())

                                val startLen = radius - 5.dp.toPx()
                                val endLen = radius
                                
                                val startTickX = center.x + (startLen * Math.cos(angleRad)).toFloat()
                                val startTickY = center.y + (startLen * Math.sin(angleRad)).toFloat()
                                
                                val endTickX = center.x + (endLen * Math.cos(angleRad)).toFloat()
                                val endTickY = center.y + (endLen * Math.sin(angleRad)).toFloat()

                                val isActiveTick = sweepProgress >= (idx * 0.5f)
                                drawLine(
                                    color = if (idx == 0 && sweepProgress <= 0.15f) {
                                        Color(0xFFEF4444)
                                    } else {
                                        if (isActiveTick) {
                                            if (isLight) Color(0xFF059669) else Color(0xFF00FAF2)
                                        } else {
                                            if (isLight) Color(0xFFE2E8F0) else Color(0xFF00FAF2).copy(alpha = 0.2f)
                                        }
                                    },
                                    start = Offset(startTickX, startTickY),
                                    end = Offset(endTickX, endTickY),
                                    strokeWidth = 1.5f.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                val textRadius = radius - 11.dp.toPx()
                                val textX = center.x + (textRadius * Math.cos(angleRad)).toFloat()
                                val textY = center.y + (textRadius * Math.sin(angleRad)).toFloat()

                                drawContext.canvas.nativeCanvas.drawText(
                                    labels[idx],
                                    textX,
                                    textY + 2.dp.toPx(),
                                    android.graphics.Paint().apply {
                                        color = if (idx == 0 && sweepProgress <= 0.15f) {
                                            Color(0xFFEF4444).toArgb()
                                        } else {
                                            if (isActiveTick) {
                                                (if (isLight) Color(0xFF059669) else Color(0xFF00FAF2)).toArgb()
                                            } else {
                                                (if (isLight) Color(0xFFE2E8F0) else Color(0xFF00FAF2).copy(alpha = 0.35f)).toArgb()
                                            }
                                        }
                                        textSize = 7.5f.sp.toPx()
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                        isAntiAlias = true
                                    }
                                )
                            }

                            // Glowing small hardware cursor dot on the tank level representation
                            val needleAngle = 135f + (sweepProgress * 270f)
                            val needleRad = Math.toRadians(needleAngle.toDouble())
                            val cursorX = center.x + (radius * Math.cos(needleRad)).toFloat()
                            val cursorY = center.y + (radius * Math.sin(needleRad)).toFloat()

                            // Sleek glowing analog needle pointing to current fuel representation
                            val fuelNeedleLength = radius * 0.82f
                            val fNeedleEndX = center.x + (fuelNeedleLength * Math.cos(needleRad)).toFloat()
                            val fNeedleEndY = center.y + (fuelNeedleLength * Math.sin(needleRad)).toFloat()
                            
                            // Draw fuel needle line
                            drawLine(
                                color = fuelColor,
                                start = center,
                                end = Offset(fNeedleEndX, fNeedleEndY),
                                strokeWidth = 1.75f.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Center hub cap for the fuel needle
                            drawCircle(
                                color = fuelColor,
                                radius = 4.5f.dp.toPx(),
                                center = center
                            )
                            drawCircle(
                                color = if (isLight) Color.White else Color(0xFF04060C),
                                radius = 2.dp.toPx(),
                                center = center
                            )

                            drawCircle(
                                color = fuelColor,
                                radius = 3.dp.toPx(),
                                center = Offset(cursorX, cursorY)
                            )
                        }
                    }

                    // Bottom Range representation in matching cyan color values
                    val maxRange = fuelCapacity * (mileageInput.toDoubleOrNull() ?: 12.5)
                    val fuelColor = if (fuelPercent <= 0.15f) {
                        Color(0xFFEF4444) 
                    } else if (fuelPercent <= 0.3f) {
                        Color(0xFFF59E0B) 
                    } else {
                        if (isLight) Color(0xFF059669) else Color(0xFF00FAF2)
                    }

                    SmallRangeDialer(
                        rangeRemaining = rangeRemaining,
                        maxRange = maxRange,
                        isLight = isLight,
                        accentColor = fuelColor,
                        remainingFuel = remainingFuel,
                        fuelCapacity = fuelCapacity
                    )
                }
            }

            if (isOverSpeedLimit) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("speed_warning_visual_alert"),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = warningRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, color = warningRed.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = warningRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SPEED WARNING: EXCEEDING LIMIT OF $speedLimitInput KM/H",
                            color = warningRed,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // High-Density horizontal metrics row
            Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            var showLifetimeOdo by remember { mutableStateOf(true) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalMetricItem(
                    label = if (showLifetimeOdo) "LIFETIME ODO" else "TRIP METER",
                    value = String.format(Locale.US, "%.2f", if (showLifetimeOdo) lifetimeOdometer else tripState.distanceKm),
                    unit = "KM",
                    color = primaryColor,
                    modifier = Modifier
                        .clickable { showLifetimeOdo = !showLifetimeOdo }
                        .testTag("odometer_metric_toggle")
                )
                VerticalGridDivider()
                HorizontalMetricItem(
                    label = "DURATION",
                    value = formatTripDurationCompact(tripState.durationSeconds),
                    unit = "MIN",
                    color = primaryColor
                )
                VerticalGridDivider()
                HorizontalMetricItem(
                    label = "AVG SPD",
                    value = String.format(Locale.US, "%.1f", tripState.avgSpeedKmh),
                    unit = "KM/H",
                    color = primaryColor
                )
                VerticalGridDivider()
                HorizontalMetricItem(
                    label = "PEAK SPD",
                    value = String.format(Locale.US, "%.1f", tripState.maxSpeedKmh),
                    unit = "KM/H",
                    color = activeIntervalColor
                )
            }
        }
    }
}

@Composable
fun HorizontalMetricItem(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 7.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = unit,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun VerticalGridDivider() {
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
fun RangePredictorCard(
    fuelInput: String,
    mileageInput: String,
    fuelCapacityInput: String,
    estimatedRange: Double,
    onFuelChanged: (String) -> Unit,
    onMileageChanged: (String) -> Unit,
    onFuelCapacityChanged: (String) -> Unit,
    onReset: () -> Unit,
    isLight: Boolean
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("range_predictor_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color = secondaryColor.copy(alpha = 0.15f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left summary panel showing remaining range
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = "Fuel Info",
                            tint = secondaryColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "FUEL RANGE PREDICTOR",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isLight) Color(0xFF475569) else Color.Gray
                        )
                    }

                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(22.dp)
                            .testTag("fuel_gauge_reset_button")
                    ) {
                        Text(
                            text = "RESET",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = String.format("%.1f", estimatedRange),
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = secondaryColor
                    )
                    Text(
                        text = "KM REMAINING",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isLight) Color(0xFF475569) else Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                }

                // Glow Progress line
                val progressRatio = ((estimatedRange / 600.0).coerceIn(0.0, 1.0)).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(1.5f.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressRatio)
                            .background(
                                Brush.horizontalGradient(listOf(secondaryColor, activeColor)), 
                                RoundedCornerShape(1.5f.dp)
                            )
                    )
                }
            }

            // Right input controls panel
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.Start
            ) {
                // Option 1: Fuel Tank Capacity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Tank Cap (L):",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isLight) Color(0xFF334155) else Color.White
                    )
                    MicroTextField(
                        value = fuelCapacityInput,
                        onValueChange = onFuelCapacityChanged,
                        placeholder = "Tank L",
                        isLight = isLight,
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("fuel_tank_capacity_input")
                    )
                }

                // Option 2: Added Fuel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Added Fuel (L):",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isLight) Color(0xFF334155) else Color.White
                    )
                    MicroTextField(
                        value = fuelInput,
                        onValueChange = onFuelChanged,
                        placeholder = "Add L",
                        isLight = isLight,
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("fuel_quantity_input")
                    )
                }

                // Option 3: Mileage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. Milage (km/L):",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isLight) Color(0xFF334155) else Color.White
                    )
                    MicroTextField(
                        value = mileageInput,
                        onValueChange = onMileageChanged,
                        placeholder = "km/L",
                        isLight = isLight,
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("vehicle_mileage_input")
                    )
                }
            }
        }
    }
}

@Composable
fun MicroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isLight: Boolean,
    modifier: Modifier
) {
    val tc = if (isLight) Color(0xFF0F172A) else Color.White
    val borderCol = if (isLight) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f)
    val bgCol = if (isLight) Color.White else Color.Black.copy(alpha = 0.4f)

    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = tc,
            fontWeight = FontWeight.Bold
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(tc),
        modifier = modifier.height(34.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .background(bgCol, RoundedCornerShape(6.dp))
                    .border(1.dp, borderCol, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun TrackBreadcrumbCard(
    pathPoints: List<PathPoint>,
    activeStatus: TrackingStatus,
    theme: DashboardTheme
) {
    val primaryThemeColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .testTag("breadcrumb_trail_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color = primaryThemeColor.copy(alpha = 0.15f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ROUTE PREVIEW",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "COORD: ${pathPoints.size} NODES",
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Radar Feed",
                    tint = primaryThemeColor,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Radar display sandbox box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(10.dp))
                    .border(1.dp, primaryThemeColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val cen = Offset(w / 2, h / 2)

                    // Draw Instrument Grid Layout
                    val gridColor = primaryThemeColor.copy(alpha = 0.08f)
                    val stepGridX = w / 6f
                    val stepGridY = h / 4f
                    for (i in 1..5) {
                        drawLine(gridColor, Offset(i * stepGridX, 0f), Offset(i * stepGridX, h), strokeWidth = 0.5.dp.toPx())
                    }
                    for (j in 1..3) {
                        drawLine(gridColor, Offset(0f, j * stepGridY), Offset(w, j * stepGridY), strokeWidth = 0.5.dp.toPx())
                    }

                    // Circle Sonar traces
                    drawCircle(
                        color = primaryThemeColor.copy(alpha = 0.08f),
                        radius = (w.coerceAtMost(h) / 2.8f),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Render breadcrumb navigation line coordinates if present
                    if (pathPoints.size > 1) {
                        try {
                            val minLat = pathPoints.minOf { it.latitude }
                            val maxLat = pathPoints.maxOf { it.latitude }
                            val minLng = pathPoints.minOf { it.longitude }
                            val maxLng = pathPoints.maxOf { it.longitude }

                            val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
                            val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

                            val drawPath = Path()
                            pathPoints.forEachIndexed { idx, pt ->
                                val normLng = ((pt.longitude - minLng) / lngSpan).toFloat()
                                val normLat = ((pt.latitude - minLat) / latSpan).toFloat()

                                val canvasX = 10.dp.toPx() + normLng * (w - 20.dp.toPx())
                                val canvasY = (h - 10.dp.toPx()) - (normLat * (h - 20.dp.toPx()))

                                if (idx == 0) {
                                    drawPath.moveTo(canvasX, canvasY)
                                } else {
                                    drawPath.lineTo(canvasX, canvasY)
                                }
                            }

                            drawPath(
                                path = drawPath,
                                color = primaryThemeColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Start Node Dot
                            val startPt = pathPoints.first()
                            val sLng = ((startPt.longitude - minLng) / lngSpan).toFloat()
                            val sLat = ((startPt.latitude - minLat) / latSpan).toFloat()
                            drawCircle(
                                color = SpeedEcoGreen,
                                radius = 4.dp.toPx(),
                                center = Offset(10.dp.toPx() + sLng * (w - 20.dp.toPx()), (h - 10.dp.toPx()) - (sLat * (h - 20.dp.toPx())))
                            )

                            // Current Head Dot
                            val curPt = pathPoints.last()
                            val cLng = ((curPt.longitude - minLng) / lngSpan).toFloat()
                            val cLat = ((curPt.latitude - minLat) / latSpan).toFloat()
                            drawCircle(
                                color = primaryThemeColor,
                                radius = 6.dp.toPx(),
                                center = Offset(10.dp.toPx() + cLng * (w - 20.dp.toPx()), (h - 10.dp.toPx()) - (cLat * (h - 20.dp.toPx())))
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        drawContext.canvas.nativeCanvas.drawText(
                            "STANDBY MODE",
                            cen.x,
                            cen.y + 3.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = primaryThemeColor.copy(alpha = 0.3f).toArgb()
                                textSize = 9.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                typeface = android.graphics.Typeface.MONOSPACE
                                isAntiAlias = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (activeStatus == TrackingStatus.TRACKING) "NODE REFRESH: ON" else "STANDBY FEED",
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                color = primaryThemeColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun TripControlCard(
    activeStatus: TrackingStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onEnd: () -> Unit,
    isLight: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trip_control_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color = if (isLight) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TRIP CONSOLE",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isLight) Color(0xFF475569) else Color.Gray,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeStatus == TrackingStatus.IDLE) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLight) Color(0xFFFF5A36) else MaterialTheme.colorScheme.primary,
                            contentColor = if (isLight) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_trip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Excursion",
                            tint = if (isLight) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START EXCURSION",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    if (activeStatus == TrackingStatus.TRACKING) {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pause_trip_button")
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause Excursion", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "PAUSE",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else if (activeStatus == TrackingStatus.PAUSED) {
                        Button(
                            onClick = onStart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLight) Color(0xFFFF5A36) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_trip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume Excursion",
                                tint = if (isLight) Color.White else Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "RESUME",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isLight) Color.White else Color.Black
                            )
                        }
                    }

                    // Tactile Emergency console Stop button
                    Button(
                        onClick = onEnd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7F1D1D).copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, color = Color(0xFFEF4444).copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(55.dp)
                            .height(48.dp)
                            .testTag("end_trip_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "End Excursion",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogsHeaderView(
    itemCount: Int,
    onExportClick: () -> Unit,
    onClearAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "TRIP LOGBOOK",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "$itemCount sessions recorded",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray
            )
        }

        if (itemCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onClearAllClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SpeedDangerRed
                    )
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Delete Logs", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Clear All", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onExportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("export_csv_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.Black, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EXPORT CSV", fontSize = 9.sp, color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun EmptyLogsPlaceholder(theme: DashboardTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.HistoryToggleOff,
                contentDescription = "Empty History logs",
                tint = Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No excursions logged yet.",
                fontSize = 11.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TripLogItemRow(
    trip: Trip,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val tripDate = formatter.format(Date(trip.date))
    val distanceUnit = String.format("%.2f km", trip.distanceTravelled)
    val durationText = CSVExportUtility.formatDuration(trip.duration)
    val maxSpeedText = String.format("%.1f km/h", trip.maxSpeed)
    val avgSpeedText = String.format("%.1f km/h", trip.avgSpeed)
    val fuelText = String.format("%.2f L", trip.fuelUsed)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trip_log_item_${trip.id}"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color = Color.White.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tripDate,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fuel used: $fuelText",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("DISTANCE", fontSize = 7.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(distanceUnit, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Column {
                        Text("DURATION", fontSize = 7.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(durationText, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Column {
                        Text("AVG SPEED", fontSize = 7.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(avgSpeedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Column {
                        Text("PEAK SPEED", fontSize = 7.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(maxSpeedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = getSpeedColor(trip.maxSpeed))
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .testTag("delete_trip_log_button_${trip.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Excursion",
                    tint = SpeedDangerRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CockpitBottomNavigation(
    selectedTheme: DashboardTheme,
    logCount: Int
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.04f))
            .navigationBarsPadding()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.5.dp)
                    .background(activeColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Telemetry Cockpit View",
                    tint = activeColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "DASH",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = activeColor
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .alpha(0.55f)
        ) {
            Icon(
                imageVector = Icons.Default.Assignment,
                contentDescription = "Trip History Logs",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "LOGS ($logCount)",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .alpha(0.55f)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Cockpit Settings",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "SYSTEM",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun CockpitBottomNavigation(
    selectedTheme: DashboardTheme,
    currentTab: CockpitTab,
    onTabSelected: (CockpitTab) -> Unit,
    logCount: Int
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.04f))
            .navigationBarsPadding()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavigationItem(
            name = "DASH",
            icon = Icons.Default.Speed,
            isSelected = currentTab == CockpitTab.DASH,
            activeColor = activeColor,
            onClick = { onTabSelected(CockpitTab.DASH) }
        )

        BottomNavigationItem(
            name = "RANGE",
            icon = Icons.Default.LocalGasStation,
            isSelected = currentTab == CockpitTab.RANGE,
            activeColor = activeColor,
            onClick = { onTabSelected(CockpitTab.RANGE) }
        )

        BottomNavigationItem(
            name = "HUD",
            icon = Icons.Default.Explore,
            isSelected = currentTab == CockpitTab.HUD,
            activeColor = activeColor,
            onClick = { onTabSelected(CockpitTab.HUD) }
        )

        BottomNavigationItem(
            name = "LOGS ($logCount)",
            icon = Icons.Default.Assignment,
            isSelected = currentTab == CockpitTab.LOGS,
            activeColor = activeColor,
            onClick = { onTabSelected(CockpitTab.LOGS) }
        )

        BottomNavigationItem(
            name = "SYSTEM",
            icon = Icons.Default.Settings,
            isSelected = currentTab == CockpitTab.SYSTEM,
            activeColor = activeColor,
            onClick = { onTabSelected(CockpitTab.SYSTEM) }
        )
    }
}

@Composable
fun RowScope.BottomNavigationItem(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .alpha(if (isSelected) 1.0f else 0.55f)
            .padding(vertical = 4.dp)
            .testTag("nav_${name.lowercase().split(" ").first()}_button")
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.5.dp)
                    .background(activeColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
        } else {
            Spacer(modifier = Modifier.height(8.5.dp))
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (isSelected) activeColor else Color.White,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = name,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) activeColor else Color.White
            )
        }
    }
}

enum class CockpitTab {
    DASH, RANGE, HUD, LOGS, SYSTEM
}

const val mapDarkStyleJson = """
[
  {
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#121214"
      }
    ]
  },
  {
    "elementType": "labels.icon",
    "stylers": [
      {
        "visibility": "off"
      }
    ]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#757575"
      }
    ]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [
      {
        "color": "#121214"
      }
    ]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#18181c"
      }
    ]
  },
  {
    "featureType": "road",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#1f1f23"
      }
    ]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#2c2c35"
      }
    ]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#000000"
      }
    ]
  }
]
"""

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    
    val lifecycleObserver = rememberMapLifecycleObserver(mapView)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    
    return mapView
}

@Composable
fun rememberMapLifecycleObserver(mapView: MapView): LifecycleEventObserver =
    remember(mapView) {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> { /* No-op */ }
            }
        }
    }

@Composable
fun GoogleMapView(
    pathPoints: List<PathPoint>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapViewWithLifecycle()
    
    AndroidView(
        factory = { mapView },
        modifier = modifier
    ) { view ->
        view.getMapAsync { googleMap ->
            googleMap.clear()
            
            try {
                googleMap.setMapStyle(MapStyleOptions(mapDarkStyleJson))
            } catch (e: Exception) {
                // Ignore styling errors
            }
            
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isMyLocationButtonEnabled = false
            
            if (pathPoints.isNotEmpty()) {
                val coordinates = pathPoints.map { LatLng(it.latitude, it.longitude) }
                
                // Polyline tracing
                googleMap.addPolyline(
                    PolylineOptions()
                        .addAll(coordinates)
                        .color(accentColor.toArgb())
                        .width(10f)
                )
                
                // Start Node Marker
                googleMap.addMarker(
                    MarkerOptions()
                        .position(coordinates.first())
                        .title("Start")
                )
                
                // Current Node Marker
                val lastPos = coordinates.last()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(lastPos)
                        .title("Current Location")
                )
                
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastPos, 14.5f))
            }
        }
    }
}

@Composable
fun InteractiveRadarScanBackdrop(
    accentColor: Color,
    isSimulating: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweepAngle"
    )

    val gridAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GridAlphaPulse"
    )

    Canvas(modifier = modifier.background(Color(0xFF070709))) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2, h / 2)
        val radius = w.coerceAtMost(h) / 2.2f

        drawLine(
            color = Color.White.copy(alpha = 0.04f),
            start = Offset(0f, center.y),
            end = Offset(w, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.04f),
            start = Offset(center.x, 0f),
            end = Offset(center.x, h),
            strokeWidth = 1.dp.toPx()
        )

        for (i in 1..4) {
            drawCircle(
                color = accentColor.copy(alpha = 0.05f * i),
                radius = radius * (i / 4f),
                style = Stroke(width = (1f + i * 0.5f).dp.toPx())
            )
        }

        rotate(sweepAngle, center) {
            val endPoint = Offset(
                center.x + radius,
                center.y
            )
            
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(accentColor, Color.Transparent),
                    startX = center.x,
                    endX = endPoint.x
                ),
                start = center,
                end = endPoint,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(accentColor.copy(alpha = 0.15f), Color.Transparent),
                    center = center
                ),
                startAngle = -25f,
                sweepAngle = 25f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }

        drawContext.canvas.nativeCanvas.drawText(
            "SYS NAV OVERVIEW [STANDBY]",
            center.x,
            center.y - radius - 12.dp.toPx(),
            android.graphics.Paint().apply {
                color = accentColor.copy(alpha = 0.5f).toArgb()
                textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                isAntiAlias = true
            }
        )

        drawContext.canvas.nativeCanvas.drawText(
            "N 00° 00' 00\" / W 00° 00' 00\"",
            center.x,
            center.y + radius + 22.dp.toPx(),
            android.graphics.Paint().apply {
                color = Color.Gray.copy(alpha = gridAlpha).toArgb()
                textSize = 8.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
                isAntiAlias = true
            }
        )

        if (isSimulating) {
            val randomOffsetLat = 40.dp.toPx()
            drawCircle(
                color = SpeedEcoGreen,
                radius = 6.dp.toPx(),
                center = Offset(center.x + randomOffsetLat, center.y - randomOffsetLat)
            )
            drawCircle(
                color = SpeedEcoGreen.copy(alpha = gridAlpha * 0.5f),
                radius = 18.dp.toPx() * gridAlpha,
                center = Offset(center.x + randomOffsetLat, center.y - randomOffsetLat),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
fun MapHUDTabScreen(
    activeState: TripState,
    viewModel: SpeedometerViewModel,
    theme: DashboardTheme
) {
    val accentColor = when (theme) {
        DashboardTheme.CYBER_NEON -> ElectricCyan
        DashboardTheme.CARBON_GOLD -> GoldAccent
        DashboardTheme.MATRIX_GREEN -> MatrixGreenAccent
        DashboardTheme.DAY_LIGHT -> Color(0xFFFF5A36)
    }
    
    var isSimulatingLocalRoute by remember { mutableStateOf(false) }
    var simulatedPoints by remember { mutableStateOf(listOf<PathPoint>()) }
    var simulatedSpeed by remember { mutableStateOf(0.0) }
    var simulatedDistance by remember { mutableStateOf(0.0) }
    var simulatedDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(isSimulatingLocalRoute) {
        if (isSimulatingLocalRoute) {
            val routeCoords = listOf(
                Pair(47.6062, -122.3321),
                Pair(47.6085, -122.3343),
                Pair(47.6101, -122.3365),
                Pair(47.6120, -122.3382),
                Pair(47.6145, -122.3400),
                Pair(47.6168, -122.3425),
                Pair(47.6191, -122.3451),
                Pair(47.6205, -122.3493),
                Pair(47.6224, -122.3520),
                Pair(47.6250, -122.3501),
                Pair(47.6241, -122.3458),
                Pair(47.6212, -122.3430),
                Pair(47.6185, -122.3392),
                Pair(47.6152, -122.3355),
                Pair(47.6120, -122.3330)
            )
            
            var index = 0
            simulatedPoints = listOf(PathPoint(routeCoords[0].first, routeCoords[0].second))
            simulatedDistance = 0.0
            simulatedDuration = 0L

            while (isSimulatingLocalRoute) {
                kotlinx.coroutines.delay(1000)
                simulatedDuration += 1
                
                index = (index + 1) % routeCoords.size
                val currentCoord = routeCoords[index]
                
                simulatedPoints = simulatedPoints + PathPoint(
                    latitude = currentCoord.first + (Math.random() - 0.5) * 0.0002,
                    longitude = currentCoord.second + (Math.random() - 0.5) * 0.0002
                )
                
                simulatedSpeed = if (index % 4 == 0) {
                    (45..55).random().toDouble()
                } else if (index % 4 == 1) {
                    (70..85).random().toDouble()
                } else if (index % 4 == 2) {
                    (90..115).random().toDouble()
                } else {
                    (20..35).random().toDouble()
                }
                
                simulatedDistance += (simulatedSpeed / 3600.0)
            }
        } else {
            simulatedPoints = emptyList()
            simulatedSpeed = 0.0
            simulatedDistance = 0.0
            simulatedDuration = 0L
        }
    }

    val displayPoints = if (activeState.status == TrackingStatus.TRACKING) {
        activeState.pathPoints
    } else if (isSimulatingLocalRoute) {
        simulatedPoints
    } else {
        activeState.pathPoints
    }

    val displaySpeed = if (activeState.status == TrackingStatus.TRACKING) {
        activeState.currentSpeedKmh
    } else if (isSimulatingLocalRoute) {
        simulatedSpeed
    } else {
        0.0
    }

    val displayDistance = if (activeState.status == TrackingStatus.TRACKING) {
        activeState.distanceKm
    } else if (isSimulatingLocalRoute) {
        simulatedDistance
    } else {
        0.0
    }

    val displayDuration = if (activeState.status == TrackingStatus.TRACKING) {
        activeState.durationSeconds
    } else if (isSimulatingLocalRoute) {
        simulatedDuration
    } else {
        0L
    }

    val displayAvgSpeed = if (activeState.status == TrackingStatus.TRACKING) {
        activeState.avgSpeedKmh
    } else if (isSimulatingLocalRoute && displayDuration > 0) {
        (displayDistance / (displayDuration / 3600.0)).coerceIn(0.0, 240.0)
    } else {
        0.0
    }

    val isDay = theme == DashboardTheme.DAY_LIGHT
    val panelBg = (if (isDay) Color.White else Color.Black).copy(alpha = 0.85f)
    val panelBorder = if (isDay) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.12f)
    val textPrimary = if (isDay) Color(0xFF0F172A) else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .background(if (isDay) Color.White else Color.Black)
    ) {
        if (displayPoints.isNotEmpty()) {
            GoogleMapView(
                pathPoints = displayPoints,
                accentColor = accentColor,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            InteractiveRadarScanBackdrop(
                accentColor = accentColor,
                isSimulating = isSimulatingLocalRoute,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(panelBg, RoundedCornerShape(12.dp))
                .border(1.dp, panelBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.0f", displaySpeed),
                    fontSize = 38.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    style = TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = accentColor.copy(alpha = 0.5f),
                            blurRadius = 8f
                        )
                    )
                )
                Text(
                    text = "DIGITAL KM/H",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary.copy(alpha = 0.7f)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .width(200.dp)
                .background(panelBg, RoundedCornerShape(12.dp))
                .border(1.dp, panelBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "FLIGHT TELEMETRY",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DISTANCE:", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text(String.format("%.2f km", displayDistance), fontSize = 8.sp, color = textPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DURATION:", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text(formatTripDurationCompact(displayDuration), fontSize = 8.sp, color = textPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AVG SPEED:", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text(String.format("%.1f km/h", displayAvgSpeed), fontSize = 8.sp, color = textPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("NODES PLOTTED:", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${displayPoints.size}", fontSize = 8.sp, color = accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (activeState.status == TrackingStatus.IDLE) {
                            isSimulatingLocalRoute = !isSimulatingLocalRoute
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSimulatingLocalRoute) accentColor else Color(0xFF1E293B),
                        contentColor = if (isSimulatingLocalRoute) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                    enabled = activeState.status == TrackingStatus.IDLE
                ) {
                    Icon(
                        imageVector = if (isSimulatingLocalRoute) Icons.Default.Cancel else Icons.Default.DirectionsCar,
                        contentDescription = "Simulate Drive",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSimulatingLocalRoute) "STOP SIM" else "SIMULATE DRIVE",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (activeState.status == TrackingStatus.TRACKING) {
                    Box(
                        modifier = Modifier
                            .background(SpeedEcoGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, SpeedEcoGreen, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(SpeedEcoGreen, RoundedCornerShape(3.dp))
                            )
                            Text(
                                "GPS LIVE",
                                fontSize = 8.sp,
                                color = SpeedEcoGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.LogBookTabScreen(
    allTrips: List<Trip>,
    activeState: TripState,
    fuelInput: String,
    mileageInput: String,
    rangeRemaining: Double,
    selectedTheme: DashboardTheme,
    csvDataToSave: MutableState<String>,
    documentCreatorLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    viewModel: SpeedometerViewModel
) {
    // Unity Ads Banner placement for the Logs Tab
    UnityBannerAdView(modifier = Modifier.padding(bottom = 6.dp))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LogsHeaderView(
                itemCount = allTrips.size,
                onExportClick = {
                    val csvText = CSVExportUtility.convertToCSV(allTrips)
                    csvDataToSave.value = csvText
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    documentCreatorLauncher.launch("droute_logs_$dateStr.csv")
                },
                onClearAllClick = {
                    viewModel.clearAllLogs()
                }
            )
        }

        item {
            val remainingFuel by viewModel.remainingFuel.collectAsState()
            val remainingFuelPercentage by viewModel.remainingFuelPercentage.collectAsState()
            val estimatedRangeRemaining by viewModel.estimatedRangeRemaining.collectAsState()
            val tankCapacityStr by viewModel.fuelTankCapacityInput.collectAsState()
            val tankCapacity = tankCapacityStr.toDoubleOrNull() ?: 5.0
            val activeColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("logs_fuel_status_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Fuel Info
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = "Fuel Left",
                                tint = secondaryColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "FUEL TANK STATUS",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTheme == DashboardTheme.DAY_LIGHT) Color(0xFF475569) else Color.Gray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = String.format("%.2f", remainingFuel),
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTheme == DashboardTheme.DAY_LIGHT) Color(0xFF0F172A) else Color.White
                            )
                            Text(
                                text = "L / ${String.format("%.1f", tankCapacity)} L LEFT",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (selectedTheme == DashboardTheme.DAY_LIGHT) Color(0xFF475569) else Color.LightGray,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    // Right Column: Progress bar and range
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "EST RANGE: ${String.format("%.1f", estimatedRangeRemaining)} KM",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = secondaryColor
                        )

                        // Compact horizontal gauge
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .background(
                                    if (selectedTheme == DashboardTheme.DAY_LIGHT) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(3.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(remainingFuelPercentage.toFloat())
                                    .background(
                                        Brush.horizontalGradient(listOf(secondaryColor, activeColor)),
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                        }

                        Text(
                            text = "${(remainingFuelPercentage * 100).toInt()}% Fuel Level",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (selectedTheme == DashboardTheme.DAY_LIGHT) Color(0xFF64748B) else Color.Gray
                        )
                    }
                }
            }
        }

        if (allTrips.isEmpty()) {
            item {
                EmptyLogsPlaceholder(theme = selectedTheme)
            }
        } else {
            items(allTrips) { trip ->
                TripLogItemRow(
                    trip = trip,
                    onDelete = { viewModel.deleteTrip(trip.id) }
                )
            }
        }
    }
}

@Composable
fun ColumnScope.SystemsTabScreen(
    activeState: TripState,
    viewModel: SpeedometerViewModel,
    theme: DashboardTheme,
    isLight: Boolean
) {
    val accentColor = when (theme) {
        DashboardTheme.CYBER_NEON -> ElectricCyan
        DashboardTheme.CARBON_GOLD -> GoldAccent
        DashboardTheme.MATRIX_GREEN -> MatrixGreenAccent
        DashboardTheme.DAY_LIGHT -> Color(0xFFFF5A36)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SYSTEMS DIAGNOSTICS",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, color = accentColor.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ACTIVE INTERFACE PROFILES",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    ThemeProfileRow(name = "CYBER NEON (CYBER)", desc = "High-contrast electric cyan and vivid magenta, inspired by futuristic retro-wave aesthetics.", isActive = theme == DashboardTheme.CYBER_NEON, accent = ElectricCyan)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                    ThemeProfileRow(name = "CARBON GOLD (GOLD)", desc = "Sophisticated ultra-premium gold accents set against carbonaceous textured tones.", isActive = theme == DashboardTheme.CARBON_GOLD, accent = GoldAccent)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                    ThemeProfileRow(name = "MATRIX GREEN (MATRIX)", desc = "Monochromatic cyber-grid terminal theme referencing traditional cascade phosphor tubes.", isActive = theme == DashboardTheme.MATRIX_GREEN, accent = MatrixGreenAccent)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                    ThemeProfileRow(name = "DAY LIGHT (DAY)", desc = "High-luminance day theme with maximum direct sunlight clarity and ultra high-contrast vector visuals.", isActive = theme == DashboardTheme.DAY_LIGHT, accent = Color(0xFFFF5A36))
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, color = Color.White.copy(alpha = 0.05f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "HARDWARE INTEGRITY",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    CalibrationSpecItem(label = "COCKPIT VERSION", value = "D_ROUTE v1.0.4")
                    CalibrationSpecItem(label = "DATABASE PERSISTENCE", value = "SQLITE via ROOM DB")
                    CalibrationSpecItem(label = "EXPORT STANDARD", value = "RFC 4180 CSV LOGS")
                    CalibrationSpecItem(label = "GPS RECEIVER STATUS", value = if (activeState.status == TrackingStatus.TRACKING) "ACTIVE RECEIVER" else "RECEIVER STANDBY")
                }
            }
        }

        item {
            val odometerOffset by viewModel.odometerOffsetInput.collectAsState()
            val totalDistanceTracked by viewModel.lifetimeOdometer.collectAsState()
            
            Card(
                modifier = Modifier.fillMaxWidth().testTag("odometer_calibration_card"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, color = accentColor.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "GPS ODOMETER SYSTEM",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    
                    Text(
                        text = "MATHEMATICAL BASELINE: HAVERSINE GREAT-CIRCLE FORMULA\nCalculates geodesic earth surface distance between consecutive coordinates, correcting for spherical distortion using 6371.0 km radius.",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray,
                        lineHeight = 11.sp
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIFETIME ODOMETER",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.3f KM", totalDistanceTracked),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "CALIBRATION OFFSET (KM)",
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            MicroTextField(
                                value = odometerOffset,
                                onValueChange = { viewModel.odometerOffsetInput.value = it },
                                placeholder = "0.0",
                                isLight = isLight,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.resetOdometer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("reset_odometer_button")
                    ) {
                        Text(
                            text = "RESET LIFETIME ODOMETER",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            val speedLimit by viewModel.speedLimitInput.collectAsState()
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speed_limit_calibration_card"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, color = accentColor.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "DYNAMIC SPEED THRESHOLD ALERT",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    
                    Text(
                        text = "ALERT TRIGGERS: Displays dynamic high-contrast visual alarm banners and shifts entire cockpit speed dials and gauges from core cyan to crimson-warning theme upon exceeding custom speed baseline.",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray,
                        lineHeight = 11.sp
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CURRENT SPEED THRESHOLD",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Text(
                                text = "$speedLimit KM/H",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.testTag("current_speed_limit_value_label")
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "CONFIG LIMIT (KM/H)",
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            MicroTextField(
                                value = speedLimit,
                                onValueChange = { viewModel.speedLimitInput.value = it },
                                placeholder = "100",
                                isLight = isLight,
                                modifier = Modifier
                                    .width(100.dp)
                                    .testTag("speed_limit_input_field")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeProfileRow(
    name: String,
    desc: String,
    isActive: Boolean,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(8.dp)
                .background(if (isActive) accent else Color.Gray, RoundedCornerShape(4.dp))
        )
        Column {
            Text(
                name,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isActive) accent else Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                desc,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun CalibrationSpecItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

fun formatTripDurationCompact(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d", h * 60 + m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

@Composable
fun ColumnScope.RangeTabScreen(
    fuelInput: String,
    mileageInput: String,
    fuelCapacityInput: String,
    estimatedRange: Double,
    onFuelChanged: (String) -> Unit,
    onMileageChanged: (String) -> Unit,
    onFuelCapacityChanged: (String) -> Unit,
    onReset: () -> Unit,
    isLight: Boolean,
    remainingFuel: Double,
    fuelPercent: Double,
    fuelCapacity: Double,
    theme: DashboardTheme
) {
    val accentColor = when (theme) {
        DashboardTheme.CYBER_NEON -> ElectricCyan
        DashboardTheme.CARBON_GOLD -> GoldAccent
        DashboardTheme.MATRIX_GREEN -> MatrixGreenAccent
        DashboardTheme.DAY_LIGHT -> Color(0xFFFF5A36)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "FUEL RANGE PREDICTIONS",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            RangePredictorCard(
                fuelInput = fuelInput,
                mileageInput = mileageInput,
                fuelCapacityInput = fuelCapacityInput,
                estimatedRange = estimatedRange,
                onFuelChanged = onFuelChanged,
                onMileageChanged = onMileageChanged,
                onFuelCapacityChanged = onFuelCapacityChanged,
                onReset = onReset,
                isLight = isLight
            )
        }

        item {
            var refuelAmountStr by remember { mutableStateOf("2.0") }
            val currentRemaining = remainingFuel
            val addNum = refuelAmountStr.toDoubleOrNull() ?: 0.0
            val previewTotal = currentRemaining + addNum

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("refuel_and_reset_card"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, color = accentColor.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "REFUEL & SYSTEM RESET",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )

                    Text(
                        text = "Add fuel into the vehicle tank. The newly added fuel instantly sums with the remaining fuel for updated mileage and range predictions.",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray,
                        lineHeight = 11.sp
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Dynamic refuel preview: e.g. "1.3L + 2.0L = 3.3L"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = accentColor.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = "Refuel Preview",
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "PREVIEW: %.1fL REMAINING + %.1fL ADDED = %.1fL TOTAL",
                                currentRemaining,
                                addNum,
                                previewTotal
                            ),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FUEL AMOUNT TO ADD (L)",
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            MicroTextField(
                                value = refuelAmountStr,
                                onValueChange = { refuelAmountStr = it },
                                placeholder = "2.0",
                                isLight = isLight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("refuel_amount_add_input")
                            )
                        }

                        Button(
                            onClick = {
                                val currentInputVal = fuelInput.toDoubleOrNull() ?: 0.0
                                val newTotalInput = currentInputVal + addNum
                                onFuelChanged(String.format(Locale.US, "%.1f", newTotalInput))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("add_fuel_plus_button"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Fuel",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ADD FUEL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Prominent physical reset option
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("prominent_fuel_reset_button"),
                        contentPadding = PaddingValues(vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Fuel Range Predictor",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RESET FUEL & PREDICTIONS TO DEFAULT",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, color = accentColor.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "RANGE METRICS SPECIFICATIONS",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("REMAINING FUEL", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(String.format("%.2f Liters", remainingFuel), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("FUEL PERCENTAGE", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(String.format("%.0f%%", fuelPercent * 100), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TOTAL TANK CAPACITY", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(String.format("%.1f Liters", fuelCapacity), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("AVERAGE MILEAGE", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text(String.format("%s km/L", mileageInput), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SmallRangeDialer(
    rangeRemaining: Double,
    maxRange: Double,
    isLight: Boolean,
    accentColor: Color,
    remainingFuel: Double,
    fuelCapacity: Double
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().testTag("small_range_dialer_canvas")) {
                val width = size.width
                val height = size.height
                val center = Offset(width / 2, height / 2)
                val radius = size.minDimension / 2f - 3.dp.toPx()

                // Outline track
                drawArc(
                    color = if (isLight) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.04f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Fill ratio
                val sweepLimit = if (maxRange > 0) (rangeRemaining / maxRange).coerceIn(0.0, 1.0).toFloat() else 0f
                val sweepAngle = sweepLimit * 270f

                // Active range progress arc
                drawArc(
                    color = accentColor,
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 3.5f.dp.toPx(), cap = StrokeCap.Round)
                )

                // Pointer needle or dot representation
                val needleAngle = 135f + sweepAngle
                val needleRad = Math.toRadians(needleAngle.toDouble())
                val needleLen = radius - 3.dp.toPx()
                val needlePoint = Offset(
                    center.x + (needleLen * Math.cos(needleRad)).toFloat(),
                    center.y + (needleLen * Math.sin(needleRad)).toFloat()
                )

                // Draw tiny pointer needle or indicator dot
                drawCircle(
                    color = accentColor,
                    radius = 2.dp.toPx(),
                    center = needlePoint
                )
            }

            // Text in the middle of current range
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("%.0f", rangeRemaining),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = if (isLight) Color(0xFF0F172A) else Color.White
                )
                Text(
                    text = "KM",
                    fontSize = 6.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isLight) Color(0xFF475569) else Color.Gray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = String.format("%.1fL / %.0fL CAP", remainingFuel, fuelCapacity),
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isLight) Color(0xFF475569) else Color.Gray,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ColumnScope.HudTabScreen(
    activeState: TripState,
    theme: DashboardTheme,
    isLight: Boolean,
    viewModel: SpeedometerViewModel,
    context: Context
) {
    var isMirrored by remember { mutableStateOf(false) }
    var speedUnitMph by remember { mutableStateOf(false) }
    
    val speedLimitStr by viewModel.speedLimitInput.collectAsState()
    val speedLimitValue = speedLimitStr.toDoubleOrNull() ?: 100.0
    val speedKmh = activeState.currentSpeedKmh
    val speedDisplay = if (speedUnitMph) speedKmh * 0.621371 else speedKmh
    val isOverSpeedLimit = speedDisplay > speedLimitValue
    
    val accentColor = when (theme) {
        DashboardTheme.CYBER_NEON -> ElectricCyan
        DashboardTheme.CARBON_GOLD -> GoldAccent
        DashboardTheme.MATRIX_GREEN -> MatrixGreenAccent
        DashboardTheme.DAY_LIGHT -> Color(0xFFFF5A36)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HIGH-PRECISION HUD COCKPIT",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (activeState.status == TrackingStatus.TRACKING) SpeedEcoGreen else Color.Gray,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = if (activeState.status == TrackingStatus.TRACKING) "GEOLOCATION LOCK" else "GPS IDLE",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (activeState.status == TrackingStatus.TRACKING) SpeedEcoGreen else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            HudCircularGaugeCard(
                activeState = activeState,
                accentColor = accentColor,
                isLight = isLight,
                isMirrored = isMirrored,
                speedUnitMph = speedUnitMph,
                onMirrorToggle = { isMirrored = !isMirrored },
                onUnitToggle = { speedUnitMph = !speedUnitMph },
                isOverSpeedLimit = isOverSpeedLimit,
                speedLimitStr = speedLimitStr
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, color = accentColor.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HUD EXCURSION SESSION SYSTEM",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startTrip(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeState.status == TrackingStatus.TRACKING) SpeedEcoGreen.copy(alpha = 0.2f) else SpeedEcoGreen
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f).testTag("hud_trip_start_button")
                        ) {
                            Text(
                                "START GPS", 
                                fontSize = 9.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (activeState.status == TrackingStatus.TRACKING) SpeedEcoGreen else Color.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = { viewModel.pauseTrip(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeState.status == TrackingStatus.PAUSED) SpeedWarningOrange.copy(alpha = 0.2f) else SpeedWarningOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.9f).testTag("hud_trip_pause_button")
                        ) {
                            Text(
                                "PAUSE", 
                                fontSize = 9.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (activeState.status == TrackingStatus.PAUSED) SpeedWarningOrange else Color.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = { viewModel.endTrip(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = SpeedDangerRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.1f).testTag("hud_trip_end_button")
                        ) {
                            Text("SAVE / STOP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item {
            GeolocationTelemetryGrid(
                activeState = activeState,
                accentColor = accentColor,
                isLight = isLight
            )
        }
    }
}

@Composable
fun HudCircularGaugeCard(
    activeState: TripState,
    accentColor: Color,
    isLight: Boolean,
    isMirrored: Boolean,
    speedUnitMph: Boolean,
    onMirrorToggle: () -> Unit,
    onUnitToggle: () -> Unit,
    isOverSpeedLimit: Boolean = false,
    speedLimitStr: String = "100"
) {
    val speedKmh = activeState.currentSpeedKmh
    val speedDisplay = if (speedUnitMph) speedKmh * 0.621371 else speedKmh
    val speedUnitLabel = if (speedUnitMph) "MPH" else "KM/H"
    val darkBg = if (isLight) Color(0xFFF4F6F9) else Color(0xFF04060C) // Rich neutral dark isolated minimal background
    val safeGreen = if (isLight) Color(0xFF16A34A) else Color(0xFF00FF66) // Vivid green for safety
    val warningRed = Color(0xFFFF3B30) // Brilliant red for over limit
    val finalAccentColor = if (isOverSpeedLimit) warningRed else safeGreen

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hud_circular_gauge_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color = if (isLight) Color(0xFFE2E8F0) else finalAccentColor.copy(alpha = 0.20f)),
        colors = CardDefaults.cardColors(containerColor = darkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onUnitToggle,
                    modifier = Modifier.testTag("hud_toggle_unit")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        tint = finalAccentColor,
                        contentDescription = "Toggle Measurement Unit"
                    )
                }
                
                Text(
                    text = "UNIT: $speedUnitLabel",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = finalAccentColor.copy(alpha = 0.6f)
                )
                
                Button(
                    onClick = onMirrorToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMirrored) finalAccentColor else finalAccentColor.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("hud_mirror_reflection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipToFront, 
                        contentDescription = "Mirror", 
                        tint = if (isMirrored) Color.Black else finalAccentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMirrored) "MIRRORED" else "MIRROR HUD",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMirrored) Color.Black else finalAccentColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(210.dp)
                    .graphicsLayer {
                        if (isMirrored) {
                            scaleX = -1f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hud_circular_gauge_canvas")
                ) {
                    val width = size.width
                    val height = size.height
                    val center = Offset(width / 2, height / 2)
                    val radius = (size.minDimension / 2.3f)

                    // --- OUTER DECORATIVE DASHED RINGS (HUD High Tech Aesthetic) ---
                    // 1. Subtle Outer Dashed Ring 1
                    drawArc(
                        color = if (isLight) Color(0xFFE2E8F0) else finalAccentColor.copy(alpha = 0.12f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - (radius + 12.dp.toPx()), center.y - (radius + 12.dp.toPx())),
                        size = Size((radius + 12.dp.toPx()) * 2, (radius + 12.dp.toPx()) * 2),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 8.dp.toPx()), 0f)
                        )
                    )

                    // 2. Subtle Outer Solid Ring 2
                    drawArc(
                        color = if (isLight) Color(0xFFE2E8F0) else finalAccentColor.copy(alpha = 0.08f),
                        startAngle = 130f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(center.x - (radius + 6.dp.toPx()), center.y - (radius + 6.dp.toPx())),
                        size = Size((radius + 6.dp.toPx()) * 2, (radius + 6.dp.toPx()) * 2),
                        style = Stroke(width = 0.75f.dp.toPx())
                    )

                    // 3. Subtle Inner Dashed Ring 3
                    drawArc(
                        color = if (isLight) Color(0xFFE2E8F0) else finalAccentColor.copy(alpha = 0.10f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - (radius - 12.dp.toPx()), center.y - (radius - 12.dp.toPx())),
                        size = Size((radius - 12.dp.toPx()) * 2, (radius - 12.dp.toPx()) * 2),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f)
                        )
                    )

                    // 4. Base Inactive Trace Track
                    drawArc(
                        color = if (isLight) Color(0xFFE2E8F0) else finalAccentColor.copy(alpha = 0.08f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // --- DYNAMIC VISUAL THRESHOLD INDICATORS (HUD VIEW) ---
                    val maxUnits = if (speedUnitMph) 150.0 else 240.0
                    val speedLimitValue = speedLimitStr.toDoubleOrNull() ?: (if (speedUnitMph) 60.0 else 100.0)
                    val limitProgress = (speedLimitValue / maxUnits).coerceIn(0.0, 1.0).toFloat()
                    val limitAngle = 135f + limitProgress * 270f
                    val remainingSweep = (1.0f - limitProgress) * 270f

                    // A. Translucent danger / redline warning zone from threshold to max speed
                    if (remainingSweep > 0.1f) {
                        drawArc(
                            color = warningRed.copy(alpha = 0.18f),
                            startAngle = limitAngle,
                            sweepAngle = remainingSweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(
                                width = 8.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    // B. Physical dynamic tick notch at the exact limit angle boundary
                    val radLimit = Math.toRadians(limitAngle.toDouble())
                    val markerOuterRadius = radius + 6.dp.toPx()
                    val markerInnerRadius = radius - 4.dp.toPx()
                    
                    val markerStartX = center.x + (markerInnerRadius * Math.cos(radLimit)).toFloat()
                    val markerStartY = center.y + (markerInnerRadius * Math.sin(radLimit)).toFloat()
                    val markerEndX = center.x + (markerOuterRadius * Math.cos(radLimit)).toFloat()
                    val markerEndY = center.y + (markerOuterRadius * Math.sin(radLimit)).toFloat()
                    
                    // Radiant glow underlay
                    drawLine(
                        color = warningRed.copy(alpha = 0.40f),
                        start = Offset(markerStartX, markerStartY),
                        end = Offset(markerEndX, markerEndY),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Precise core marker
                    drawLine(
                        color = warningRed,
                        start = Offset(markerStartX, markerStartY),
                        end = Offset(markerEndX, markerEndY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val sweepProgress = (speedDisplay / maxUnits).coerceIn(0.0, 1.0).toFloat()
                    val targetSweepAngle = sweepProgress * 270f

                    // 5. Active sweep progress dual-glow segments
                    drawArc(
                        color = finalAccentColor.copy(alpha = 0.25f),
                        startAngle = 135f,
                        sweepAngle = targetSweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawArc(
                        color = finalAccentColor,
                        startAngle = 135f,
                        sweepAngle = targetSweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 6. Glowing Ticks (Every 10 KM/H / 5 MPH)
                    val tickSteps = if (speedUnitMph) 150 else 240
                    val majorStep = if (speedUnitMph) 25 else 40
                    val minorStep = if (speedUnitMph) 5 else 10
                    
                    for (i in 0..tickSteps step minorStep) {
                        val angleRatio = i.toFloat() / tickSteps
                        val currentAngle = 135f + (angleRatio * 270f)
                        val angleRad = Math.toRadians(currentAngle.toDouble())

                        val isMajor = i % majorStep == 0
                        val tickLen = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                        val startLen = radius - tickLen
                        val endLen = radius
                        
                        val startTickX = center.x + (startLen * Math.cos(angleRad)).toFloat()
                        val startTickY = center.y + (startLen * Math.sin(angleRad)).toFloat()
                        
                        val endTickX = center.x + (endLen * Math.cos(angleRad)).toFloat()
                        val endTickY = center.y + (endLen * Math.sin(angleRad)).toFloat()

                        val activeTick = speedDisplay >= i
                        val tickColor = if (activeTick) {
                            finalAccentColor
                        } else {
                            if (isLight) Color(0xFFE2E8F0) else finalAccentColor.copy(alpha = 0.15f)
                        }

                        if (activeTick) {
                            drawLine(
                                color = finalAccentColor.copy(alpha = 0.3f),
                                start = Offset(startTickX, startTickY),
                                end = Offset(endTickX, endTickY),
                                strokeWidth = if (isMajor) 4.dp.toPx() else 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        drawLine(
                            color = tickColor,
                            start = Offset(startTickX, startTickY),
                            end = Offset(endTickX, endTickY),
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        if (isMajor) {
                            val textRadius = radius - 18.dp.toPx()
                            val textX = center.x + (textRadius * Math.cos(angleRad)).toFloat()
                            val textY = center.y + (textRadius * Math.sin(angleRad)).toFloat()

                            drawContext.canvas.nativeCanvas.drawText(
                                i.toString(),
                                textX,
                                textY + 3.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = if (activeTick) {
                                        finalAccentColor.toArgb()
                                    } else {
                                        if (isLight) Color(0xFFE2E8F0).toArgb() else finalAccentColor.copy(alpha = 0.35f).toArgb()
                                    }
                                    textSize = 7.5f.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                    isAntiAlias = true
                                }
                            )
                        }
                    }

                    // Cursor telemetry point at exact current angle
                    val cursorAngle = 135f + targetSweepAngle
                    val cursorRad = Math.toRadians(cursorAngle.toDouble())
                    val cursorPoint = Offset(
                        center.x + (radius * Math.cos(cursorRad)).toFloat(),
                        center.y + (radius * Math.sin(cursorRad)).toFloat()
                    )
                    // Sleek glowing analog needle pointing to current speed representation
                    val needleLength = radius * 0.85f
                    val needleEndX = center.x + (needleLength * Math.cos(cursorRad)).toFloat()
                    val needleEndY = center.y + (needleLength * Math.sin(cursorRad)).toFloat()
                    
                    // Draw needle line
                    drawLine(
                        color = finalAccentColor,
                        start = center,
                        end = Offset(needleEndX, needleEndY),
                        strokeWidth = 2.2f.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Center hub cap for the needle
                    drawCircle(
                        color = finalAccentColor,
                        radius = 6.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = if (isLight) Color.White else Color(0xFF04060C),
                        radius = 3.dp.toPx(),
                        center = center
                    )

                    drawCircle(
                        color = finalAccentColor,
                        radius = 4.dp.toPx(),
                        center = cursorPoint
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = cursorPoint
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isOverSpeedLimit) {
                        Text(
                            text = "⚠️ OVER LIMIT",
                            color = finalAccentColor,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    val formattedSpeed = String.format(Locale.US, "%.0f", speedDisplay)
                    Text(
                        text = formattedSpeed,
                        fontSize = 54.sp,
                        fontFamily = FontFamily.Monospace,
                        color = finalAccentColor,
                        fontWeight = FontWeight.Black,
                        style = TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = finalAccentColor.copy(alpha = 0.65f),
                                offset = Offset(0f, 0f),
                                blurRadius = 14f
                            )
                        ),
                        modifier = Modifier.testTag("hud_speed_display_value")
                    )
                    
                    Text(
                        text = speedUnitLabel,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLight) Color(0xFF475569) else finalAccentColor.copy(alpha = 0.60f),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    
                    val mps = speedKmh / 3.6
                    Text(
                        text = String.format(Locale.US, "%.2f m/s", mps),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLight) Color(0xFF475569) else finalAccentColor.copy(alpha = 0.40f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Text(
                text = if (isMirrored) "PLACE PHONE ON DASHBOARD UNDER WINDSHIELD TO REFLECT NORMAL VIEW" else "ACTIVATE MIRROR HUD AND PLACE PHONE ON THE DASHBOARD FOR WINDSHIELD REFLECTION",
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isMirrored) finalAccentColor.copy(alpha = 0.8f) else Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
fun GeolocationTelemetryGrid(
    activeState: TripState,
    accentColor: Color,
    isLight: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GeolocationCard(
                title = "COORDINATE POINT",
                value = String.format(Locale.US, "%.5f° N\n%.5f° E", activeState.latitude, activeState.longitude),
                icon = Icons.Default.LocationOn,
                accentColor = accentColor,
                isLight = isLight,
                modifier = Modifier.weight(1f)
            )

            GeolocationCard(
                title = "API HORIZ. ACCURACY",
                value = if (activeState.accuracyMeters > 0.1f) String.format(Locale.US, "±%.1f METERS\n%s LOCK", activeState.accuracyMeters, if (activeState.accuracyMeters < 10f) "PRISTINE" else "STANDARD") else "NO GPS LOCK\nSIGNAL SEARCH",
                icon = Icons.Default.MyLocation,
                accentColor = if (activeState.accuracyMeters > 0.1f && activeState.accuracyMeters < 10f) SpeedEcoGreen else accentColor,
                isLight = isLight,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GeolocationCard(
                title = "BAROMETRIC HEIGHT",
                value = String.format(Locale.US, "%.1f METERS\nABS ELEVATION", activeState.altitudeMeters),
                icon = Icons.Default.TrendingUp,
                accentColor = accentColor,
                isLight = isLight,
                modifier = Modifier.weight(1f)
            )

            val bearingStr = getBearingDirection(activeState.bearingDegrees)
            GeolocationCard(
                title = "COMPASS BEARING",
                value = String.format(Locale.US, "%.1f° DEGREES\nHEADING %s", activeState.bearingDegrees, bearingStr),
                icon = Icons.Default.Explore,
                accentColor = accentColor,
                isLight = isLight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun getBearingDirection(bearing: Float): String {
    val b = (bearing % 360 + 360) % 360
    return when {
        b >= 337.5 || b < 22.5 -> "NORTH [N]"
        b >= 22.5 && b < 67.5 -> "NORTHEAST [NE]"
        b >= 67.5 && b < 112.5 -> "EAST [E]"
        b >= 112.5 && b < 157.5 -> "SOUTHEAST [SE]"
        b >= 157.5 && b < 202.5 -> "SOUTH [S]"
        b >= 202.5 && b < 247.5 -> "SOUTHWEST [SW]"
        b >= 247.5 && b < 292.5 -> "WEST [W]"
        b >= 292.5 && b < 337.5 -> "NORTHWEST [NW]"
        else -> "N"
    }
}

@Composable
fun GeolocationCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isLight: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("geolocation_telemetry_" + title.lowercase().replace(" ", "_")),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color = accentColor.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(11.dp)
                )
            }

            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 14.sp
            )
        }
    }
}
