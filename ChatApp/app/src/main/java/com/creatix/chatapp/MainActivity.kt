package com.creatix.chatapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.creatix.chatapp.ui.theme.ChatAppTheme
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.creatix.chatapp.navigation.AppNavigation
import com.creatix.chatapp.services.MyFirebaseMessagingService
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.repository.AiChatRepository

class MainActivity : ComponentActivity() {

    // الشات اللي لازم نفتحه لو اليوزر جاي من إشعار (state عشان يفضل يحدث الشاشة حتى لو مفيش recreate)
    private val pendingChatUid = mutableStateOf<String?>(null)

    /**
     * بتاخد التوكن الحالي وتحدّثه في Firestore لليوزر المسجل دخوله دلوقتي.
     * بتترفض بصمت لو مفيش يوزر مسجل لسه، وده بالظبط اللي كان بيحصل قبل كده
     * لو الكود اتنفذ قبل ما جلسة تسجيل الدخول تسترجع نفسها.
     */
    private fun refreshFcmToken() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "DEBUG: مفيش currentUser لسه، هنستنى AuthStateListener", Toast.LENGTH_LONG).show()
            return
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Toast.makeText(this, "DEBUG: هيتحدث توكن لليوزر $userId: ${token.take(12)}...", Toast.LENGTH_LONG).show()
                FirebaseFirestore.getInstance().collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Toast.makeText(this, "DEBUG: التوكن اتحفظ في Firestore بنجاح", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "DEBUG: فشل حفظ التوكن: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(this, "DEBUG: فشل جلب التوكن: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyFirebaseMessagingService.ensureNotificationChannel(applicationContext)
        pendingChatUid.value = intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_OPEN_CHAT_UID)
        AiChatRepository.init(applicationContext)

        // بنحاول نحدّث التوكن فورًا (لو فيه جلسة دخول محفوظة ومسترجعة بالفعل)...
        refreshFcmToken()
        // ...وبرضو بنراقب أي تغيير في حالة تسجيل الدخول (زي استرجاع الجلسة بعد التثبيت الجديد
        // أو تسجيل دخول جديد) عشان نضمن إن التوكن هيتحدث حتى لو onCreate اتنفذ قبل الأوان
        FirebaseAuth.getInstance().addAuthStateListener { refreshFcmToken() }

        setContent {
            ChatAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel = remember { AuthViewModel() }

                    // من أندرويد 13 (API 33) لازم تطلب إذن الإشعارات صراحة من المستخدم
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { /* مش محتاجين نعمل حاجة بالنتيجة */ }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // بيراقب التطبيق كله (مش الشاشة بس): لما يفتح في المقدمة online=true،
                    // لما يروح للخلفية (المستخدم يقفله أو يفتح تطبيق تاني) online=false
                    DisposableEffect(Unit) {
                        val observer = object : DefaultLifecycleObserver {
                            override fun onStart(owner: LifecycleOwner) {
                                authViewModel.setOnlinePresence(true)
                            }

                            override fun onStop(owner: LifecycleOwner) {
                                authViewModel.setOnlinePresence(false)
                            }
                        }
                        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                        onDispose {
                            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                        }
                    }

                    AppNavigation(
                        authViewModel = authViewModel,
                        pendingChatUid = pendingChatUid.value,
                        onPendingChatUidConsumed = { pendingChatUid.value = null }
                    )
                }
            }
        }
    }

    // لما التطبيق يكون شغال أصلًا (singleTask) والمستخدم يضغط على إشعار تاني،
    // مش بيتعمل onCreate جديد، لازم نمسك الـ intent الجديد من هنا
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatUid.value = intent.getStringExtra(MyFirebaseMessagingService.EXTRA_OPEN_CHAT_UID)
    }
}
