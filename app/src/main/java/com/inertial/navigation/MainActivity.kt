package com.inertial.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.inertial.navigation.ui.theme.InertialnavigationTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
class MainActivity : ComponentActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var map : MapView
    private var userMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        enableEdgeToEdge()
        setContent {
            InertialnavigationTheme {
                InertialnavigationApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun InertialnavigationApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    var userLocation by remember { mutableStateOf(GeoPoint(52.2297, 21.0122)) } // Domyślnie Warszawa lub 0.0, 0.0
    var currentMode by remember { mutableStateOf("GPS") }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (currentDestination) {
                    AppDestinations.HOME -> GpsScreen(onLocationReceived = { userLocation = it })
                    AppDestinations.MAP -> MapScreen(currentLocation = userLocation)
                    AppDestinations.PROFILE -> InfoScreen("Profil")
                }
            }
        }
    }
}

@Composable
fun GpsScreen(onLocationReceived: (GeoPoint) -> Unit) {

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var isTracking by remember { mutableStateOf(false) }


    var locationText by remember { mutableStateOf("Click the button to obtain position") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            locationText = "Permissions granted! Click Start again."
        } else {
            locationText = "No GPS permissions admitted!"
        }
    }

    // Update callback
    val locationCallback = remember {
        object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                for (location in result.locations) {
                    val point = GeoPoint(location.latitude, location.longitude)
                    onLocationReceived(point)
                    locationText = "Lat: ${location.latitude}\nLon: ${location.longitude}"
                }
            }
        }
    }

    //sensors
    var accData by remember { mutableStateOf("Acc: x, y, z") }
    var gyroData by remember { mutableStateOf("Gyro: x, y, z") }
    var magData by remember { mutableStateOf("Mag: x, y, z") }

    val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager

    DisposableEffect(Unit) {
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                event ?: return
                val values = event.values
                val text = "x: %.2f, y: %.2f, z: %.2f".format(values[0], values[1], values[2])

                when (event.sensor.type) {
                    android.hardware.Sensor.TYPE_ACCELEROMETER -> accData = "Acc: $text"
                    android.hardware.Sensor.TYPE_GYROSCOPE -> gyroData = "Gyro: $text"
                    android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> magData = "Mag: $text"
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }

        val acc = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)
        val mag = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)

        sensorManager.registerListener(listener, acc, android.hardware.SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, gyro, android.hardware.SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, mag, android.hardware.SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "GPS Location:", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = locationText)
        Spacer(modifier = Modifier.height(24.dp))

        //sensors
        Text(text = "Sensor Data:", style = MaterialTheme.typography.titleMedium)
        Text(text = accData, style = MaterialTheme.typography.bodySmall)
        Text(text = gyroData, style = MaterialTheme.typography.bodySmall)
        Text(text = magData, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (!isTracking) {
                    val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, 1000 // 1 second
                    ).setMinUpdateIntervalMillis(500).build()

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                    isTracking = true
                    locationText = "GPS active"
                }
                else {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    isTracking = false
                    locationText = "GPS inactive"
                }
            }
            else {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }) {
            Text(if (isTracking) "Stop GPS" else "Start Live GPS")
        }
    }
}

@Composable
fun MapScreen (currentLocation: GeoPoint) {
    val context = LocalContext.current

    val marker = remember {
        Marker(MapView(context)).apply {
            title = "Current position"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(18.0)

                overlays.add(marker)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapView ->
            marker.position = currentLocation
            mapView.controller.animateTo(currentLocation)
            mapView.invalidate()
        }
    )

}
enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    MAP("Map", Icons.Default.Place),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun InfoScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Display: $title")
    }
}