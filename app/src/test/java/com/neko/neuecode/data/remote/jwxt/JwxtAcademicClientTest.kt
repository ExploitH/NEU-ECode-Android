package com.neko.neuecode.data.remote.jwxt

import com.neko.neuecode.data.remote.NeuCampusHttp
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtAcademicClientTest {

    @Test
    fun getScores_postsHomeappStudentEndpointWithTermCode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"code":"0","datas":[{"courseName":"线性代数","courseNo":"A1001","courseType":"必修","credit":"3.0","score":"87","passStatus":"及格"}]}"""
                )
            )
            val client = JwxtAcademicClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
            )

            val rows = client.getScores("2025-2026-2")

            assertEquals(1, rows.size())
            assertEquals("线性代数", rows[0].asJsonObject.get("courseName").asString)
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/jwapp/sys/homeapp/api/home/student/scores.do", recorded.path)
            assertEquals("termCode=2025-2026-2", recorded.body.readUtf8())
            assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
            assertEquals(
                "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/home/index.html?av=&contextPath=/jwapp",
                recorded.getHeader("Referer"),
            )
            assertEquals(NeuCampusHttp.BROWSER_USER_AGENT, recorded.getHeader("User-Agent"))
        }
    }

    @Test
    fun getExams_postsHomeappStudentEndpointWithTermCode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"code":"0","datas":[{"courseName":"线性代数","examDate":"2026-06-18","examPlace":"信息A101"}]}"""
                )
            )
            val client = JwxtAcademicClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
            )

            val rows = client.getExams("2025-2026-2")

            assertEquals(1, rows.size())
            val recorded = server.takeRequest()
            assertEquals("/jwapp/sys/homeapp/api/home/student/exams.do", recorded.path)
            assertEquals("termCode=2025-2026-2", recorded.body.readUtf8())
        }
    }

    @Test
    fun getScores_emptyDatasIsSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":[],"msg":null}"""))
            val client = JwxtAcademicClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
            )

            val rows = client.getScores("2026-2027-1")

            assertEquals(0, rows.size())
        }
    }
}
