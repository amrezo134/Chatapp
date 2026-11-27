package com.creatix.chatapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MessageDao {

    /** الرسايل مرتبة بالوقت، والـ UI بيقرا منها هي بس */
    @Query("SELECT * FROM cached_messages WHERE chatKey = :chatKey ORDER BY timestamp ASC")
    abstract fun observe(chatKey: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM cached_messages WHERE chatKey = :chatKey AND pending = 0")
    abstract suspend fun deleteSynced(chatKey: String)

    @Query("UPDATE cached_messages SET pending = 0 WHERE chatKey = :chatKey AND id = :id")
    abstract suspend fun markSynced(chatKey: String, id: String)

    /** بيستبدل النسخة المتزامنة من الشات بلقطة Firestore، من غير ما يمسح الرسايل اللي لسه pending */
    @Transaction
    open suspend fun replaceSynced(chatKey: String, messages: List<MessageEntity>) {
        deleteSynced(chatKey)
        upsertAll(messages)
    }
}
