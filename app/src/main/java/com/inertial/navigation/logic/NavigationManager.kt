package com.inertial.navigation.logic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import com.inertial.navigation.model.Mode
import com.inertial.navigation.model.RecordingState

class NavigationManager(private val context: Context) : SensorEventListener {

    private val nativeLib = NativeLib()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    var currentLocation by mutableStateOf(GeoPoint(52.2297, 21.0122))
    var activeMode by mutableStateOf(Mode.IMU)
    var heading by mutableFloatStateOf(0f)
    var stepCount by mutableIntStateOf(0)
    var statusText by mutableStateOf("System Ready")

    var recordingState by mutableStateOf(RecordingState.IDLE)
    val recordedRoute = mutableStateListOf<GeoPoint>()
    var recordingTimeSeconds by mutableLongStateOf(0L)

    private var timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (recordingState == RecordingState.RECORDING) {
                recordingTimeSeconds++
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private var gpxFile: File? = null

    var accValues by mutableStateOf(FloatArray(3))
    var gyroValues by mutableStateOf(FloatArray(3))
    var magValues by mutableStateOf(FloatArray(3))

    private var lastTimestamp = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (activeMode == Mode.GPS || activeMode == Mode.GPS_AND_IMU) {
                result.lastLocation?.let {
                    val newPoint = GeoPoint(it.latitude, it.longitude)
                    updateCurrentLocation(newPoint)
                    statusText = "GPS : ${it.latitude.toString().take(7)}, ${it.longitude.toString().take(7)}"
                }
            }
        }
    }

    private fun updateCurrentLocation(newPoint: GeoPoint) {
        currentLocation = newPoint
        if (recordingState == RecordingState.RECORDING) {
            recordedRoute.add(newPoint)
            savePointToGpx(newPoint)
        }
    }

    fun startRecording() {
        recordedRoute.clear()
        recordingTimeSeconds = 0L
        recordingState = RecordingState.RECORDING
        recordedRoute.add(currentLocation)
        initGpxFile()
        savePointToGpx(currentLocation)
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    fun pauseRecording() {
        recordingState = RecordingState.PAUSED
        timerHandler.removeCallbacks(timerRunnable)
    }

    fun resumeRecording() {
        recordingState = RecordingState.RECORDING
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    fun stopRecording() {
        val path = gpxFile?.absolutePath
        recordingState = RecordingState.IDLE
        timerHandler.removeCallbacks(timerRunnable)
        finalizeGpxFile()
        statusText = "Saved to: $path"
    }

    private fun initGpxFile() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "route_$timestamp.gpx"
        gpxFile = File(context.getExternalFilesDir(null), fileName)
        
        val header = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="InertialNavigation" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <name>Route $timestamp</name>
                <trkseg>
        """.trimIndent() + "\n"
        
        FileOutputStream(gpxFile).use { it.write(header.toByteArray()) }
    }

    private fun savePointToGpx(point: GeoPoint) {
        val file = gpxFile ?: return
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        
        val pointXml = """
                  <trkpt lat="${point.latitude}" lon="${point.longitude}">
                    <time>$time</time>
                  </trkpt>
        """.trimIndent() + "\n"
        
        FileOutputStream(file, true).use { it.write(pointXml.toByteArray()) }
    }

    private fun finalizeGpxFile() {
        val file = gpxFile ?: return
        val footer = """
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        FileOutputStream(file, true).use { it.write(footer.toByteArray()) }
        gpxFile = null
    }

    fun getGpxFiles(): List<File> {
        val dir = context.getExternalFilesDir(null)
        return dir?.listFiles { file -> file.extension == "gpx" }?.toList() ?: emptyList()
    }

    fun deleteGpxFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    fun updateMode(newMode: Mode) {
        activeMode = newMode
        refreshGpsSubscription()
    }

    fun start() {
        val sensors = listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD)
        sensors.forEach { type ->
            sensorManager.getDefaultSensor(type)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        refreshGpsSubscription()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun refreshGpsSubscription() {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission && (activeMode == Mode.GPS || activeMode == Mode.GPS_AND_IMU)) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build()

            try {
                fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                statusText = "GPS Error: Permission missing"
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accValues = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> magValues = event.values.clone()
            Sensor.TYPE_GYROSCOPE -> {
                gyroValues = event.values.clone()
                processImu(event.timestamp)
            }
        }
    }

    private fun processImu(timestamp: Long) {
        if (lastTimestamp != 0L) {
            val dt = (timestamp - lastTimestamp) * 1e-9f

            val result = nativeLib.updateIMU(
                gyroValues[0], gyroValues[1], gyroValues[2],
                accValues[0], accValues[1], accValues[2],
                magValues[0], magValues[1], magValues[2],
                dt
            )

            heading = result[0]
            val isStep = result[1] == 1.0f
            val stepLength = result[2]

            if (isStep) {
                stepCount++
                if (activeMode == Mode.IMU || activeMode == Mode.GPS_AND_IMU) {
                    calculateDeadReckoning(stepLength.toDouble())
                }
            }
        }
        lastTimestamp = timestamp
    }

    private fun calculateDeadReckoning(stepLength: Double) {
        val latRad = Math.toRadians(currentLocation.latitude)
        val mPerDegLat = 111111.0
        val mPerDegLon = 111111.0 * cos(latRad)

        val dLat = (stepLength * cos(heading.toDouble())) / mPerDegLat
        val dLon = (stepLength * sin(heading.toDouble())) / mPerDegLon

        updateCurrentLocation(GeoPoint(currentLocation.latitude + dLat, currentLocation.longitude + dLon))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}