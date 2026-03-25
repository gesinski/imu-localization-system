package com.inertial.navigation.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.inertial.navigation.model.Mode

class HomeViewModel : ViewModel() {

    var activeMode by mutableStateOf(Mode.IMU)
        private set

    var locationText by mutableStateOf("Select mode to start")

    fun updateMode(newMode: Mode) {
        activeMode = newMode
    }
}