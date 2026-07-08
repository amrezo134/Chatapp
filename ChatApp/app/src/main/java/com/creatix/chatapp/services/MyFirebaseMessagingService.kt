package com.creatix.chatapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.creatix.chatapp.MainActivity
import com.creatix.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * الخدمة دي بتشتغل لوحدها حتى لو التطبيق مقفول تمامًا،
 * وبتستقبل أي إشعار جاي من Firebase Cloud Messaging.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "chat_messages_channel"
        const val CHANNEL_NAME = "رسائل الشات"
    }

    /** كل ما التوكن بتاع الجهاز يتغير، لازم نحدثه في Firestore عشان السيرفر يقدر يبعتله */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    /** لما إشعار يوصل والتطبيق شغال (foreground) */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "رسالة جديدة"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""
        val senderUid = message.data["senderId"] ?: ""

        showNotification(title, body, senderUid)
    }

    private fun showNotification(title: String, body: String, senderUid: String) {
        createChannelIfNeeded()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("openChatWithUid", senderUid)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, senderUid.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // الأيقونة الأبيض/أسود
            .setColor(getColor(R.color.notification_color))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
    }
}
