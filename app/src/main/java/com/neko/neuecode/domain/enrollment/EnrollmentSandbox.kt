package com.neko.neuecode.domain.enrollment

enum class EnrollmentBatchMode(
    val typeCode: String,
    val label: String
) {
    GRAB("02", "先到先得"),
    WEIGHT("04", "权重选课")
}

data class EnrollmentCourse(
    val id: String,
    val name: String,
    val teacher: String,
    val clazzType: String,
    val clazzTypeLabel: String,
    val clazzId: String,
    val credits: Double,
    val selectedCount: Int,
    val capacity: Int
)

data class EnrollmentTarget(
    val course: EnrollmentCourse,
    val weight: Int = MIN_COURSE_WEIGHT
)

data class WeightBudget(
    val total: Int,
    val assigned: Int,
    val remaining: Int,
    val invalidCourseIds: Set<String>,
    val isValid: Boolean
)

data class EnrollmentPreview(
    val title: String,
    val requestLines: List<String>,
    val warnings: List<String>,
    val canSimulate: Boolean
)

data class EnrollmentSimulationResult(
    val accepted: Boolean,
    val title: String,
    val detail: String
)

const val MIN_COURSE_WEIGHT = 5
const val DEFAULT_WEIGHT_BUDGET = 100

/**
 * Builds a local-only enrollment plan. This object deliberately has no HTTP client,
 * Cookie access, WebView bridge, or live submission implementation.
 */
object EnrollmentSandbox {
    const val LIVE_SUBMISSION_SUPPORTED = false

    fun calculateBudget(
        targets: List<EnrollmentTarget>,
        total: Int = DEFAULT_WEIGHT_BUDGET
    ): WeightBudget {
        val invalidIds = targets
            .filter { it.weight < MIN_COURSE_WEIGHT }
            .mapTo(linkedSetOf()) { it.course.id }
        val assigned = targets.sumOf { it.weight }
        return WeightBudget(
            total = total,
            assigned = assigned,
            remaining = total - assigned,
            invalidCourseIds = invalidIds,
            isValid = invalidIds.isEmpty() && assigned <= total
        )
    }

    fun buildPreview(
        mode: EnrollmentBatchMode,
        targets: List<EnrollmentTarget>,
        batchId: String = "TEST-BATCH"
    ): EnrollmentPreview {
        if (targets.isEmpty()) {
            return EnrollmentPreview(
                title = "尚未添加待选课程",
                requestLines = emptyList(),
                warnings = listOf("请从课程列表添加至少一个教学班。"),
                canSimulate = false
            )
        }

        val budget = calculateBudget(targets)
        val commonWarnings = listOf(
            "本地提交模拟不读取 secretVal，也不复用只读会话头。",
            "本地提交模拟不发送写请求，也不会创建定时或后台抢课任务。"
        )
        val requestLines = when (mode) {
            EnrollmentBatchMode.GRAB -> targets.flatMapIndexed { index, target ->
                listOf(
                    "#${index + 1} POST /xsxk/elective/clazz/add",
                    "headers: Authorization=<未读取>; batchId=$batchId",
                    "form: clazzType=${target.course.clazzType}; clazzId=${target.course.clazzId}; secretVal=<未读取>"
                )
            }

            EnrollmentBatchMode.WEIGHT -> targets.flatMapIndexed { index, target ->
                listOf(
                    "#${index + 1} 事务预览 ${target.course.name}",
                    "退回旧权重 -> 刷新教学班参数 -> 重投权重 ${target.weight} -> 查询确认",
                    "fields: clazzType=${target.course.clazzType}; clazzId=${target.course.clazzId}; secretVal=<未读取>"
                )
            }
        }

        val warnings = if (mode == EnrollmentBatchMode.WEIGHT && !budget.isValid) {
            commonWarnings + if (budget.invalidCourseIds.isNotEmpty()) {
                "每门课程权重必须是不小于 $MIN_COURSE_WEIGHT 的整数。"
            } else {
                "目标权重合计 ${budget.assigned}，超过测试预算 ${budget.total}。"
            }
        } else {
            commonWarnings
        }

        return EnrollmentPreview(
            title = when (mode) {
                EnrollmentBatchMode.GRAB -> "先到先得请求预览"
                EnrollmentBatchMode.WEIGHT -> "权重事务预览"
            },
            requestLines = requestLines,
            warnings = warnings,
            canSimulate = mode == EnrollmentBatchMode.GRAB || budget.isValid
        )
    }

    fun simulate(
        mode: EnrollmentBatchMode,
        targets: List<EnrollmentTarget>
    ): EnrollmentSimulationResult {
        val preview = buildPreview(mode, targets)
        if (!preview.canSimulate) {
            return EnrollmentSimulationResult(
                accepted = false,
                title = "模拟已阻止",
                detail = preview.warnings.lastOrNull() ?: "计划不满足测试规则。"
            )
        }
        return EnrollmentSimulationResult(
            accepted = true,
            title = "本地模拟通过",
            detail = "已校验 ${targets.size} 个教学班；未读取写参数，未发送任何写请求。"
        )
    }
}