package com.creatix.chatapp.repository

import com.creatix.chatapp.data.ChatUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser get() = auth.currentUser

    fun isLoggedIn(): Boolean = auth.currentUser != null

    /** تسجيل الدخول بايميل وباسورد */
    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            setOnlineStatus(true)
            saveFcmToken()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** تسجيل حساب جديد + حفظ بيانات المستخدم في Firestore */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        photoUrl: String = "",
        bio: String = ""
    ): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("فشل إنشاء الحساب")

            val newUser = ChatUser(
                uid = uid,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
                bio = bio,
                online = true,
                lastSeen = System.currentTimeMillis()
            )
            db.collection("users").document(uid).set(newUser).await()
            saveFcmToken()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** يجيب توكن الجهاز الحالي ويخزنه في مستند المستخدم عشان السيرفر يقدر يبعتله إشعارات */
    private suspend fun saveFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            db.collection("users").document(uid).update("fcmToken", token).await()
        } catch (_: Exception) {
            // لو فشل، مش مشكلة كبيرة - هيتحدث تاني مع onNewToken
        }
    }

    fun logout() {
        setOnlineStatus(false)
        auth.signOut()
    }

    /** يراقب حالة تسجيل الدخول أول ما التطبيق يفتح (onAuthStateChanged) */
    fun observeAuthState(onChange: (Boolean) -> Unit) {
        auth.addAuthStateListener { firebaseAuth ->
            onChange(firebaseAuth.currentUser != null)
        }
    }

    private fun setOnlineStatus(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update(mapOf("online" to online, "lastSeen" to System.currentTimeMillis()))
    }
}
