package com.neko.neuecode

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.neko.neuecode.data.remote.config.RemoteProtocolConfigRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NeuECodeApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var remoteProtocolConfigRepository: RemoteProtocolConfigRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("NEU eCode Kotlin Edition Started")
        warmUpRemoteProtocolConfig()
        
        // Schedule background workers
        scheduleBackgroundTasks()
    }
    
    private fun warmUpRemoteProtocolConfig() {
        appScope.launch {
            remoteProtocolConfigRepository.prefetch()
        }
    }

    private fun scheduleBackgroundTasks() {
        try {
            // Create notification channels
            com.neko.neuecode.util.NotificationUtil.createNotificationChannels(this)
            
            // Schedule session refresh (every 2 hours)
            com.neko.neuecode.worker.SessionRefreshWorker.schedule(this)
            
            // Schedule balance sync (every 6 hours)
            com.neko.neuecode.worker.BalanceSyncWorker.schedule(this)
            
            Timber.i("Background tasks scheduled successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule background tasks")
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
