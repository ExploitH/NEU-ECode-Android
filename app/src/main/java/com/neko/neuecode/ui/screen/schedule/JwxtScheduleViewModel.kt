package com.neko.neuecode.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.local.schedule.JwxtScheduleCacheStore
import com.neko.neuecode.data.local.schedule.ScheduleSettings
import com.neko.neuecode.data.local.schedule.ScheduleSettingsStore
import com.neko.neuecode.data.remote.campus.CampusIntranetProbe
import com.neko.neuecode.data.repository.JwxtScheduleRepository
import com.neko.neuecode.domain.jwxt.CourseDetail
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleTodayHighlight
import com.neko.neuecode.domain.jwxt.ScheduleWeekClock
import com.neko.neuecode.domain.jwxt.ScheduleSyncProgress
import com.neko.neuecode.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SchedulePane {
    Week,
    Today,
}

data class JwxtScheduleUiState(
    val loading: Boolean = false,
    val message: String = "点右上角「同步」拉取课表。同步前会先 ping ipgw.neu.edu.cn；不通即停止。",
    val document: JwxtScheduleDocument? = null,
    val terms: List<JwxtNamedCode> = emptyList(),
    val selectedTermCode: String? = null,
    val selectedWeek: Int = 1,
    val maxWeek: Int = 20,
    val pane: SchedulePane = SchedulePane.Week,
    val todayWeekday: Int = 1,
    val actualWeek: Int? = null,
    val markedWeekday: Int = 0,
    val termStartEpochDay: Long? = null,
    val showSettings: Boolean = false,
    val selectedDetail: CourseDetail? = null,
    val showIntranetHint: Boolean = false,
)

