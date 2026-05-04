package com.campusbite.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.campusbite.app.ui.navigation.NavGraph
import com.campusbite.app.ui.theme.CampusBiteTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        installSplashScreen()

        //  FCM TOKEN FETCH (ADD THIS BLOCK)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Token: $token")

                saveTokenToFirestore(token)
            } else {
                Log.e("FCM", "Token fetch failed", task.exception)
            }
        }

        setContent {
            CampusBiteTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }

    // SAVE TOKEN IN FIRESTORE
    private fun saveTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d("FCM", "Token saved successfully")
            }
            .addOnFailureListener {
                Log.e("FCM", "Failed to save token", it)
            }
    }
}