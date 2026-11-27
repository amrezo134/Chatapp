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
    val pending: Boolean
) {
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
        fun fromMessage(chatKey: String, m: Message, pending: Boolean) = MessageEntity(
            chatKey = chatKey, id = m.id, senderId = m.senderId, senderName = "",
            receiverId = m.receiverId, text = m.text, timestamp = m.timestamp, seen = m.seen,
            fileUrl = m.fileUrl, fileName = m.fileName, fileType = m.fileType,
            durationMs = m.durationMs, contactName = m.contactName, contactPhone = m.contactPhone,
            pollQuestion = m.pollQuestion, pollOptionsJson = listToJson(m.pollOptions),
            pollVotesJson = mapToJson(m.pollVotes), eventTitle = m.eventTitle,
            eventTimestamp = m.eventTimestamp, replyToId = m.replyToId,
            replyToText = m.replyToText, replyToSenderName = m.replyToSenderName,
            edited = m.edited, deleted = m.deleted, forwarded = m.forwarded,
            reactionsJson = mapToJson(m.reactions), pending = pending
        )

        fun fromGroupMessage(chatKey: String, m: GroupMessage, pending: Boolean) = MessageEntity(
            chatKey = chatKey, id = m.id, senderId = m.senderId, senderName = m.senderName,
            receiverId = m.receiverId, text = m.text, timestamp = m.timestamp, seen = m.seen,
            fileUrl = m.fileUrl, fileName = m.fileName, fileType = m.fileType,
            durationMs = m.durationMs, contactName = m.contactName, contactPhone = m.contactPhone,
            pollQuestion = m.pollQuestion, pollOptionsJson = listToJson(m.pollOptions),
            pollVotesJson = mapToJson(m.pollVotes), eventTitle = m.eventTitle,
            eventTimestamp = m.eventTimestamp, replyToId = m.replyToId,
            replyToText = m.replyToText, replyToSenderName = m.replyToSenderName,
            edited = m.edited, deleted = m.deleted, forwarded = m.forwarded,
            reactionsJson = mapToJson(m.reactions), pending = pending
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
