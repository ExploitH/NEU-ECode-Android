package com.neko.neuecode.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class ScheduleWidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ScheduleWidgetRefreshPolicy.acceptsRefreshAction(intent.action)) return
        Timber.d("Schedule widget clock event: %s", intent.action)
        ScheduleWidgetUpdater.updateAll(context)
        ScheduleWidgetMidnightAlarm.scheduleNext(context)
    }
}
