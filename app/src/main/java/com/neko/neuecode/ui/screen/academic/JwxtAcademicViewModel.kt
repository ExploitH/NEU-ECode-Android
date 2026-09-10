package com.neko.neuecode.ui.screen.academic

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.local.academic.JwxtAcademicCacheStore
import com.neko.neuecode.data.local.schedule.ScheduleSettingsStore
import com.neko.neuecode.data.remote.campus.CampusIntranetProbe
import com.neko.neuecode.data.repository.JwxtAcademicRepository
import com.neko.neuecode.domain.jwxt.AcademicKind
import com.neko.neuecode.domain.jwxt.AcademicSyncProgress
import com.neko.neuecode.domain.jwxt.JwxtExam
import com.neko.neuecode.domain.jwxt.JwxtExamDocument
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScore
import com.neko.neuecode.domain.jwxt.JwxtScoreDocument
import com.neko.neuecode.domain.jwxt.ScheduleLoginInitHint
import com.neko.neuecode.domain.model.Result
import com.neko.neuecode.widget.ExamWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class JwxtAcademicUiState(
    val kind: AcademicKind = AcademicKind.Scores,
    val loading: Boolean = false,
    val message: String = "",
    val scores: JwxtScoreDocument? = null,
    val exams: JwxtExamDocument? = null,
    val terms: List<JwxtNamedCode> = emptyList(),
    val selectedTermCode: String? = null,
    val selectedScore: JwxtScore? = null,
    val selectedExam: JwxtExam? = null,
    val showIntranetHint: Boolean = false,
    val syncStep: Int = 0,
    val showLoginInitHint: Boolean = false,
)