@HiltViewModel
class JwxtScheduleViewModel @Inject constructor(
    private val repository: JwxtScheduleRepository,
    private val cacheStore: JwxtScheduleCacheStore,
    private val settingsStore: ScheduleSettingsStore,
    private val intranetProbe: CampusIntranetProbe,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JwxtScheduleUiState())
    val uiState: StateFlow<JwxtScheduleUiState> = _uiState

    init {
        val settings = settingsStore.load()
        val todayEpochDay = ScheduleWeekClock.todayEpochDay()
        val weekday = ScheduleWeekClock.todayWeekday()
        val cached = cacheStore.load()
        val actualWeek = actualWeekOf(settings.termStartEpochDay, todayEpochDay)
        val selectedTerm = settings.defaultTermCode ?: cached?.term?.code
        val week = actualWeek ?: 1
        _uiState.value = _uiState.value.copy(
            document = cached,
            selectedTermCode = selectedTerm,
            terms = listOfNotNull(cached?.term).distinctBy { it.code },
            selectedWeek = week.coerceAtMost(maxWeekOf(cached)),
            maxWeek = maxWeekOf(cached),
            todayWeekday = weekday,
            actualWeek = actualWeek,
            markedWeekday = ScheduleTodayHighlight.weekdayToMark(week, actualWeek, weekday),
            termStartEpochDay = settings.termStartEpochDay,
            message = when {
                cached != null -> "${cached.term.name} · ${cached.campus.name} · 本地缓存（未自动同步）"
                else -> _uiState.value.message
            },
        )
    }

    fun refresh(termCode: String? = _uiState.value.selectedTermCode) {
        viewModelScope.launch {
            publishProgress(ScheduleSyncProgress.probing())
            val probe = withContext(Dispatchers.IO) { intranetProbe.probe() }
            if (probe.shouldAbortScheduleSync) {
                val pingFailed = probe.host.contains("ipgw")
                val message = if (pingFailed) {
                    "未接入校园网（无法 ping 通 ipgw.neu.edu.cn），已停止课表同步"
                } else {
                    "未接入校园网（无法连接教务系统），已停止课表同步"
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    message = message,
                    showIntranetHint = true,
                )
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                repository.loadMySchedule(
                    termCode = termCode,
                    onProgress = { progress -> publishProgress(progress) },
                )
            }
            when (result) {
                is Result.Success -> {
                    cacheStore.save(result.data)
                    val today = ScheduleWeekClock.todayEpochDay()
                    val weekday = ScheduleWeekClock.todayWeekday()
                    val actualWeek = actualWeekOf(_uiState.value.termStartEpochDay, today)
                    val week = (actualWeek ?: _uiState.value.selectedWeek).coerceIn(1, maxWeekOf(result.data))
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        message = "${result.data.term.name} · ${result.data.campus.name} · ${result.data.summary.courseCount} 门课",
                        document = result.data,
                        selectedTermCode = result.data.term.code,
                        selectedWeek = week,
                        maxWeek = maxWeekOf(result.data),
                        todayWeekday = weekday,
                        actualWeek = actualWeek,
                        markedWeekday = ScheduleTodayHighlight.weekdayToMark(week, actualWeek, weekday),
                        showIntranetHint = false,
                    )
                    loadTerms(result.data.term)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        message = result.message ?: "课表同步失败",
                        showIntranetHint = looksLikeCampusFailure(result.message),
                    )
                }
                Result.Loading -> publishProgress(ScheduleSyncProgress.loggingIn())
            }
        }
    }

    private fun publishProgress(progress: ScheduleSyncProgress) {
        _uiState.value = _uiState.value.copy(
            loading = true,
            message = progress.line,
            showIntranetHint = false,
        )
    }

    fun selectTerm(code: String) {
        if (code.isBlank() || code == _uiState.value.selectedTermCode) return
        _uiState.value = _uiState.value.copy(selectedTermCode = code)
        refresh(termCode = code)
    }

    fun selectWeek(week: Int) {
        val max = _uiState.value.maxWeek.coerceAtLeast(1)
        val selected = week.coerceIn(1, max)
        val marked = ScheduleTodayHighlight.weekdayToMark(
            selectedWeek = selected,
            actualWeek = _uiState.value.actualWeek,
            todayWeekday = _uiState.value.todayWeekday,
        )
        _uiState.value = _uiState.value.copy(selectedWeek = selected, markedWeekday = marked)
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

    fun openSettings() {
        _uiState.value = _uiState.value.copy(showSettings = true)
    }

    fun dismissSettings() {
        _uiState.value = _uiState.value.copy(showSettings = false)
    }

    fun saveSettings(defaultTermCode: String?, termStartEpochDay: Long?) {
        val settings = ScheduleSettings(
            defaultTermCode = defaultTermCode?.takeIf { it.isNotBlank() },
            termStartEpochDay = termStartEpochDay,
        )
        settingsStore.save(settings)
        val today = ScheduleWeekClock.todayEpochDay()
        val weekday = ScheduleWeekClock.todayWeekday()
        val actualWeek = actualWeekOf(termStartEpochDay, today)
        val max = _uiState.value.maxWeek.coerceAtLeast(1)
        val week = (actualWeek ?: _uiState.value.selectedWeek).coerceIn(1, max)
        _uiState.value = _uiState.value.copy(
            selectedTermCode = settings.defaultTermCode ?: _uiState.value.selectedTermCode,
            termStartEpochDay = termStartEpochDay,
            actualWeek = actualWeek,
            selectedWeek = week,
            todayWeekday = weekday,
            markedWeekday = ScheduleTodayHighlight.weekdayToMark(week, actualWeek, weekday),
            showSettings = false,
        )
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

    private fun actualWeekOf(termStartEpochDay: Long?, todayEpochDay: Long): Int? {
        return ScheduleWeekClock.actualWeek(termStartEpochDay, todayEpochDay)
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
            text.contains("ping") ||
            text.contains("Unable to resolve", ignoreCase = true) ||
            text.contains("Failed to connect", ignoreCase = true) ||
            text.contains("jwxt", ignoreCase = true)
    }
}
