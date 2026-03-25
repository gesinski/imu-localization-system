package com.inertial.navigation.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.inertial.navigation.logic.NativeLib
import com.inertial.navigation.model.Mode
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import com.inertial.navigation.ui.components.HomeViewModel
import org.osmdroid.util.GeoPoint
import kotlin.math.*

@Composable
fun GpsScreen(currentLocation: GeoPoint, onLocationReceived: (GeoPoint) -> Unit, homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val nativeLib = remember { NativeLib() }
    val activeMode = homeViewModel.activeMode

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) homeViewModel.locationText = "Permissions granted! Re-select mode."
        else homeViewModel.locationText = "GPS permissions denied!"
    }

    var locationText by remember { mutableStateOf("Click to start") }


    var acc by remember { mutableStateOf(FloatArray(3)) }
    var gyro by remember { mutableStateOf(FloatArray(3)) }
    var mag by remember { mutableStateOf(FloatArray(3)) }
    var heading by remember { mutableStateOf(0f) }
    var stepCount by remember { mutableIntStateOf(0) }



    val locationCallback = remember {
        object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                if (activeMode == Mode.GPS || activeMode == Mode.GPS_AND_IMU) {
                    result.lastLocation?.let { location ->
                        val point = GeoPoint(location.latitude, location.longitude)
                        onLocationReceived(point)
                        homeViewModel.locationText = "Lat: ${location.latitude}\nLon: ${location.longitude}"
                    }
                }
            }
        }
    }

    LaunchedEffect(activeMode) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission && (activeMode == Mode.GPS || activeMode == Mode.GPS_AND_IMU)) {
            val request = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
            ).setMinUpdateIntervalMillis(500).build()

            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            homeViewModel.locationText = "GPS Active"
        } else {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            if (activeMode == Mode.IMU) homeViewModel.locationText = "GPS Stopped (IMU Active)"
        }
    }


    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        var lastTimestamp = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> acc = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> mag = event.values.clone()
                    Sensor.TYPE_GYROSCOPE -> {
                        gyro = event.values.clone()

                        if (lastTimestamp != 0L) {
                            val dt = (event.timestamp - lastTimestamp) * 1e-9f

                            val result = nativeLib.updateIMU(
                                gyro[0], gyro[1], gyro[2],
                                acc[0], acc[1], acc[2],
                                mag[0], mag[1], mag[2],
                                dt
                            )

                            heading = result[0]
                            val isStep = (result[1] == 1.0f)
                            val stepLength = result[2]

                            if (isStep) {
                                android.util.Log.d("IMU_DEBUG", "STEP Length: $stepLength")
                            }

                            if (isStep && (activeMode == Mode.IMU || activeMode == Mode.GPS_AND_IMU)) {
                                stepCount++
                                val latRad = Math.toRadians(currentLocation.latitude)
                                val mPerDegLat = 111111.0
                                val mPerDegLon = 111111.0 * cos(latRad)

                                val dLat = (stepLength * cos(heading.toDouble())) / mPerDegLat
                                val dLon = (stepLength * sin(heading.toDouble())) / mPerDegLon

                                onLocationReceived(GeoPoint(
                                    currentLocation.latitude + dLat,
                                    currentLocation.longitude + dLon
                                ))
                            }
                        }
                        lastTimestamp = event.timestamp
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Heading", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "${Math.toDegrees(heading.toDouble()).toInt()}°",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Steps Detected", style = MaterialTheme.typography.labelMedium)
                Text("$stepCount", style = MaterialTheme.typography.headlineLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Raw Sensor Data", style = MaterialTheme.typography.titleMedium)
        SensorDataRow("ACC", acc)
        SensorDataRow("GYRO", gyro)
        SensorDataRow("MAG", mag)

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Status:", style = MaterialTheme.typography.titleLarge)
        Text(text = homeViewModel.locationText)

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Select Tracking Mode:", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.entries.forEach { mode ->
                androidx.compose.material3.Button(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission && mode != Mode.IMU) {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            homeViewModel.updateMode(mode)
                        }
                    },
                    colors = if (activeMode == mode)
                        androidx.compose.material3.ButtonDefaults.buttonColors()
                    else
                        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(mode.name)
                }
            }
        }
    }
}

@Composable
fun SensorDataRow(label: String, values: FloatArray) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.width(50.dp))
        Text("X: ${"%.2f".format(values[0])}", style = MaterialTheme.typography.bodySmall)
        Text("Y: ${"%.2f".format(values[1])}", style = MaterialTheme.typography.bodySmall)
        Text("Z: ${"%.2f".format(values[2])}", style = MaterialTheme.typography.bodySmall)
    }
}