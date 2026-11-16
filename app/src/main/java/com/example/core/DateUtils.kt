package com.example.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        return calendarToEpochDay(cal)
    }

    fun calendarToEpochDay(cal: Calendar): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
        return utc.timeInMillis / MILLIS_PER_DAY
    }

    fun epochMillisToEpochDay(epochMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return calendarToEpochDay(cal)
    }

    fun dayOfWeekFromEpochDay(epochDay: Long): Int {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochDay * MILLIS_PER_DAY
        }
        // Calendar.SUNDAY = 1, MONDAY = 2..SATURDAY = 7 -> convert to ISO 1=Mon..7=Sun
        val calDow = utc.get(Calendar.DAY_OF_WEEK)
        return if (calDow == Calendar.SUNDAY) 7 else calDow - 1
    }

    fun todayIsoDayOfWeek(): Int = dayOfWeekFromEpochDay(todayEpochDay())

    fun formatEpochDayShort(epochDay: Long): String {
        val today = todayEpochDay()
        return when (epochDay) {
            today -> "Today"
            today + 1 -> "Tomorrow"
            today - 1 -> "Yesterday"
            else -> {
                val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = epochDay * MILLIS_PER_DAY
                }
                val month = utc.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) ?: ""
                val day = utc.get(Calendar.DAY_OF_MONTH)
                "$month $day"
            }
        }
    }

    fun dayOfWeekLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "Day"
    }

    fun dayOfWeekFullLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Day"
    }

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 1440)
        val hour24 = (clamped / 60) % 24
        val min = clamped % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, min, amPm)
    }

    fun formatDurationSeconds(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
