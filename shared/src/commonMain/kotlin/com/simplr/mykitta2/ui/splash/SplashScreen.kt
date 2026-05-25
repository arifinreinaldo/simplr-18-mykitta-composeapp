package com.simplr.mykitta2.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.feature.splash.SplashStore
import com.simplr.mykitta2.feature.splash.SplashViewModel
import com.simplr.mykitta2.ui.theme.MyKittaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import mykitta.shared.generated.resources.Res
import mykitta.shared.generated.resources.app_logo
import org.jetbrains.compose.resources.imageResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Multi-layer choreographed splash, sliced from `app_logo.png` (512×512 source).
 *
 * Three layers render the same canvas size and stack in a Box — each Canvas only
 * draws its slice of the bitmap, with the rest transparent — so they line up
 * exactly as the full logo. graphicsLayer transforms animate each piece on its
 * own timeline:
 *
 *   t=0–400ms   M device: scale 0.85→1 (spring), alpha 0→1, drop translateY -24→0
 *   t=200–600ms Bag:      slide translateX 32→0 (spring), alpha 0→1
 *   t=400–800ms Wordmark: rise translateY 24→0, alpha 0→1
 *
 * Once the entrance finishes, the assembled logo gently breathes (1.0 ↔ 1.04
 * scale, 1200ms reverse) for as long as [SplashStore] reports [SplashStore.State.Working].
 * Navigation is driven by [SplashStore.Label.NavigateTo] — the view never
 * decides the destination itself.
 *
 * Source slice coordinates (in the 512-space source — eyeballed from the icon):
 *   M device:  ( 60, 30) → 210×315
 *   Bag:       (280,120) → 190×235
 *   Wordmark:  ( 60,355) → 390×120
 */
@Composable
fun SplashScreen(
    onDestination: (SplashStore.Destination) -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bitmap = imageResource(Res.drawable.app_logo)

    val mAlpha = remember { Animatable(0f) }
    val mScale = remember { Animatable(0.85f) }
    val mTransY = remember { Animatable(-24f) }

    val bagAlpha = remember { Animatable(0f) }
    val bagTransX = remember { Animatable(32f) }

    val wmAlpha = remember { Animatable(0f) }
    val wmTransY = remember { Animatable(24f) }

    var entranceDone by remember { mutableStateOf(false) }
    val pulseScale = remember { Animatable(1f) }

    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                is SplashStore.Label.NavigateTo -> onDestination(label.destination)
            }
        }
    }

    // Entrance choreography. joinAll waits for every animation (including the
    // springs, which don't have deterministic end times) before flipping
    // `entranceDone` — so the pulse never starts mid-overshoot.
    LaunchedEffect(Unit) {
        val mAlphaJob = launch { mAlpha.animateTo(1f, tween(durationMillis = 400)) }
        val mScaleJob = launch {
            mScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
        val mTransJob = launch { mTransY.animateTo(0f, tween(durationMillis = 400)) }

        val bagAlphaJob = launch {
            delay(200)
            bagAlpha.animateTo(1f, tween(durationMillis = 400))
        }
        val bagTransJob = launch {
            delay(200)
            bagTransX.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }

        val wmAlphaJob = launch {
            delay(400)
            wmAlpha.animateTo(1f, tween(durationMillis = 400))
        }
        val wmTransJob = launch {
            delay(400)
            wmTransY.animateTo(0f, tween(durationMillis = 400))
        }

        joinAll(
            mAlphaJob, mScaleJob, mTransJob,
            bagAlphaJob, bagTransJob,
            wmAlphaJob, wmTransJob,
        )
        entranceDone = true
    }

    // Pulse loop. Runs only while the entrance is finished AND the store is
    // still busy. When the store flips to Ready (state changes), the
    // LaunchedEffect re-launches into the else branch and snaps back to 1f —
    // not visible because we're navigating away on the same tick.
    val pulsing = entranceDone && state is SplashStore.State.Working
    LaunchedEffect(pulsing) {
        if (pulsing) {
            while (isActive) {
                pulseScale.animateTo(
                    PULSE_PEAK,
                    tween(durationMillis = PULSE_HALF_MS, easing = FastOutSlowInEasing),
                )
                pulseScale.animateTo(
                    1f,
                    tween(durationMillis = PULSE_HALF_MS, easing = FastOutSlowInEasing),
                )
            }
        } else {
            pulseScale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MyKittaColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        // Pulse rides on the outer Box so all three layers breathe as one
        // assembled unit, not three drifting pieces.
        Box(
            modifier = Modifier
                .size(LOGO_SIZE_DP.dp)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                },
        ) {
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
                drawSlice(bitmap, srcX = 60, srcY = 30, srcW = 210, srcH = 315)
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
                drawSlice(bitmap, srcX = 280, srcY = 120, srcW = 190, srcH = 235)
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
private const val PULSE_PEAK = 1.04f
private const val PULSE_HALF_MS = 1200

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
