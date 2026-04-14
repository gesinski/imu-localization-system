package com.inertial.navigation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inertial.navigation.logic.NavigationManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

@Composable
fun InfoScreen(navManager: NavigationManager, title: String) {
    var selectedFile by remember { mutableStateOf<File?>(null) }

    if (selectedFile == null) {
        GpxListView(
            navManager = navManager,
            onFileSelected = { selectedFile = it }
        )
    } else {
        GpxDetailView(
            file = selectedFile!!,
            onBack = { selectedFile = null }
        )
    }
}

@Composable
fun GpxListView(navManager: NavigationManager, onFileSelected: (File) -> Unit) {
    var files by remember { mutableStateOf(navManager.getGpxFiles()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Activity History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp),
            fontWeight = FontWeight.Bold
        )

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Text("No activities recorded yet", color = Color.Gray)
                }
            }
        } else {
            LazyColumn {
                items(files) { file ->
                    GpxItem(
                        file = file,
                        onClick = { onFileSelected(file) },
                        onDelete = {
                            navManager.deleteGpxFile(file)
                            files = navManager.getGpxFiles()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun GpxItem(file: File, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(file.name) },
        supportingContent = { Text(date) },
        leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpxDetailView(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val points = remember(file) { parseGpx(file) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (points.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No path data found in file")
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                val polyline = Polyline().apply {
                                    outlinePaint.color = android.graphics.Color.parseColor("#FC4C02")
                                    outlinePaint.strokeWidth = 12f
                                    setPoints(points)
                                }
                                overlays.add(polyline)
                                if (points.isNotEmpty()) {
                                    controller.setZoom(16.0)
                                    controller.setCenter(points.first())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Activity Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Points recorded: ${points.size}")
                        Text("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))}")
                    }
                }
            }
        }
    }
}

fun parseGpx(file: File): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    try {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val trkpts = doc.getElementsByTagName("trkpt")
        
        for (i in 0 until trkpts.length) {
            val node = trkpts.item(i)
            val lat = node.attributes.getNamedItem("lat").nodeValue.toDouble()
            val lon = node.attributes.getNamedItem("lon").nodeValue.toDouble()
            points.add(GeoPoint(lat, lon))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return points
}
