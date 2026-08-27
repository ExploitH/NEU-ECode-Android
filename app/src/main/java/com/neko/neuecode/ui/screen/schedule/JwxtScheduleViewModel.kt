package com.neko.neuecode.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.local.schedule.JwxtScheduleCacheStore
import com.neko.neuecode.data.repository.JwxtScheduleRepository
import com.neko.neuecode.domain.jwxt.CourseDetail
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleWeekClock
import com.neko.neuecode.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

enum class SchedulePane {
    Week,
    Today,
}

data class JwxtScheduleUiState(
    val loading: Boolean = false,
    val message: String = "同步课表后可查看本周网格。需要校园网时请先去「我的 → 内网连接」。",
    val document: JwxtScheduleDocument? = null,
    val terms: List<JwxtNamedCode> = emptyList(),
    val selectedTermCode: String? = null,
    val selectedWeek: Int = 1,
    val maxWeek: Int = 20,
    val pane: SchedulePane = SchedulePane.Week,
    val todayWeekday: Int = 1,
    val selectedDetail: CourseDetail? = null,
    val showIntranetHint: Boolean = false,
)

@HiltViewModel
class JwxtScheduleViewModel @Inject constructor(
    private val repository: JwxtScheduleRepository,
    private val cacheStore: JwxtScheduleCacheStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JwxtScheduleUiState())
    val uiState: StateFlow<JwxtScheduleUiState> = _uiState

    init {
        val todayEpochDay = todayEpochDay()
        val weekday = ScheduleWeekClock.weekdayOf(todayEpochDay)
        val cached = cacheStore.load()
        val week = inferWeek(cached, todayEpochDay)
        _uiState.value = _uiState.value.copy(
            document = cached,
            selectedTermCode = cached?.term?.code,
            terms = cached?.term?.let { listOf(it) }.orEmpty(),
            selectedWeek = week,
            maxWeek = maxWeekOf(cached),
            todayWeekday = weekday,
            message = if (cached != null) {
                "${cached.term.name} · ${cached.campus.name} · 本地缓存"
            } else {
                _uiState.value.message
            },
        )
        refresh()
    }

    fun refresh(termCode: String? = _uiState.value.selectedTermCode) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                message = "正在同步教务课表…",
                showIntranetHint = false,
            )
            val result = withContext(Dispatchers.IO) {
                repository.loadMySchedule(termCode = termCode)
            }
            when (result) {
                is Result.Success -> {
                    cacheStore.save(result.data)
                    val week = inferWeek(result.data, todayEpochDay())
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        message = "${result.data.term.name} · ${result.data.campus.name} · ${result.data.summary.courseCount} 门课",
                        document = result.data,
                        selectedTermCode = result.data.term.code,
                        selectedWeek = week,
                        maxWeek = maxWeekOf(result.data),
                        showIntranetHint = false,
                    )
                    loadTerms(result.data.term)
                }
                is Result.Error -> {
                    val needsCampus = looksLikeCampusFailure(result.message)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        message = result.message ?: "课表同步失败",
                        showIntranetHint = needsCampus,
                    )
                }
                Result.Loading -> _uiState.value = _uiState.value.copy(loading = true, message = "正在同步教务课表…")
            }
        }
    }

    fun selectTerm(code: String) {
        if (code.isBlank() || code == _uiState.value.selectedTermCode) return
        _uiState.value = _uiState.value.copy(selectedTermCode = code)
        refresh(termCode = code)
    }

    fun selectWeek(week: Int) {
        val max = _uiState.value.maxWeek.coerceAtLeast(1)
        _uiState.value = _uiState.value.copy(selectedWeek = week.coerceIn(1, max))
    }

    fun shiftWeek(delta: Int) {
        selectWeek(_uiState.value.selectedWeek + delta)
    }

    fun selectPane(pane: SchedulePane) {
        _uiState.value = _uiState.value.copy(pane = pane)
    }

    fun openEvent(eventId: String) {
        val event = _uiState.value.document?.events?.firstOrNull { it.id == eventId } ?: return
        _uiState.value = _uiState.value.copy(selectedDetail = SchedulePresentation.detail(event))
    }

    fun dismissDetail() {
        _uiState.value = _uiState.value.copy(selectedDetail = null)
    }

    private fun loadTerms(current: JwxtNamedCode) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.listRecentTerms(currentCode = current.code)
            }
            if (result is Result.Success && result.data.isNotEmpty()) {
                val merged = (result.data + current).distinctBy { it.code }.sortedBy { it.code }
                _uiState.value = _uiState.value.copy(terms = merged)
            } else if (_uiState.value.terms.none { it.code == current.code }) {
                _uiState.value = _uiState.value.copy(terms = listOf(current))
            }
        }
    }

    private fun inferWeek(document: JwxtScheduleDocument?, todayEpochDay: Long): Int {
        val weeks = document?.events.orEmpty().flatMap { it.weeks }
        if (weeks.isEmpty()) return 1
        val min = weeks.min()
        val max = weeks.max()
        val guessed = ScheduleWeekClock.weekOf(termStartEpochDay = null, todayEpochDay = todayEpochDay)
        val preferred = if (guessed in min..max) guessed else max
        return preferred.coerceIn(min, max)
    }

    private fun maxWeekOf(document: JwxtScheduleDocument?): Int {
        val max = document?.events.orEmpty().flatMap { it.weeks }.maxOrNull() ?: 20
        return max.coerceAtLeast(1)
    }

    private fun looksLikeCampusFailure(message: String?): Boolean {
        val text = message.orEmpty()
        return text.contains("校园网") ||
            text.contains("内网") ||
            text.contains("超时") ||
            text.contains("Unable to resolve", ignoreCase = true) ||
            text.contains("Failed to connect", ignoreCase = true) ||
            text.contains("jwxt", ignoreCase = true)
    }

    private fun todayEpochDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 86_400_000L
    }
}
