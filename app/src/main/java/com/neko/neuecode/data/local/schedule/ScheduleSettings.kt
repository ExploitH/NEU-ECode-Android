package com.neko.neuecode.data.local.schedule

data class ScheduleSettings(
    val defaultTermCode: String? = null,
    val termStartEpochDay: Long? = null,
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
