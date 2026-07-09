package com.creatix.chatapp.repository

import android.content.Context
import com.creatix.chatapp.R
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.data.chatIdFor
import com.creatix.chatapp.data.GroupMessage
import com.creatix.chatapp.data.GLOBAL_GROUP_ID
import com.creatix.chatapp.services.FcmPushSender
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()

    /** يجيب بيانات كل اليوزرز (لعمل شات جديد) */
    fun observeUsers(currentUid: String): Flow<List<ChatUser>> = callbackFlow {
        val listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val users = snapshot?.documents
                    ?.mapNotNull { it.toObject(ChatUser::class.java) }
                    ?.filter { it.uid != currentUid }
                    ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    /** بث لحظي (real-time) للرسائل بتاعة شات معين */
    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener: ListenerRegistration = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val messages = snapshot?.documents
                    ?.mapNotNull { it.toObject(Message::class.java) }
                    ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getCurrentUserName(uid: String): String {
        return try {
            db.collection("users").document(uid).get().await()
                .getString("displayName")?.ifBlank { null } ?: "أنا"
        } catch (e: Exception) {
            "أنا"
            }
        }
        
    /** بيتأكد إن مستند الشات موجود (بالـ participants) قبل ما نـ observe أي حاجة جواه */
    suspend fun ensureChatDocument(myUid: String, otherUid: String) {
        val chatId = chatIdFor(myUid, otherUid)
        val chatRef = db.collection("chats").document(chatId)
        try {
            val snapshot = chatRef.get().await()
            if (!snapshot.exists()) {
                chatRef.set(
                    mapOf("participants" to listOf(myUid, otherUid)),
                    SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    /** إرسال رسالة (لازم مستند الشات يتعمل الأول عشان قواعد الأمان) */
    suspend fun sendMessage(context: Context, senderId: String, receiverId: String, text: String) {
        val chatId = chatIdFor(senderId, receiverId)
        val chatRef = db.collection("chats").document(chatId)
        val messageTimestamp = System.currentTimeMillis()

        chatRef.set(
            mapOf(
                "participants" to listOf(senderId, receiverId),
                "lastMessage" to text,
                "lastTimestamp" to messageTimestamp
            ),
            SetOptions.merge()
        ).await()

        val docRef = chatRef.collection("messages").document()
        val message = Message(
            id = docRef.id,
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            timestamp = messageTimestamp
        )
        docRef.set(message).await()

        // بعد ما الرسالة اتسجلت، ابعت إشعار push مباشر للمستقبل (بدون سيرفر)
        notifyReceiver(context, senderId, receiverId, text)
    }

    /**
     * بيجيب توكن المستقبل واسم المرسل، وبيبعت الإشعار مباشرة.
     * لو فشل الإرسال (النت مقطوع، التوكن قديم..) الرسالة نفسها متأثرتش، بنسجل الخطأ بس.
     */
    private suspend fun notifyReceiver(context: Context, senderId: String, receiverId: String, text: String) {
        try {
            val receiverDoc = db.collection("users").document(receiverId).get().await()
            val receiverToken = receiverDoc.getString("fcmToken")
            if (receiverToken.isNullOrBlank()) {
                logDebug("no_token", "receiver $receiverId has no fcmToken saved")
                return
            }

            val senderDoc = db.collection("users").document(senderId).get().await()
            val senderName = senderDoc.getString("displayName")?.ifBlank { null } ?: "رسالة جديدة"

            val projectId = context.getString(R.string.project_id)
            FcmPushSender.sendNotification(
                context = context,
                projectId = projectId,
                targetToken = receiverToken,
                title = senderName,
                body = text,
                senderId = senderId
            )
            logDebug("success", "sent to $receiverId")
        } catch (e: Exception) {
            logDebug("error", e.message ?: e.toString())
        }
    }

    private fun logDebug(status: String, message: String) {
        db.collection("debug_logs").add(
            mapOf(
                "status" to status,
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    // ---------------------------------------------------------------
    // ميزة "بيكتب دلوقتي..." (Typing indicator)
    // ---------------------------------------------------------------

    /** بيسجل في مستند الشات إن اليوزر ده بيكتب دلوقتي أو خلص كتابة: typing.{uid} = true/false */
    fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        db.collection("chats").document(chatId)
            .set(mapOf("typing" to mapOf(uid to isTyping)), SetOptions.merge())
    }

    /** بيراقب هل "الطرف التاني" بيكتب دلوقتي ولا لأ */
    fun observeTyping(chatId: String, otherUid: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                @Suppress("UNCHECKED_CAST")
                val typingMap = snapshot?.get("typing") as? Map<String, Boolean>
                trySend(typingMap?.get(otherUid) == true)
            }
        awaitClose { listener.remove() }
    }

    // ---------------------------------------------------------------
    // ميزة "تمت القراءة" (Read receipts) — بونص بسيط بيفيد الإشعارات كمان
    // ---------------------------------------------------------------

    /** بيعلّم كل الرسائل اللي وصلتلي من الطرف التاني ولسه ما اتقرتش، على إنها اتقرت */
    suspend fun markMessagesAsSeen(chatId: String, myUid: String, messages: List<Message>) {
        val unseen = messages.filter { it.receiverId == myUid && !it.seen }
        if (unseen.isEmpty()) return

        val batch = db.batch()
        unseen.forEach { message ->
            val ref = db.collection("chats").document(chatId)
                .collection("messages").document(message.id)
            batch.update(ref, "seen", true)
        }
        batch.commit().await()
    }
    // ---------------------------------------------------------------
    // الجروب العام: بيضم كل المستخدمين المسجلين في قاعدة البيانات
    // ---------------------------------------------------------------

    /** بث لحظي لرسائل الجروب العام */
    fun observeGroupMessages(): Flow<List<GroupMessage>> = callbackFlow {
        val listener = db.collection("groups").document(GLOBAL_GROUP_ID)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val messages = snapshot?.documents
                    ?.mapNotNull { it.toObject(GroupMessage::class.java) }
                    ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    /** إرسال رسالة في الجروب العام */
    suspend fun sendGroupMessage(senderId: String, senderName: String, text: String) {
        val groupRef = db.collection("groups").document(GLOBAL_GROUP_ID)
        groupRef.set(mapOf("lastMessage" to text, "lastTimestamp" to System.currentTimeMillis()), SetOptions.merge()).await()

        val docRef = groupRef.collection("messages").document()
        val message = GroupMessage(
            id = docRef.id,
            senderId = senderId,
            senderName = senderName,
            text = text
        )
        docRef.set(message).await()
    }
}
