package com.neko.neuecode.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.neko.neuecode.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker for session refresh
 * Runs every 2 hours to keep session alive
 */
@HiltWorker
class SessionRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "session_refresh"
        private const val REFRESH_INTERVAL_HOURS = 2L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SessionRefreshWorker>(
                REFRESH_INTERVAL_HOURS, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .addTag("session")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Timber.i("SessionRefreshWorker scheduled: every $REFRESH_INTERVAL_HOURS hours")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.i("SessionRefreshWorker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Timber.d("SessionRefreshWorker started, attempt: $runAttemptCount")

        return try {
            when (val result = authRepository.refreshSession()) {
                is com.neko.neuecode.domain.model.Result.Success -> {
                    Timber.i("Session refreshed successfully in background")
                    Result.success()
                }
                is com.neko.neuecode.domain.model.Result.Error -> {
                    Timber.w(result.exception, "Session refresh failed: ${result.message}")
                    if (result.message?.contains("需要重新登录") == true) {
                        Timber.w("Session expired, user needs to re-login")
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }
                else -> {
                    Timber.w("Session refresh returned unexpected result")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SessionRefreshWorker failed with exception")
            Result.retry()
        }
    }
}
