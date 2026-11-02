package com.wangzan101.mika.core.easel

import java.time.LocalDate
import java.time.LocalDateTime

/** 内容日历里的排期:只认本地日期或日期时间,并确认它真的存在。 */
object EaselContentDate {
    const val FORMAT_HINT = "Use YYYY-MM-DD or YYYY-MM-DDTHH:mm."

    private val DATE_OR_TIME = Regex("""\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2})?)?""")

    /** 空白返回 null;格式或日期不合法抛 [IllegalArgumentException],消息可直接给用户看。 */
    fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (!DATE_OR_TIME.matches(value)) throw IllegalArgumentException(FORMAT_HINT)
        val iso = value.replace(' ', 'T')
        val parsed = runCatching {
            if (iso.length == 10) LocalDate.parse(iso) else LocalDateTime.parse(iso)
        }.getOrElse { throw IllegalArgumentException("That date or time does not exist. $FORMAT_HINT") }
        return parsed.toString().replace(' ', 'T')
    }
}
