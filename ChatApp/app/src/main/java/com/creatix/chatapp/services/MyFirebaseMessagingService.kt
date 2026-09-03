package com.creatix.chatapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.creatix.chatapp.MainActivity
import com.creatix.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * الخدمة دي بتشتغل لوحدها حتى لو التطبيق مقفول تمامًا،
 * وبتستقبل أي إشعار جاي من Firebase Cloud Messaging.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "chat_messages_channel"
        const val CHANNEL_NAME = "رسائل الشات"
        const val EXTRA_OPEN_CHAT_UID = "openChatWithUid"
        private const val EXTRA_SENDER_KEY = "senderKey"

        // بنخزن هنا كل الرسائل اللي لسه معروضة كإشعار لكل شخص،
        // عشان لو جالنا رسالتين من نفس الشخص نجمعهم في إشعار واحد بدل ما يبقوا اتنين
        private val pendingMessages = ConcurrentHashMap<String, MutableList<String>>()

        /** بتتنادى لما المستخدم يفتح المحادثة (بالضغط على الإشعار أو من جوه التطبيق)
         *  عشان نمسح الرسائل المتجمعة ونقفل إشعارها */
        fun clearConversation(context: Context, senderKey: String) {
            if (senderKey.isBlank()) return
            pendingMessages.remove(senderKey)
            NotificationManagerCompat.from(context).cancel(senderKey.hashCode())
        }

        /**
         * لازم القناة دي تتعمل أول ما التطبيق يفتح (مش بس لما إشعار يوصل وهو فاتح)،
         * عشان لما إشعار notification+data يوصل والتطبيق مقفول، النظام يقدر يعرضه
         * على القناة دي مباشرة من غير ما يشغّل onMessageReceived أصلاً.
         */
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** بيتنفذ لو المستخدم مسح الإشعار بإيده (سحب لبرا) من غير ما يضغط عليه */
    class DismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val senderKey = intent.getStringExtra(EXTRA_SENDER_KEY) ?: return
            pendingMessages.remove(senderKey)
        }
    }

    /** كل ما التوكن بتاع الجهاز يتغير، لازم نحدثه في Firestore عشان السيرفر يقدر يبعتله */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    /** بتظهر رسالة على الشاشة عشان نقدر نتابع تنفيذ الكود خطوة خطوة من غير Logcat */
    private fun debugToast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "DEBUG: $text", Toast.LENGTH_LONG).show()
        }
    }

    /** لما إشعار يوصل والتطبيق شغال (foreground) أو في الخلفية */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        debugToast("onMessageReceived اتنفذت")

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "رسالة جديدة"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""
        val senderUid = message.data["senderId"] ?: ""

        try {
            showNotification(title, body, senderUid)
        } catch (e: Exception) {
            debugToast("استثناء في showNotification: ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    private fun showNotification(title: String, body: String, senderUid: String) {
        createChannelIfNeeded()
        debugToast("القناة اتعملت، جاري بناء الإشعار")

        // مفتاح تجميع الرسائل: بيستخدم الـ senderUid لو موجود، عشان كل محادثة
        // ليها إشعار واحد بس بيتحدث لما يجيله رسالة جديدة
        val senderKey = senderUid.ifBlank { title }
        val notificationId = senderKey.hashCode()

        val messages = pendingMessages.getOrPut(senderKey) { mutableListOf() }
        messages.add(body)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_CHAT_UID, senderUid)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, DismissReceiver::class.java).apply {
            putExtra(EXTRA_SENDER_KEY, senderKey)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this, notificationId, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // بنستخدم MessagingStyle عشان نعرض كل الرسائل اللي لسه ماتفتحتش من نفس الشخص
        // في إشعار واحد بس (زي الوتس بالظبط)
        val sender = Person.Builder().setName(title).build()
        val me = Person.Builder().setName("أنا").build()
        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (messages.size > 1) title else null)

        messages.forEach { line ->
            messagingStyle.addMessage(line, System.currentTimeMillis(), sender)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.notification_color))
            .setStyle(messagingStyle)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val areEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        debugToast("قبل notify() - الإشعارات مفعّلة على مستوى النظام: $areEnabled")

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
            debugToast("notify() اتنفذت من غير أي استثناء")
        } catch (e: SecurityException) {
            // المستخدم لسه ما ادّاش إذن الإشعارات (أندرويد 13+)
            debugToast("SecurityException في notify(): ${e.message}")
        } catch (e: Exception) {
            debugToast("استثناء تاني في notify(): ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    private fun createChannelIfNeeded() {
        ensureNotificationChannel(applicationContext)
    }
}
