package com.neko.neuecode.domain.jwxt

/**
 * Stable hue in [0, 360) from a courseKey. Independent hash — not copied from Sleepy.
 */
object CourseColorHasher {
    fun hue(courseKey: String): Float {
        var hash = 0x811C9DC5.toInt()
        for (unit in courseKey) {
            hash = hash xor unit.code
            hash *= 0x01000193
        }
        return ((hash ushr 1) % 360).toFloat()
    }
}

object ScheduleWeekClock {
    fun weekOf(termStartEpochDay: Long?, todayEpochDay: Long): Int {
        if (termStartEpochDay == null) return 1
        val delta = todayEpochDay - termStartEpochDay
        if (delta < 0L) return 1
        return ((delta / 7L) + 1L).toInt().coerceAtLeast(1)
    }

    fun actualWeek(termStartEpochDay: Long?, todayEpochDay: Long): Int? {
        if (termStartEpochDay == null) return null
        if (todayEpochDay < termStartEpochDay) return null
        return weekOf(termStartEpochDay, todayEpochDay)
    }

    fun weekdayOf(epochDay: Long): Int {
        // 1970-01-01 was Thursday. Convert ISO day (1=Mon..7=Sun).
        val thursdayBased = ((epochDay % 7L) + 7L) % 7L
        val iso = ((thursdayBased + 3L) % 7L) + 1L
        return iso.toInt()
    }

    fun localEpochDay(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utc.clear()
        utc.set(java.util.Calendar.YEAR, year)
        utc.set(java.util.Calendar.MONTH, month - 1)
        utc.set(java.util.Calendar.DAY_OF_MONTH, day)
        return utc.timeInMillis / 86_400_000L
    }

    fun todayEpochDay(): Long {
        val local = java.util.Calendar.getInstance()
        return localEpochDay(
            year = local.get(java.util.Calendar.YEAR),
            month = local.get(java.util.Calendar.MONTH) + 1,
            day = local.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    fun todayWeekday(): Int {
        val local = java.util.Calendar.getInstance()
        val dow = local.get(java.util.Calendar.DAY_OF_WEEK)
        return if (dow == java.util.Calendar.SUNDAY) 7 else dow - 1
    }

    fun fromUtcMillis(millis: Long): Long = millis / 86_400_000L
}
