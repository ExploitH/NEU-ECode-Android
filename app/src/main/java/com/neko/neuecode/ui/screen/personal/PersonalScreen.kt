package com.neko.neuecode.ui.screen.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neko.neuecode.BuildConfig
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.repository.AuthRepository
import com.neko.neuecode.domain.model.SessionState
import com.neko.neuecode.ui.common.LegalText
import com.neko.neuecode.ui.components.Panel
import com.neko.neuecode.ui.components.PanelDivider
import com.neko.neuecode.ui.components.PanelRow
import com.neko.neuecode.ui.components.SectionLabel
import com.neko.neuecode.util.CacheCleaner
import kotlinx.coroutines.launch

@Composable
fun PersonalScreen(
    sessionState: SessionState.Authenticated,
    cookieJar: PersistentCookieJar,
    userPreferences: UserPreferences,
    authRepository: AuthRepository,
    onLogout: () -> Unit,
    onOpenIntranet: () -> Unit = {},
    onOpenScores: () -> Unit = {},
    onOpenExams: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAgreementDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheCleanMessage by remember { mutableStateOf<String?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cookieJar.restoreFromStorage()
    }
    
    val user = sessionState.user
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val initial = user.name.trim().take(1)
                if (initial.isNotEmpty()) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = listOfNotNull(user.studentId ?: user.username, user.department).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionLabel("教务")
        Panel {
            PanelRow(icon = Icons.Outlined.Event, title = "考试", onClick = onOpenExams, trailing = { Chevron() })
            PanelDivider()
            PanelRow(icon = Icons.Outlined.Grade, title = "成绩", onClick = onOpenScores, trailing = { Chevron() })
            PanelDivider()
            PanelRow(icon = Icons.Outlined.VpnLock, title = "内网连接", onClick = onOpenIntranet, trailing = { Chevron() })
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("登录状态")
        Panel {
            PanelRow(
                icon = Icons.Outlined.History,
                title = "上次刷新",
                value = formatPast(sessionState.lastRefresh)
            )
            PanelDivider()
            PanelRow(
                icon = Icons.Outlined.Schedule,
                title = "登录有效期",
                value = formatExpiry(sessionState.expiresAt)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("其他")
        Panel {
            PanelRow(
                icon = Icons.Outlined.Info,
                title = "关于 / 用户协议",
                onClick = { showAboutDialog = true },
                trailing = { Chevron() }
            )
            PanelDivider()
            PanelRow(
                icon = Icons.Outlined.CleaningServices,
                title = if (isClearingCache) "正在清理缓存…" else "清理缓存",
                onClick = {
                    if (!isClearingCache) {
                        showClearCacheDialog = true
                    }
                },
                trailing = { Chevron() }
            )
            cacheCleanMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Panel {
            PanelRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = "退出登录",
                titleColor = MaterialTheme.colorScheme.error,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = { showLogoutDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "NEU e码通 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Text(
            text = "Powered by Neko 🐱",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("确认退出") },
            text = { Text("退出后需要重新登录，是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onOpenAgreement = { showAgreementDialog = true }
        )
    }

    if (showAgreementDialog) {
        AgreementReadOnlyDialog(
            onDismiss = { showAgreementDialog = false }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存") },
            text = { Text("将清理临时文件、WebView 缓存、更新包缓存和本地诊断缓存；不会清除登录 Cookie、长效登录凭证或协议配置。是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        scope.launch {
                            isClearingCache = true
                            cacheCleanMessage = null
                            val result = CacheCleaner.clearNonSessionCache(context)
                            cacheCleanMessage = "已清理 ${formatBytes(result.bytesDeleted)} / ${result.filesDeleted} 个文件，登录态已保留"
                            isClearingCache = false
                        }
                    }
                ) {
                    Text("清理")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于东大码") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "NEU e码通 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "这是一个面向个人学习与自用便利场景的东北大学 e码通辅助客户端，并非学校或相关服务提供方的官方应用。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "核心能力：原生协议登录、长效自动登录、e码通/充值页面、余额同步、小组件、Cloudflare Worker/R2 更新链路。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "默认后端：${BuildConfig.ECHELP_BASE_URL}\n包名：${BuildConfig.APPLICATION_ID}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onOpenAgreement,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("查看用户协议与免责声明")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun AgreementReadOnlyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LegalText.AGREEMENT_TITLE) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = LegalText.AGREEMENT_BODY,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline
    )
}

private fun formatPast(timestamp: Long): String {
    val minutes = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 1440 -> "${minutes / 60} 小时前"
        else -> "${minutes / 1440} 天前"
    }
}

/** [expiresAt] lies in the future; the old formatter only handled the past and showed 「刚刚」. */
private fun formatExpiry(expiresAt: Long): String {
    val minutes = (expiresAt - System.currentTimeMillis()) / 60_000
    return when {
        minutes <= 0 -> "已过期"
        minutes < 60 -> "${minutes} 分钟后过期"
        minutes < 1440 -> "${minutes / 60} 小时后过期"
        else -> "${minutes / 1440} 天后过期"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1fKB".format(kb)
    val mb = kb / 1024.0
    return "%.1fMB".format(mb)
}
