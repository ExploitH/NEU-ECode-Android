package com.neko.neuecode.ui.screen.enrollment

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neko.neuecode.data.repository.EnrollmentRepository
import com.neko.neuecode.domain.enrollment.EnrollmentBatchMode
import com.neko.neuecode.domain.enrollment.EnrollmentCourse
import com.neko.neuecode.domain.enrollment.EnrollmentSandbox
import com.neko.neuecode.domain.enrollment.EnrollmentSimulationResult
import com.neko.neuecode.domain.enrollment.EnrollmentSelectedCourse
import com.neko.neuecode.domain.enrollment.EnrollmentScheduleEntry
import com.neko.neuecode.domain.enrollment.EnrollmentTarget
import com.neko.neuecode.domain.enrollment.MIN_COURSE_WEIGHT
import com.neko.neuecode.ui.enrollment.EnrollmentPortalActivity

private enum class EnrollmentTab(val label: String) {
    CATALOG("课程"),
    SELECTED("已选"),
    TARGETS("待选"),
    PREVIEW("预览")
}

@Composable
fun EnrollmentScreen(
    viewModel: EnrollmentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var mode by rememberSaveable { mutableStateOf(EnrollmentBatchMode.GRAB) }
    var selectedTab by rememberSaveable { mutableStateOf(EnrollmentTab.CATALOG) }
    var query by rememberSaveable { mutableStateOf("") }
    var simulationResult by remember { mutableStateOf<EnrollmentSimulationResult?>(null) }
    val targets = remember { mutableStateListOf<EnrollmentTarget>() }
    val portalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onPortalResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.session?.typeCode) {
        mode = when (uiState.session?.typeCode) {
            EnrollmentBatchMode.WEIGHT.typeCode -> EnrollmentBatchMode.WEIGHT
            else -> EnrollmentBatchMode.GRAB
        }
    }

    val openPortal = {
        portalLauncher.launch(EnrollmentPortalActivity.createIntent(context))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EnrollmentHeader(
            mode = mode,
            targetCount = targets.size,
            uiState = uiState,
            onSync = openPortal,
            onRefresh = viewModel::refreshAll
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnrollmentBatchMode.entries.forEach { item ->
                FilterChip(
                    selected = mode == item,
                    onClick = {
                        mode = item
                        simulationResult = null
                    },
                    label = { Text("${item.label} · ${item.typeCode}") }
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            EnrollmentTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(if (tab == EnrollmentTab.TARGETS) "${tab.label} ${targets.size}" else tab.label)
                    }
                )
            }
        }

        when (selectedTab) {
            EnrollmentTab.CATALOG -> CourseCatalog(
                uiState = uiState,
                query = query,
                onQueryChange = { query = it },
                selectedIds = targets.mapTo(hashSetOf()) { it.course.id },
                onCategoryChange = viewModel::selectCategory,
                onLoadMore = viewModel::loadNextPage,
                onSync = openPortal,
                onRetry = viewModel::refreshAll,
                onAdd = { course ->
                    targets += EnrollmentTarget(course)
                    simulationResult = null
                }
            )

            EnrollmentTab.SELECTED -> SelectedCoursesPanel(
                uiState = uiState,
                onSync = openPortal,
                onRetry = viewModel::refreshAll
            )

            EnrollmentTab.TARGETS -> TargetList(
                mode = mode,
                targets = targets,
                onMove = { index, offset ->
                    val target = index + offset
                    if (target in targets.indices) {
                        val item = targets.removeAt(index)
                        targets.add(target, item)
                    }
                },
                onWeightChange = { index, delta ->
                    targets[index] = targets[index].copy(weight = targets[index].weight + delta)
                    simulationResult = null
                },
                onRemove = { index ->
                    targets.removeAt(index)
                    simulationResult = null
                },
                onBrowse = { selectedTab = EnrollmentTab.CATALOG }
            )

            EnrollmentTab.PREVIEW -> PreviewPanel(
                mode = mode,
                targets = targets,
                result = simulationResult,
                onSimulate = { simulationResult = EnrollmentSandbox.simulate(mode, targets) }
            )
        }
    }
}

