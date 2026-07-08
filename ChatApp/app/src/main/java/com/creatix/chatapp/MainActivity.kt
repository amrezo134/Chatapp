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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.creatix.chatapp.navigation.AppNavigation
import com.creatix.chatapp.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

                    AppNavigation(authViewModel = authViewModel)
                }
            }
        }
    }
}
