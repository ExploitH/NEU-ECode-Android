package com.neko.neuecode.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.neko.neuecode.R
import com.neko.neuecode.domain.vpn.VpnStatusArtwork

@Composable
fun VpnStatusMark(
    artwork: VpnStatusArtwork,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val resId = when (artwork) {
        VpnStatusArtwork.Idle -> R.raw.vpn_idle
        VpnStatusArtwork.Connecting -> R.raw.mountain_river_loading
        VpnStatusArtwork.Connected -> R.raw.vpn_connected
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier.size(size),
    )
}
