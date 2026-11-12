package com.neko.neuecode.data.remote.jwxt

import java.net.URI
import java.util.regex.Pattern

data class CasSecondAuthChallenge(
    val isPresent: Boolean,
    val maskedPhone: String? = null,
    val formAction: String = "",
    val captchaSrc: String? = null,
    val needsGraphicCaptcha: Boolean = false,
    val needsSmsCode: Boolean = false,
    val fields: Map<String, String> = emptyMap(),
)

data class CasSecondAuthRequest(
    val url: String,
    val fields: Map<String, String>,
)

object CasSecondAuthParser {
    private val VISIBLE_MARKERS = listOf(
        "当前设备需进行身份验证",
        "绑定手机尾号",
        "获取验证码",
        "输入验证码",
        "图形验证码",
        "二次认证",
        "登录码已发送",
        "动态验证码",
        "手机验证码",
        "scendAuthCode",
        "getScendAuthCode",
        "second_auth_form",
        "login_second.js",
    )
    private val inputPattern = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE)
    private val attrPattern = Pattern.compile(
        "([a-zA-Z_:][\\w:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
    )
    private val phonePattern = Pattern.compile("\\*{3,}\\d{4}")

    fun parse(html: String, pageUrl: String): CasSecondAuthChallenge {
        if (!isChallengeHtml(html)) {
            return CasSecondAuthChallenge(isPresent = false)
        }
        val activeHtml = removeInertBlocks(html)
        val form = extractForm(activeHtml, pageUrl)
        val captchaSrc = extractCaptchaSrc(activeHtml)
        val matcher = phonePattern.matcher(activeHtml)
        val maskedPhone = if (matcher.find()) matcher.group() else null
        return CasSecondAuthChallenge(
            isPresent = true,
            maskedPhone = maskedPhone,
            formAction = form?.action.orEmpty().ifBlank { pageUrl },
            captchaSrc = captchaSrc,
            needsGraphicCaptcha = activeHtml.contains("imgCode") ||
                activeHtml.contains("codeImage") || activeHtml.contains("图形验证码"),
            needsSmsCode = true,
            fields = form?.fields.orEmpty(),
        )
    }

    fun isChallengeHtml(html: String): Boolean {
        val activeHtml = removeInertBlocks(html)
        if (hasElementId(activeHtml, "loginForm") &&
            hasElementId(activeHtml, "un") &&
            hasElementId(activeHtml, "pd") &&
            !activeHtml.contains("当前设备需进行身份验证") &&
            !hasElementId(activeHtml, "second_auth_form") &&
            !activeHtml.contains("图形验证码")
        ) {
            return false
        }
        return VISIBLE_MARKERS.any { activeHtml.contains(it) }
    }

    // The new password page contains a second-auth script[type=text/html]
    // template. Templates and script text are not an active challenge.
    // This strips inert blocks, not arbitrary CSS-hidden elements.
    private fun removeInertBlocks(html: String): String = html
        .replace(Regex("<!--[\\s\\S]*?-->"), "")
        .replace(Regex("<script\\b[^>]*>[\\s\\S]*?</script\\s*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<template\\b[^>]*>[\\s\\S]*?</template\\s*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<style\\b[^>]*>[\\s\\S]*?</style\\s*>", RegexOption.IGNORE_CASE), "")

    private fun hasElementId(html: String, id: String): Boolean = Regex(
        """<\w+\b[^>]*\bid\s*=\s*["']${Regex.escape(id)}["']""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(html)

    fun sendCodeRequest(pageUrl: String, graphicCaptcha: String): CasSecondAuthRequest {
        val url = URI(pageUrl).resolve("/tpass/secondAuthCode").toString()
        return CasSecondAuthRequest(
            url = url,
            fields = mapOf(
                "code" to graphicCaptcha.trim(),
                "method" to "mobile",
            ),
        )
    }

    fun submitSmsRequest(
        challenge: CasSecondAuthChallenge,
        smsCode: String,
    ): CasSecondAuthRequest {
        val fields = linkedMapOf<String, String>()
        fields.putAll(challenge.fields)
        fields["authCode"] = smsCode.trim()
        return CasSecondAuthRequest(
            url = challenge.formAction,
            fields = fields,
        )
    }

    private data class Form(val action: String, val fields: Map<String, String>)

    private fun extractForm(html: String, pageUrl: String): Form? {
        val formMatch = Regex(
            """<form\b[^>]*id\s*=\s*["']second_auth_form["'][^>]*>([\s\S]*?)</form>""",
            RegexOption.IGNORE_CASE,
        ).find(html) ?: return null
        val openTag = Regex(
            """<form\b[^>]*id\s*=\s*["']second_auth_form["'][^>]*>""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.value.orEmpty()
        val action = resolveUrl(pageUrl, readAttr(openTag, "action").ifBlank { pageUrl })
        val fields = linkedMapOf<String, String>()
        val matcher = inputPattern.matcher(formMatch.groupValues[1])
        while (matcher.find()) {
            val attrs = readAttrs(matcher.group())
            val name = attrs["name"].orEmpty()
            val type = attrs["type"]?.lowercase().orEmpty().ifBlank { "text" }
            if (name.isBlank() || type in setOf("button", "submit", "checkbox")) continue
            if (name in setOf("imgCode", "authCode", "scendAuthCode")) continue
            fields[name] = attrs["value"].orEmpty()
        }
        return Form(action = action, fields = fields)
    }

    private fun extractCaptchaSrc(html: String): String? {
        val match = Regex(
            """<img\b[^>]*id\s*=\s*["']codeImage["'][^>]*>""",
            RegexOption.IGNORE_CASE,
        ).find(html) ?: return null
        return readAttr(match.value, "src").ifBlank { null }
    }

    private fun readAttrs(tag: String): Map<String, String> {
        val attrs = linkedMapOf<String, String>()
        val matcher = attrPattern.matcher(tag)
        while (matcher.find()) {
            val name = matcher.group(1)?.lowercase().orEmpty()
            val value = matcher.group(3) ?: matcher.group(4) ?: matcher.group(5).orEmpty()
            attrs[name] = value
        }
        return attrs
    }

    private fun readAttr(tag: String, name: String): String = readAttrs(tag)[name].orEmpty()

    private fun resolveUrl(pageUrl: String, maybeRelative: String): String {
        return URI(pageUrl).resolve(maybeRelative).toString()
    }
}
