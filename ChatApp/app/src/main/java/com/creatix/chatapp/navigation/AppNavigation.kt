package com.creatix.chatapp.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.ui.screens.*
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat"
    const val GROUP_CHAT = "group_chat"
    const val CREATE_GROUP = "create_group"
    const val CUSTOM_GROUP_CHAT = "custom_group_chat"
    const val GROUP_INFO = "group_info"
    const val GROUP_PHOTO = "group_photo"
    const val PROFILE = "profile"
    const val PROFILE_PHOTO = "profile_photo"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(authViewModel: AuthViewModel) {
    val navController: NavHostController = rememberNavController()
    val chatViewModel = remember { ChatViewModel() }
    var selectedUser by remember { mutableStateOf<ChatUser?>(null) }
    var profilePhotoUser by remember { mutableStateOf<ChatUser?>(null) }
    var isViewingOwnPhoto by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<ChatGroup?>(null) }

    val startDestination = if (authViewModel.isLoggedIn) Routes.CHAT_LIST else Routes.LOGIN

    SharedTransitionLayout {
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
                    chatViewModel = chatViewModel,
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
                    onOpenCustomGroup = { group ->
                        selectedGroup = group
                        navController.navigate(Routes.CUSTOM_GROUP_CHAT)
                    },
                    onOpenCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenProfilePhoto = { user ->
                        profilePhotoUser = user
                        isViewingOwnPhoto = false
                        navController.navigate(Routes.PROFILE_PHOTO)
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.CHAT_LIST) { inclusive = true }
                        }
                    },
                    onOpenMyPhoto = {
                        profilePhotoUser = chatViewModel.myProfile.value
                        isViewingOwnPhoto = true
                        navController.navigate(Routes.PROFILE_PHOTO)
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }

            composable(Routes.CHAT) {
                selectedUser?.let { user ->
                    ChatScreen(
                        authViewModel = authViewModel,
                        chatViewModel = chatViewModel,
                        otherUser = user,
                        onBack = { navController.popBackStack() },
                        onOpenProfilePhoto = {
                            profilePhotoUser = user
                            isViewingOwnPhoto = false
                            navController.navigate(Routes.PROFILE_PHOTO)
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
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

            composable(Routes.CREATE_GROUP) {
                CreateGroupScreen(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                    onGroupCreated = {
                        // بمجرد ما الجروب يتعمل، نرجع لقائمة المحادثات وهيظهر فيها تلقائيًا
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.CUSTOM_GROUP_CHAT) {
                selectedGroup?.let { group ->
                    val myGroups by chatViewModel.myCustomGroups.collectAsState()
                    val liveGroup = myGroups.find { it.id == group.id } ?: group
                    CustomGroupChatScreen(
                        authViewModel = authViewModel,
                        chatViewModel = chatViewModel,
                        group = liveGroup,
                        onBack = { navController.popBackStack() },
                        onOpenGroupInfo = {
                            selectedGroup = liveGroup
                            navController.navigate(Routes.GROUP_INFO)
                        },
                        onOpenGroupPhoto = {
                            selectedGroup = liveGroup
                            navController.navigate(Routes.GROUP_PHOTO)
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }
            }

            composable(Routes.GROUP_INFO) {
                selectedGroup?.let { group ->
                    val myGroups by chatViewModel.myCustomGroups.collectAsState()
                    val liveGroup = myGroups.find { it.id == group.id } ?: group
                    GroupInfoScreen(
                        authViewModel = authViewModel,
                        chatViewModel = chatViewModel,
                        group = liveGroup,
                        onBack = { navController.popBackStack() },
                        onOpenGroupPhoto = {
                            selectedGroup = liveGroup
                            navController.navigate(Routes.GROUP_PHOTO)
                        },
                        onGroupDeleted = {
                            navController.navigate(Routes.CHAT_LIST) {
                                popUpTo(Routes.CHAT_LIST) { inclusive = true }
                            }
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }
            }

            composable(Routes.GROUP_PHOTO) {
                selectedGroup?.let { group ->
                    ProfilePhotoScreen(
                        photoUrl = group.photoUrl,
                        displayName = group.name,
                        sharedKey = "group-photo-${group.id}",
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Routes.PROFILE_PHOTO) {
                val myProfileLive by chatViewModel.myProfile.collectAsState()
                val user = if (isViewingOwnPhoto) (myProfileLive ?: profilePhotoUser) else profilePhotoUser
                user?.let { u ->
                    ProfilePhotoScreen(
                        photoUrl = u.photoUrl,
                        displayName = u.displayName.ifBlank { u.email },
                        sharedKey = "profile-${u.uid}",
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        onBack = { navController.popBackStack() },
                        isOwnProfile = isViewingOwnPhoto,
                        currentBio = u.bio,
                        myUid = authViewModel.currentUid,
                        chatViewModel = chatViewModel
                    )
                }
            }
        }
    }
}
