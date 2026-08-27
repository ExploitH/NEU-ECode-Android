package com.neko.neuecode.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.neko.neuecode.R
import com.neko.neuecode.domain.vpn.VpnStatusArtwork

private const val CROSSFADE_MS = 180

@Composable
fun VpnStatusMark(
    artwork: VpnStatusArtwork,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val idle by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.vpn_idle))
    val connecting by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.mountain_river_loading),
    )
    val connected by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.vpn_connected))

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = artwork,
            animationSpec = tween(durationMillis = CROSSFADE_MS),
            label = "vpn-status-artwork",
        ) { target ->
            val composition = when (target) {
                VpnStatusArtwork.Idle -> idle
                VpnStatusArtwork.Connecting -> connecting
                VpnStatusArtwork.Connected -> connected
            }
            if (composition != null) {
                val visualScale =
                    if (target == VpnStatusArtwork.Connecting) {
                        VpnStatusArtwork.CONNECTING_VISUAL_SCALE
                    } else {
                        1f
                    }
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer {
                            scaleX = visualScale
                            scaleY = visualScale
                        },
                )
            }
        }
    }
}
