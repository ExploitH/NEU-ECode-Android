package com.neko.neuecode.ui.screen.ecode

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neko.neuecode.ui.components.NeuWebView

@Composable
fun ECodeScreen(
    viewModel: ECodeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val isSyncingBalance by viewModel.isSyncingBalance.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is ECodeUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在加载 e码通...")
                }
            }
            
            is ECodeUiState.Ready -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Balance header
                    if (balance != null) {
                        BalanceHeader(
                            balance = balance!!,
                            isSyncing = isSyncingBalance,
                            onRefresh = { viewModel.loadBalance() }
                        )
                    }
                    
                    // WebView
                    NeuWebView(
                        url = currentUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onPageFinished = { url ->
                            viewModel.onPageFinished(url)
                        },
                        onError = { error ->
                            // Handle error silently or show toast
                        }
                    )
                }
            }
            
            is ECodeUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "😿 加载失败",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text((uiState as ECodeUiState.Error).message)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.reload() }) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceHeader(
    balance: com.neko.neuecode.domain.model.Balance,
    isSyncing: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (balance.cardBalance.isNotEmpty()) {
                    Text(
                        text = "校园卡：${balance.cardBalance}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (balance.networkBalance.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "网费：${balance.networkBalance}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(balance.lastUpdate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            IconButton(
                onClick = onRefresh,
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("🔄", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "刚刚更新"
        minutes < 60 -> "${minutes}分钟前更新"
        minutes < 1440 -> "${minutes / 60}小时前更新"
        else -> "${minutes / 1440}天前更新"
    }
}
