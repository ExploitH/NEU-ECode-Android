package com.neko.neuecode.ui.screen.intranet

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neko.neuecode.data.local.secure.SecureCredentialStore
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.EntryPoint
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IntranetVpnEntryPoint {
    fun credentialStore(): SecureCredentialStore
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntranetVpnScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf<String?>(null) }
    var installedClient by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            IntranetVpnEntryPoint::class.java,
        )
        username = entry.credentialStore().load()?.username
        installedClient = OpenVpnLaunchIntent.firstAvailable(installedPackages(context.packageManager))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("内网连接") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
        ) {
            Text(
                "连接东北大学学生 VPN 后才能访问教务 / 付款码校园接口。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "本页不内置 OpenVPN 引擎，也不拷贝 GPL 的 ics-openvpn。配置必须保留分流（pull-filter ignore redirect-gateway），账号密码与 CAS 学号/密码相同。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "账号：" + (username ?: "未保存长效登录学号"),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (installedClient != null) "已检测到 OpenVPN 客户端" else "未检测到 OpenVPN 客户端",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val pkg = installedClient
                    if (pkg == null) {
                        message = "请先安装 OpenVPN 官方客户端，再用学生配置导入。"
                        return@Button
                    }
                    val launched = launchPackage(context, pkg)
                    message = if (launched) {
                        "已打开 OpenVPN。导入学生配置后连接，短信挑战只提交一次。"
                    } else {
                        "无法打开 $pkg"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (installedClient != null) "打开 OpenVPN" else "未安装 OpenVPN")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val market = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=net.openvpn.openvpn"),
                    )
                    try {
                        context.startActivity(market)
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=net.openvpn.openvpn"),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("去安装官方 OpenVPN")
            }
            message?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun installedPackages(packageManager: PackageManager): Set<String> {
    return OpenVpnLaunchIntent.CANDIDATE_PACKAGES.filter { pkg ->
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }.toSet()
}

private fun launchPackage(context: android.content.Context, packageName: String): Boolean {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return try {
        context.startActivity(launch)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
