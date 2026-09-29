package com.neko.neuecode.ui.screen.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neko.neuecode.ui.common.LegalText
import com.neko.neuecode.ui.components.BrandMark

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
            viewModel.consumeSuccess()
            onLoginSuccess()
        }
    }
    
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            BrandMark(size = 76.dp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "东大码",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "使用东北大学统一身份认证账号登录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(36.dp))

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
                shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(14.dp),
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("登录中…")
                    } else {
                        Text("登录", style = MaterialTheme.typography.titleMedium)
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
                            text = "短信验证",
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
                            text = "收到短信后在此输入验证码；没收到可点「重新发送」。",
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
                                    viewModel.resendSmsCode()
                                },
                                enabled = uiState !is LoginUiState.Loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("重新发送")
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

            if (!agreementAccepted && !isAwaitingSms && username.isNotBlank() && password.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请先勾选同意用户协议与免责声明",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        Text(
            text = "我已阅读并同意",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "《用户协议与免责声明》",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onOpenAgreement)
                .padding(vertical = 6.dp)
        )
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

