package com.creatix.chatapp.repository

import android.content.Context
import com.creatix.chatapp.R
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.data.chatIdFor
import com.creatix.chatapp.data.GroupMessage
import com.creatix.chatapp.data.GLOBAL_GROUP_ID
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.services.FcmPushSender
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()

    // ---------------------------------------------------------------
    // رفع الملفات على Cloudflare (يستخدمها كل أنواع المرفقات: مستند، صورة، فيديو، صوت، ملصق)
    // ---------------------------------------------------------------

    private val uploadApiBase = "https://chatapp-upload-api.amrezo134.workers.dev"
    private val httpClient = OkHttpClient()
    private val maxFileSizeBytes = 20 * 1024 * 1024L // 20 ميجا

    /** بيرفع الملف على الـ Worker وبيرجع رابطه العام. بيرمي Exception لو فشل الرفع أو الحجم أكبر من 20 ميجا. */
    suspend fun uploadFileToCloudflare(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        if (bytes.size > maxFileSizeBytes) {
            throw Exception("حجم الملف أكبر من 20 ميجا")
        }

        val idToken = FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)?.await()?.token ?: ""

        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$uploadApiBase/upload")
            .addHeader("Authorization", "Bearer $idToken")
            .addHeader("X-File-Name", fileName)
            .addHeader("X-File-Type", mimeType)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 413) throw Exception("حجم الملف أكبر من 20 ميجا")
            if (!response.isSuccessful) throw Exception("فشل الرفع: ${response.code}")
            val json = JSONObject(response.body!!.string())
            json.getString("url")
        }
    }

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

    /** بيجيب بيانات المستخدم الحالي كاملة (الاسم، الصورة، البايو..) لصفحة البروفايل */
    suspend fun getCurrentUser(uid: String): ChatUser? {
        return try {
            db.collection("users").document(uid).get().await()
                .toObject(ChatUser::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
    /** إرسال رسالة (لازم مستند الشات يتعمل الأول عشان قواعد الأمان) - بتدعم النص والمرفقات كلها */
    suspend fun sendMessage(
        context: Context,
        senderId: String,
        receiverId: String,
        text: String,
        replyTo: Message? = null,
        replyToSenderName: String = "",
        forwarded: Boolean = false,
        fileUrl: String = "",
        fileName: String = "",
        fileType: String = "",
        durationMs: Long = 0L,
        contactName: String = "",
        contactPhone: String = "",
        pollQuestion: String = "",
        pollOptions: List<String> = emptyList(),
        eventTitle: String = "",
        eventTimestamp: Long = 0L
    ) {
        val chatId = chatIdFor(senderId, receiverId)
        val chatRef = db.collection("chats").document(chatId)
        val messageTimestamp = System.currentTimeMillis()

        val previewText = when {
            text.isNotBlank() -> text
            fileType == "image" -> "صورة"
            fileType == "video" -> "فيديو"
            fileType == "audio" -> "مقطع صوتي"
            fileType == "document" -> "مستند"
            fileType == "sticker" -> "ملصق"
            contactName.isNotBlank() -> "جهة اتصال: $contactName"
            pollQuestion.isNotBlank() -> "استطلاع رأي: $pollQuestion"
            eventTitle.isNotBlank() -> "مناسبة: $eventTitle"
            else -> text
        }

        chatRef.set(
            mapOf(
                "participants" to listOf(senderId, receiverId),
                "lastMessage" to previewText,
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
            timestamp = messageTimestamp,
            fileUrl = fileUrl,
            fileName = fileName,
            fileType = fileType,
            durationMs = durationMs,
            contactName = contactName,
            contactPhone = contactPhone,
            pollQuestion = pollQuestion,
            pollOptions = pollOptions,
            eventTitle = eventTitle,
            eventTimestamp = eventTimestamp,
            replyToId = replyTo?.id ?: "",
            replyToText = replyTo?.let {
                it.text.ifBlank {
                    when {
                        it.fileType == "image" -> "صورة"
                        it.fileType.isNotBlank() -> it.fileName
                        else -> ""
                    }
                }
            } ?: "",
            replyToSenderName = if (replyTo != null) replyToSenderName else "",
            forwarded = forwarded
        )
        docRef.set(message).await()

        // بعد ما الرسالة اتسجلت، ابعت إشعار push مباشر للمستقبل (بدون سيرفر)
        notifyReceiver(context, senderId, receiverId, previewText)
    }

    /** تعديل نص رسالة في شات خاص (بيستخدمها صاحب الرسالة بس من الـ UI) */
    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .update(mapOf("text" to newText, "edited" to true))
            .await()
    }

    /** حذف رسالة في شات خاص (حذف عند الطرفين، بيسيب أثر "تم حذف هذه الرسالة") */
    suspend fun deleteMessage(chatId: String, messageId: String) {
        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "text" to "",
                    "deleted" to true,
                    "fileUrl" to "",
                    "fileName" to "",
                    "fileType" to ""
                )
            )
            .await()
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
    // ميزة "عدد الرسائل الغير مقروءة" (Unread count) — تظهر في قائمة المحادثات
    // ---------------------------------------------------------------

    /** بيراقب عدد الرسائل اللي جاتلي في الشات ده ولسه ما اتفتحتش (seen = false) */
    fun observeUnreadCount(chatId: String, myUid: String): Flow<Int> = callbackFlow {
        val listener = db.collection("chats").document(chatId)
            .collection("messages")
            .whereEqualTo("receiverId", myUid)
            .whereEqualTo("seen", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.size() ?: 0)
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
    // ميزة "بيكتب دلوقتي..." للجروب العام (ممكن أكتر من شخص يكتبوا مع بعض)
    // ---------------------------------------------------------------

    /** بيسجل/بيمسح اسم اليوزر من قائمة "بيكتبوا دلوقتي" بتاعة الجروب */
    fun setGroupTyping(uid: String, displayName: String, isTyping: Boolean) {
        val groupRef = db.collection("groups").document(GLOBAL_GROUP_ID)
        if (isTyping) {
            groupRef.set(mapOf("typing" to mapOf(uid to displayName)), SetOptions.merge())
        } else {
            groupRef.update("typing.$uid", com.google.firebase.firestore.FieldValue.delete())
                .addOnFailureListener { /* هيفشل لو المستند أو الفيلد مش موجود أصلاً، تجاهل */ }
        }
    }

    /** بيراقب أسماء كل اليوزرز (ما عدا أنا) اللي بيكتبوا دلوقتي في الجروب */
    fun observeGroupTyping(myUid: String): Flow<List<String>> = callbackFlow {
        val listener = db.collection("groups").document(GLOBAL_GROUP_ID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                @Suppress("UNCHECKED_CAST")
                val typingMap = snapshot?.get("typing") as? Map<String, String>
                val names = typingMap
                    ?.filterKeys { it != myUid }
                    ?.values
                    ?.toList()
                    ?: emptyList()
                trySend(names)
            }
        awaitClose { listener.remove() }
    }

    // ---------------------------------------------------------------
    // ميزة "عدد رسائل الجروب الغير مقروءة"
    // ---------------------------------------------------------------

    /** بيراقب آخر وقت فتحت فيه الجروب (بيتسجل في groups/global_group/reads/{uid}) */
    fun observeGroupLastRead(myUid: String): Flow<Long> = callbackFlow {
        val ref = db.collection("groups").document(GLOBAL_GROUP_ID)
            .collection("reads").document(myUid)
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.getLong("lastReadTimestamp") ?: 0L)
        }
        awaitClose { listener.remove() }
    }

    /** بتتنادى وقت ما اليوزر يفتح شاشة الجروب، بتسجل إنه شاف كل الرسائل لحد دلوقتي */
    suspend fun markGroupAsRead(myUid: String) {
        db.collection("groups").document(GLOBAL_GROUP_ID)
            .collection("reads").document(myUid)
            .set(mapOf("lastReadTimestamp" to System.currentTimeMillis()), SetOptions.merge())
            .await()
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
    suspend fun sendGroupMessage(
        context: Context,
        senderId: String,
        senderName: String,
        text: String,
        forwarded: Boolean = false
    ) {
        val groupRef = db.collection("groups").document(GLOBAL_GROUP_ID)
        groupRef.set(mapOf("lastMessage" to text, "lastTimestamp" to System.currentTimeMillis()), SetOptions.merge()).await()

        val docRef = groupRef.collection("messages").document()
        val message = GroupMessage(
            id = docRef.id,
            senderId = senderId,
            senderName = senderName,
            text = text,
            forwarded = forwarded
        )
        docRef.set(message).await()

        // بعد ما الرسالة اتسجلت، ابعت إشعار push لكل أعضاء الجروب (كل المستخدمين ما عدا اللي بعت)
        notifyGroupMembers(context, senderId, senderName, text)
    }

    /** تعديل نص رسالة في الجروب العام */
    suspend fun editGroupMessage(messageId: String, newText: String) {
        db.collection("groups").document(GLOBAL_GROUP_ID)
            .collection("messages").document(messageId)
            .update(mapOf("text" to newText, "edited" to true))
            .await()
    }

    /** حذف رسالة في الجروب العام */
    suspend fun deleteGroupMessage(messageId: String) {
        db.collection("groups").document(GLOBAL_GROUP_ID)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "text" to "",
                    "deleted" to true,
                    "fileUrl" to "",
                    "fileName" to "",
                    "fileType" to ""
                )
            )
            .await()
    }

    /**
     * بيجيب كل اليوزرز المسجلين (ما عدا المرسل)، وبيبعتلهم كلهم إشعار push بالتوازي.
     * لو فشل الإرسال لواحد منهم (توكن قديم مثلاً)، الباقي بيكمل عادي.
     */
    private suspend fun notifyGroupMembers(context: Context, senderId: String, senderName: String, text: String) {
        try {
            val usersSnapshot = db.collection("users").get().await()
            val projectId = context.getString(R.string.project_id)

            coroutineScope {
                usersSnapshot.documents
                    .filter { it.id != senderId }
                    .mapNotNull { doc ->
                        val token = doc.getString("fcmToken")
                        if (token.isNullOrBlank()) null else token
                    }
                    .map { token ->
                        async {
                            try {
                                FcmPushSender.sendNotification(
                                    context = context,
                                    projectId = projectId,
                                    targetToken = token,
                                    title = "الجروب العام - $senderName",
                                    body = text,
                                    senderId = senderId
                                )
                            } catch (e: Exception) {
                                logDebug("group_notify_error", e.message ?: e.toString())
                            }
                        }
                    }
                    .awaitAll()
            }
            logDebug("success", "group notification sent by $senderId")
        } catch (e: Exception) {
            logDebug("error", e.message ?: e.toString())
        }
    }

    // ---------------------------------------------------------------
    // الجروبات المخصصة (Custom Groups) — أي مستخدم يقدر يعمل جروب ويختار أعضاءه
    // ---------------------------------------------------------------

    /** بيراقب كل الجروبات المخصصة اللي أنا عضو فيها */
    fun observeMyCustomGroups(myUid: String): Flow<List<ChatGroup>> = callbackFlow {
        val listener = db.collection("custom_groups")
            .whereArrayContains("memberIds", myUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val groups = snapshot?.documents
                    ?.mapNotNull { it.toObject(ChatGroup::class.java) }
                    ?.sortedByDescending { it.lastTimestamp }
                    ?: emptyList()
                trySend(groups)
            }
        awaitClose { listener.remove() }
    }

    /** بيعمل جروب جديد، وبيحط صاحب الجروب نفسه ضمن الأعضاء تلقائيًا */
    suspend fun createCustomGroup(name: String, ownerId: String, memberIds: List<String>): String {
        val docRef = db.collection("custom_groups").document()
        val allMembers = (memberIds + ownerId).distinct()
        val group = ChatGroup(
            id = docRef.id,
            name = name,
            ownerId = ownerId,
            memberIds = allMembers,
            lastMessage = "",
            lastTimestamp = System.currentTimeMillis()
        )
        docRef.set(group).await()
        return docRef.id
    }

    /** بث لحظي لرسائل جروب مخصص معين */
    fun observeCustomGroupMessages(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        val listener = db.collection("custom_groups").document(groupId)
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

    /** إرسال رسالة في جروب مخصص */
    suspend fun sendCustomGroupMessage(
        context: Context,
        groupId: String,
        senderId: String,
        senderName: String,
        text: String,
        forwarded: Boolean = false
    ) {
        val groupRef = db.collection("custom_groups").document(groupId)
        groupRef.set(
            mapOf("lastMessage" to text, "lastTimestamp" to System.currentTimeMillis()),
            SetOptions.merge()
        ).await()

        val docRef = groupRef.collection("messages").document()
        val message = GroupMessage(
            id = docRef.id,
            senderId = senderId,
            senderName = senderName,
            text = text,
            forwarded = forwarded
        )
        docRef.set(message).await()

        notifyCustomGroupMembers(context, groupId, senderId, senderName, text)
    }

    /** تعديل نص رسالة في جروب مخصص */
    suspend fun editCustomGroupMessage(groupId: String, messageId: String, newText: String) {
        db.collection("custom_groups").document(groupId)
            .collection("messages").document(messageId)
            .update(mapOf("text" to newText, "edited" to true))
            .await()
    }

    /** حذف رسالة في جروب مخصص */
    suspend fun deleteCustomGroupMessage(groupId: String, messageId: String) {
        db.collection("custom_groups").document(groupId)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "text" to "",
                    "deleted" to true,
                    "fileUrl" to "",
                    "fileName" to "",
                    "fileType" to ""
                )
            )
            .await()
    }

    /** بيبعت إشعار push لكل أعضاء الجروب المخصص ما عدا اللي بعت الرسالة */
    private suspend fun notifyCustomGroupMembers(
        context: Context,
        groupId: String,
        senderId: String,
        senderName: String,
        text: String
    ) {
        try {
            val groupDoc = db.collection("custom_groups").document(groupId).get().await()
            val group = groupDoc.toObject(ChatGroup::class.java) ?: return
            val projectId = context.getString(R.string.project_id)

            coroutineScope {
                group.memberIds
                    .filter { it != senderId }
                    .map { uid ->
                        async {
                            try {
                                val userDoc = db.collection("users").document(uid).get().await()
                                val token = userDoc.getString("fcmToken")
                                if (!token.isNullOrBlank()) {
                                    FcmPushSender.sendNotification(
                                        context = context,
                                        projectId = projectId,
                                        targetToken = token,
                                        title = "${group.name} - $senderName",
                                        body = text,
                                        senderId = senderId
                                    )
                                }
                            } catch (e: Exception) {
                                logDebug("custom_group_notify_error", e.message ?: e.toString())
                            }
                        }
                    }
                    .awaitAll()
            }
            logDebug("success", "custom group notification sent by $senderId")
        } catch (e: Exception) {
            logDebug("error", e.message ?: e.toString())
        }
    }

    /** بيسجل/بيمسح اسم اليوزر من قائمة "بيكتبوا دلوقتي" بتاعة جروب مخصص */
    fun setCustomGroupTyping(groupId: String, uid: String, displayName: String, isTyping: Boolean) {
        val groupRef = db.collection("custom_groups").document(groupId)
        if (isTyping) {
            groupRef.set(mapOf("typing" to mapOf(uid to displayName)), SetOptions.merge())
        } else {
            groupRef.update("typing.$uid", com.google.firebase.firestore.FieldValue.delete())
                .addOnFailureListener { /* تجاهل لو مش موجودة أصلاً */ }
        }
    }

    /** بيراقب أسماء كل اليوزرز (ما عدا أنا) اللي بيكتبوا دلوقتي في جروب مخصص */
    fun observeCustomGroupTyping(groupId: String, myUid: String): Flow<List<String>> = callbackFlow {
        val listener = db.collection("custom_groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                @Suppress("UNCHECKED_CAST")
                val typingMap = snapshot?.get("typing") as? Map<String, String>
                val names = typingMap
                    ?.filterKeys { it != myUid }
                    ?.values
                    ?.toList()
                    ?: emptyList()
                trySend(names)
            }
        awaitClose { listener.remove() }
    }

    /** بيراقب آخر وقت فتحت فيه الجروب المخصص ده (بيتسجل في custom_groups/{id}/reads/{uid}) */
    fun observeCustomGroupLastRead(groupId: String, myUid: String): Flow<Long> = callbackFlow {
        val ref = db.collection("custom_groups").document(groupId)
            .collection("reads").document(myUid)
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.getLong("lastReadTimestamp") ?: 0L)
        }
        awaitClose { listener.remove() }
    }

    /** بتتنادى وقت ما اليوزر يفتح شاشة الجروب المخصص، بتسجل إنه شاف كل الرسائل لحد دلوقتي */
    suspend fun markCustomGroupAsRead(groupId: String, myUid: String) {
        db.collection("custom_groups").document(groupId)
            .collection("reads").document(myUid)
            .set(mapOf("lastReadTimestamp" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }
}
