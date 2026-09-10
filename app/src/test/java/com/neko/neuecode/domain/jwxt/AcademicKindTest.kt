package com.neko.neuecode.domain.jwxt

import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicKindTest {
    @Test
    fun fromRoute_mapsExamAndDefaultsToScores() {
        assertEquals(AcademicKind.Exams, AcademicKind.fromRoute("exams"))
        assertEquals(AcademicKind.Scores, AcademicKind.fromRoute("scores"))
        assertEquals(AcademicKind.Scores, AcademicKind.fromRoute(null))
        assertEquals("考试", AcademicKind.Exams.title)
        assertEquals("成绩", AcademicKind.Scores.title)
    }
}
