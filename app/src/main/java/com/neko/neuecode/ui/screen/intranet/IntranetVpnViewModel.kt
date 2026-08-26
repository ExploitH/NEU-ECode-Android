package com.neko.neuecode.ui.screen.intranet

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.neko.neuecode.data.vpn.StudentVpnController
import com.neko.neuecode.domain.vpn.StudentVpnUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class IntranetVpnViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: StudentVpnController,
) : ViewModel() {
    val state: StateFlow<StudentVpnUiState> = controller.state

    fun prepareIntent() = controller.prepareIntent()
    fun connect() = controller.connect()
    fun submitChallenge(code: String) = controller.submitChallenge(code)
    fun disconnect() = controller.disconnect()

    fun openInstalledClient() {
        val pkg = OpenVpnLaunchIntent.firstAvailable(installedPackages())
        if (pkg != null) {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.openvpn.openvpn"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=net.openvpn.openvpn"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun installedPackages(): Set<String> {
        return OpenVpnLaunchIntent.CANDIDATE_PACKAGES.filter { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }.toSet()
    }
}
