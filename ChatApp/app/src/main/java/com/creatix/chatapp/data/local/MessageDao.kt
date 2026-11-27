package com.creatix.chatapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MessageDao {

    /**
     * الرسايل مرتبة بمفتاح السيرفر لو موجود (serverTimestampMillis)، وإلا بوقت التليفون —
     * كده الرسايل القديمة والـ pending بتترتب صح جنب الجديدة، والـ UI بيقرا منها هي بس
     */
    @Query(
        "SELECT * FROM cached_messages WHERE chatKey = :chatKey " +
            "ORDER BY CASE WHEN serverTimestampMillis > 0 THEN serverTimestampMillis ELSE timestamp END ASC, id ASC"
    )
    abstract fun observe(chatKey: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM cached_messages")
    abstract suspend fun count(): Int

    /** كل الرسايل المتخزنة محليًا — بتستخدمها النسخة الاحتياطية على Google Drive */
    @Query("SELECT * FROM cached_messages")
    abstract suspend fun getAll(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM cached_messages WHERE chatKey = :chatKey AND pending = 0")
    abstract suspend fun deleteSynced(chatKey: String)

    @Query("UPDATE cached_messages SET pending = 0 WHERE chatKey = :chatKey AND id = :id")
    abstract suspend fun markSynced(chatKey: String, id: String)

    /** بيعلّم الرسايل المتزامنة اللي اختفت من لقطة السيرفر إنها بقت محلية بس (اتمسحت من السيرفر بعد الاستلام) */
    @Query("UPDATE cached_messages SET serverPruned = 1 WHERE chatKey = :chatKey AND pending = 0 AND id NOT IN (:ids)")
    abstract suspend fun markMissingPruned(chatKey: String, ids: List<String>)

    @Query("UPDATE cached_messages SET serverPruned = 1 WHERE chatKey = :chatKey AND pending = 0")
    abstract suspend fun markAllSyncedPruned(chatKey: String)

    /**
     * بيستبدل النسخة المتزامنة من الشات بلقطة Firestore، من غير ما يمسح الرسايل اللي لسه pending.
     * keepMissing = true (وضع "الحذف بعد الاستلام"): الرسايل اللي مش في اللقطة مش بتتمسح من الكاش،
     * بتتعلّم serverPruned = 1 لأنها اتمسحت من السيرفر بعد ما اتخزنت عندنا — الكاش المحلي هو النسخة الباقية.
     */
    @Transaction
    open suspend fun replaceSynced(chatKey: String, messages: List<MessageEntity>, keepMissing: Boolean = false) {
        if (keepMissing) {
            upsertAll(messages)
            if (messages.isEmpty()) markAllSyncedPruned(chatKey)
            else markMissingPruned(chatKey, messages.map { it.id })
        } else {
            deleteSynced(chatKey)
            upsertAll(messages)
        }
    }
}
