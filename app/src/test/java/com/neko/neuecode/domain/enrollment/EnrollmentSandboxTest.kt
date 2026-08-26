package com.neko.neuecode.domain.enrollment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentSandboxTest {
    private val course = EnrollmentCourse(
        id = "test-course",
        name = "测试课程",
        teacher = "测试教师",
        clazzType = "XGKC",
        clazzTypeLabel = "校公选课",
        clazzId = "TEST-XGKC-001",
        credits = 2.0,
        selectedCount = 10,
        capacity = 30
    )

    @Test
    fun `live submission is compile time disabled`() {
        assertFalse(EnrollmentSandbox.LIVE_SUBMISSION_SUPPORTED)
    }

    @Test
    fun `weight plan rejects a course below official minimum`() {
        val budget = EnrollmentSandbox.calculateBudget(
            listOf(EnrollmentTarget(course, weight = MIN_COURSE_WEIGHT - 1))
        )

        assertFalse(budget.isValid)
        assertTrue(course.id in budget.invalidCourseIds)
    }

    @Test
    fun `weight plan rejects totals over budget`() {
        val second = course.copy(id = "test-course-2", clazzId = "TEST-XGKC-002")
        val budget = EnrollmentSandbox.calculateBudget(
            listOf(EnrollmentTarget(course, 60), EnrollmentTarget(second, 45))
        )

        assertFalse(budget.isValid)
        assertTrue(budget.remaining < 0)
    }

    @Test
    fun `preview contains placeholders instead of session secrets`() {
        val preview = EnrollmentSandbox.buildPreview(
            EnrollmentBatchMode.GRAB,
            listOf(EnrollmentTarget(course))
        )
        val text = preview.requestLines.joinToString("\n")

        assertTrue(preview.canSimulate)
        assertTrue(text.contains("Authorization=<未读取>"))
        assertTrue(text.contains("secretVal=<未读取>"))
        assertFalse(text.contains("Bearer "))
    }

    @Test
    fun `simulation never claims a server enrollment result`() {
        val result = EnrollmentSandbox.simulate(
            EnrollmentBatchMode.GRAB,
            listOf(EnrollmentTarget(course))
        )

        assertTrue(result.accepted)
        assertTrue(result.detail.contains("未发送任何写请求"))
    }
}