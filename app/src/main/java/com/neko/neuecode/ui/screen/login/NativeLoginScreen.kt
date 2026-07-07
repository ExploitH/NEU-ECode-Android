package com.neko.neuecode.ui.screen.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neko.neuecode.ui.common.LegalText

/**
 * Native login screen with RSA encryption
 * 
 * Features:
 * - Pure native UI (no WebView)
 * - RSA-1024 encrypted login
 * - Remember password
 * - Auto-login support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeLoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAwaitingSms = uiState is LoginUiState.AwaitingSms
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberUsername by remember { mutableStateOf(false) }
    var longTermLogin by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var smsCode by remember { mutableStateOf("") }
    var agreementAccepted by remember { mutableStateOf(false) }
    var showAgreementDialog by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    
    // Handle login success
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("东大码登录") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon
            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = "东大码",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Title
            Text(
                text = "东北大学 统一身份认证",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "使用智慧东大原生 RSA 登录协议",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("学工号") },
                placeholder = { Text("请输入学工号") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = "用户名")
                },
                singleLine = true,
                enabled = uiState !is LoginUiState.Loading && !isAwaitingSms,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("统一身份认证密码") },
                placeholder = { Text("请输入密码") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = "密码")
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                enabled = uiState !is LoginUiState.Loading && !isAwaitingSms,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isNotBlank() && password.isNotBlank() && agreementAccepted) {
                            viewModel.login(username, password, rememberUsername, longTermLogin)
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Remember username checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberUsername,
                    onCheckedChange = { rememberUsername = it },
                    enabled = uiState !is LoginUiState.Loading && !isAwaitingSms
                )
                Text(
                    text = "记住学工号",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = longTermLogin,
                    onCheckedChange = { checked ->
                        longTermLogin = checked
                        if (checked) rememberUsername = true
                    },
                    enabled = uiState !is LoginUiState.Loading && !isAwaitingSms
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "长效登录（推荐）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "使用系统密钥加密保存凭证，登录态过期时自动重新获取票据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AgreementCheckboxRow(
                checked = agreementAccepted,
                enabled = uiState !is LoginUiState.Loading && !isAwaitingSms,
                onCheckedChange = { agreementAccepted = it },
                onOpenAgreement = { showAgreementDialog = true }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Step 1: login / trigger SMS verification
            if (!isAwaitingSms) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.login(username, password, rememberUsername, longTermLogin)
                    },
                    enabled = username.isNotBlank() && password.isNotBlank() && agreementAccepted && uiState !is LoginUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("处理中...")
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("登录 / 获取验证码", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (uiState is LoginUiState.AwaitingSms) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "第二步：提交短信验证码",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (uiState as LoginUiState.AwaitingSms).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "验证码已发送后，请直接在这里输入并点击下方“提交验证码”。如需重发，请使用“重新发送验证码”，不要再次点击第一步按钮。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = { smsCode = it },
                            label = { Text("短信验证码") },
                            placeholder = { Text("请输入验证码") },
                            singleLine = true,
                            enabled = uiState !is LoginUiState.Loading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.verifySmsCode(smsCode)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.verifySmsCode(smsCode)
                                },
                                enabled = smsCode.isNotBlank() && uiState !is LoginUiState.Loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("提交验证码")
                            }
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.login(username, password, rememberUsername, longTermLogin)
                                },
                                enabled = username.isNotBlank() && password.isNotBlank() && agreementAccepted && uiState !is LoginUiState.Loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("重新发送验证码")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.resetState() },
                            enabled = uiState !is LoginUiState.Loading,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("返回修改账号密码")
                        }
                    }
                }
            }

            if (!agreementAccepted && !isAwaitingSms) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "登录前必须阅读并同意用户协议与免责声明",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // Error message
            if (uiState is LoginUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (uiState as LoginUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "原生协议登录 · 非 WebView 登录",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• 使用原生协议 content + authorization-str 请求\n• 登录响应在本地解密并保存票据\n• 若失败，可导出脱敏日志辅助排查",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAgreementDialog) {
        AgreementDialog(
            onDismiss = { showAgreementDialog = false },
            onAccept = {
                agreementAccepted = true
                showAgreementDialog = false
            }
        )
    }
}

@Composable
private fun AgreementCheckboxRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenAgreement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "我已阅读并同意用户协议与免责声明（必选）",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = onOpenAgreement,
                enabled = enabled,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("查看协议内容")
            }
        }
    }
}

@Composable
private fun AgreementDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
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
            TextButton(onClick = onAccept) {
                Text("已阅读并同意")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不同意")
            }
        }
    )
}

