package com.campusbite.app.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.campusbite.app.MainActivity
import com.campusbite.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.media.AudioAttributes
import android.media.RingtoneManager

class CampusBiteMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "CampusBite"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have an order update."

        val orderId = message.data["orderId"].orEmpty()
        val status = message.data["status"].orEmpty()
        val type = message.data["type"].orEmpty()

        showOrderNotification(
            title = title,
            body = body,
            orderId = orderId,
            status = status,
            type = type
        )
    }

    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update(
                mapOf(
                    "fcmToken" to token,
                    "fcmTokens" to FieldValue.arrayUnion(token),
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
    }

    private fun showOrderNotification(
        title: String,
        body: String,
        orderId: String,
        status: String,
        type: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) return
        }

        createOrderUpdatesChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("orderId", orderId)
            putExtra("status", status)
            putExtra("type", type)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            "${orderId}_${type}_${status}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, ORDER_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()


        try {
            NotificationManagerCompat.from(this)
                .notify("${orderId}_${type}_${status}".hashCode(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun createOrderUpdatesChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            ORDER_UPDATES_CHANNEL_ID,
            "Order Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications about CampusBite order status updates"
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val ORDER_UPDATES_CHANNEL_ID = "order_updates_high"    }
}