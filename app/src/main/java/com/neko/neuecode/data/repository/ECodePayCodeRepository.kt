package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.NeuCampusHttp
import com.neko.neuecode.data.remote.ecode.ECodePayCodeApi
import com.neko.neuecode.data.remote.ecode.ECodePayCodeHttpResponse
import com.neko.neuecode.data.remote.ecode.ECodePayCodeParser
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtHumanVerificationRequired
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ECodePayCodeRepository @Inject constructor(
    private val api: ECodePayCodeApi,
    private val authenticator: JwxtCasAuthenticator,
    private val credentialStore: SecureCredentialStore,
    private val http: OkHttpClient,
) {
    suspend fun fetchPayCode(nowEpochMs: Long = System.currentTimeMillis()): PayCodeParseResult {
        val credentials = credentialStore.load()
            ?: return PayCodeParseResult.Failure(
                PayCodeFailure.Unauthenticated,
                "需要先开启长效登录，才能同步付款码",
            )
        return withContext(Dispatchers.IO) {
            try {
                authenticator.login(
                    username = credentials.username,
                    password = credentials.password,
                    service = NeuCampusHttp.ECODE_SSO,
                )
                warmupEcodeSession()
                fetchQrWithRetry(nowEpochMs)
            } catch (e: JwxtHumanVerificationRequired) {
                Timber.w(e, "eCode CAS requires human verification")
                PayCodeParseResult.Failure(
                    PayCodeFailure.NeedRelogin,
                    "付款码登录需要短信/验证码，请稍后在网页完成验证后再试",
                )
            } catch (e: Exception) {
                Timber.w(e, "eCode pay-code fetch failed")
                classifyNetworkFailure(e)
            }
        }
    }

    private fun warmupEcodeSession() {
        val request = Request.Builder()
            .url(NeuCampusHttp.ECODE_HOME)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
            .get()
            .build()
        runCatching {
            http.newCall(request).execute().use { it.body?.string() }
        }
    }

    private fun fetchQrWithRetry(nowEpochMs: Long): PayCodeParseResult {
        var last: PayCodeParseResult? = null
        repeat(3) { attempt ->
            val response = try {
                api.getQrCode()
            } catch (e: Exception) {
                val failure = classifyNetworkFailure(e)
                last = failure
                if (!NeuCampusHttp.looksLikeCampusTransport(e.message.orEmpty()) || attempt == 2) {
                    return failure
                }
                return@repeat
            }
            val parsed = interpret(response, nowEpochMs)
            last = parsed
            val retryable = parsed is PayCodeParseResult.Failure &&
                (parsed.reason == PayCodeFailure.NeedCampusNet ||
                    NeuCampusHttp.isRetryableGateway(response.code))
            if (!retryable || attempt == 2) {
                return parsed
            }
        }
        return last ?: PayCodeParseResult.Failure(PayCodeFailure.ProtocolError)
    }

    private fun interpret(
        response: ECodePayCodeHttpResponse,
        nowEpochMs: Long,
    ): PayCodeParseResult {
        return when {
            response.code == 401 || response.code == 403 ->
                PayCodeParseResult.Failure(
                    PayCodeFailure.Unauthenticated,
                    "HTTP ${response.code}",
                )
            NeuCampusHttp.isRetryableGateway(response.code) ->
                PayCodeParseResult.Failure(
                    PayCodeFailure.NeedCampusNet,
                    "HTTP ${response.code}",
                )
            response.code == 200 && isHtml(response) ->
                PayCodeParseResult.Failure(PayCodeFailure.NeedRelogin, "HTML login page")
            response.code != 200 ->
                PayCodeParseResult.Failure(
                    classifyMessage(response.body.ifBlank { "HTTP ${response.code}" }),
                    "HTTP ${response.code}",
                )
            else -> ECodePayCodeParser.parse(response.body, nowEpochMs)
        }
    }

    private fun isHtml(response: ECodePayCodeHttpResponse): Boolean {
        val contentType = response.contentType.orEmpty()
        if (contentType.contains("text/html", ignoreCase = true)) return true
        return response.body.trimStart().startsWith("<")
    }

    private fun classifyNetworkFailure(e: Exception): PayCodeParseResult.Failure {
        val message = e.message.orEmpty()
        return PayCodeParseResult.Failure(
            classifyMessage(message),
            if (NeuCampusHttp.looksLikeCampusTransport(message)) {
                "校园网超时或网关繁忙，请确认内网连接后重试"
            } else {
                message.ifBlank { null }
            },
        )
    }

    private fun classifyMessage(message: String): PayCodeFailure {
        return if (NeuCampusHttp.looksLikeCampusTransport(message)) {
            PayCodeFailure.NeedCampusNet
        } else {
            PayCodeFailure.ProtocolError
        }
    }
}
