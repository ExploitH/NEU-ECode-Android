package com.neko.neuecode.ui.screen.recharge

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.repository.RechargeSession
import com.neko.neuecode.data.repository.RechargeSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.IOException

@HiltViewModel
class RechargeViewModel @Inject constructor(
    private val repository: RechargeSessionRepository,
    private val cookieJar: PersistentCookieJar,
) : ViewModel() {
    private val preparationLock = Mutex()

    suspend fun prepare(): RechargeSession = preparationLock.withLock {
        val session = repository.prepare()
        if (session is RechargeSession.ManualLogin) return@withLock session
        // Wait for CookieManager writes before the first WebView request.
        // Copy only cookies matching each real origin, preserving Path/HttpOnly/Secure.
        try { withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(true)
            for (url in listOf(RechargeSessionRepository.PORTAL_URL, "https://pass.neu.edu.cn/tpass/login")) {
                for (stored in cookieJar.getCookiesForUrl(url)) {
                    currentCoroutineContext().ensureActive()
                    val cookie = stored.toOkHttpCookie() ?: continue
                    suspendCancellableCoroutine<Unit> { continuation ->
                        manager.setCookie(url, cookie.toString()) { accepted ->
                            if (continuation.isActive) {
                                if (accepted) continuation.resume(Unit)
                                else continuation.resumeWithException(IOException("Cookie handoff rejected"))
                            }
                        }
                    }
                }
            }
            manager.flush()
        } } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withLock RechargeSession.Error("无法恢复充值登录状态，请点击刷新重试")
        }
        session
    }
}
