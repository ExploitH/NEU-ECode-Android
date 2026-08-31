package com.neko.neuecode.data.local.schedule

enum class WeekStartDay {
    SUNDAY,
    MONDAY,
    ;

    val storedValue: String
        get() = name.lowercase()

    companion object {
        fun fromStored(value: String?): WeekStartDay {
            return if (value.equals("monday", ignoreCase = true)) MONDAY else SUNDAY
        }
    }
}

data class ScheduleSettings(
    val defaultTermCode: String? = null,
    val termStartEpochDay: Long? = null,
    val weekStartDay: WeekStartDay = WeekStartDay.SUNDAY,
)

interface ScheduleSettingsStore {
    fun load(): ScheduleSettings
    fun save(settings: ScheduleSettings)
}

class InMemoryScheduleSettingsStore(
    initial: ScheduleSettings = ScheduleSettings(),
) : ScheduleSettingsStore {
    private var settings: ScheduleSettings = initial

    override fun load(): ScheduleSettings = settings

    override fun save(settings: ScheduleSettings) {
        this.settings = settings
    }
}
