package com.creatix.chatapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var instance: ChatDatabase? = null

        /** بتتنادى مرة من MainActivity.onCreate. آمن تتنادى أكتر من مرة. */
        fun init(context: Context) {
            if (instance != null) return
            synchronized(this) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        ChatDatabase::class.java,
                        "chat_cache.db"
                    ).fallbackToDestructiveMigration().build()
                }
            }
        }

        /** null لو init لسه ما اتناداش، وساعتها الريبو بيشتغل مباشرة على Firestore زي الأول */
        fun dao(): MessageDao? = instance?.messageDao()

        /** بيمسح الكاش (عند تسجيل الخروج) */
        fun clearAsync() {
            val db = instance ?: return
            Thread { try { db.clearAllTables() } catch (_: Exception) {} }.start()
        }
    }
}
