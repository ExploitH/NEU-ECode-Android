package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.jwxt.JwxtAcademicClient
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtCasLoginResult
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleClient
import com.neko.neuecode.domain.model.Result
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtAcademicRepositoryTest {

    @Test
    fun loadScores_requiresSavedCredentials() = runBlocking {
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns null
        val http = OkHttpClient()
        val repository = JwxtAcademicRepository(
            authenticator = mockk(relaxed = true),
            scheduleClient = JwxtScheduleClient(http),
            academicClient = JwxtAcademicClient(http),
            credentialStore = store,
        )

        val result = repository.loadScores()

        assertTrue(result is Result.Error)
        assertEquals("需要先开启长效登录，才能同步教务成绩", (result as Result.Error).message)
    }

    @Test
    fun loadExams_requiresSavedCredentials() = runBlocking {
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns null
        val http = OkHttpClient()
        val repository = JwxtAcademicRepository(
            authenticator = mockk(relaxed = true),
            scheduleClient = JwxtScheduleClient(http),
            academicClient = JwxtAcademicClient(http),
            credentialStore = store,
        )

        val result = repository.loadExams()

        assertTrue(result is Result.Error)
        assertEquals("需要先开启长效登录，才能同步教务考试", (result as Result.Error).message)
    }

    @Test
    fun loadScores_logsInThenPostsTermCode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"code":"0","datas":{"cxmrxnxq":{"rows":[{"XNXQDM":"2025-2026-2","XNXQMC":"2025-2026学年春季学期"}]}}}"""
                )
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"code":"0","datas":[{"courseName":"线性代数","courseNo":"A1001","courseType":"必修","credit":"3.0","score":"87","passStatus":"及格"}]}"""
                )
            )
            val authenticator = mockk<JwxtCasAuthenticator>()
            every { authenticator.login(any(), any(), any()) } returns JwxtCasLoginResult(
                ok = true,
                account = "20240001",
                finalUrl = "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do",
            )
            val store = mockk<SecureCredentialStore>()
            every { store.load() } returns SecureCredentialStore.Credentials("20240001", "secret")
            val base = server.url("/").toString().trimEnd('/')
            val http = OkHttpClient()
            val repository = JwxtAcademicRepository(
                authenticator = authenticator,
                scheduleClient = JwxtScheduleClient(http, base),
                academicClient = JwxtAcademicClient(http, base),
                credentialStore = store,
            )

            val result = repository.loadScores()

            assertTrue(result is Result.Success)
            val document = (result as Result.Success).data
            assertEquals("2025-2026-2", document.term.code)
            assertEquals(1, document.scores.size)
            assertEquals("线性代数", document.scores[0].courseName)
            assertEquals("/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do", server.takeRequest().path)
            val scores = server.takeRequest()
            assertEquals("/jwapp/sys/homeapp/api/home/student/scores.do", scores.path)
            assertEquals("termCode=2025-2026-2", scores.body.readUtf8())
            verify { authenticator.login("20240001", "secret", JwxtAcademicClient.HOME_SERVICE) }
        }
    }

    @Test
    fun loadExams_emptyDatasIsSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":[],"msg":null}"""))
            val authenticator = mockk<JwxtCasAuthenticator>()
            every { authenticator.login(any(), any(), any()) } returns JwxtCasLoginResult(
                ok = true,
                account = "20240001",
                finalUrl = "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do",
            )
            val store = mockk<SecureCredentialStore>()
            every { store.load() } returns SecureCredentialStore.Credentials("20240001", "secret")
            val base = server.url("/").toString().trimEnd('/')
            val http = OkHttpClient()
            val repository = JwxtAcademicRepository(
                authenticator = authenticator,
                scheduleClient = JwxtScheduleClient(http, base),
                academicClient = JwxtAcademicClient(http, base),
                credentialStore = store,
            )

            val result = repository.loadExams(termCode = "2026-2027-1")

            assertTrue(result is Result.Success)
            val document = (result as Result.Success).data
            assertEquals(0, document.exams.size)
            assertEquals("2026-2027-1", document.term.code)
            assertEquals("2026-2027学年秋季学期", document.term.name)
            assertEquals("/jwapp/sys/homeapp/api/home/student/exams.do", server.takeRequest().path)
        }
    }
}
