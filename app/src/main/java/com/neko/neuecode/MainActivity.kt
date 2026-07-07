package com.neko.neuecode

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.remote.update.AppUpdateRepository
import com.neko.neuecode.data.remote.update.AppVersionInfo
import com.neko.neuecode.data.repository.AuthRepository
import com.neko.neuecode.domain.model.SessionState
import com.neko.neuecode.ui.screen.login.NativeLoginScreen
import com.neko.neuecode.ui.theme.NeuECodeTheme
import com.neko.neuecode.ui.update.AppUpdateDialog
import com.neko.neuecode.ui.update.UpdateVerificationActivity
import com.neko.neuecode.util.AppUpdateInstaller
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var cookieJar: PersistentCookieJar

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var appUpdateRepository: AppUpdateRepository

    @Inject
    lateinit var appUpdateInstaller: AppUpdateInstaller

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("MainActivity created")

        setContent {
            NeuECodeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        cookieJar = cookieJar,
                        authRepository = authRepository,
                        userPreferences = userPreferences,
                        appUpdateRepository = appUpdateRepository,
                        appUpdateInstaller = appUpdateInstaller
                    )
                }
            }
        }
    }
}

@Composable
fun MainNavigation(
    cookieJar: PersistentCookieJar,
    authRepository: AuthRepository,
    userPreferences: UserPreferences,
    appUpdateRepository: AppUpdateRepository,
    appUpdateInstaller: AppUpdateInstaller
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sessionState by remember { mutableStateOf<SessionState>(SessionState.Loading) }
    var showLogin by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppVersionInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissOptionalUpdate by rememberSaveable { mutableStateOf(false) }
    var pendingInstallPath by rememberSaveable { mutableStateOf<String?>(null) }

    val verifyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val claim = result.data?.getStringExtra(UpdateVerificationActivity.EXTRA_UPDATE_CLAIM)
        if (result.resultCode == Activity.RESULT_OK && !claim.isNullOrBlank()) {
            scope.launch {
                updateBusy = true
                updateError = null
                try {
                    val downloadInfo = appUpdateRepository.resolveDownloadInfo(claim)
                    val apkFile = appUpdateInstaller.downloadApk(downloadInfo)
                    if (appUpdateInstaller.canRequestPackageInstalls()) {
                        appUpdateInstaller.installDownloadedApk(apkFile)
                        if (updateInfo?.forceUpdate != true) {
                            dismissOptionalUpdate = true
                        }
                    } else {
                        pendingInstallPath = apkFile.absolutePath
                        updateError = "下载完成，请授予“安装未知应用”权限后继续安装。"
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to complete app update")
                    updateError = e.message ?: "更新失败，请稍后重试"
                } finally {
                    updateBusy = false
                }
            }
        } else if (updateInfo?.forceUpdate == true) {
            updateError = "尚未完成更新验证，请重新尝试。"
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pendingFile = pendingInstallPath?.let(::File)
        if (pendingFile != null && pendingFile.exists()) {
            if (appUpdateInstaller.canRequestPackageInstalls()) {
                appUpdateInstaller.installDownloadedApk(pendingFile)
                pendingInstallPath = null
                if (updateInfo?.forceUpdate != true) {
                    dismissOptionalUpdate = true
                }
            } else {
                updateError = "尚未授予安装未知应用权限，请授权后重试。"
            }
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            val sessionDeferred = async {
                cookieJar.restoreFromStorage()
                authRepository.checkSession()
            }
            val updateDeferred = async {
                runCatching { appUpdateRepository.fetchLatestVersion() }
                    .onFailure { Timber.w(it, "App update check failed") }
                    .getOrNull()
            }

            sessionState = sessionDeferred.await()
            showLogin = when (val state = sessionState) {
                is SessionState.Idle, is SessionState.Expired -> true
                is SessionState.Error -> state.needRelogin
                else -> false
            }
            updateInfo = updateDeferred.await()?.takeIf { it.updateRequired }
        }
    }

    val shouldShowUpdateDialog = updateInfo?.let { it.forceUpdate || !dismissOptionalUpdate } == true

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            sessionState is SessionState.Loading -> {
                LoadingScreen()
            }
            showLogin -> {
                NativeLoginScreen(
                    onLoginSuccess = {
                        scope.launch {
                            sessionState = authRepository.checkSession()
                            showLogin = false
                        }
                    }
                )
            }
            sessionState is SessionState.Authenticated -> {
                com.neko.neuecode.ui.navigation.MainAppScreen(
                    sessionState = sessionState as SessionState.Authenticated,
                    cookieJar = cookieJar,
                    userPreferences = userPreferences,
                    authRepository = authRepository,
                    onLogout = {
                        scope.launch {
                            authRepository.logout()
                            sessionState = SessionState.Idle
                            showLogin = true
                        }
                    }
                )
            }
            else -> {
                ErrorScreen(
                    message = if (sessionState is SessionState.Error) {
                        (sessionState as SessionState.Error).message
                    } else {
                        "未知错误"
                    },
                    onRetry = {
                        scope.launch {
                            sessionState = authRepository.checkSession()
                        }
                    }
                )
            }
        }

        if (shouldShowUpdateDialog) {
            AppUpdateDialog(
                updateInfo = updateInfo!!,
                isBusy = updateBusy,
                onUpdate = {
                    scope.launch {
                        if (updateBusy) return@launch
                        updateBusy = true
                        updateError = null
                        try {
                            val session = appUpdateRepository.createUpdateSession()
                            verifyLauncher.launch(
                                UpdateVerificationActivity.createIntent(context, session.verifyUrl)
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to create update session")
                            updateError = e.message ?: "无法创建更新验证会话"
                        } finally {
                            updateBusy = false
                        }
                    }
                },
                onDismiss = {
                    dismissOptionalUpdate = true
                }
            )
        }

        if (updateError != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!updateBusy) {
                        updateError = null
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pendingFile = pendingInstallPath?.let(::File)
                            if (pendingFile != null && pendingFile.exists() && !appUpdateInstaller.canRequestPackageInstalls()) {
                                installPermissionLauncher.launch(appUpdateInstaller.createUnknownSourcesSettingsIntent())
                            } else {
                                updateError = null
                            }
                        }
                    ) {
                        Text(
                            if (pendingInstallPath != null && !appUpdateInstaller.canRequestPackageInstalls()) {
                                "去授权"
                            } else {
                                "知道了"
                            }
                        )
                    }
                },
                dismissButton = if (pendingInstallPath != null && appUpdateInstaller.canRequestPackageInstalls()) {
                    {
                        TextButton(
                            onClick = {
                                val pendingFile = pendingInstallPath?.let(::File)
                                if (pendingFile != null && pendingFile.exists()) {
                                    appUpdateInstaller.installDownloadedApk(pendingFile)
                                    pendingInstallPath = null
                                    updateError = null
                                }
                            }
                        ) {
                            Text("继续安装")
                        }
                    }
                } else {
                    null
                },
                title = { Text("更新提示") },
                text = { Text(updateError.orEmpty()) }
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("加载中...")
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "😿 出错了",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(message)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        minutes < 1440 -> "${minutes / 60}小时前"
        else -> "${minutes / 1440}天前"
    }
}
