package com.creatix.chatapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.ui.screens.*
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat"
    const val GROUP_CHAT = "group_chat"  
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel) {
    val navController: NavHostController = rememberNavController()
    val chatViewModel = remember { ChatViewModel() }
    var selectedUser by remember { mutableStateOf<ChatUser?>(null) }

    val startDestination = if (authViewModel.isLoggedIn) Routes.CHAT_LIST else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onGoToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                onOpenChat = { user ->
                    selectedUser = user
                    navController.navigate(Routes.CHAT)
                },
                onOpenGroupChat = { navController.navigate(Routes.GROUP_CHAT) },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CHAT_LIST) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CHAT) {
            selectedUser?.let { user ->
                ChatScreen(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    otherUser = user,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.GROUP_CHAT) {
            GroupChatScreen(
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
