package com.inertial.navigation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.inertial.navigation.model.AppDestinations
import org.osmdroid.util.GeoPoint

@Composable
fun MainNavigationScreen() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    var userLocation by rememberSaveable { mutableStateOf(GeoPoint(52.2297, 21.0122)) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                when (currentDestination) {
                    AppDestinations.HOME -> GpsScreen(
                        currentLocation = userLocation,
                        onLocationReceived = { newLocation -> userLocation = newLocation }
                    )

                    AppDestinations.MAP -> MapScreen(
                        currentLocation = userLocation
                    )

                    AppDestinations.PROFILE -> InfoScreen(
                        title = "Profile"
                    )
                }
            }
        }
    }
}