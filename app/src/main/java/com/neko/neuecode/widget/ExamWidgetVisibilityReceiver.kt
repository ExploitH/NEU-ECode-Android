package com.neko.neuecode.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ExamWidgetVisibilityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ExamWidgetVisibilityAlarm.action) return
        ExamWidgetProvider.updateAll(context)
        ExamWidgetVisibilityAlarm.scheduleNext(context)
    }
}
