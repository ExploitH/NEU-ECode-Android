package com.neko.neuecode.ui.screen.intranet

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neko.neuecode.domain.vpn.StudentVpnPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntranetVpnScreen(
    onBack: () -> Unit,
    viewModel: IntranetVpnViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var challenge by remember { mutableStateOf("") }
    val prepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
        }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "连接东北大学学生 VPN 后才能访问教务 / 付款码校园接口。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "应用内引擎走官方 OpenVPN 3（OpenVPN/openvpn3，MPL-2.0），自研 VpnService 包装。不嵌入 ics-openvpn，也不把 CA / tls-auth 写进公开仓库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "账号：" + (state.username ?: "未保存长效登录学号"),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "状态：" + phaseLabel(state.phase),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.message?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (state.phase == StudentVpnPhase.NeedChallenge) {
                OutlinedTextField(
                    value = challenge,
                    onValueChange = { challenge = it },
                    label = { Text("短信验证码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.submitChallenge(challenge)
                        challenge = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("提交验证码")
                }
            } else if (state.phase == StudentVpnPhase.Connected || state.phase == StudentVpnPhase.Disconnecting) {
                Button(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("断开")
                }
            } else {
                Button(
                    onClick = {
                        val prepare = viewModel.prepareIntent()
                        if (prepare != null) {
                            prepareLauncher.launch(prepare)
                        } else {
                            viewModel.connect()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("连接学生 VPN")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::openInstalledClient,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("备用：打开已安装的 OpenVPN")
            }
        }
    }
}

private fun phaseLabel(phase: StudentVpnPhase): String = when (phase) {
    StudentVpnPhase.Idle -> "未连接"
    StudentVpnPhase.Connecting -> "连接中"
    StudentVpnPhase.NeedChallenge -> "等待短信验证码"
    StudentVpnPhase.SubmittingChallenge -> "正在提交验证码"
    StudentVpnPhase.Connected -> "已连接"
    StudentVpnPhase.Disconnecting -> "正在断开"
    StudentVpnPhase.Failed -> "失败"
}
