package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.api.PersonalApi
import com.neko.neuecode.data.remote.model.LoginCommitData
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.neko.neuecode.domain.model.Result
import com.neko.neuecode.domain.model.SessionState
import com.neko.neuecode.domain.model.User
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import java.net.URLEncoder
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication repository
 * Handles app login, session management, and auto-refresh
 * Based on reverse engineering of 智慧东大 app
 */
@Singleton
class AuthRepository @Inject constructor(
    private val personalApi: PersonalApi,
    private val userPreferences: UserPreferences,
    private val cookieJar: PersistentCookieJar,
    private val rsaCrypto: com.neko.neuecode.data.remote.crypto.NeuRsaCrypto,
    private val secureCredentialStore: SecureCredentialStore
) {
    data class SmsVerificationRequired(
        val username: String,
        val password: String,
        val tempToken: String,
        val imei: String,
        val rememberUsername: Boolean,
        val longTermLogin: Boolean
    )
    
    companion object {
        private const val PERSONAL_SERVICE_URL = "https://personal.neu.edu.cn/portal/"
        private const val ECODE_SERVICE_URL = "https://ecode.neu.edu.cn/ecode/"
        private const val SESSION_REFRESH_THRESHOLD = 2 * 60 * 60 * 1000L // 2 hours
        private const val SESSION_EXPIRE_TIME = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Login with username and password using RSA encryption
     * 
     * Protocol reverse-engineered from 智慧东大 v3.1.5:
     * 1. Encrypt login JSON with RSA public key -> content parameter
     * 2. Encrypt timestamp+ticket JSON with RSA -> authorization-str header
     * 3. POST to /app_login/commit
     * 4. Extract cookies (CK_LC, CK_VL, SESS_ID) from response
     * 5. Save to persistent cookie jar
     */
    suspend fun login(
        username: String,
        password: String,
        rememberUsername: Boolean = false,
        longTermLogin: Boolean = false
    ): Result<User> {
        return try {
            Timber.i("Starting RSA-encrypted login")
            rsaCrypto.warmUpRemoteConfig()
            val imei = rsaCrypto.stableImei()
            
            // Encrypt login content
            val encryptedContent = rsaCrypto.encryptLoginContent(username, password, imei)
            Timber.d("Encrypted content length: ${encryptedContent.length}")
            
            // Encrypt authorization-str (first login has empty ticket)
            val encryptedAuth = rsaCrypto.encryptAuthorizationStr(ticket = "")
            Timber.d("Encrypted authorization-str length: ${encryptedAuth.length}")
            
            // Manually URL-encode the content parameter
            val urlEncodedContent = java.net.URLEncoder.encode(encryptedContent, "UTF-8")
            Timber.d("URL-encoded content length: ${urlEncodedContent.length}")
            
            // Construct form body manually
            // Use "content" parameter as found in blutter rsaPost function
            val requestBody = okhttp3.RequestBody.create(
                "application/x-www-form-urlencoded".toMediaType(),
                "content=$urlEncodedContent"
            )
            
            // Call commit API with encrypted parameters
            val response = personalApi.appLoginCommit(
                content = requestBody,
                authorizationStr = encryptedAuth
            )
            
            if (!response.isSuccessful) {
                Timber.w("Login failed with HTTP ${response.code()}")
                return Result.Error(
                    Exception("Login failed: ${response.code()}"),
                    "登录失败，请检查用户名和密码"
                )
            }
            
            val body = response.body()
            if (body == null) {
                Timber.w("Login response body is null")
                return Result.Error(
                    Exception("Empty response"),
                    "登录响应为空"
                )
            }
            
            // Try to decrypt error message if present
            if (body.code != "0" && body.dataRaw is String) {
                Timber.w("Login API returned error code: ${body.code}, message: ${body.message}")
                
                // Attempt to decrypt error details
                val decryptedError = rsaCrypto.decryptResponse(body.dataRaw)
                if (decryptedError != null) {
                    Timber.e("Decrypted error details received (${decryptedError.length} chars)")
                    return Result.Error(
                        Exception("Login error: ${body.message}"),
                        "${body.message}\n详细信息已隐藏，请查看脱敏日志"
                    )
                } else {
                    return Result.Error(
                        Exception("Login error: ${body.message}"),
                        body.message ?: "未知错误"
                    )
                }
            }
            
            if (body.code != "0") {
                val errorMsg = body.message ?: "未知错误"
                Timber.w("Login API returned error: $errorMsg")
                return Result.Error(
                    Exception("Login error: $errorMsg"),
                    errorMsg
                )
            }
            
            // Check if response data is encrypted
            val dataRaw = body.dataRaw
            if (dataRaw == null) {
                Timber.w("Login response data is null")
                return Result.Error(
                    Exception("Empty login response"),
                    "登录响应数据为空"
                )
            }
            
            // Handle encrypted response
            if (body.isDataEncrypted && dataRaw is String) {
                Timber.i("Response data is encrypted (Base64 length: ${dataRaw.length})")
                
                // Decrypt response
                val decryptedJson = rsaCrypto.decryptResponse(dataRaw)
                if (decryptedJson == null) {
                    Timber.e("Failed to decrypt response with all available private keys")
                    return Result.Error(
                        Exception("Decryption failed"),
                        "响应解密失败，可能需要更新解密密钥"
                    )
                }
                
                // Parse decrypted JSON
                val loginData = try {
                    parseLoginCommitData(decryptedJson)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse decrypted JSON")
                    return Result.Error(
                        Exception("JSON parse error"),
                        "解密后的数据格式错误: ${e.message}"
                    )
                }
                
                return handleLoginData(loginData, username, password, imei, rememberUsername, longTermLogin)
            }
            
            // If we reach here, data might be plain JSON (unexpected, but handle it)
            Timber.i("Response data appears to be plain JSON")
            return Result.Error(
                Exception("Unexpected plain response"),
                "收到明文响应（预期为加密），协议可能已变更"
            )
            
        } catch (e: Exception) {
            Timber.e(e, "RSA-encrypted login failed")
            Result.Error(e, "登录失败: ${e.message}")
        }
    }

    suspend fun verifySmsCode(pending: SmsVerificationRequired, smsCode: String): Result<User> {
        if (smsCode.isBlank()) {
            return Result.Error(Exception("Empty SMS code"), "请输入短信验证码")
        }
        return try {
            val response = personalApi.appLoginVerifyCommit(
                content = encryptedFormBody(
                    mapOf(
                        "temp_token" to pending.tempToken,
                        "imei" to pending.imei,
                        "sms_code" to smsCode.trim(),
                        "trust_device" to "1"
                    )
                ),
                authorizationStr = rsaCrypto.encryptAuthorizationStr(ticket = "")
            )
            parseLoginResponse(
                dataRaw = response.body()?.dataRaw,
                username = pending.username,
                password = pending.password,
                imei = pending.imei,
                rememberUsername = pending.rememberUsername,
                longTermLogin = pending.longTermLogin
            )
        } catch (e: Exception) {
            Timber.e(e, "SMS verification failed")
            Result.Error(e, "短信验证失败: ${e.message}")
        }
    }

    private suspend fun handleLoginData(
        loginData: LoginCommitData,
        username: String,
        password: String,
        imei: String,
        rememberUsername: Boolean,
        longTermLogin: Boolean
    ): Result<User> {
        if (loginData.loginCode == "3" && !loginData.loginResult.isNullOrBlank()) {
            Timber.i("login_code=3 requires SMS verification; sending code")
            val pending = SmsVerificationRequired(username, password, loginData.loginResult, imei, rememberUsername, longTermLogin)
            sendSmsCode(pending)
            return Result.Error(NeedSmsVerificationException(pending), "需要短信验证码，已尝试发送")
        }

        return completeLogin(loginData, username, password, rememberUsername, longTermLogin)
    }

    private suspend fun sendSmsCode(pending: SmsVerificationRequired) {
        try {
            val response = personalApi.appLoginSmsSend(
                content = encryptedFormBody(
                    mapOf(
                        "temp_token" to pending.tempToken,
                        "imei" to pending.imei
                    )
                ),
                authorizationStr = rsaCrypto.encryptAuthorizationStr(ticket = "")
            )
            Timber.d("SMS send response code: ${response.code()}")
        } catch (e: Exception) {
            Timber.w(e, "Failed to send SMS code automatically")
        }
    }

    private suspend fun parseLoginResponse(
        dataRaw: Any?,
        username: String,
        password: String,
        imei: String,
        rememberUsername: Boolean,
        longTermLogin: Boolean
    ): Result<User> {
        if (dataRaw !is String) {
            return Result.Error(Exception("Unexpected login response"), "登录响应格式异常")
        }
        val decryptedJson = rsaCrypto.decryptResponse(dataRaw)
            ?: return Result.Error(Exception("Decryption failed"), "响应解密失败")
        val loginData = parseLoginCommitData(decryptedJson)
        Timber.d("Parsed login_code=${loginData.loginCode ?: "null"}, has_login_result=${!loginData.loginResult.isNullOrBlank()}")
        return handleLoginData(loginData, username, password, imei, rememberUsername, longTermLogin)
    }

    private fun parseLoginCommitData(json: String): LoginCommitData {
        val obj = JsonParser.parseString(json).asJsonObject
        fun str(name: String): String? = obj.get(name)?.takeIf { !it.isJsonNull }?.asString
        return LoginCommitData(
            loginCode = str("login_code"),
            loginResult = str("login_result"),
            loginTicket = str("login_ticket"),
            name = str("name"),
            avatar = str("avatar"),
            xgh = str("xgh"),
            roleType = str("role_type"),
            role = str("role"),
            depart = str("depart"),
            ckLc = str("CK_LC"),
            ckVl = str("CK_VL")
        )
    }

    private suspend fun completeLogin(
        loginData: LoginCommitData,
        username: String,
        password: String,
        rememberUsername: Boolean,
        longTermLogin: Boolean
    ): Result<User> {
        if (loginData.loginTicket != null) {
            userPreferences.saveLoginTicket(loginData.loginTicket)
            Timber.d("Saved login ticket")
        }
        if (loginData.loginResult != null) {
            userPreferences.saveTgt(loginData.loginResult)
            Timber.d("Saved TGT")
        }
        saveLoginCookies(loginData)

        val user = User(
            userId = loginData.xgh ?: username,
            username = username,
            name = loginData.name ?: "NEU User",
            studentId = loginData.xgh,
            department = loginData.depart,
            avatar = loginData.avatar,
            email = null
        )
        userPreferences.saveUser(user)
        if (rememberUsername || longTermLogin) {
            userPreferences.saveUsername(username)
        }
        userPreferences.setAutoLoginEnabled(longTermLogin)
        if (longTermLogin) {
            secureCredentialStore.save(username, password)
        } else {
            secureCredentialStore.clear()
            userPreferences.clearLegacySavedPassword()
        }
        userPreferences.updateLastRefresh(System.currentTimeMillis())
        Timber.i("Login successful for user: ${user.name}")
        return Result.Success(user)
    }

    private fun encryptedFormBody(values: Map<String, String>): RequestBody {
        val encryptedContent = rsaCrypto.encryptContentMap(values)
        val urlEncodedContent = URLEncoder.encode(encryptedContent, "UTF-8")
        return RequestBody.create(
            "application/x-www-form-urlencoded".toMediaType(),
            "content=$urlEncodedContent"
        )
    }

    class NeedSmsVerificationException(val pending: SmsVerificationRequired) : Exception("Need SMS verification")

    suspend fun importWebViewLoginSession(currentUrl: String, cookieUrls: List<String>): Result<User> {
        return try {
            val importedCount = cookieJar.snapshotFromWebView(cookieUrls)
            val hasSession = hasUsefulSessionCookie()
            Timber.d("WebView snapshot on $currentUrl imported=$importedCount hasSession=$hasSession")

            if (!hasSession) {
                return Result.Error(Exception("No login cookie"), "等待网页登录完成")
            }

            val saved = userPreferences.getUser()
            val userInfo = getUserInfo()
            val user = when (userInfo) {
                is Result.Success -> userInfo.data
                else -> saved ?: User(
                    userId = "webview-session",
                    username = "NEU WebView",
                    name = "已通过校方页面登录",
                    studentId = null
                )
            }

            userPreferences.saveUser(user)
            userPreferences.updateLastRefresh(System.currentTimeMillis())
            Result.Success(user)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import WebView login session")
            Result.Error(e, "网页登录状态同步失败: ${e.message}")
        }
    }
    
    /**
     * Check current session status
     */
    suspend fun checkSession(): SessionState {
        return try {
            val user = userPreferences.getUser()
            if (user == null) {
                Timber.d("No saved user, session is idle")
                return SessionState.Idle
            }

            if (!hasUsefulSessionCookie()) {
                Timber.d("No useful NEU session cookie, session is idle")
                return SessionState.Idle
            }
            
            val lastRefresh = userPreferences.getLastRefresh().takeIf { it > 0 }
                ?: System.currentTimeMillis().also { userPreferences.updateLastRefresh(it) }
            val now = System.currentTimeMillis()
            
            // For WebView/CAS mode, cookie presence is the reliable local gate.
            // Avoid calling the old plaintext app_login/check endpoint here.
            SessionState.Authenticated(
                user = user,
                lastRefresh = lastRefresh,
                expiresAt = lastRefresh + SESSION_EXPIRE_TIME
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to check session")
            SessionState.Error(e.message ?: "会话检查失败", needRelogin = false)
        }
    }
    
    /**
     * Validate the saved app login ticket against the official app check endpoint.
     */
    suspend fun validateLoginTicket(): Result<Boolean> {
        return try {
            val ticket = userPreferences.getLoginTicket()?.takeIf { it.isNotBlank() }
                ?: return Result.Error(Exception("No login ticket"), "缺少登录票据")

            val response = personalApi.appLoginTicketCheck(
                content = encryptedFormBody(mapOf("ticket" to ticket)),
                authorizationStr = rsaCrypto.encryptAuthorizationStr(ticket = ticket)
            )
            if (!response.isSuccessful) {
                Timber.w("Login ticket check failed with HTTP ${response.code()}")
                return Result.Error(Exception("HTTP ${response.code()}"), "登录状态校验失败")
            }

            val body = response.body()?.string().orEmpty()
            val root = JsonParser.parseString(body)
            val code = root.findFirstString("e") ?: root.findFirstString("code")
            val message = root.findFirstString("m") ?: root.findFirstString("message") ?: ""
            val ok = code == null || code == "0"
            val saysLoginRequired = message.contains("请先登录") || message.contains("登录") && message.contains("失效")
            if (ok && !saysLoginRequired) {
                userPreferences.updateLastRefresh(System.currentTimeMillis())
                Timber.d("Login ticket check succeeded")
                Result.Success(true)
            } else {
                Timber.w("Login ticket check rejected session: code=$code message=$message")
                Result.Error(Exception(message.ifBlank { "ticket invalid" }), "登录状态已过期")
            }
        } catch (e: Exception) {
            Timber.e(e, "Login ticket check exception")
            Result.Error(e, "登录状态校验异常: ${e.message}")
        }
    }

    /**
     * Ensure the protocol session is actually fresh. If ticket validation fails,
     * perform one official-style auto login using the encrypted credentials saved
     * when long-term login is enabled.
     */
    suspend fun ensureFreshSession(): Result<Boolean> {
        when (val valid = validateLoginTicket()) {
            is Result.Success -> return valid
            else -> Timber.i("Saved ticket is not valid; attempting long-term auto login")
        }

        if (!userPreferences.isAutoLoginEnabled()) {
            return Result.Error(Exception("Auto login disabled"), "登录已过期，请重新登录")
        }
        val credentials = secureCredentialStore.load()
            ?: return Result.Error(Exception("No saved credentials"), "登录已过期，请重新登录")

        return when (val relogin = login(
            username = credentials.username,
            password = credentials.password,
            rememberUsername = true,
            longTermLogin = true
        )) {
            is Result.Success -> {
                Timber.i("Long-term auto login refreshed session")
                Result.Success(true)
            }
            is Result.Error -> {
                Timber.w(relogin.exception, "Long-term auto login failed")
                Result.Error(relogin.exception, relogin.message ?: "自动登录失败，请重新登录")
            }
            else -> Result.Error(Exception("Unexpected auto login result"), "自动登录状态异常")
        }
    }

    /**
     * Refresh session using a real ticket check + optional official-style auto login.
     */
    suspend fun refreshSession(): Result<Boolean> {
        return try {
            Timber.d("Refreshing session with real ticket validation...")
            ensureFreshSession()
        } catch (e: Exception) {
            Timber.e(e, "Session refresh failed")
            Result.Error(e, "会话刷新失败: ${e.message}")
        }
    }
    
    /**
     * Logout and clear all data
     */
    suspend fun logout(): Result<Boolean> {
        return try {
            Timber.d("Logging out...")
            
            // Call logout API (best effort, don't fail if it errors)
            try {
                personalApi.logout()
            } catch (e: Exception) {
                Timber.w(e, "Logout API call failed, continuing cleanup")
            }
            
            // Clear cookies
            cookieJar.clearAll()
            
            // Clear user preferences (but keep username for convenience)
            val username = userPreferences.getUsername()
            userPreferences.clearUser()
            userPreferences.clearPassword()
            userPreferences.setAutoLoginEnabled(false)
            secureCredentialStore.clear()
            if (!username.isNullOrEmpty()) {
                userPreferences.saveUsername(username) // Keep username for next login
            }
            
            Timber.i("Logout successful")
            Result.Success(true)
            
        } catch (e: Exception) {
            Timber.e(e, "Logout failed")
            Result.Error(e, "登出失败: ${e.message}")
        }
    }
    
    /**
     * Get user info from Personal API
     */
    private suspend fun getUserInfo(): Result<User> {
        return try {
            val response = personalApi.getPersonalInfo()
            
            if (!response.isSuccessful || response.body()?.data == null) {
                return Result.Error(
                    Exception("Failed to get user info"),
                    "无法获取用户信息"
                )
            }
            
            val data = response.body()!!.data!!
            Result.Success(
                User(
                    userId = data.userId,
                    username = data.username,
                    name = data.name,
                    studentId = data.studentId,
                    department = data.department,
                    avatar = data.avatar,
                    email = data.email
                )
            )
        } catch (e: Exception) {
            Result.Error(e, "获取用户信息失败: ${e.message}")
        }
    }
    
    private fun hasUsefulSessionCookie(): Boolean {
        return cookieJar.hasCookie("SESSION") ||
                cookieJar.hasCookie("SESS_ID") ||
                cookieJar.hasCookie("CK_LC") ||
                cookieJar.hasCookie("CASTGC") ||
                cookieJar.hasCookie("JSESSIONID")
    }

    private fun JsonElement.findFirstString(name: String): String? {
        if (isJsonObject) {
            val obj = asJsonObject
            obj.get(name)?.let { element ->
                if (element.isJsonPrimitive) return element.asString
            }
            obj.entrySet().forEach { (_, value) ->
                value.findFirstString(name)?.let { return it }
            }
        } else if (isJsonArray) {
            asJsonArray.forEach { element ->
                element.findFirstString(name)?.let { return it }
            }
        }
        return null
    }

    private suspend fun saveLoginCookies(loginData: LoginCommitData) {
        val personalDomain = "personal.neu.edu.cn"
        val passDomain = "pass.neu.edu.cn"
        val ecodeDomain = "ecode.neu.edu.cn"

        loginData.loginResult?.takeIf { it.isNotBlank() }?.let { value ->
            userPreferences.saveTgt(value)
            cookieJar.saveManualCookie(PERSONAL_SERVICE_URL, "CASTGC", value, personalDomain, path = "/")
            cookieJar.saveManualCookie("https://pass.neu.edu.cn/tpass/", "CASTGC", value, passDomain, path = "/tpass/")
            cookieJar.saveManualCookie("https://pass.neu.edu.cn/", "CASTGC", value, passDomain, path = "/")
            cookieJar.saveManualCookie(ECODE_SERVICE_URL, "CASTGC", value, ecodeDomain, path = "/")
        }

        loginData.ckLc?.takeIf { it.isNotBlank() }?.let { value ->
            cookieJar.saveManualCookie(PERSONAL_SERVICE_URL, "CK_LC", value, personalDomain)
            cookieJar.saveManualCookie(ECODE_SERVICE_URL, "CK_LC", value, ecodeDomain)
        }
        loginData.ckVl?.takeIf { it.isNotBlank() }?.let { value ->
            cookieJar.saveManualCookie(PERSONAL_SERVICE_URL, "CK_VL", value, personalDomain)
            cookieJar.saveManualCookie(ECODE_SERVICE_URL, "CK_VL", value, ecodeDomain)
        }
        Timber.d("Saved app-login cookies from decrypted response to personal/pass/ecode domains")
    }
}
