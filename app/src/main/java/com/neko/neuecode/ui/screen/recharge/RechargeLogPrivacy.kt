package com.neko.neuecode.ui.screen.recharge

internal fun redactRechargeLog(text: String): String = text
    .replace(Regex("(?i)(;jsessionid=)[^?&#\\s\\\"']+"), "$1<redacted>")
    .replace(Regex("(?i)((?:[?&]|&amp;)(?:ticket|execution|lt|authCode|code|token)=)[^&#\\s\\\"']+"), "$1<redacted>")
    .replace(Regex("(?:ST|TGT|LT)-[A-Za-z0-9._:-]+"), "<redacted>")
