package com.neko.neuecode.debug

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.vpn.StudentVpnController
import com.neko.neuecode.domain.vpn.StudentVpnPhase
import com.neko.neuecode.ui.theme.NeuECodeTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DebugVpnProbeActivity : ComponentActivity() {

    @Inject lateinit var credentials: SecureCredentialStore
    @Inject lateinit var controller: StudentVpnController

    private val prepare = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Timber.i("debug vpn prepare granted")
            controller.connect()
        } else {
            Timber.w("debug vpn prepare cancelled code=%s", result.resultCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = intent.getStringExtra(EXTRA_USER).orEmpty()
        val pass = intent.getStringExtra(EXTRA_PASS).orEmpty()
        if (user.isNotBlank() && pass.isNotBlank()) {
            credentials.save(user, pass)
            Timber.i("debug vpn seeded long-term login username length=%s", user.length)
        }
        setContent {
            NeuECodeTheme {
                val state by controller.state.collectAsState()
                var sms by remember { mutableStateOf("") }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    Text("VPN 探测（仅 debug）", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("coreReady=${state.coreReady}")
                    Text("phase=${state.phase}")
                    Text("user=${state.username ?: "none"}")
                    Text("challenge=${if (state.challenge != null) "yes" else "no"}")
                    Text("message=${state.message ?: "-"}")
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (state.phase == StudentVpnPhase.Connected ||
                                state.phase == StudentVpnPhase.Disconnecting
                            ) {
                                controller.disconnect()
                            } else {
                                val intent = VpnService.prepare(this@DebugVpnProbeActivity)
                                if (intent != null) {
                                    prepare.launch(intent)
                                } else {
                                    controller.connect()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.phase == StudentVpnPhase.Connected) "断开" else "连接学生 VPN",
                        )
                    }
                    if (state.phase == StudentVpnPhase.NeedChallenge) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = sms,
                            onValueChange = { sms = it },
                            label = { Text("短信验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { controller.submitChallenge(sms) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("提交验证码")
                        }
                    }
                }
            }
        }
        if (intent.getBooleanExtra(EXTRA_AUTO, false)) {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                prepare.launch(prepareIntent)
            } else {
                controller.connect()
            }
        }
    }

    companion object {
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"
        const val EXTRA_AUTO = "auto"

        fun intent(activity: Activity, user: String, pass: String, auto: Boolean = false): Intent {
            return Intent(activity, DebugVpnProbeActivity::class.java)
                .putExtra(EXTRA_USER, user)
                .putExtra(EXTRA_PASS, pass)
                .putExtra(EXTRA_AUTO, auto)
        }
    }
}
