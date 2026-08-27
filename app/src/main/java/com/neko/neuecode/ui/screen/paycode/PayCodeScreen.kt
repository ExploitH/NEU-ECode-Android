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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
                    TextButton(onClick = { viewModel.refresh() }) {
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
            if (state.balance != null) {
                BalanceHeader(
                    balance = state.balance!!,
                    isSyncing = state.isSyncingBalance,
                    onRefresh = { viewModel.refresh() },
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

            when (state.home.status) {
                PayCodeHomeStatus.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.home.syncHint ?: "正在同步付款码…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PayCodeHomeStatus.Ready -> {
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
                PayCodeHomeStatus.Failed -> {
                    if (!state.home.syncHint.isNullOrBlank()) {
                        Text(
                            text = state.home.syncHint!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "小组件显示余额",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.widgetShowBalance,
                    onCheckedChange = { viewModel.setWidgetShowBalance(it) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenRecharge) {
                Text("校园卡充值")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
