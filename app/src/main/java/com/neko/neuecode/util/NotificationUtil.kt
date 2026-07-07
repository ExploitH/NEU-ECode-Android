package com.neko.neuecode.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.neko.neuecode.MainActivity
import com.neko.neuecode.R
import timber.log.Timber

/**
 * Notification utility for session and balance updates
 */
object NotificationUtil {
    
    private const val CHANNEL_ID_SESSION = "session_channel"
    private const val CHANNEL_ID_BALANCE = "balance_channel"
    private const val NOTIFICATION_ID_SESSION = 1001
    private const val NOTIFICATION_ID_BALANCE = 1002
    
    /**
     * Initialize notification channels (Android O+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService<NotificationManager>() ?: return
            
            // Session channel
            val sessionChannel = NotificationChannel(
                CHANNEL_ID_SESSION,
                "登录状态",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "登录会话过期提醒"
            }
            
            // Balance channel
            val balanceChannel = NotificationChannel(
                CHANNEL_ID_BALANCE,
                "余额提醒",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "校园卡余额变动提醒"
            }
            
            notificationManager.createNotificationChannels(
                listOf(sessionChannel, balanceChannel)
            )
            
            Timber.d("Notification channels created")
        }
    }
    
    /**
     * Send session expired notification
     */
    fun notifySessionExpired(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SESSION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("登录已过期")
            .setContentText("请重新登录以继续使用")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService<NotificationManager>()
        notificationManager?.notify(NOTIFICATION_ID_SESSION, notification)
        
        Timber.d("Session expired notification sent")
    }
    
    /**
     * Send balance update notification (optional feature)
     */
    fun notifyBalanceUpdate(context: Context, cardBalance: String, networkBalance: String) {
        val intent = Intent(context, MainActivity::class.java)
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BALANCE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("余额已更新")
            .setContentText("校园卡：$cardBalance | 网费：$networkBalance")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService<NotificationManager>()
        notificationManager?.notify(NOTIFICATION_ID_BALANCE, notification)
        
        Timber.d("Balance update notification sent")
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAll(context: Context) {
        val notificationManager = context.getSystemService<NotificationManager>()
        notificationManager?.cancelAll()
        Timber.d("All notifications cancelled")
    }
}
