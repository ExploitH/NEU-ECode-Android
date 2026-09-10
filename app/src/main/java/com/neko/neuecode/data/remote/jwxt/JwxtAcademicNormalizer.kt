package com.neko.neuecode.data.remote.jwxt

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.neko.neuecode.domain.jwxt.JwxtExam
import com.neko.neuecode.domain.jwxt.JwxtExamDocument
import com.neko.neuecode.domain.jwxt.JwxtExamSummary
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScore
import com.neko.neuecode.domain.jwxt.JwxtScoreDocument
import com.neko.neuecode.domain.jwxt.JwxtScoreSummary

object JwxtAcademicNormalizer {
    const val SCORE_SOURCE = "NEU JWXT /jwapp/sys/homeapp/api/home/student/scores.do"
    const val EXAM_SOURCE = "NEU JWXT /jwapp/sys/homeapp/api/home/student/exams.do"

    fun normalizeScores(
        account: String,
        termCode: String,
        termName: String,
        rows: JsonArray,
        generatedAt: String,
    ): JwxtScoreDocument {
        val scores = rows.map { element ->
            val raw = element.asJsonObject
            JwxtScore(
                courseNo = raw.stringOrEmpty("courseNo"),
                courseName = raw.stringOrEmpty("courseName"),
                courseType = raw.stringOrEmpty("courseType"),
                credit = raw.numberOrNull("credit"),
                creditHours = raw.stringOrEmpty("creditHours"),
                score = raw.stringOrEmpty("score"),
                passStatus = raw.stringOrEmpty("passStatus"),
            )
        }
        return JwxtScoreDocument(
            source = SCORE_SOURCE,
            generatedAt = generatedAt,
            account = account,
            term = JwxtNamedCode(termCode, termName),
            summary = JwxtScoreSummary(count = scores.size),
            scores = scores,
        )
    }

    fun normalizeExams(
        account: String,
        termCode: String,
        termName: String,
        rows: JsonArray,
        generatedAt: String,
    ): JwxtExamDocument {
        val exams = rows.map { element ->
            val raw = element.asJsonObject
            JwxtExam(
                courseNo = raw.stringOrEmpty("courseNo"),
                courseName = raw.stringOrEmpty("courseName"),
                courseDesc = raw.stringOrEmpty("courseDesc"),
                date = raw.stringOrEmpty("examDate"),
                place = raw.stringOrEmpty("examPlace"),
                seatNo = raw.stringOrEmpty("examSeatNo"),
                status = raw.stringOrEmpty("examStatus"),
                timeDescription = raw.stringOrEmpty("examTimeDescription"),
                examType = raw.stringOrEmpty("examType"),
                examTypeCode = raw.stringOrEmpty("examTypeCode"),
                startTime = raw.stringOrEmpty("startTime"),
                endTime = raw.stringOrEmpty("endTime"),
                week = raw.intOrNull("week"),
                teachers = splitTeachers(raw.stringOrEmpty("teachers")),
                taskId = raw.stringOrEmpty("taskId"),
                teachingClassId = raw.stringOrEmpty("teachingClassId"),
            )
        }
        return JwxtExamDocument(
            source = EXAM_SOURCE,
            generatedAt = generatedAt,
            account = account,
            term = JwxtNamedCode(termCode, termName),
            summary = JwxtExamSummary(count = exams.size, arrangedCount = exams.size),
            exams = exams,
        )
    }

    private fun splitTeachers(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(*arrayOf("、", ",", "，", ";", "；", "/", " "))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun JsonObject.stringOrEmpty(name: String): String {
        val value = get(name) ?: return ""
        if (value.isJsonNull) return ""
        return try {
            value.asString
        } catch (_: Exception) {
            value.toString().trim('"')
        }
    }

    private fun JsonObject.numberOrNull(name: String): Double? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return try {
            value.asDouble
        } catch (_: Exception) {
            value.asString.toDoubleOrNull()
        }
    }

    private fun JsonObject.intOrNull(name: String): Int? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return try {
            value.asInt
        } catch (_: Exception) {
            value.asString.toIntOrNull()
        }
    }
}
