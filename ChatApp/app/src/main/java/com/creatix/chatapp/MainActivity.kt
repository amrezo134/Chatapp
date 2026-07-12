package com.creatix.chatapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import com.creatix.chatapp.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel = remember { AuthViewModel() }
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnCompleteListener
                        FirebaseFirestore.getInstance().collection("users")
                            .document(userId)
                            .update("fcmToken", token)
                    }
                }

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

                    AppNavigation(authViewModel = authViewModel)
                }
            }
        }
    }
}
