package com.neko.neuecode.domain.vpn

/**
 * Sanitize a student OpenVPN profile before handing it to official OpenVPN 3.
 *
 * Never logs inline CA / tls-auth / passwords. Forces split-tunnel by dropping
 * pushed and local redirect-gateway and inserting the verified pull-filter.
 */
object StudentVpnProfileSanitizer {
    private const val SPLIT_FILTER = """pull-filter ignore "redirect-gateway""""

    fun sanitize(raw: String): String {
        val out = ArrayList<String>()
        var inInline = false
        var sawSplitFilter = false
        for (rawLine in raw.replace("\r\n", "\n").split('\n')) {
            val trimmed = rawLine.trim()
            val lower = trimmed.lowercase()
            if (trimmed.startsWith("<") && trimmed.endsWith(">") && !trimmed.startsWith("</")) {
                inInline = true
                out.add(rawLine)
                continue
            }
            if (trimmed.startsWith("</") && trimmed.endsWith(">")) {
                inInline = false
                out.add(rawLine)
                continue
            }
            if (inInline) {
                out.add(rawLine)
                continue
            }
            if (lower.startsWith("redirect-gateway")) {
                continue
            }
            if (lower.startsWith("auth-user-pass")) {
                out.add("auth-user-pass")
                continue
            }
            if (lower == SPLIT_FILTER.lowercase() || lower.startsWith("pull-filter ignore") && lower.contains("redirect-gateway")) {
                if (!sawSplitFilter) {
                    out.add(SPLIT_FILTER)
                    sawSplitFilter = true
                }
                continue
            }
            out.add(rawLine)
        }
        if (!sawSplitFilter) {
            out.add(SPLIT_FILTER)
        }
        return out.joinToString("\n").trimEnd() + "\n"
    }

    fun inlineUserPass(sanitized: String, username: String, password: String): String {
        val withoutAuth = sanitized.lineSequence()
            .filterNot { it.trim().lowercase().startsWith("auth-user-pass") }
            .joinToString("\n")
            .trimEnd()
        return buildString {
            append(withoutAuth)
            append("\n<auth-user-pass>\n")
            append(username)
            append('\n')
            append(password)
            append("\n</auth-user-pass>\n")
        }
    }

    fun redactedForLog(profile: String): String {
        val sb = StringBuilder()
        var inInline = false
        for (rawLine in profile.replace("\r\n", "\n").split('\n')) {
            val trimmed = rawLine.trim()
            val lower = trimmed.lowercase()
            if (trimmed.startsWith("<") && trimmed.endsWith(">") && !trimmed.startsWith("</")) {
                inInline = true
                sb.append(rawLine).append('\n')
                sb.append("[REDACTED]\n")
                continue
            }
            if (trimmed.startsWith("</") && trimmed.endsWith(">")) {
                inInline = false
                sb.append(rawLine).append('\n')
                continue
            }
            if (inInline) {
                continue
            }
            if (lower.startsWith("auth-user-pass ") && lower != "auth-user-pass") {
                sb.append("auth-user-pass [REDACTED]\n")
                continue
            }
            sb.append(rawLine).append('\n')
        }
        return sb.toString()
    }
}
