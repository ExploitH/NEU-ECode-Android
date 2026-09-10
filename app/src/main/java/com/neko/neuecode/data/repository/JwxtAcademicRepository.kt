package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.NeuCampusHttp
import com.neko.neuecode.data.remote.jwxt.JwxtAcademicClient
import com.neko.neuecode.data.remote.jwxt.JwxtAcademicNormalizer
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtHumanVerificationRequired
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleClient
import com.neko.neuecode.domain.jwxt.AcademicKind
import com.neko.neuecode.domain.jwxt.AcademicSyncProgress
import com.neko.neuecode.domain.jwxt.JwxtExamDocument
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScoreDocument
import com.neko.neuecode.domain.jwxt.JwxtTermCatalog
import com.neko.neuecode.domain.jwxt.JwxtTermNames
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
class JwxtAcademicRepository @Inject constructor(
    private val authenticator: JwxtCasAuthenticator,
    private val scheduleClient: JwxtScheduleClient,
    private val academicClient: JwxtAcademicClient,
    private val credentialStore: SecureCredentialStore,
) {
    suspend fun loadScores(
        termCode: String? = null,
        onProgress: (AcademicSyncProgress) -> Unit = {},
    ): Result<JwxtScoreDocument> {
        val credentials = credentialStore.load()
            ?: return Result.Error(
                Exception("No saved credentials"),
                "需要先开启长效登录，才能同步教务成绩",
            )
        return withContext(Dispatchers.IO) {
            try {
                onProgress(AcademicSyncProgress.loggingIn())
                authenticator.login(
                    username = credentials.username,
                    password = credentials.password,
                    service = JwxtAcademicClient.HOME_SERVICE,
                )
                val term = resolveTerm(termCode, onProgress)
                onProgress(AcademicSyncProgress.downloading(AcademicKind.Scores))
                val rows = academicClient.getScores(term.code)
                onProgress(AcademicSyncProgress.arranging(AcademicKind.Scores))
                Result.Success(
                    JwxtAcademicNormalizer.normalizeScores(
                        account = credentials.username,
                        termCode = term.code,
                        termName = term.name,
                        rows = rows,
                        generatedAt = utcNow(),
                    )
                )
            } catch (e: JwxtHumanVerificationRequired) {
                Timber.w(e, "JWXT CAS requires human verification")
                Result.Error(e, "教务登录需要短信/验证码，请稍后在网页完成验证后再试")
            } catch (e: Exception) {
                Timber.e(e, "JWXT scores sync failed")
                Result.Error(e, userMessage(e, AcademicKind.Scores))
            }
        }
    }

    suspend fun loadExams(
        termCode: String? = null,
        onProgress: (AcademicSyncProgress) -> Unit = {},
    ): Result<JwxtExamDocument> {
        val credentials = credentialStore.load()
            ?: return Result.Error(
                Exception("No saved credentials"),
                "需要先开启长效登录，才能同步教务考试",
            )
        return withContext(Dispatchers.IO) {
            try {
                onProgress(AcademicSyncProgress.loggingIn())
                authenticator.login(
                    username = credentials.username,
                    password = credentials.password,
                    service = JwxtAcademicClient.HOME_SERVICE,
                )
                val term = resolveTerm(termCode, onProgress)
                onProgress(AcademicSyncProgress.downloading(AcademicKind.Exams))
                val rows = academicClient.getExams(term.code)
                onProgress(AcademicSyncProgress.arranging(AcademicKind.Exams))
                Result.Success(
                    JwxtAcademicNormalizer.normalizeExams(
                        account = credentials.username,
                        termCode = term.code,
                        termName = term.name,
                        rows = rows,
                        generatedAt = utcNow(),
                    )
                )
            } catch (e: JwxtHumanVerificationRequired) {
                Timber.w(e, "JWXT CAS requires human verification")
                Result.Error(e, "教务登录需要短信/验证码，请稍后在网页完成验证后再试")
            } catch (e: Exception) {
                Timber.e(e, "JWXT exams sync failed")
                Result.Error(e, userMessage(e, AcademicKind.Exams))
            }
        }
    }

    suspend fun listRecentTerms(currentCode: String? = null, limit: Int = 8): Result<List<JwxtNamedCode>> {
        val credentials = credentialStore.load()
            ?: return Result.Error(
                Exception("No saved credentials"),
                "需要先开启长效登录，才能同步教务学期",
            )
        return withContext(Dispatchers.IO) {
            try {
                authenticator.login(
                    username = credentials.username,
                    password = credentials.password,
                    service = JwxtAcademicClient.HOME_SERVICE,
                )
                val current = currentCode ?: scheduleClient.getCurrentTerm().code
                val recent = JwxtTermCatalog.recent(
                    terms = scheduleClient.listTerms(),
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

    private fun resolveTerm(
        termCode: String?,
        onProgress: (AcademicSyncProgress) -> Unit,
    ): JwxtNamedCode {
        onProgress(AcademicSyncProgress.currentTerm())
        if (termCode.isNullOrBlank()) {
            val current = scheduleClient.getCurrentTerm()
            return JwxtNamedCode(current.code, JwxtTermNames.displayName(current.code, current.name))
        }
        return JwxtNamedCode(termCode, JwxtTermNames.displayName(termCode))
    }

    private fun userMessage(error: Exception, kind: AcademicKind): String {
        val message = error.message.orEmpty()
        val noun = if (kind == AcademicKind.Scores) "成绩" else "考试"
        return if (NeuCampusHttp.looksLikeCampusTransport(message)) {
            "${noun}同步超时或网关 502，请确认内网连接后重试"
        } else {
            error.message ?: "${noun}同步失败"
        }
    }

    private fun utcNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
