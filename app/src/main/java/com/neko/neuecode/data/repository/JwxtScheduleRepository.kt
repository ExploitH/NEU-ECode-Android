package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.NeuCampusHttp
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtHumanVerificationRequired
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleClient
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleNormalizer
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.JwxtTermCatalog
import com.neko.neuecode.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JwxtScheduleRepository @Inject constructor(
    private val authenticator: JwxtCasAuthenticator,
    private val client: JwxtScheduleClient,
    private val credentialStore: SecureCredentialStore
) {
    suspend fun loadMySchedule(
        termCode: String? = null,
        campusCode: String? = null
    ): Result<JwxtScheduleDocument> {
        val credentials = credentialStore.load()
            ?: return Result.Error(
                Exception("No saved credentials"),
                "需要先开启长效登录，才能同步教务课表"
            )
        return withContext(Dispatchers.IO) {
            try {
                val started = System.nanoTime()
                authenticator.login(
                    username = credentials.username,
                    password = credentials.password,
                    service = JwxtScheduleClient.HOME_SERVICE
                )
                val afterLogin = System.nanoTime()
                val bundle = client.fetchBundle(
                    termCode = termCode,
                    campusCode = campusCode
                )
                val afterFetch = System.nanoTime()
                val document = JwxtScheduleNormalizer.normalize(
                    account = credentials.username,
                    termCode = bundle.term.code,
                    termName = bundle.term.name,
                    campusCode = bundle.campus.code,
                    campusName = bundle.campus.name,
                    sections = bundle.sections,
                    schedule = bundle.schedule,
                    generatedAt = utcNow()
                )
                val afterNormalize = System.nanoTime()
                Timber.i(
                    "JWXT schedule timings login=%dms fetch=%dms normalize=%dms events=%d",
                    (afterLogin - started) / 1_000_000,
                    (afterFetch - afterLogin) / 1_000_000,
                    (afterNormalize - afterFetch) / 1_000_000,
                    document.summary.eventCount,
                )
                Result.Success(document)
            } catch (e: JwxtHumanVerificationRequired) {
                Timber.w(e, "JWXT CAS requires human verification")
                Result.Error(e, "教务登录需要短信/验证码，请稍后在网页完成验证后再试")
            } catch (e: Exception) {
                Timber.e(e, "JWXT schedule sync failed")
                val message = e.message.orEmpty()
                val userMessage = if (NeuCampusHttp.looksLikeCampusTransport(message)) {
                    "课表同步超时或网关 502，请确认内网连接后重试"
                } else {
                    e.message ?: "课表同步失败"
                }
                Result.Error(e, userMessage)
            }
        }
    }

    suspend fun listRecentTerms(currentCode: String? = null, limit: Int = 8): Result<List<JwxtNamedCode>> {
        val credentials = credentialStore.load()
            ?: return Result.Error(
                Exception("No saved credentials"),
                "需要先开启长效登录，才能同步教务课表"
            )
        return withContext(Dispatchers.IO) {
            try {
                authenticator.login(
                    username = credentials.username,
                    password = credentials.password,
                    service = JwxtScheduleClient.HOME_SERVICE
                )
                val current = currentCode ?: client.getCurrentTerm().code
                val recent = JwxtTermCatalog.recent(
                    terms = client.listTerms(),
                    currentCode = current,
                    limit = limit,
                )
                Result.Success(recent)
            } catch (e: JwxtHumanVerificationRequired) {
                Timber.w(e, "JWXT CAS requires human verification")
                Result.Error(e, "教务登录需要短信/验证码，请稍后在网页完成验证后再试")
            } catch (e: Exception) {
                Timber.e(e, "JWXT term list failed")
                Result.Error(e, e.message ?: "学期列表同步失败")
            }
        }
    }

    private fun utcNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
