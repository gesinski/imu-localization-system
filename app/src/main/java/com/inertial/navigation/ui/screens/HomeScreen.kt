package com.inertial.navigation.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import com.inertial.navigation.logic.NavigationManager
import com.inertial.navigation.model.Mode

@Composable
fun GpsScreen(navManager: NavigationManager) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) navManager.statusText = "Permissions granted! Select mode."
        else navManager.statusText = "GPS permissions denied!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Heading", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "${Math.toDegrees(navManager.heading.toDouble()).toInt()}°",
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
                Text("${navManager.stepCount}", style = MaterialTheme.typography.headlineLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Raw Sensor Data", style = MaterialTheme.typography.titleMedium)
        SensorDataRow("ACC", navManager.accValues)
        SensorDataRow("GYRO", navManager.gyroValues)
        SensorDataRow("MAG", navManager.magValues)

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Status Systemu
        Text(text = "System Status:", style = MaterialTheme.typography.titleLarge)
        Text(text = navManager.statusText, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Select Tracking Mode:", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Mode.entries.forEach { mode ->
                Button(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission && mode != Mode.IMU) {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            navManager.updateMode(mode)
                        }
                    },
                    colors = if (navManager.activeMode == mode)
                        ButtonDefaults.buttonColors()
                    else
                        ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(mode.name, style = MaterialTheme.typography.labelSmall)
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