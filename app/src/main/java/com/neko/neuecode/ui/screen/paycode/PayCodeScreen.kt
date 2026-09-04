package com.neko.neuecode.ui.screen.paycode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.neko.neuecode.ui.screen.ecode.BalanceHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayCodeScreen(
    onOpenPayCode: () -> Unit,
    onOpenRecharge: () -> Unit,
    viewModel: PayCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("付款码") },
                windowInsets = WindowInsets.statusBars,
                actions = {
                    TextButton(
                        onClick = { viewModel.refresh() },
                        enabled = state.fetchEnabled && !state.awaitingSms,
                    ) {
                        Text("刷新")
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PayCodeSwitchRow(
                enabled = state.fetchEnabled,
                hint = state.switchHint.ifBlank { state.home.switchHint },
                onCheckedChange = viewModel::setFetchEnabled,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.balance != null) {
                BalanceHeader(
                    balance = state.balance!!,
                    isSyncing = state.isSyncingBalance,
                    onRefresh = { viewModel.refresh() },
                    refreshEnabled = state.fetchEnabled && !state.awaitingSms,
                )
            } else if (state.isSyncingBalance) {
                Text(
                    text = "正在刷新余额…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.balanceError != null) {
                Text(
                    text = state.balanceError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.home.syncHint ?: "正在同步付款码…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.home.status == PayCodeHomeStatus.Ready && state.home.showNativeQr -> {
                    val payload = state.home.payload
                    val qr = remember(payload) { payload?.let { PayCodeQrEncoder.encode(it) } }
                    if (qr != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
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
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!state.home.syncHint.isNullOrBlank()) {
                        Text(
                            text = state.home.syncHint!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    if (!state.home.syncHint.isNullOrBlank()) {
                        Text(
                            text = state.home.syncHint!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (state.home.showOpenPayCodeButton) {
                        Button(
                            onClick = onOpenPayCode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(PayCodeHomePresentation.OPEN_PAY_CODE_LABEL)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onOpenRecharge) {
                Text("校园卡充值")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PayCodeSwitchRow(
    enabled: Boolean,
    hint: String?,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动取码", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (enabled) "开启后会取码并按有效期自动刷新" else "关闭后不取码、不自动刷新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onCheckedChange)
        }
        if (!hint.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (state.yhtSms) {
                "一号通登录需要短信验证"
            } else {
                "当前设备需要身份验证"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (state.yhtSms) {
                "请完成短信验证码后再刷新余额和取码"
            } else {
                state.home.maskedPhone?.let { "绑定手机尾号 $it" } ?: "请完成图形验证码和短信验证码"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!state.yhtSms) {
                OutlinedTextField(
                    value = state.graphicCaptcha,
                    onValueChange = onGraphicChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("图形验证码") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (!state.captchaImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = state.captchaImageUrl,
                        contentDescription = "图形验证码",
                        modifier = Modifier
                            .width(96.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
        if (!state.yhtSms) {
            TextButton(onClick = onRefreshCaptcha) {
                Text("看不清，换一张")
            }
        }
        OutlinedTextField(
            value = state.smsCode,
            onValueChange = onSmsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("短信验证码") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSend,
                enabled = !state.sendingSms,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.sendingSms) "发送中…" else "获取验证码")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.submittingSms,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.submittingSms) "验证中…" else "完成验证")
            }
        }
        val message = state.challengeMessage ?: state.home.syncHint
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
