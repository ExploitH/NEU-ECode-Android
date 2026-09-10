package com.neko.neuecode.domain.jwxt

object JwxtTermNames {
    private val codePattern = Regex("""^(\d{4}-\d{4})-([12])$""")

    fun displayName(code: String, name: String? = null): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.contains("学年") && trimmed.contains("学期")) return trimmed
        return fromCode(code) ?: trimmed.ifBlank { code }
    }

    private fun fromCode(code: String): String? {
        val match = codePattern.matchEntire(code.trim()) ?: return null
        val season = when (match.groupValues[2]) {
            "1" -> "秋季学期"
            "2" -> "春季学期"
            else -> return null
        }
        return "${match.groupValues[1]}学年$season"
    }
}

val JwxtNamedCode.displayName: String
    get() = JwxtTermNames.displayName(code, name)
