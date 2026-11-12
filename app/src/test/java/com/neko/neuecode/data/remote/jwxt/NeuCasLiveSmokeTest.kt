package com.neko.neuecode.data.remote.jwxt

import com.google.gson.Gson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Explicit opt-in campus smoke test. Never runs in ordinary CI.
 * NEU_LIVE_AUTH_FILE points to a private two-line file outside the repository.
 * Only aggregate counts are printed; credentials and session data stay in memory.
 */
class NeuCasLiveSmokeTest {
    @Test
    fun newSessionAuthenticatesAndReadsJwxt() {
        val authPath = System.getenv("NEU_LIVE_AUTH_FILE").orEmpty()
        assumeTrue("set NEU_LIVE_AUTH_FILE to opt in to campus requests", authPath.isNotBlank())
        val lines = File(authPath).readLines()
        require(lines.size >= 2 && lines[0].isNotBlank() && lines[1].isNotBlank())
        val jar = object : CookieJar {
            private val stored = linkedMapOf<Triple<String, String, String>, Cookie>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookies.forEach { stored[Triple(it.domain, it.path, it.name)] = it }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = stored.values
                .filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
                .sortedByDescending { it.path.length }
        }
        val http = OkHttpClient.Builder().cookieJar(jar).followRedirects(false)
            .callTimeout(40, TimeUnit.SECONDS).build()
        try {
            val auth = JwxtCasAuthenticator(http)
            val login = auth.login(lines[0], lines[1], JwxtScheduleClient.HOME_SERVICE)
            assertTrue(login.ok)
            assertTrue(login.finalUrl.startsWith("https://jwxt.neu.edu.cn/"))
            val bundle = JwxtScheduleClient(http).fetchBundle()
            val document = JwxtScheduleNormalizer.normalize(
                lines[0], bundle.term.code, bundle.term.name, bundle.campus.code,
                bundle.campus.name, bundle.sections, bundle.schedule, Instant.now().toString(),
            )
            assertEquals(document.courses.size, document.summary.courseCount)
            assertEquals(document.events.size, document.summary.eventCount)
            assertEquals(document.events.size, document.events.map { it.id }.toSet().size)
            assertTrue(document.events.all { it.weekday in 1..7 && it.sections.start in 1..12 && it.sections.end in it.sections.start..12 })
            val ids = document.events.map { it.id }.toSet()
            assertTrue(document.courses.all { course -> course.eventIds.all { it in ids } })
            val academic = JwxtAcademicClient(http)
            val scores = academic.getScores(bundle.term.code)
            val exams = academic.getExams(bundle.term.code)
            // Exercise the retained CASTGC / service-session path without a second password submission.
            assertTrue(auth.login(lines[0], lines[1], JwxtScheduleClient.HOME_SERVICE).ok)
            val result = linkedMapOf<String, Any>(
                "ok" to true, "runtime" to "Kotlin/OkHttp", "term" to bundle.term.code,
                "campus" to bundle.campus.code, "courses" to document.courses.size,
                "events" to document.events.size, "scores" to scores.size(), "exams" to exams.size(),
                "sessionReuse" to true,
            )
            println("NEU_LIVE_RESULT=" + Gson().toJson(result))
        } finally {
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
        }
    }
}
