package com.neko.neuecode.ui.screen.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.remote.enrollment.EnrollmentSessionExpiredException
import com.neko.neuecode.data.remote.enrollment.EnrollmentSessionUnavailableException
import com.neko.neuecode.data.repository.EnrollmentRepository
import com.neko.neuecode.data.repository.EnrollmentSessionMetadata
import com.neko.neuecode.domain.enrollment.EnrollmentCourse
import com.neko.neuecode.domain.enrollment.EnrollmentSchedule
import com.neko.neuecode.domain.enrollment.EnrollmentSelectedCourse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnrollmentUiState(
    val session: EnrollmentSessionMetadata? = null,
    val selectedCategory: String = EnrollmentRepository.ALL_COURSES,
    val courses: List<EnrollmentCourse> = emptyList(),
    val selectedCourses: List<EnrollmentSelectedCourse> = emptyList(),
    val schedule: EnrollmentSchedule? = null,
    val currentPage: Int = 0,
    val totalCourses: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList()
) {
    val hasSession: Boolean get() = session != null
    val selectedTeachingClassIds: Set<String>
        get() = selectedCourses.mapTo(linkedSetOf()) { it.teachingClassId }
}

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val repository: EnrollmentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EnrollmentUiState(session = repository.sessionMetadata())
    )
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    private var loadGeneration = 0L

    init {
        if (repository.hasSession()) refreshAll()
    }

    fun onPortalResult(synchronized: Boolean) {
        if (!synchronized) {
            _uiState.value = _uiState.value.copy(session = repository.sessionMetadata())
            return
        }
        refreshAll()
    }

    fun refreshAll() {
        val metadata = repository.sessionMetadata()
        if (metadata == null) {
            _uiState.value = EnrollmentUiState(
                selectedCategory = _uiState.value.selectedCategory,
                errorMessage = "请先同步当前选课轮次"
            )
            return
        }
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.value = EnrollmentUiState(
                session = metadata,
                selectedCategory = _uiState.value.selectedCategory,
                isLoading = true
            )
            try {
                val warnings = mutableListOf<String>()
                val schedule = runCatching { repository.loadSchedule() }
                    .onFailure { if (!it.isSessionFailure()) warnings += "课表读取失败" }
                    .getOrElse { error ->
                        if (error.isSessionFailure()) throw error
                        null
                    }
                val category = normalizedCategory(metadata, _uiState.value.selectedCategory)
                val catalog = repository.loadCatalogPage(category, pageNumber = 1)
                val selected = repository.loadSelectedCourses()
                warnings += selected.failures
                if (generation != loadGeneration) return@launch
                _uiState.value = EnrollmentUiState(
                    session = metadata,
                    selectedCategory = category,
                    courses = catalog.courses,
                    selectedCourses = selected.courses,
                    schedule = schedule,
                    currentPage = catalog.pageNumber,
                    totalCourses = catalog.total,
                    hasMore = catalog.hasMore,
                    warnings = warnings.distinct()
                )
            } catch (error: Exception) {
                handleLoadFailure(error, generation)
            }
        }
    }

    fun selectCategory(category: String) {
        if (category == _uiState.value.selectedCategory || _uiState.value.isLoading) return
        val metadata = repository.sessionMetadata() ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "请先同步当前选课轮次")
            return
        }
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                session = metadata,
                selectedCategory = category,
                courses = emptyList(),
                currentPage = 0,
                totalCourses = 0,
                hasMore = false,
                isLoading = true,
                errorMessage = null
            )
            try {
                val page = repository.loadCatalogPage(category, pageNumber = 1)
                if (generation != loadGeneration) return@launch
                _uiState.value = _uiState.value.copy(
                    courses = page.courses,
                    currentPage = page.pageNumber,
                    totalCourses = page.total,
                    hasMore = page.hasMore,
                    isLoading = false
                )
            } catch (error: Exception) {
                handleLoadFailure(error, generation)
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasSession || state.isLoading || state.isLoadingMore || !state.hasMore) return
        val generation = loadGeneration
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true, errorMessage = null)
            try {
                val page = repository.loadCatalogPage(
                    clazzType = state.selectedCategory,
                    pageNumber = state.currentPage + 1
                )
                if (generation != loadGeneration) return@launch
                _uiState.value = _uiState.value.copy(
                    courses = (_uiState.value.courses + page.courses).distinctBy { it.clazzId },
                    currentPage = page.pageNumber,
                    totalCourses = page.total,
                    hasMore = page.hasMore,
                    isLoadingMore = false
                )
            } catch (error: Exception) {
                handleLoadFailure(error, generation)
            }
        }
    }

    private fun handleLoadFailure(error: Exception, generation: Long) {
        if (generation != loadGeneration) return
        val sessionFailure = error.isSessionFailure()
        if (sessionFailure) repository.clearSession()
        _uiState.value = _uiState.value.copy(
            session = if (sessionFailure) null else repository.sessionMetadata(),
            isLoading = false,
            isLoadingMore = false,
            errorMessage = if (sessionFailure) {
                "选课会话已失效，请重新同步当前轮次"
            } else {
                "课程读取失败，请稍后重试"
            }
        )
    }

    private fun Throwable.isSessionFailure(): Boolean =
        this is EnrollmentSessionExpiredException || this is EnrollmentSessionUnavailableException

    private fun normalizedCategory(metadata: EnrollmentSessionMetadata, requested: String): String {
        if (requested == EnrollmentRepository.ALL_COURSES) return requested
        return requested.takeIf { it in metadata.courseTypes }
            ?: metadata.courseTypes.firstOrNull()
            ?: EnrollmentRepository.ALL_COURSES
    }
}