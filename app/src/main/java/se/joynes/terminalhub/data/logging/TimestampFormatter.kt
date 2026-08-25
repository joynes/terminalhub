package se.joynes.terminalhub.data.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun currentUtcTimestamp(nowMillis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).run {
        timeZone = TimeZone.getTimeZone("UTC")
        format(Date(nowMillis))
    }
