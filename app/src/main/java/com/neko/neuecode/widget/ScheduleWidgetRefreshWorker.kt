package com.neko.neuecode.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ScheduleWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ScheduleWidgetUpdater.updateAll(applicationContext)
            Timber.d("Schedule widget fallback refresh completed")
            Result.success()
        } catch (error: Exception) {
            Timber.w(error, "Schedule widget fallback refresh failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "schedule_widget_local_refresh"
        private const val WORK_TAG = "schedule_widget"
        private const val FLEX_HOURS = 1L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleWidgetRefreshWorker>(
                ScheduleWidgetRefreshPolicy.fallbackIntervalHours,
                TimeUnit.HOURS,
                FLEX_HOURS,
                TimeUnit.HOURS,
            )
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            Timber.i(
                "Schedule widget fallback scheduled: every %d hours",
                ScheduleWidgetRefreshPolicy.fallbackIntervalHours,
            )
        }
    }
}
