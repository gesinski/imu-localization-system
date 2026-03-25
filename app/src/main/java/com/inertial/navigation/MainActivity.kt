package com.inertial.navigation

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inertial.navigation.logic.NavigationManager
import com.inertial.navigation.ui.theme.InertialnavigationTheme
import com.inertial.navigation.ui.screens.MainNavigationScreen
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private lateinit var navManager: NavigationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        navManager = NavigationManager(this)
        navManager.start()


        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        enableEdgeToEdge()
        setContent {
            InertialnavigationTheme {
                MainNavigationScreen(navManager)
            }
        }
    }
}