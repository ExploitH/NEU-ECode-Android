package com.neko.neuecode.data.local.academic

import android.content.Context
import com.google.gson.Gson
import com.neko.neuecode.domain.jwxt.JwxtExamDocument
import com.neko.neuecode.domain.jwxt.JwxtScoreDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JwxtAcademicCacheStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val gson = Gson()
    private val scoresFile: File = File(context.filesDir, "jwxt_scores_cache.json")
    private val examsFile: File = File(context.filesDir, "jwxt_exams_cache.json")

    fun loadScores(): JwxtScoreDocument? = read(scoresFile, JwxtScoreDocument::class.java)
    fun saveScores(document: JwxtScoreDocument) = write(scoresFile, document)
    fun loadExams(): JwxtExamDocument? = read(examsFile, JwxtExamDocument::class.java)
    fun saveExams(document: JwxtExamDocument) = write(examsFile, document)

    private fun <T> read(file: File, type: Class<T>): T? {
        return try {
            if (!file.exists()) return null
            gson.fromJson(file.readText(Charsets.UTF_8), type)
        } catch (e: Exception) {
            Timber.w(e, "Failed to read local academic cache %s", file.name)
            null
        }
    }

    private fun write(file: File, value: Any) {
        try {
            file.writeText(gson.toJson(value), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "Failed to write local academic cache %s", file.name)
        }
    }
}
