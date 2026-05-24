package com.simplr.mykitta2.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.simplr.mykitta2.ui.theme.MyKittaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mykitta.shared.generated.resources.Res
import mykitta.shared.generated.resources.app_logo
import org.jetbrains.compose.resources.imageResource

/**
 * Multi-layer choreographed splash, sliced from `app_logo.png` (512×512 source).
 *
 * Three layers render the same canvas size and stack in a Box — each Canvas only
 * draws its slice of the bitmap, with the rest transparent — so they line up
 * exactly as the full logo. graphicsLayer transforms animate each piece on its
 * own timeline:
 *
 *   t=0–400ms   M device: scale 0.85→1, alpha 0→1, drop translateY -24→0
 *   t=200–600ms Bag:      slide translateX 32→0, alpha 0→1
 *   t=400–800ms Wordmark: rise translateY 24→0, alpha 0→1
 *   t=1100ms    handoff via onFinished()
 *
 * Source slice coordinates (in the 512-space source — eyeballed from the icon):
 *   M device:  ( 60, 30) → 230×315
 *   Bag:       (255,120) → 190×235
 *   Wordmark:  ( 60,355) → 390×120
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val bitmap = imageResource(Res.drawable.app_logo)

    val mAlpha = remember { Animatable(0f) }
    val mScale = remember { Animatable(0.85f) }
    val mTransY = remember { Animatable(-24f) }

    val bagAlpha = remember { Animatable(0f) }
    val bagTransX = remember { Animatable(32f) }

    val wmAlpha = remember { Animatable(0f) }
    val wmTransY = remember { Animatable(24f) }

    LaunchedEffect(Unit) {
        launch { mAlpha.animateTo(1f, tween(durationMillis = 400)) }
        launch { mScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { mTransY.animateTo(0f, tween(durationMillis = 400)) }

        launch {
            delay(200)
            launch { bagAlpha.animateTo(1f, tween(durationMillis = 400)) }
            launch { bagTransX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
        }

        launch {
            delay(400)
            launch { wmAlpha.animateTo(1f, tween(durationMillis = 400)) }
            launch { wmTransY.animateTo(0f, tween(durationMillis = 400)) }
        }

        delay(1100)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MyKittaColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(LOGO_SIZE_DP.dp)) {
            // Layer 1 — blue "M" device
            Canvas(
                modifier = Modifier
                    .size(LOGO_SIZE_DP.dp)
                    .graphicsLayer {
                        alpha = mAlpha.value
                        scaleX = mScale.value
                        scaleY = mScale.value
                        translationY = mTransY.value * density
                    },
            ) {
                drawSlice(bitmap, srcX = 60, srcY = 30, srcW = 230, srcH = 315)
            }

            // Layer 2 — red shopping bag
            Canvas(
                modifier = Modifier
                    .size(LOGO_SIZE_DP.dp)
                    .graphicsLayer {
                        alpha = bagAlpha.value
                        translationX = bagTransX.value * density
                    },
            ) {
                drawSlice(bitmap, srcX = 255, srcY = 120, srcW = 190, srcH = 235)
            }

            // Layer 3 — "MyKitaa" wordmark
            Canvas(
                modifier = Modifier
                    .size(LOGO_SIZE_DP.dp)
                    .graphicsLayer {
                        alpha = wmAlpha.value
                        translationY = wmTransY.value * density
                    },
            ) {
                drawSlice(bitmap, srcX = 60, srcY = 355, srcW = 390, srcH = 120)
            }
        }
    }
}

private const val LOGO_SIZE_DP = 240
private const val SOURCE_PX = 512f

private fun DrawScope.drawSlice(
    bitmap: ImageBitmap,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
) {
    val scale = size.width / SOURCE_PX
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset((srcX * scale).toInt(), (srcY * scale).toInt()),
        dstSize = IntSize((srcW * scale).toInt(), (srcH * scale).toInt()),
    )
}
