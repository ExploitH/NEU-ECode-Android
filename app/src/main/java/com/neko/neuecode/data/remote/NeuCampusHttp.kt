package com.neko.neuecode.data.remote

object NeuCampusHttp {
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    const val ECODE_SSO = "https://ecode.neu.edu.cn/ecode/api/sso/login"
    const val ECODE_HOME = "https://ecode.neu.edu.cn/ecode/"

    fun isEcodeIntermediateLanding(code: Int, host: String): Boolean {
        return code == 404 && host.equals("ecode.neu.edu.cn", ignoreCase = true)
    }

    fun isRetryableGateway(code: Int): Boolean {
        return code == 502 || code == 503 || code == 504
    }

    fun isRetryableGatewayStatus(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return looksLikeCampusTransport(message) &&
            (message.contains("HTTP 502") || message.contains("HTTP 503") || message.contains("HTTP 504") ||
                message.contains("timeout", ignoreCase = true) || message.contains("超时"))
    }

    fun looksLikeCampusTransport(message: String): Boolean {
        return containsAny(
            message,
            "校园网",
            "内网",
            "NeedCampusNet",
            "timeout",
            "Timeout",
            "超时",
            "502",
            "503",
            "504",
            "UnknownHost",
            "Failed to connect",
            "Unable to resolve",
            "Connection reset",
            "Network is unreachable",
        )
    }

    fun casAlreadyAuthenticated(host: String, path: String, code: Int, body: String): Boolean {
        if (isEcodeIntermediateLanding(code, host)) return true
        if (!host.endsWith("neu.edu.cn")) return false
        val onLogin = host.equals("pass.neu.edu.cn", ignoreCase = true) &&
            path.startsWith("/tpass/login")
        if (onLogin) return false
        val hasForm = body.contains("id=\"loginForm\"", ignoreCase = true) ||
            body.contains("id='loginForm'", ignoreCase = true)
        return !hasForm
    }

    private fun containsAny(haystack: String, vararg needles: String): Boolean {
        return needles.any { haystack.contains(it, ignoreCase = true) }
    }
}
