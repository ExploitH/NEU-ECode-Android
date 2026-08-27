package com.neko.neuecode.ui.screen.intranet

import androidx.lifecycle.ViewModel
import com.neko.neuecode.data.vpn.StudentVpnController
import com.neko.neuecode.domain.vpn.StudentVpnUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class IntranetVpnViewModel @Inject constructor(
    private val controller: StudentVpnController,
) : ViewModel() {
    val state: StateFlow<StudentVpnUiState> = controller.state

    fun prepareIntent() = controller.prepareIntent()
    fun connect() = controller.connect()
    fun submitChallenge(code: String) = controller.submitChallenge(code)
    fun disconnect() = controller.disconnect()
}
