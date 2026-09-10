package com.neko.neuecode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.neko.neuecode.MainActivity
import com.neko.neuecode.R
import com.neko.neuecode.data.local.academic.JwxtAcademicCacheStore
import com.neko.neuecode.ui.navigation.MainDestinations

class ExamWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Local cache only. Never ping campus or call JWXT from the widget.
        render(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, ExamWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                render(appContext, manager, ids)
            }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
        ) {
            val document = JwxtAcademicCacheStore(context).loadExams()
            val cards = ExamWidgetPresentation.cards(document)
            val emptyCopy = ExamWidgetPresentation.emptyCopy(document)
            val title = ExamWidgetPresentation.title(document)
            val open = pendingOpenExams(context)
            widgetIds.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.exam_widget)
                views.setTextViewText(R.id.widget_exam_kicker, ExamWidgetPresentation.kicker)
                views.setTextViewText(R.id.widget_exam_title, title)
                views.removeAllViews(R.id.widget_exam_cards)
                if (cards.isEmpty()) {
                    views.setViewVisibility(R.id.widget_exam_empty, View.VISIBLE)
                    views.setTextViewText(R.id.widget_exam_empty, emptyCopy)
                } else {
                    views.setViewVisibility(R.id.widget_exam_empty, View.GONE)
                    cards.forEach { card ->
                        val item = RemoteViews(context.packageName, R.layout.schedule_day_class_card)
                        item.setTextViewText(R.id.widget_day_class_name, card.courseName)
                        item.setTextViewText(R.id.widget_day_class_meta, card.meta)
                        item.setInt(
                            R.id.widget_day_class_root,
                            "setBackgroundResource",
                            ScheduleWidgetPresentation.cardBackgrounds[card.backgroundResIndex],
                        )
                        views.addView(R.id.widget_exam_cards, item)
                    }
                }
                views.setOnClickPendingIntent(R.id.widget_exam_root, open)
                manager.updateAppWidget(id, views)
            }
        }

        private fun pendingOpenExams(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_START_ROUTE, MainDestinations.widgetExamRoute)
            }
            return PendingIntent.getActivity(
                context,
                3101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
