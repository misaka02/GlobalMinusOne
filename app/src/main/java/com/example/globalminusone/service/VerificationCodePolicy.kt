package com.example.globalminusone.service

/** Only a number adjacent to an OTP label is eligible; unrelated order/phone digits are ignored. */
internal fun extractNotificationVerificationCode(text: String): String? {
    val label = "(?:验证码|校验码|verification\\s+code|security\\s+code|one[ -]time\\s+(?:code|password))"
    // Allow common separators/phrases, not arbitrary intervening words such as "订单号".
    val after = Regex("$label(?:[\\s:：#-]|为|是|is){0,8}(?<!\\d)(\\d{4,8})(?!\\d)", RegexOption.IGNORE_CASE)
    val before = Regex("(?<!\\d)(\\d{4,8})(?!\\d)(?:[\\s:：#-]|是您的){0,8}$label", RegexOption.IGNORE_CASE)
    return after.find(text)?.groupValues?.get(1) ?: before.find(text)?.groupValues?.get(1)
}
