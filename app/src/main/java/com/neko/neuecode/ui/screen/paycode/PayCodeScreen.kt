package com.neko.neuecode.ui.screen.paycode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
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

            Spacer(modifier = Modifier.height(12.dp))

            when (state.home.status) {
                PayCodeHomeStatus.Loading -> {
                    Text(
                        text = state.home.syncHint ?: "正在同步付款码…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PayCodeHomeStatus.Ready -> {
                    if (!state.home.syncHint.isNullOrBlank()) {
                        Text(
                            text = state.home.syncHint!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PayCodeHomeStatus.Failed -> {
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
        }
    }
}
