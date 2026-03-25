package com.inertial.navigation.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(currentLocation: GeoPoint) {
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