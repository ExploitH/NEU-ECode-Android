package com.neko.neuecode.domain.jwxt

data class JwxtScore(
    val courseNo: String,
    val courseName: String,
    val courseType: String,
    val credit: Double?,
    val creditHours: String,
    val score: String,
    val passStatus: String,
)

data class JwxtScoreSummary(
    val count: Int,
)

data class JwxtScoreDocument(
    val source: String,
    val generatedAt: String,
    val account: String,
    val term: JwxtNamedCode,
    val summary: JwxtScoreSummary,
    val scores: List<JwxtScore>,
)

data class JwxtExam(
    val courseNo: String,
    val courseName: String,
    val courseDesc: String,
    val date: String,
    val place: String,
    val seatNo: String,
    val status: String,
    val timeDescription: String,
    val examType: String,
    val examTypeCode: String,
    val startTime: String,
    val endTime: String,
    val week: Int?,
    val teachers: List<String>,
    val taskId: String,
    val teachingClassId: String,
)

data class JwxtExamSummary(
    val count: Int,
    val arrangedCount: Int,
)

data class JwxtExamDocument(
    val source: String,
    val generatedAt: String,
    val account: String,
    val term: JwxtNamedCode,
    val summary: JwxtExamSummary,
    val exams: List<JwxtExam>,
)
