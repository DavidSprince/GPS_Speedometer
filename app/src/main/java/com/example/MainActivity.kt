package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.TripDatabase
import com.example.data.TripRepository
import com.example.ui.DashboardScreen
import com.example.ui.DDriveLoadingScreen
import com.example.ui.theme.DRouteTheme
import com.example.ui.theme.ElectricCyan
import com.example.utils.UnityAdsManager
import com.example.viewmodel.SpeedometerViewModel
import com.example.viewmodel.SpeedometerViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var db: TripDatabase
    private lateinit var repository: TripRepository
    private lateinit var viewModel: SpeedometerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Room DB components cleanly
        db = TripDatabase.getDatabase(applicationContext)
        repository = TripRepository(db.tripDao)
        
        // Build ViewModel using standard robust ViewModelProvider to prevent viewModels lazy delegate errors
        viewModel = ViewModelProvider(
            this, 
            SpeedometerViewModelFactory(applicationContext, repository)
        )[SpeedometerViewModel::class.java]

        // Initialize Unity Ads SDK with requested Game ID 800003175
        UnityAdsManager.initialize(applicationContext)

        enableEdgeToEdge()

        setContent {
            var showLoadingScreen by remember { mutableStateOf(true) }

            if (showLoadingScreen) {
                DDriveLoadingScreen(onFinished = { showLoadingScreen = false })
            } else {
                var locationGranted by remember {
                    mutableStateOf(hasLocationPermissions())
                }

                // Launcher to request location and notification permissions dynamically
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { perms ->
                    val fineLocationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    val coarseLocationGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    locationGranted = fineLocationGranted || coarseLocationGranted
                }

                // Trigger permission request dynamically at app launch
                LaunchedEffect(Unit) {
                    if (!locationGranted) {
                        val requestedList = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestedList.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionsLauncher.launch(requestedList.toTypedArray())
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (locationGranted) {
                            DashboardScreen(viewModel = viewModel)
                        } else {
                            PermissionFallbackScreen(onRequestPermissions = {
                                val requestedList = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestedList.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionsLauncher.launch(requestedList.toTypedArray())
                            })
                        }
                    }
                }
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        val fineLoc = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLoc = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLoc || coarseLoc
    }
}

@Composable
fun PermissionFallbackScreen(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "No Location Permissions HUD Mode",
                tint = ElectricCyan,
                modifier = Modifier.size(72.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "D_ROUTE NEEDS GPS ACCESS",
                fontSize = 20.sp,
                color = ElectricCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "To drive the holographic dashboard speeds, telemetry logbooks, trip range estimation, and real-time Breadcrumb trajectories, high-precision GPS positioning permissions must be unlocked.",
                fontSize = 11.sp,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Text(
                    text = "GRANT GPS PERMISSIONS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
