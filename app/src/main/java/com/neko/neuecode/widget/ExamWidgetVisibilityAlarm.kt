package com.neko.neuecode.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.neko.neuecode.data.local.academic.JwxtAcademicCacheStore
import com.neko.neuecode.domain.jwxt.ExamWidgetVisibility
import java.util.TimeZone

object ExamWidgetVisibilityAlarm {
    private const val REQUEST_CODE = 3102
    const val action = "com.neko.neuecode.widget.ACTION_EXAM_VISIBILITY"

    fun scheduleNext(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(appContext)
        val triggerAt = JwxtAcademicCacheStore(appContext).loadExams()?.exams?.let {
            ExamWidgetVisibility.nextChangeMillis(it, nowMillis, TimeZone.getDefault())
        }
        if (triggerAt == null) alarmManager.cancel(pendingIntent)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ExamWidgetVisibilityReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
