package com.neko.neuecode.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.neko.neuecode.data.repository.PersonalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker for balance synchronization
 * Runs periodically to update balance information
 */
@HiltWorker
class BalanceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val personalRepository: PersonalRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "balance_sync"
        private const val SYNC_INTERVAL_HOURS = 6L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<BalanceSyncWorker>(
                SYNC_INTERVAL_HOURS, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.MINUTES
                )
                .addTag("balance")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Timber.i("BalanceSyncWorker scheduled: every $SYNC_INTERVAL_HOURS hours")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.i("BalanceSyncWorker cancelled")
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<BalanceSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("balance_once")
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Timber.d("BalanceSyncWorker: one-time sync requested")
        }
    }

    override suspend fun doWork(): Result {
        Timber.d("BalanceSyncWorker started, attempt: $runAttemptCount")

        return try {
            when (val result = personalRepository.getBalance()) {
                is com.neko.neuecode.domain.model.Result.Success -> {
                    val balance = result.data
                    Timber.i("Balance synced: card=${balance.cardBalance}, network=${balance.networkBalance}")
                    Result.success(
                        workDataOf(
                            "card_balance" to balance.cardBalance,
                            "network_balance" to balance.networkBalance,
                            "last_update" to balance.lastUpdate
                        )
                    )
                }
                is com.neko.neuecode.domain.model.Result.Error -> {
                    Timber.w(result.exception, "Balance sync failed: ${result.message}")
                    Result.retry()
                }
                else -> {
                    Timber.w("Balance sync returned unexpected result")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "BalanceSyncWorker failed with exception")
            Result.retry()
        }
    }
}
