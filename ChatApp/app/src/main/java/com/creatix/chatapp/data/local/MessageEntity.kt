package com.creatix.chatapp.data.local

import androidx.room.Entity
import androidx.room.Index
import com.creatix.chatapp.data.GroupMessage
import com.creatix.chatapp.data.Message
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسخة محلية (SQLite عن طريق Room) من الرسايل. بتستخدم لكل أنواع الشاتات:
 * chatKey = "chat:<chatId>" للشات الخاص، "global" للجروب العام، "group:<groupId>" للجروب المخصص.
 * pending = true معناها الرسالة اتكتبت محليًا ولسه السيرفر ما أكدش استلامها.
 */
@Entity(
    tableName = "cached_messages",
    primaryKeys = ["chatKey", "id"],
    indices = [Index(value = ["chatKey", "timestamp"])]
)
data class MessageEntity(
    val chatKey: String,
    val id: String,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val text: String,
    val timestamp: Long,
    val seen: Boolean,
    val fileUrl: String,
    val fileName: String,
    val fileType: String,
    val durationMs: Long,
    val contactName: String,
    val contactPhone: String,
    val pollQuestion: String,
    val pollOptionsJson: String,
    val pollVotesJson: String,
    val eventTitle: String,
    val eventTimestamp: Long,
    val replyToId: String,
    val replyToText: String,
    val replyToSenderName: String,
    val edited: Boolean,
    val deleted: Boolean,
    val forwarded: Boolean,
    val reactionsJson: String,
    val pending: Boolean,
    /** وقت السيرفر بالميلي ثانية (من حقل serverTimestamp في Firestore)، أو 0 لو لسه pending أو رسالة قديمة */
    val serverTimestampMillis: Long,
    /** true معناها السيرفر ماعندوش الرسالة دي (اتمسحت بعد الاستلام) والكاش المحلي هو النسخة الوحيدة */
    val serverPruned: Boolean
) {

    /** مفتاح الترتيب: وقت السيرفر لو موجود، وإلا وقت التليفون (للرسايل القديمة والـ pending) */
    fun sortTimestampMillis(): Long = if (serverTimestampMillis > 0L) serverTimestampMillis else timestamp

    fun toMessage() = Message(
        id = id, senderId = senderId, receiverId = receiverId, text = text,
        timestamp = timestamp, seen = seen, fileUrl = fileUrl, fileName = fileName,
        fileType = fileType, durationMs = durationMs, contactName = contactName,
        contactPhone = contactPhone, pollQuestion = pollQuestion,
        pollOptions = jsonToList(pollOptionsJson), pollVotes = jsonToIntMap(pollVotesJson),
        eventTitle = eventTitle, eventTimestamp = eventTimestamp, replyToId = replyToId,
        replyToText = replyToText, replyToSenderName = replyToSenderName,
        edited = edited, deleted = deleted, forwarded = forwarded,
        reactions = jsonToStringMap(reactionsJson)
    )

    fun toGroupMessage() = GroupMessage(
        id = id, senderId = senderId, senderName = senderName, receiverId = receiverId,
        text = text, timestamp = timestamp, seen = seen, fileUrl = fileUrl, fileName = fileName,
        fileType = fileType, durationMs = durationMs, contactName = contactName,
        contactPhone = contactPhone, pollQuestion = pollQuestion,
        pollOptions = jsonToList(pollOptionsJson), pollVotes = jsonToIntMap(pollVotesJson),
        eventTitle = eventTitle, eventTimestamp = eventTimestamp, replyToId = replyToId,
        replyToText = replyToText, replyToSenderName = replyToSenderName,
        edited = edited, deleted = deleted, forwarded = forwarded,
        reactions = jsonToStringMap(reactionsJson)
    )

    companion object {
        fun fromMessage(chatKey: String, m: Message, pending: Boolean, serverTimestampMillis: Long = 0L) = MessageEntity(
            chatKey = chatKey, id = m.id, senderId = m.senderId, senderName = "",
            receiverId = m.receiverId, text = m.text, timestamp = m.timestamp, seen = m.seen,
            fileUrl = m.fileUrl, fileName = m.fileName, fileType = m.fileType,
            durationMs = m.durationMs, contactName = m.contactName, contactPhone = m.contactPhone,
            pollQuestion = m.pollQuestion, pollOptionsJson = listToJson(m.pollOptions),
            pollVotesJson = mapToJson(m.pollVotes), eventTitle = m.eventTitle,
            eventTimestamp = m.eventTimestamp, replyToId = m.replyToId,
            replyToText = m.replyToText, replyToSenderName = m.replyToSenderName,
            edited = m.edited, deleted = m.deleted, forwarded = m.forwarded,
            reactionsJson = mapToJson(m.reactions), pending = pending,
            serverTimestampMillis = serverTimestampMillis, serverPruned = false
        )

        fun fromGroupMessage(chatKey: String, m: GroupMessage, pending: Boolean, serverTimestampMillis: Long = 0L) = MessageEntity(
            chatKey = chatKey, id = m.id, senderId = m.senderId, senderName = m.senderName,
            receiverId = m.receiverId, text = m.text, timestamp = m.timestamp, seen = m.seen,
            fileUrl = m.fileUrl, fileName = m.fileName, fileType = m.fileType,
            durationMs = m.durationMs, contactName = m.contactName, contactPhone = m.contactPhone,
            pollQuestion = m.pollQuestion, pollOptionsJson = listToJson(m.pollOptions),
            pollVotesJson = mapToJson(m.pollVotes), eventTitle = m.eventTitle,
            eventTimestamp = m.eventTimestamp, replyToId = m.replyToId,
            replyToText = m.replyToText, replyToSenderName = m.replyToSenderName,
            edited = m.edited, deleted = m.deleted, forwarded = m.forwarded,
            reactionsJson = mapToJson(m.reactions), pending = pending,
            serverTimestampMillis = serverTimestampMillis, serverPruned = false
        )

        /** تسلسل/فك تسلسل JSON — بتستخدمها النسخة الاحتياطية على Google Drive */
        fun toJson(e: MessageEntity): JSONObject {
            val o = JSONObject()
            o.put("chatKey", e.chatKey); o.put("id", e.id); o.put("senderId", e.senderId)
            o.put("senderName", e.senderName); o.put("receiverId", e.receiverId); o.put("text", e.text)
            o.put("timestamp", e.timestamp); o.put("seen", e.seen); o.put("fileUrl", e.fileUrl)
            o.put("fileName", e.fileName); o.put("fileType", e.fileType); o.put("durationMs", e.durationMs)
            o.put("contactName", e.contactName); o.put("contactPhone", e.contactPhone)
            o.put("pollQuestion", e.pollQuestion); o.put("pollOptionsJson", e.pollOptionsJson)
            o.put("pollVotesJson", e.pollVotesJson); o.put("eventTitle", e.eventTitle)
            o.put("eventTimestamp", e.eventTimestamp); o.put("replyToId", e.replyToId)
            o.put("replyToText", e.replyToText); o.put("replyToSenderName", e.replyToSenderName)
            o.put("edited", e.edited); o.put("deleted", e.deleted); o.put("forwarded", e.forwarded)
            o.put("reactionsJson", e.reactionsJson); o.put("pending", e.pending)
            o.put("serverTimestampMillis", e.serverTimestampMillis); o.put("serverPruned", e.serverPruned)
            return o
        }

        fun fromJson(o: JSONObject): MessageEntity = MessageEntity(
            chatKey = o.getString("chatKey"), id = o.getString("id"), senderId = o.getString("senderId"),
            senderName = o.optString("senderName"), receiverId = o.optString("receiverId"),
            text = o.optString("text"), timestamp = o.optLong("timestamp"), seen = o.optBoolean("seen"),
            fileUrl = o.optString("fileUrl"), fileName = o.optString("fileName"),
            fileType = o.optString("fileType"), durationMs = o.optLong("durationMs"),
            contactName = o.optString("contactName"), contactPhone = o.optString("contactPhone"),
            pollQuestion = o.optString("pollQuestion"), pollOptionsJson = o.optString("pollOptionsJson", "[]"),
            pollVotesJson = o.optString("pollVotesJson", "{}"), eventTitle = o.optString("eventTitle"),
            eventTimestamp = o.optLong("eventTimestamp"), replyToId = o.optString("replyToId"),
            replyToText = o.optString("replyToText"), replyToSenderName = o.optString("replyToSenderName"),
            edited = o.optBoolean("edited"), deleted = o.optBoolean("deleted"),
            forwarded = o.optBoolean("forwarded"), reactionsJson = o.optString("reactionsJson", "{}"),
            pending = o.optBoolean("pending"), serverTimestampMillis = o.optLong("serverTimestampMillis"),
            serverPruned = o.optBoolean("serverPruned")
        )

        private fun listToJson(list: List<String>): String = JSONArray(list).toString()

        private fun mapToJson(map: Map<String, *>): String {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k, v) }
            return o.toString()
        }

        private fun jsonToList(json: String): List<String> = try {
            val a = JSONArray(json)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }

        private fun jsonToStringMap(json: String): Map<String, String> = try {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }

        private fun jsonToIntMap(json: String): Map<String, Int> = try {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getInt(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
