package com.neko.neuecode.ui.screen.recharge

import android.webkit.CookieManager
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.cookie.SerializableCookie
import com.neko.neuecode.data.repository.RechargeSession
import com.neko.neuecode.data.repository.RechargeSessionRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RechargeViewModelTest {
    private val manager = mockk<CookieManager>(relaxed = true)
    private val repo = mockk<RechargeSessionRepository>()
    private val jar = mockk<PersistentCookieJar>()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() } returns manager
        every { jar.getCookiesForUrl(any()) } returns emptyList()
        coEvery { repo.prepare() } returns RechargeSession.Ready(RechargeSessionRepository.PORTAL_URL)
    }

    @After fun cleanup() {
        unmockkStatic(CookieManager::class)
        Dispatchers.resetMain()
    }

    @Test fun handoffPreservesHttpOnlySecurePathAndExpiry() = runBlocking {
        val original = SerializableCookie("JSESSIONID", "fixture", "pass.neu.edu.cn", path = "/tpass", secure = true,
            httpOnly = true, hostOnly = true, persistent = true, expiresAt = 4_102_444_800_000)
        every { jar.getCookiesForUrl("https://pass.neu.edu.cn/tpass/login") } returns listOf(original)
        val header = slot<String>()
        every { manager.setCookie(any(), capture(header), any()) } answers {
            thirdArg<android.webkit.ValueCallback<Boolean>>().onReceiveValue(true)
        }
        val result = RechargeViewModel(repo, jar).prepare()
        assertTrue(result is RechargeSession.Ready)
        val parsed = okhttp3.Cookie.parse(okhttp3.HttpUrl.Builder().scheme("https").host("pass.neu.edu.cn").build(), header.captured)!!
        assertEquals("/tpass", parsed.path)
        assertTrue(parsed.httpOnly)
        assertTrue(parsed.secure)
        assertTrue(parsed.hostOnly)
        assertEquals(original.expiresAt, parsed.expiresAt)
    }

    @Test fun rejectedCookieDoesNotLoadAsReady() = runBlocking {
        every { jar.getCookiesForUrl(RechargeSessionRepository.PORTAL_URL) } returns listOf(
            SerializableCookie("JSESSIONID", "fixture", "pay.neu.edu.cn", httpOnly = true),
        )
        every { manager.setCookie(any(), any(), any()) } answers {
            thirdArg<android.webkit.ValueCallback<Boolean>>().onReceiveValue(false)
        }
        assertTrue(RechargeViewModel(repo, jar).prepare() is RechargeSession.Error)
    }

    @Test fun manualFallbackDoesNotOverwriteBrowserOwnedSession() = runBlocking {
        coEvery { repo.prepare() } returns RechargeSession.ManualLogin(RechargeSessionRepository.CAS_SERVICE, "登录")
        RechargeViewModel(repo, jar).prepare()
        verify(exactly = 0) { manager.setCookie(any(), any(), any()) }
    }
}
