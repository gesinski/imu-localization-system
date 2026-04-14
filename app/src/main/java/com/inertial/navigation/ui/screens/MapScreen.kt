package com.inertial.navigation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inertial.navigation.logic.NavigationManager
import com.inertial.navigation.model.RecordingState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(navManager: NavigationManager) {
    val context = LocalContext.current
    val currentLocation = navManager.currentLocation
    val recordingState = navManager.recordingState
    val route = navManager.recordedRoute
    val recordingTimeSeconds = navManager.recordingTimeSeconds

    var isExpanded by remember { mutableStateOf(true) }

    val formattedTime = remember(recordingTimeSeconds) {
        val h = recordingTimeSeconds / 3600
        val m = (recordingTimeSeconds % 3600) / 60
        val s = recordingTimeSeconds % 60
        if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    val mapView = remember { MapView(context) }

    val currentMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "You are here"
        }
    }

    val startMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Start"
            // You could set a custom icon here for the start point
        }
    }

    val polyline = remember {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.parseColor("#FC4C02") // Strava Orange
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(18.0)
                    overlays.add(polyline)
                    overlays.add(startMarker)
                    overlays.add(currentMarker)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                currentMarker.position = currentLocation

                if (route.isNotEmpty()) {
                    startMarker.position = route.first()
                    startMarker.isEnabled = true

                    // Update polyline with all past positions + current live position
                    val allPoints = route.toList()
                    polyline.setPoints(allPoints)
                } else {
                    startMarker.isEnabled = false
                    polyline.setPoints(emptyList())
                }

                mv.controller.animateTo(currentLocation)
                mv.invalidate()
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            contentAlignment = if (isExpanded) Alignment.Center else Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isExpanded) {
                    if (recordingState != RecordingState.IDLE) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    RecordingButtons(
                        state = recordingState,
                        onStart = { navManager.startRecording() },
                        onPause = { navManager.pauseRecording() },
                        onResume = { navManager.resumeRecording() },
                        onStop = { navManager.stopRecording() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        onClick = { isExpanded = false },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        tonalElevation = 4.dp
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = { isExpanded = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 8.dp,
                        tonalElevation = 4.dp
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expand",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingButtons(
    state: RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        when (state) {
            RecordingState.IDLE -> {
                LargeCircularButton(
                    onClick = onStart,
                    icon = Icons.Default.PlayArrow,
                    containerColor = Color(0xFFFF4500)
                )
            }
            RecordingState.RECORDING -> {
                LargeCircularButton(
                    onClick = onPause,
                    icon = Icons.Default.Pause,
                    containerColor = Color.LightGray
                )
            }
            RecordingState.PAUSED -> {
                LargeCircularButton(
                    onClick = onResume,
                    icon = Icons.Default.PlayArrow,
                    containerColor = Color(0xFF4CAF50)
                )
                LargeCircularButton(
                    onClick = onStop,
                    icon = Icons.Default.Stop,
                    containerColor = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun LargeCircularButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = Color.White
        )
    }
}