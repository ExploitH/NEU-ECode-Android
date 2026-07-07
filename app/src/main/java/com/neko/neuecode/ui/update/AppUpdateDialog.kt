package com.neko.neuecode.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.neko.neuecode.BuildConfig
import com.neko.neuecode.data.remote.update.AppVersionInfo

@Composable
fun AppUpdateDialog(
    updateInfo: AppVersionInfo,
    isBusy: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val forceUpdate = updateInfo.forceUpdate
    AlertDialog(
        onDismissRequest = {
            if (!forceUpdate && !isBusy) {
                onDismiss()
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !isBusy
            ) {
                Text(if (isBusy) "处理中..." else "立即更新")
            }
        },
        dismissButton = if (!forceUpdate) {
            {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy
                ) {
                    Text("稍后")
                }
            }
        } else {
            null
        },
        title = {
            Text(if (forceUpdate) "需要升级应用" else "发现新版本")
        },
        text = {
            Column {
                Text(
                    text = buildString {
                        append("当前版本：")
                        append(BuildConfig.VERSION_NAME)
                        append(" (code ")
                        append(BuildConfig.VERSION_CODE)
                        append(")\n最新版本：")
                        append(updateInfo.latestVersionName.ifBlank { updateInfo.latestVersionCode.toString() })
                        append(" (code ")
                        append(updateInfo.latestVersionCode)
                        append(')')
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (forceUpdate) {
                    Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                    Text(
                        text = "当前版本低于最低支持版本，继续使用前必须完成更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                    Text("更新说明", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isBusy) {
                    Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = androidx.compose.ui.Modifier)
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !forceUpdate && !isBusy,
            dismissOnClickOutside = !forceUpdate && !isBusy
        )
    )
}