@HiltViewModel
class JwxtAcademicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: JwxtAcademicRepository,
    private val cacheStore: JwxtAcademicCacheStore,
    private val settingsStore: ScheduleSettingsStore,
    private val intranetProbe: CampusIntranetProbe,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val kind = AcademicKind.fromRoute(savedStateHandle.get<String>("kind"))
    private val _uiState = MutableStateFlow(
        JwxtAcademicUiState(
            kind = kind,
            message = defaultIdleMessage(kind),
        )
    )
    val uiState: StateFlow<JwxtAcademicUiState> = _uiState
    private var loginInitHint = ScheduleLoginInitHint.State()
    private var loginInitWatchJob: Job? = null

    init {
        // Cache only. Never auto-sync exams/scores on open; JWXT fetch is the 同步 button.
        val settings = settingsStore.load()
        val cachedScores = cacheStore.loadScores()
        val cachedExams = cacheStore.loadExams()
        val selectedTerm = settings.defaultTermCode
            ?: if (kind == AcademicKind.Scores) cachedScores?.term?.code else cachedExams?.term?.code
        val cachedTerm = if (kind == AcademicKind.Scores) cachedScores?.term else cachedExams?.term
        _uiState.value = _uiState.value.copy(
            scores = cachedScores,
            exams = cachedExams,
            selectedTermCode = selectedTerm,
            terms = listOfNotNull(cachedTerm).distinctBy { it.code },
            message = when {
                kind == AcademicKind.Scores && cachedScores != null ->
                    "${cachedScores.term.name} · ${cachedScores.summary.count} 门 · 本地缓存（未自动同步）"
                kind == AcademicKind.Exams && cachedExams != null ->
                    "${cachedExams.term.name} · ${cachedExams.summary.count} 场 · 本地缓存（未自动同步）"
                else -> defaultIdleMessage(kind)
            },
        )
    }

    fun refresh(termCode: String? = _uiState.value.selectedTermCode) {
        viewModelScope.launch {
            publishProgress(AcademicSyncProgress.probing())
            val probe = withContext(Dispatchers.IO) { intranetProbe.probe() }
            if (probe.shouldAbortScheduleSync) {
                val pingFailed = probe.host.contains("ipgw")
                val noun = kind.title
                val message = if (pingFailed) {
                    "未接入校园网（无法 ping 通 ipgw.neu.edu.cn），已停止${noun}同步"
                } else {
                    "未接入校园网（无法连接教务系统），已停止${noun}同步"
                }
                finishSyncProgress(loading = false, message = message, showIntranetHint = true)
                return@launch
            }
            when (kind) {
                AcademicKind.Scores -> {
                    val result = withContext(Dispatchers.IO) {
                        repository.loadScores(termCode = termCode, onProgress = { publishProgress(it) })
                    }
                    when (result) {
                        is Result.Success -> {
                            cacheStore.saveScores(result.data)
                            finishSyncProgress(
                                loading = false,
                                message = "${result.data.term.name} · ${result.data.summary.count} 门成绩",
                            )
                            _uiState.value = _uiState.value.copy(
                                scores = result.data,
                                selectedTermCode = result.data.term.code,
                                showIntranetHint = false,
                            )
                            loadTerms(result.data.term)
                        }
                        is Result.Error -> finishSyncProgress(
                            loading = false,
                            message = result.message ?: "成绩同步失败",
                            showIntranetHint = looksLikeCampusFailure(result.message),
                        )
                        Result.Loading -> publishProgress(AcademicSyncProgress.loggingIn())
                    }
                }
                AcademicKind.Exams -> {
                    val result = withContext(Dispatchers.IO) {
                        repository.loadExams(termCode = termCode, onProgress = { publishProgress(it) })
                    }
                    when (result) {
                        is Result.Success -> {
                            cacheStore.saveExams(result.data)
                            ExamWidgetProvider.updateAll(appContext)
                            finishSyncProgress(
                                loading = false,
                                message = "${result.data.term.name} · ${result.data.summary.count} 场考试",
                            )
                            _uiState.value = _uiState.value.copy(
                                exams = result.data,
                                selectedTermCode = result.data.term.code,
                                showIntranetHint = false,
                            )
                            loadTerms(result.data.term)
                        }
                        is Result.Error -> finishSyncProgress(
                            loading = false,
                            message = result.message ?: "考试同步失败",
                            showIntranetHint = looksLikeCampusFailure(result.message),
                        )
                        Result.Loading -> publishProgress(AcademicSyncProgress.loggingIn())
                    }
                }
            }
        }
    }

    fun selectTerm(code: String) {
        if (code.isBlank() || code == _uiState.value.selectedTermCode) return
        _uiState.value = _uiState.value.copy(selectedTermCode = code)
        refresh(termCode = code)
    }

    fun openScore(courseNo: String, courseName: String) {
        val score = _uiState.value.scores?.scores?.firstOrNull {
            it.courseNo == courseNo && it.courseName == courseName
        } ?: return
        _uiState.value = _uiState.value.copy(selectedScore = score)
    }

    fun openExam(courseNo: String, date: String, place: String) {
        val exam = _uiState.value.exams?.exams?.firstOrNull {
            it.courseNo == courseNo && it.date == date && it.place == place
        } ?: return
        _uiState.value = _uiState.value.copy(selectedExam = exam)
    }

    fun dismissDetail() {
        _uiState.value = _uiState.value.copy(selectedScore = null, selectedExam = null)
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

    private fun publishProgress(progress: AcademicSyncProgress) {
        val wasWatching = loginInitHint.watchingLogin
        loginInitHint = ScheduleLoginInitHint.onProgress(loginInitHint, progress.step)
        if (loginInitHint.watchingLogin && !wasWatching && !loginInitHint.latched) {
            loginInitWatchJob?.cancel()
            loginInitWatchJob = viewModelScope.launch {
                delay(ScheduleLoginInitHint.DELAY_MS)
                loginInitHint = ScheduleLoginInitHint.onLoginWaitElapsed(
                    loginInitHint,
                    stillOnLoginStep = _uiState.value.syncStep == ScheduleLoginInitHint.LOGIN_STEP,
                )
                if (loginInitHint.latched && _uiState.value.loading) {
                    _uiState.value = _uiState.value.copy(showLoginInitHint = true)
                }
            }
        } else if (!loginInitHint.watchingLogin) {
            loginInitWatchJob?.cancel()
            loginInitWatchJob = null
        }
        _uiState.value = _uiState.value.copy(
            loading = true,
            message = progress.line,
            syncStep = progress.step,
            showIntranetHint = false,
            showLoginInitHint = loginInitHint.latched,
        )
    }

    private fun finishSyncProgress(
        loading: Boolean,
        message: String,
        showIntranetHint: Boolean = false,
    ) {
        loginInitWatchJob?.cancel()
        loginInitWatchJob = null
        loginInitHint = ScheduleLoginInitHint.onFinished()
        _uiState.value = _uiState.value.copy(
            loading = loading,
            message = message,
            syncStep = 0,
            showIntranetHint = showIntranetHint,
            showLoginInitHint = false,
        )
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

    companion object {
        fun defaultIdleMessage(kind: AcademicKind): String {
            return "点右上角「同步」拉取${kind.title}。同步前会先 ping ipgw.neu.edu.cn；不通即停止。"
        }
    }
}
