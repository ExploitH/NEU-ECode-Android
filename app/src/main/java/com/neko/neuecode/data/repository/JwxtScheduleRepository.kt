package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtHumanVerificationRequired
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleClient
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleNormalizer
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.model.Result
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
        return try {
            authenticator.login(
                username = credentials.username,
                password = credentials.password,
                service = JwxtScheduleClient.HOME_SERVICE
            )
            val bundle = client.fetchBundle(
                termCode = termCode,
                campusCode = campusCode
            )
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
            Result.Success(document)
        } catch (e: JwxtHumanVerificationRequired) {
            Timber.w(e, "JWXT CAS requires human verification")
            Result.Error(e, "教务登录需要短信/验证码，请稍后在网页完成验证后再试")
        } catch (e: Exception) {
            Timber.e(e, "JWXT schedule sync failed")
            Result.Error(e, e.message ?: "课表同步失败")
        }
    }

    private fun utcNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
