package com.mindtrace.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateUtils {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val zhWeekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val enWeekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val enMonths = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    private val enFullMonths = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private fun isEnglish(): Boolean = AppLocale.current.isEnglish

    fun today(): LocalDate = LocalDate.now()

    fun iso(date: LocalDate): String = date.format(isoFormatter)

    fun todayIso(): String = iso(today())

    fun parse(iso: String): LocalDate? = runCatching { LocalDate.parse(iso, isoFormatter) }.getOrNull()

    fun daysAgo(days: Long, from: LocalDate = today()): LocalDate = from.minusDays(days)

    fun rangeForDays(days: Int, end: LocalDate = today()): Pair<String, String> {
        val start = end.minusDays((days - 1).coerceAtLeast(0).toLong())
        return iso(start) to iso(end)
    }

    fun displayDate(iso: String): String {
        val date = parse(iso) ?: return iso
        return if (isEnglish()) {
            "${enMonths[date.monthValue - 1]} ${date.dayOfMonth}, ${weekday(date)}"
        } else {
            "${date.monthValue}月${date.dayOfMonth}日 ${weekday(date)}"
        }
    }

    fun displayFullDate(iso: String): String {
        val date = parse(iso) ?: return iso
        return if (isEnglish()) {
            "${enFullMonths[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year} · ${weekday(date)}"
        } else {
            "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekday(date)}"
        }
    }

    fun displayShort(iso: String): String {
        val date = parse(iso) ?: return iso
        return if (isEnglish()) {
            "${enMonths[date.monthValue - 1]} ${date.dayOfMonth}"
        } else {
            "${date.monthValue}/${date.dayOfMonth}"
        }
    }

    fun weekday(date: LocalDate): String =
        if (isEnglish()) enWeekdays[date.dayOfWeek.value - 1]
        else zhWeekdays[date.dayOfWeek.value - 1]

    fun weekdayHeaders(): List<String> =
        (if (isEnglish()) enWeekdays else zhWeekdays).toList()

    fun relativeDay(iso: String): String {
        val date = parse(iso) ?: return iso
        val days = ChronoUnit.DAYS.between(date, today())
        return when (days) {
            0L -> if (isEnglish()) "Today" else "今天"
            1L -> if (isEnglish()) "Yesterday" else "昨天"
            2L -> if (isEnglish()) "2 days ago" else "前天"
            else -> displayDate(iso)
        }
    }

    fun monthLabel(year: Int, month: Int): String =
        if (isEnglish()) "${enFullMonths[month - 1]} $year" else "${year}年${month}月"

    fun timeLabel(hour: Int, minute: Int): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun nowTimeLabel(): String {
        val now = LocalTime.now()
        return timeLabel(now.hour, now.minute)
    }

    fun nowTimestamp(): Long = System.currentTimeMillis()

    fun timeOfTimestamp(timestamp: Long): String {
        val dt = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return timeLabel(dt.hour, dt.minute)
    }

    fun displayTimestamp(timestamp: Long): String {
        val dt = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return if (isEnglish()) {
            "${enMonths[dt.monthValue - 1]} ${dt.dayOfMonth}, ${timeLabel(dt.hour, dt.minute)}"
        } else {
            "${dt.monthValue}月${dt.dayOfMonth}日 ${timeLabel(dt.hour, dt.minute)}"
        }
    }

    fun greeting(): String = when (LocalTime.now().hour) {
        in 5..10 -> if (isEnglish()) "Good morning" else "早上好"
        in 11..13 -> if (isEnglish()) "Good afternoon" else "中午好"
        in 14..17 -> if (isEnglish()) "Good afternoon" else "下午好"
        in 18..22 -> if (isEnglish()) "Good evening" else "晚上好"
        else -> if (isEnglish()) "Late night" else "夜深了"
    }

    fun lastNDates(days: Int, end: LocalDate = today()): List<LocalDate> {
        val safe = days.coerceIn(1, 366)
        return (safe - 1 downTo 0).map { end.minusDays(it.toLong()) }
    }

    fun startOfMonth(year: Int, month: Int): LocalDate = LocalDate.of(year, month, 1)

    fun monthLength(year: Int, month: Int): Int = LocalDate.of(year, month, 1).lengthOfMonth()

    fun firstWeekdayIndex(year: Int, month: Int): Int {
        // 周一为 0
        return LocalDate.of(year, month, 1).dayOfWeek.value - 1
    }

    fun toLocalDateTime(timestamp: Long): LocalDateTime =
        java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
}
