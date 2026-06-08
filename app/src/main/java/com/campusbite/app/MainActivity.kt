package com.campusbite.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.campusbite.app.ui.navigation.NavGraph
import com.campusbite.app.ui.theme.CampusBiteTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val messaging: FirebaseMessaging by lazy {
        FirebaseMessaging.getInstance()
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser

        if (user != null) {
            fetchAndSaveFcmToken(user.uid)
        }
    }
    private val notificationOrderId = mutableStateOf<String?>(null)
    private val notificationType = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        installSplashScreen()

        requestNotificationPermissionIfNeeded()
        handleNotificationIntent(intent)

        setContent {
            CampusBiteTheme {
                val navController = rememberNavController()
                val orderIdFromNotification by notificationOrderId
                val typeFromNotification by notificationType

                NavGraph(
                    navController = navController,
                    notificationOrderId = orderIdFromNotification,
                    notificationType = typeFromNotification,
                    onNotificationHandled = {
                        notificationOrderId.value = null
                        notificationType.value = null
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun fetchAndSaveFcmToken(userId: String) {
        messaging.token
            .addOnSuccessListener { token ->
                Log.d(TAG, "FCM token fetched: $token")
                saveFcmTokenToFirestore(userId, token)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to fetch FCM token", error)
            }
    }

    private fun saveFcmTokenToFirestore(
        userId: String,
        token: String
    ) {
        firestore.collection("users")
            .document(userId)
            .update(
                mapOf(
                    "fcmToken" to token,
                    "fcmTokens" to FieldValue.arrayUnion(token),
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved successfully")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save FCM token", error)
            }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val orderId = intent?.getStringExtra("orderId").orEmpty()
        val type = intent?.getStringExtra("type").orEmpty()

        if (orderId.isNotBlank()) {
            notificationOrderId.value = orderId
        }

        if (type.isNotBlank()) {
            notificationType.value = type
        }
    }

    companion object {
        private const val TAG = "CampusBiteFCM"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}