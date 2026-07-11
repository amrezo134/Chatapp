package com.creatix.chatapp.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * شاشة عرض صورة البروفايل بالحجم الكامل - نفس فكرة واتساب:
 * تدوس على الصورة الصغيرة، تتحرك وتتكبر بسلاسة لحد ما توصل هنا (Shared Element).
 * تدوس على أي مكان في الشاشة -> ترجع تصغر تاني لمكانها الأصلي.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProfilePhotoScreen(
    photoUrl: String,
    displayName: String,
    sharedKey: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit
) {
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onBack() }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .sharedElement(
                            state = rememberSharedContentState(key = sharedKey),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                )
            }

            Text(
                text = displayName,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}
