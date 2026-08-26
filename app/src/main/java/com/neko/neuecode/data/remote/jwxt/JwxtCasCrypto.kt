package com.neko.neuecode.data.remote.jwxt

import java.net.URI
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.regex.Pattern
import javax.crypto.Cipher

data class JwxtCasSubmission(
    val action: String,
    val fields: Map<String, String>
)

object JwxtCasCrypto {
    private val inputPattern = Pattern.compile(
        "<input\\b[^>]*>",
        Pattern.CASE_INSENSITIVE
    )
    private val attrPattern = Pattern.compile(
        "([a-zA-Z_:][\\w:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"
    )

    fun jsLength(value: String): Int = value.toByteArray(Charsets.UTF_16LE).size / 2

    fun encryptCredentials(publicKeyB64: String, username: String, password: String): String {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64))
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal((username + password).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun buildLoginSubmission(
        pageHtml: String,
        pageUrl: String,
        publicKeyB64: String,
        username: String,
        password: String
    ): JwxtCasSubmission {
        val form = extractLoginForm(pageHtml) ?: throw JwxtProtocolException("CAS login form is unavailable")
        val fields = linkedMapOf<String, String>()
        for (input in form.inputs) {
            val name = input["name"].orEmpty()
            val type = input["type"]?.lowercase().orEmpty().ifBlank { "text" }
            if (name.isBlank() || name == "un" || name == "pd" || type in setOf("button", "submit", "checkbox")) {
                continue
            }
            fields[name] = input["value"].orEmpty()
        }
        fields["rsa"] = encryptCredentials(publicKeyB64, username, password)
        fields["ul"] = jsLength(username).toString()
        fields["pl"] = jsLength(password).toString()
        val action = resolveUrl(pageUrl, form.action.ifBlank { pageUrl })
        return JwxtCasSubmission(action = action, fields = fields)
    }

    fun extractPublicKeyFromJs(js: String): String {
        val match = Regex("""publicKeyStr\s*=\s*"([A-Za-z0-9+/=]+)"""").find(js)
            ?: throw JwxtProtocolException("CAS RSA public key is unavailable")
        return match.groupValues[1]
    }

    fun extractLoginScriptUrl(pageHtml: String, pageUrl: String): String? {
        val matcher = Pattern.compile(
            "<script\\b[^>]*src\\s*=\\s*(\"([^\"]+)\"|'([^']+)')[^>]*>",
            Pattern.CASE_INSENSITIVE
        ).matcher(pageHtml)
        while (matcher.find()) {
            val src = matcher.group(2) ?: matcher.group(3).orEmpty()
            if ("login_neu" in src) return resolveUrl(pageUrl, src)
        }
        return null
    }

    private data class LoginForm(val action: String, val inputs: List<Map<String, String>>)

    private fun extractLoginForm(pageHtml: String): LoginForm? {
        val formMatch = Regex(
            """<form\b[^>]*id\s*=\s*["']loginForm["'][^>]*>([\s\S]*?)</form>""",
            RegexOption.IGNORE_CASE
        ).find(pageHtml) ?: return null
        val openTag = Regex(
            """<form\b[^>]*id\s*=\s*["']loginForm["'][^>]*>""",
            RegexOption.IGNORE_CASE
        ).find(pageHtml)?.value.orEmpty()
        val action = readAttr(openTag, "action")
        val inputs = mutableListOf<Map<String, String>>()
        val matcher = inputPattern.matcher(formMatch.groupValues[1])
        while (matcher.find()) {
            inputs += readAttrs(matcher.group())
        }
        return LoginForm(action = action, inputs = inputs)
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