@Composable
private fun EnrollmentHeader(
    mode: EnrollmentBatchMode,
    targetCount: Int,
    uiState: EnrollmentUiState,
    onSync: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("协议选课测试台", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(uiState.session?.batchName?.takeIf { it.isNotBlank() } ?: "尚未同步选课轮次")
                        append(" · ${mode.label} · $targetCount 门待选")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                )
            }
            if (uiState.isLoading || uiState.isLoadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSync) {
                    Icon(Icons.Default.Sync, contentDescription = "同步选课轮次")
                }
                if (uiState.hasSession) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新只读数据")
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCatalog(
    uiState: EnrollmentUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedIds: Set<String>,
    onCategoryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSync: () -> Unit,
    onRetry: () -> Unit,
    onAdd: (EnrollmentCourse) -> Unit
) {
    if (!uiState.hasSession) {
        SessionRequiredState(message = uiState.errorMessage, onSync = onSync)
        return
    }
    val filtered = uiState.courses.filter { course ->
        query.isBlank() || listOf(course.name, course.teacher, course.clazzTypeLabel)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    val categoryChoices = buildList {
        add(EnrollmentRepository.ALL_COURSES)
        addAll(uiState.session?.courseTypes.orEmpty())
    }.distinct()
    val scheduleIds = uiState.schedule?.entries
        ?.mapTo(hashSetOf()) { it.teachingClassId }
        .orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("搜索课程或教师") }
            )
        }
        item {
            Text(
                "已读取 ${uiState.courses.size}/${uiState.totalCourses} 个教学班 · 官网已选 ${uiState.selectedCourses.size} · 课表 ${uiState.schedule?.entries?.size ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        uiState.errorMessage?.let { message ->
            item { ReadErrorState(message = message, onRetry = onRetry) }
        }
        items(uiState.warnings) { warning ->
            ReadWarning(message = warning)
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryChoices.forEach { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(categoryLabel(category)) }
                    )
                }
            }
        }
        if (uiState.isLoading && uiState.courses.isEmpty()) {
            item { LoadingReadState("正在串行读取课表、课程目录和已选课程") }
        }
        items(filtered, key = { it.id }) { course ->
            CourseCard(
                course = course,
                targetSelected = course.id in selectedIds,
                officiallySelected = course.clazzId in uiState.selectedTeachingClassIds,
                inSchedule = course.clazzId in scheduleIds,
                onAdd = { onAdd(course) }
            )
        }
        if (filtered.isEmpty() && !uiState.isLoading) {
            item { EmptyState(if (query.isBlank()) "当前分类没有课程" else "没有符合搜索条件的课程") }
        }
        if (uiState.hasMore || uiState.isLoadingMore) {
            item {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !uiState.isLoadingMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在读取下一页")
                    } else {
                        Text("加载下一页")
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: EnrollmentCourse,
    targetSelected: Boolean,
    officiallySelected: Boolean,
    inSchedule: Boolean,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${course.teacher} · ${course.credits} 学分 · ${course.clazzTypeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                val available = course.capacity - course.selectedCount
                Text(
                    buildString {
                        if (course.capacity > 0) {
                            append("已选 ${course.selectedCount}/${course.capacity} · 余量 ${available.coerceAtLeast(0)}")
                        } else {
                            append("已选 ${course.selectedCount}")
                        }
                        if (officiallySelected) append(" · 官网已选")
                        else if (inSchedule) append(" · 课表内")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        officiallySelected -> MaterialTheme.colorScheme.primary
                        course.capacity <= 0 || available > 0 -> Color(0xFF167A53)
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            IconButton(onClick = onAdd, enabled = !targetSelected && !officiallySelected) {
                Icon(
                    imageVector = if (targetSelected || officiallySelected) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = when {
                        officiallySelected -> "官网已选"
                        targetSelected -> "已加入本地待选"
                        else -> "加入本地待选"
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectedCoursesPanel(
    uiState: EnrollmentUiState,
    onSync: () -> Unit,
    onRetry: () -> Unit
) {
    if (!uiState.hasSession) {
        SessionRequiredState(message = uiState.errorMessage, onSync = onSync)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("官网已选 ${uiState.selectedCourses.size} 门", style = MaterialTheme.typography.titleMedium)
            Text(
                "${uiState.schedule?.termName?.takeIf { it.isNotBlank() } ?: "当前学期"} · 课表 ${uiState.schedule?.entries?.size ?: 0} 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        uiState.errorMessage?.let { message ->
            item { ReadErrorState(message = message, onRetry = onRetry) }
        }
        items(uiState.warnings) { warning -> ReadWarning(warning) }
        if (uiState.isLoading) {
            item { LoadingReadState("正在读取官网已选与课表") }
        }
        items(uiState.selectedCourses, key = { "selected-${it.teachingClassId}" }) { course ->
            SelectedCourseCard(course)
        }
        if (!uiState.isLoading && uiState.selectedCourses.isEmpty()) {
            item { EmptyState("当前三个已选接口均未返回课程") }
        }
        if (!uiState.schedule?.entries.isNullOrEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("当前课表", style = MaterialTheme.typography.titleMedium)
            }
            items(
                items = uiState.schedule?.entries.orEmpty(),
                key = { "schedule-${it.teachingClassId}-${it.weekday}-${it.startSection}" }
            ) { entry ->
                ScheduleEntryRow(entry)
            }
        }
    }
}

@Composable
private fun SelectedCourseCard(course: EnrollmentSelectedCourse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(course.courseName, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(course.teacher, course.sourceLabel, course.clazzType)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            course.currentWeight?.let { weight ->
                Text("当前权重 $weight", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ScheduleEntryRow(entry: EnrollmentScheduleEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.courseName, style = MaterialTheme.typography.titleSmall)
            Text(
                "周${weekdayLabel(entry.weekday)} 第${entry.startSection}-${entry.endSection}节 · ${entry.teacher} · ${entry.weeks}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionRequiredState(message: String?, onSync: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(message ?: "尚未同步当前选课轮次", style = MaterialTheme.typography.titleSmall)
            Button(onClick = onSync) { Text("打开选课官网") }
        }
    }
}

@Composable
private fun LoadingReadState(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReadErrorState(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun ReadWarning(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(message, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun categoryLabel(category: String): String = when (category) {
    EnrollmentRepository.ALL_COURSES -> "全部"
    "XGKC" -> "校公选"
    "TYKC" -> "体育"
    "FANKC" -> "方案内"
    "TJKC" -> "推荐课"
    else -> category
}

private fun weekdayLabel(weekday: Int): String = when (weekday) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> weekday.toString()
}

@Composable
private fun TargetList(
    mode: EnrollmentBatchMode,
    targets: List<EnrollmentTarget>,
    onMove: (Int, Int) -> Unit,
    onWeightChange: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onBrowse: () -> Unit
) {
    if (targets.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState("待选池为空")
                TextButton(onClick = onBrowse) { Text("浏览课程") }
            }
        }
        return
    }

    val budget = EnrollmentSandbox.calculateBudget(targets)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                if (mode == EnrollmentBatchMode.GRAB) "按顺序模拟尝试，首选成功后停止。"
                else "测试预算 ${budget.assigned}/${budget.total}，预计剩余 ${budget.remaining}。",
                style = MaterialTheme.typography.bodyMedium,
                color = if (mode == EnrollmentBatchMode.WEIGHT && !budget.isValid) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        items(targets.size, key = { targets[it].course.id }) { index ->
            val target = targets[index]
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.course.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${target.course.teacher} · ${target.course.clazzTypeLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onMove(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "提高优先级")
                        }
                        IconButton(onClick = { onMove(index, 1) }, enabled = index < targets.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "降低优先级")
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (mode == EnrollmentBatchMode.WEIGHT) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("目标权重", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(
                                onClick = { onWeightChange(index, -5) },
                                enabled = target.weight > MIN_COURSE_WEIGHT
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "权重减五")
                            }
                            Text(
                                target.weight.toString(),
                                modifier = Modifier.width(40.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { onWeightChange(index, 5) }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "权重加五")
                            }
                        }
                        if (target.weight < MIN_COURSE_WEIGHT) {
                            Text("每门课程最低 $MIN_COURSE_WEIGHT", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    mode: EnrollmentBatchMode,
    targets: List<EnrollmentTarget>,
    result: EnrollmentSimulationResult?,
    onSimulate: () -> Unit
) {
    val preview = EnrollmentSandbox.buildPreview(mode, targets)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(preview.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "TEST-BATCH · ${targets.size} 个教学班",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (preview.requestLines.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        preview.requestLines.joinToString("\n"),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        items(preview.warnings) { warning ->
            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(
                onClick = onSimulate,
                enabled = preview.canSimulate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("运行本地模拟")
            }
        }
        result?.let { simulation ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (simulation.accepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(simulation.title, style = MaterialTheme.typography.titleSmall)
                            Text(simulation.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "真实提交功能尚未接入。正式版需要重新采集当前会话参数、限流、互斥锁、结果确认与权重回滚保护。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
