package com.neko.neuecode.data.remote.jwxt

import com.neko.neuecode.data.remote.NeuCampusHttp
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtScheduleClientTest {

    @Test
    fun getCurrentTerm_postsObservedEmapForm() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"code":"0","datas":{"cxmrxnxq":{"rows":[{"XNXQDM":"2026-2027-1","XNXQMC":"2026-2027学年秋季学期"}]}}}"""
                )
            )
            val client = JwxtScheduleClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/')
            )

            val term = client.getCurrentTerm()

            assertEquals("2026-2027-1", term.code)
            assertEquals("2026-2027学年秋季学期", term.name)
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do", recorded.path)
            assertEquals("CSDM=SYS&ZCSDM=DQXNXQDM&SFSY=1", recorded.body.readUtf8())
            assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        }
    }

    @Test
    fun fetchBundle_usesObservedScheduleEndpoints() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>kbapp</html>"))
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":{"getMyScheduledCampus":[{"id":"01","name":"浑南校区"}]}}"""))
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":{"getMySectionList":[{"code":1,"name":"第一节"}]}}"""))
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":{"getMyScheduleDetail":{"arrangedList":[{"courseName":"测试课程"}],"notArrangeList":[],"practiceList":[]}}}"""))
            val client = JwxtScheduleClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/')
            )

            val bundle = client.fetchBundle(termCode = "2026-2027-1", termName = "2026-2027学年秋季学期")

            assertEquals("01", bundle.campus.code)
            assertEquals("浑南校区", bundle.campus.name)
            assertEquals(1, bundle.sections.size)
            assertEquals(1, bundle.schedule.getAsJsonArray("arrangedList").size())
            assertEquals("/jwapp/sys/kbapp/*default/index.do", server.takeRequest().path)
            assertEquals("/jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do", server.takeRequest().path)
            val section = server.takeRequest()
            assertEquals("/jwapp/sys/kbapp/api/wdkbcx/getMySectionList.do", section.path)
            assertEquals("XNXQDM=2026-2027-1&XQDM=01", section.body.readUtf8())
            val detail = server.takeRequest()
            assertEquals("/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do", detail.path)
            assertEquals("XNXQDM=2026-2027-1&XQDM=01", detail.body.readUtf8())
            assertEquals(
                NeuCampusHttp.BROWSER_USER_AGENT,
                detail.getHeader("User-Agent"),
            )
        }
    }

    @Test
    fun getCampuses_retriesOnceAfterHttp403() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>kbapp</html>"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
            server.enqueue(MockResponse().setBody("<html>kbapp</html>"))
            server.enqueue(MockResponse().setBody("""{"code":"0","datas":{"getMyScheduledCampus":[{"id":"01","name":"浑南校区"}]}}"""))
            val client = JwxtScheduleClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/')
            )

            val campuses = client.getCampuses("2025-2026-2")

            assertEquals(1, campuses.size())
            assertEquals("/jwapp/sys/kbapp/*default/index.do", server.takeRequest().path)
            assertEquals("/jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do", server.takeRequest().path)
            assertEquals("/jwapp/sys/kbapp/*default/index.do", server.takeRequest().path)
            assertEquals("/jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do", server.takeRequest().path)
        }
    }

    @Test
    fun listTerms_postsObservedXnxqcxForm() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"code":"0","datas":{"xnxqcx":{"rows":[{"DM":"2025-2026-1","MC":"2025-2026学年秋季学期"},{"DM":"2025-2026-2","MC":"2025-2026学年春季学期"}]}}}"""
                )
            )
            val client = JwxtScheduleClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/')
            )

            val terms = client.listTerms()

            assertEquals(2, terms.size)
            assertEquals("2025-2026-2", terms[1].code)
            assertEquals("2025-2026学年春季学期", terms[1].name)
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/jwapp/sys/jwpubapp/modules/zdgl/xnxqcx.do", recorded.path)
            assertEquals("*order=%2BDM", recorded.body.readUtf8())
        }
    }

    @Test
    fun getCurrentTerm_retriesOnceAfterHttp502() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
            server.enqueue(
                MockResponse().setBody(
                    """{"code":"0","datas":{"cxmrxnxq":{"rows":[{"XNXQDM":"2026-2027-1","XNXQMC":"2026-2027学年秋季学期"}]}}}"""
                )
            )
            val client = JwxtScheduleClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/')
            )

            val term = client.getCurrentTerm()

            assertEquals("2026-2027-1", term.code)
            assertEquals("/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do", server.takeRequest().path)
            assertEquals("/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do", server.takeRequest().path)
        }
    }
}
