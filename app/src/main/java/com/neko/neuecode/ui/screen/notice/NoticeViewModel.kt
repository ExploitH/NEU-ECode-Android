package com.neko.neuecode.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.local.notice.NoticeStore
import com.neko.neuecode.domain.notice.AppNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoticeUiState(
    val notices: List<AppNotice> = emptyList(),
    val readIds: Set<String> = emptySet(),
)

@HiltViewModel
class NoticeViewModel @Inject constructor(
    private val noticeStore: NoticeStore,
) : ViewModel() {
    val uiState: StateFlow<NoticeUiState> = combine(
        noticeStore.noticesFlow,
        noticeStore.readIdsFlow,
    ) { notices, readIds ->
        NoticeUiState(notices = notices, readIds = readIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoticeUiState())

    fun markAllReadOnOpen() {
        viewModelScope.launch {
            noticeStore.markAllRead()
        }
    }
}
