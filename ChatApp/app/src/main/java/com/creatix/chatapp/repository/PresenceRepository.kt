package com.creatix.chatapp.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * نظام "متصل الآن" الحقيقي باستخدام Realtime Database.
 * السبب إننا مش بنستخدم Firestore لده: Firestore معندهوش أي طريقة تخلي السيرفر
 * يعرف إن الاتصال اتقطع فجأة (قفل تطبيق بالقوة / النت راح / الجهاز طفى).
 * RTDB بس هي اللي عندها onDisconnect() اللي بتشتغل من على السيرفر نفسه.
 */
class PresenceRepository {

    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance()
    private var connectedListener: ValueEventListener? = null

    /**
     * بتتنادى مرة كل ما التطبيق يرجع للمقدمة (أو بعد تسجيل الدخول مباشرة).
     * بتسجل onDisconnect() على السيرفر (شبكة أمان لو الاتصال اتقطع فجأة)،
     * وبتحط حالتي "online" طول ما فعلاً متصل بالسيرفر.
     */
    fun goOnline() {
        val uid = auth.currentUser?.uid ?: return
        val myStatusRef = rtdb.getReference("status").child(uid)
        val connectedRef = rtdb.getReference(".info/connected")

        // لو كان في listener قديم من قبل، امسحه الأول عشان ميتكررش
        connectedListener?.let { connectedRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                if (isConnected) {
                    myStatusRef.onDisconnect().setValue(
                        mapOf("state" to "offline", "last_changed" to ServerValue.TIMESTAMP)
                    )
                    myStatusRef.setValue(
                        mapOf("state" to "online", "last_changed" to ServerValue.TIMESTAMP)
                    )
                }
            }
            override fun onCancelled(error: DatabaseError) { /* تجاهل */ }
        }
        connectedListener = listener
        connectedRef.addValueEventListener(listener)
    }

    /** بتتنادى وقت ما التطبيق يروح للخلفية (خروج نظيف وسريع، مش لازم ننتظر انقطاع الاتصال) */
    fun goOffline() {
        val uid = auth.currentUser?.uid ?: return
        rtdb.getReference("status").child(uid).setValue(
            mapOf("state" to "offline", "last_changed" to ServerValue.TIMESTAMP)
        )
    }

    /** بيراقب حالة كل اليوزرز مرة واحدة: uid -> متصل ولا لأ */
    fun observeAllPresence(): Flow<Map<String, Boolean>> = callbackFlow {
        val ref = rtdb.getReference("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, Boolean>()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val state = child.child("state").getValue(String::class.java)
                    map[uid] = state == "online"
                }
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
