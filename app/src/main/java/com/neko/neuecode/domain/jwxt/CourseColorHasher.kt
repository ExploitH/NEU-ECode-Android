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

    fun weekdayOf(epochDay: Long): Int {
        // 1970-01-01 was Thursday. Convert ISO day (1=Mon..7=Sun).
        val thursdayBased = ((epochDay % 7L) + 7L) % 7L
        val iso = ((thursdayBased + 3L) % 7L) + 1L
        return iso.toInt()
    }
}
