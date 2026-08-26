package com.neko.neuecode.data.repository

import com.neko.neuecode.data.remote.ecode.ECodePayCodeApi
import com.neko.neuecode.data.remote.ecode.ECodePayCodeHttpResponse
import com.neko.neuecode.data.remote.ecode.ECodePayCodeParser
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ECodePayCodeRepository @Inject constructor(
    private val api: ECodePayCodeApi,
) {
    suspend fun fetchPayCode(nowEpochMs: Long = System.currentTimeMillis()): PayCodeParseResult {
        val response = try {
            api.getQrCode()
        } catch (e: Exception) {
            return classifyNetworkFailure(e)
        }

        return when {
            response.code == 401 || response.code == 403 ->
                PayCodeParseResult.Failure(
                    PayCodeFailure.Unauthenticated,
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
            message.ifBlank { null },
        )
    }

    private fun classifyMessage(message: String): PayCodeFailure {
        return if (containsAny(message, "校园网", "NeedCampusNet", "campus", "vpn")) {
            PayCodeFailure.NeedCampusNet
        } else {
            PayCodeFailure.ProtocolError
        }
    }

    private fun containsAny(haystack: String, vararg needles: String): Boolean {
        return needles.any { haystack.contains(it, ignoreCase = true) }
    }
}
