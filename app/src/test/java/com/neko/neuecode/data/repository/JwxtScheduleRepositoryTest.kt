package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
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

class JwxtScheduleRepositoryTest {

    @Test
    fun loadMySchedule_requiresSavedCredentials() = runBlocking {
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns null
        val repository = JwxtScheduleRepository(
            authenticator = mockk(relaxed = true),
            client = JwxtScheduleClient(OkHttpClient()),
            credentialStore = store
        )

        val result = repository.loadMySchedule()

        assertTrue(result is Result.Error)
        assertEquals("需要先开启长效登录，才能同步教务课表", (result as Result.Error).message)
    }

    @Test
    fun loadMySchedule_logsInOnceThenNormalizesReadOnlyBundle() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":{"cxmrxnxq":{"rows":[{"XNXQDM":"2026-2027-1","XNXQMC":"2026-2027学年秋季学期"}]}}}"""))
            server.enqueue(MockResponse().setBody("<html>kbapp</html>"))
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":{"getMyScheduledCampus":[{"id":"01","name":"浑南校区"}]}}"""))
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":{"getMySectionList":[{"code":1,"name":"第一节","id":"13"}]}}"""))
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"code":"0","datas":{"getMyScheduleDetail":{"arrangedList":[{
                      "courseCode":"A1001","courseName":"测试课程","teachClassId":"JX001",
                      "courseSerialNo":"JX001","teachClassName":"信息2401","teachingTarget":"信息2401",
                      "credit":"2.0","campusName":"浑南校区","weeksAndTeachers":"1-3周[理论]/张三[主讲]",
                      "beginTime":"08:30","endTime":"10:10","beginSection":1,"endSection":2,
                      "placeName":"信息A101","dayOfWeek":1,"secretVal":"must-not-survive",
                      "titleDetail":["信息2401","考试 / 百分制"],
                      "titleWeekTeacherClassroomDetail":["1-3周 张三 浑南校区 信息A101"]
                    }],"notArrangeList":[],"practiceList":[]}}}
                    """.trimIndent()
                )
            )
            val authenticator = mockk<JwxtCasAuthenticator>()
            every { authenticator.login(any(), any(), any()) } returns JwxtCasLoginResult(
                ok = true,
                account = "20240001",
                finalUrl = "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do"
            )
            val store = mockk<SecureCredentialStore>()
            every { store.load() } returns SecureCredentialStore.Credentials("20240001", "secret")
            val repository = JwxtScheduleRepository(
                authenticator = authenticator,
                client = JwxtScheduleClient(OkHttpClient(), server.url("/").toString().trimEnd('/')),
                credentialStore = store
            )

            val stages = mutableListOf<String>()
            val result = repository.loadMySchedule { stages.add(it.line) }

            assertTrue(result is Result.Success)
            val document = (result as Result.Success).data
            assertEquals("2026-2027-1", document.term.code)
            assertEquals(1, document.summary.eventCount)
            assertEquals("测试课程", document.events[0].courseName)
            assertTrue(document.toDebugSnapshot().contains("测试课程"))
            assertTrue(!document.toDebugSnapshot().contains("must-not-survive"))
            verify(exactly = 1) {
                authenticator.login("20240001", "secret", JwxtScheduleClient.HOME_SERVICE)
            }
            assertEquals("/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do", server.takeRequest().path)
            assertEquals(
                listOf(
                    "2/7 正在登录教务…",
                    "3/7 正在查询当前学期…",
                    "4/7 正在查询上课校区…",
                    "5/7 正在获取上课节次…",
                    "6/7 正在下载课程明细…",
                    "7/7 正在整理课表…",
                ),
                stages,
            )
        }
    }
}
