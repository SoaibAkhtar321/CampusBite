package com.campusbite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.campusbite.app.ui.navigation.NavGraph
import com.campusbite.app.ui.theme.CampusBiteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusBiteTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}