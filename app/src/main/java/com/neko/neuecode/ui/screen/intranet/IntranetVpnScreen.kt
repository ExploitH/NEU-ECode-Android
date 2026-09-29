package com.neko.neuecode.ui.screen.intranet

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neko.neuecode.domain.vpn.StudentVpnPhase
import com.neko.neuecode.domain.vpn.VpnStatusArtwork
import com.neko.neuecode.ui.components.Panel
import com.neko.neuecode.ui.components.PanelRow
import com.neko.neuecode.ui.components.VpnStatusMark

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
                windowInsets = WindowInsets.statusBars,
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VpnStatusMark(artwork = VpnStatusArtwork.forPhase(state.phase), size = 200.dp)
            Spacer(modifier = Modifier.height(4.dp))
            PhasePill(phase = state.phase)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "连接学生 VPN 后即可访问教务系统等校内接口，其他流量不经过隧道。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            state.message?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.phase == StudentVpnPhase.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Panel {
                PanelRow(
                    icon = Icons.Outlined.Person,
                    title = "账号",
                    value = state.username ?: "未保存长效登录学号",
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("提交验证码")
                }
            } else if (state.phase == StudentVpnPhase.Connected || state.phase == StudentVpnPhase.Disconnecting) {
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    enabled = state.phase != StudentVpnPhase.Disconnecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(if (state.phase == StudentVpnPhase.Disconnecting) "正在断开…" else "断开")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("连接学生 VPN")
                }
            }
        }
    }
}

@Composable
private fun PhasePill(phase: StudentVpnPhase) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (phase) {
        StudentVpnPhase.Connected -> colors.tertiaryContainer to colors.onTertiaryContainer
        StudentVpnPhase.Failed -> colors.errorContainer to colors.onErrorContainer
        StudentVpnPhase.Idle -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(content),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = phaseLabel(phase),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
        )
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
