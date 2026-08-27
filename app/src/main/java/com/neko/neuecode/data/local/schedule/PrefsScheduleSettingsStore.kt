package com.neko.neuecode.data.local.schedule

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsScheduleSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : ScheduleSettingsStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): ScheduleSettings {
        val term = prefs.getString(KEY_TERM, null)?.takeIf { it.isNotBlank() }
        val start = if (prefs.contains(KEY_START)) prefs.getLong(KEY_START, 0L) else null
        return ScheduleSettings(
            defaultTermCode = term,
            termStartEpochDay = start,
        )
    }

    override fun save(settings: ScheduleSettings) {
        prefs.edit()
            .putString(KEY_TERM, settings.defaultTermCode)
            .apply {
                if (settings.termStartEpochDay == null) remove(KEY_START)
                else putLong(KEY_START, settings.termStartEpochDay)
            }
            .apply()
    }

    companion object {
        private const val PREFS = "jwxt_schedule_settings"
        private const val KEY_TERM = "default_term_code"
        private const val KEY_START = "term_start_epoch_day"
    }
}
