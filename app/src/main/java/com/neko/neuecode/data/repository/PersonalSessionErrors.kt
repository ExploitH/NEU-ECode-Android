package com.neko.neuecode.data.repository

object PersonalSessionErrors {
    enum class RetryAction {
        ReloginAndRetry,
        StopForSms,
        Fail,
    }

    fun retryAction(
        code: String?,
        message: String?,
        error: Throwable? = null,
        alreadyRefreshed: Boolean,
    ): RetryAction {
        if (isNeedSms(error) || isNeedSms(message)) return RetryAction.StopForSms
        if (!alreadyRefreshed && isExpired(code, message)) return RetryAction.ReloginAndRetry
        return RetryAction.Fail
    }

    fun isExpired(code: String?, message: String?): Boolean {
        val text = listOfNotNull(code, message).joinToString(" ")
        if (text.isBlank()) return false
        val normalized = text.lowercase()
        return code == "10013" ||
            code == "401" ||
            code == "403" ||
            text.contains("10013") ||
            text.contains("请先登录") ||
            text.contains("登录已过期") ||
            text.contains("登录信息已失效") ||
            text.contains("会话无效") ||
            normalized.contains("ticket") ||
            text.contains("401") ||
            text.contains("403")
    }

    fun isNeedSms(message: String?): Boolean {
        val text = message.orEmpty()
        return text.contains("需要短信") ||
            text.contains("短信验证码") ||
            text.contains("login_code=3")
    }

    fun isNeedSms(error: Throwable?): Boolean {
        return error is AuthRepository.NeedSmsVerificationException || isNeedSms(error?.message)
    }
}
