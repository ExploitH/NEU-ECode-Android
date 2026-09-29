package com.neko.neuecode.ui.screen.paycode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.neko.neuecode.domain.model.Balance
import com.neko.neuecode.ui.components.BrandLoadingMark
import com.neko.neuecode.ui.components.Panel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayCodeScreen(
    onOpenPayCode: () -> Unit,
    onOpenRecharge: () -> Unit,
    viewModel: PayCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshEnabled = state.fetchEnabled && !state.awaitingSms
    val busy = state.isSyncingBalance || state.home.status == PayCodeHomeStatus.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("付款码") },
                windowInsets = WindowInsets.statusBars,
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = refreshEnabled && !busy,
                    ) {
                        if (busy && refreshEnabled) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PayCodeHero(state = state, viewModel = viewModel, onOpenPayCode = onOpenPayCode)
                }
            }

            BalancePanel(
                balance = state.balance,
                isSyncing = state.isSyncingBalance,
                error = state.balanceError,
                onOpenRecharge = onOpenRecharge,
            )

            PayCodeSwitchPanel(
                enabled = state.fetchEnabled,
                hint = state.switchHint.ifBlank { state.home.switchHint },
                onCheckedChange = viewModel::setFetchEnabled,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PayCodeHero(
    state: PayCodeUiState,
    viewModel: PayCodeViewModel,
    onOpenPayCode: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when {
        state.home.showSmsChallenge || state.awaitingSms -> {
            SmsChallengeSection(
                state = state,
                onGraphicChange = viewModel::updateGraphicCaptcha,
                onSmsChange = viewModel::updateSmsCode,
                onRefreshCaptcha = viewModel::refreshCaptchaImage,
                onSend = viewModel::sendSmsCode,
                onSubmit = viewModel::submitSmsCode,
            )
        }
        state.home.status == PayCodeHomeStatus.Loading -> {
            BrandLoadingMark(
                size = 120.dp,
                caption = state.home.syncHint ?: "正在同步付款码…",
            )
        }
        state.home.status == PayCodeHomeStatus.Ready && state.home.showNativeQr -> {
            val payload = state.home.payload
            val qr = remember(payload) { payload?.let { PayCodeQrEncoder.encode(it) } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) {
                    // Always black-on-white so scanners read it in dark mode too.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qr,
                            contentDescription = "校园卡付款码",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Text(
                        text = "付款码已取到，但无法绘制二维码",
                        color = colors.error,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                TtlCountdown(payload = payload, ttlSeconds = state.home.ttlSeconds)
            }
        }
        else -> {
            val off = !state.fetchEnabled
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.QrCode2,
                    contentDescription = null,
                    tint = colors.outline,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (off) "自动取码已关闭" else "暂未取到付款码",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                val hint = if (off) "打开下方「自动取码」后才会取码" else state.home.syncHint
                if (!hint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (state.home.showOpenPayCodeButton) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onOpenPayCode) {
                        Text(PayCodeHomePresentation.OPEN_PAY_CODE_LABEL)
                    }
                }
            }
        }
    }
}

/** Local countdown for the current payload; the ViewModel owns the actual refresh. */
@Composable
private fun TtlCountdown(payload: String?, ttlSeconds: Int?) {
    val colors = MaterialTheme.colorScheme
    val total = ttlSeconds?.takeIf { it > 0 }
    if (total == null) {
        Text(
            text = "向收银员出示此码",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        return
    }
    var remaining by remember(payload) { mutableIntStateOf(total) }
    LaunchedEffect(payload, total) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.82f),
    ) {
        LinearProgressIndicator(
            progress = { remaining / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            trackColor = colors.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (remaining > 0) "${remaining} 秒后自动刷新" else "正在刷新…",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun BalancePanel(
    balance: Balance?,
    isSyncing: Boolean,
    error: String?,
    onOpenRecharge: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Panel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "校园卡余额",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                when {
                    balance != null && balance.cardBalance.isNotEmpty() -> Text(
                        text = balance.cardBalance,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                    )
                    isSyncing -> Text(
                        text = "正在刷新…",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                    )
                    else -> Text(
                        text = "—",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                val meta = when {
                    error != null && balance == null -> error
                    balance != null -> listOfNotNull(
                        balance.networkBalance.takeIf { it.isNotEmpty() }?.let { "网费 $it" },
                        formatUpdated(balance.lastUpdate),
                    ).joinToString(" · ")
                    else -> null
                }
                if (!meta.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null && balance == null) colors.error else colors.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(onClick = onOpenRecharge) {
                Text("充值")
            }
        }
    }
}

@Composable
private fun PayCodeSwitchPanel(
    enabled: Boolean,
    hint: String?,
    onCheckedChange: (Boolean) -> Unit,
) {
    Panel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动取码", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (enabled) "按有效期自动刷新付款码" else "关闭时不取码、不自动刷新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onCheckedChange)
        }
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun SmsChallengeSection(
    state: PayCodeUiState,
    onGraphicChange: (String) -> Unit,
    onSmsChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSend: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sms, contentDescription = null, tint = colors.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (state.yhtSms) "一号通登录需要短信验证" else "当前设备需要身份验证",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (state.yhtSms) {
                "完成短信验证后即可刷新余额和取码"
            } else {
                state.home.maskedPhone?.let { "验证码将发送至尾号 $it 的手机" } ?: "请完成图形验证码和短信验证码"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (!state.yhtSms) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.graphicCaptcha,
                    onValueChange = onGraphicChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("图形验证码") },
                    singleLine = true,
                )
                if (!state.captchaImageUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AsyncImage(
                        model = state.captchaImageUrl,
                        contentDescription = "图形验证码",
                        modifier = Modifier
                            .width(104.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
            TextButton(onClick = onRefreshCaptcha, modifier = Modifier.align(Alignment.End)) {
                Text("看不清，换一张")
            }
        }
        OutlinedTextField(
            value = state.smsCode,
            onValueChange = onSmsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("短信验证码") },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = onSend, enabled = !state.sendingSms) {
                    Text(if (state.sendingSms) "发送中…" else "获取验证码")
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = !state.submittingSms,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(if (state.submittingSms) "验证中…" else "完成验证")
        }
        val message = state.challengeMessage ?: state.home.syncHint
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}

private fun formatUpdated(timestamp: Long): String {
    val minutes = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000
    return when {
        minutes < 1 -> "刚刚更新"
        minutes < 60 -> "${minutes} 分钟前更新"
        minutes < 1440 -> "${minutes / 60} 小时前更新"
        else -> "${minutes / 1440} 天前更新"
    }
}
