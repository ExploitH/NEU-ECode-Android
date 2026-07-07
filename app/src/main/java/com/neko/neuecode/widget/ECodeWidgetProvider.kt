package com.neko.neuecode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.text.isDigitsOnly
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.neko.neuecode.R
import com.neko.neuecode.domain.model.Result
import timber.log.Timber

class ECodeWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_REFRESH = "com.neko.neuecode.widget.ACTION_REFRESH"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refreshAll(context: Context) {
            val intent = Intent(context, ECodeWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        private fun pendingRefreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, ECodeWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            return PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ECodeWidgetProvider::class.java)
            return manager.getAppWidgetIds(component)
        }

        private fun render(context: Context, widgetIds: IntArray, loading: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val snapshot = ECodeWidgetStore.load(context)
            val qrBitmap = ECodeWidgetStore.loadQrBitmap(context)
            widgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.ecode_widget)
                views.setTextViewText(R.id.widget_title, "NEU e码通")
                views.setTextViewText(R.id.widget_status, if (loading) "刷新中…" else snapshot.status)
                views.setTextViewText(
                    R.id.widget_card_balance,
                    if (snapshot.cardBalance.isNotBlank()) "校园卡：${snapshot.cardBalance}" else "校园卡：--"
                )
                views.setTextViewText(
                    R.id.widget_network_balance,
                    if (snapshot.networkBalance.isNotBlank()) "网费：${snapshot.networkBalance}" else "网费：--"
                )
                views.setTextViewText(R.id.widget_updated_at, formatTimestamp(snapshot.updatedAt))
                if (qrBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_qr, qrBitmap)
                } else {
                    views.setImageViewResource(R.id.widget_qr, R.drawable.ic_launcher)
                }
                val refreshIntent = pendingRefreshIntent(context)
                views.setOnClickPendingIntent(R.id.widget_root, refreshIntent)
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent)
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp <= 0L) return "未刷新"
            val diff = System.currentTimeMillis() - timestamp
            val minutes = diff / 60000L
            return when {
                minutes < 1L -> "刚刚更新"
                minutes < 60L -> "${minutes}分钟前"
                minutes < 1440L -> "${minutes / 60}小时前"
                else -> "${minutes / 1440}天前"
            }
        }

        private fun refreshAsync(context: Context, pendingResult: PendingResult?) {
            val appContext = context.applicationContext
            val widgetIds = allWidgetIds(appContext)
            render(appContext, widgetIds, loading = true)
            scope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        appContext,
                        ECodeWidgetEntryPoint::class.java
                    )
                    val balanceRepo = entryPoint.personalRepository()
                    val qrRepo = entryPoint.eCodeQrRepository()

                    var status = "刷新完成"
                    var card = ECodeWidgetStore.load(appContext).cardBalance
                    var network = ECodeWidgetStore.load(appContext).networkBalance
                    var updatedAt = System.currentTimeMillis()

                    when (val balanceResult = balanceRepo.getBalance()) {
                        is Result.Success -> {
                            card = balanceResult.data.cardBalance
                            network = balanceResult.data.networkBalance
                            updatedAt = balanceResult.data.lastUpdate
                        }
                        is Result.Error -> {
                            status = balanceResult.message ?: "余额刷新失败"
                        }
                        else -> Unit
                    }

                    val qrBitmap = qrRepo.fetchQrBitmap()
                    if (qrBitmap != null) {
                        ECodeWidgetStore.saveQrBitmap(appContext, qrBitmap)
                    } else if (status == "刷新完成") {
                        status = "二维码刷新失败"
                    }

                    ECodeWidgetStore.saveBalances(appContext, card, network, updatedAt, status)
                    render(appContext, widgetIds, loading = false)
                } catch (e: Exception) {
                    Timber.e(e, "Widget refresh failed")
                    ECodeWidgetStore.saveStatus(appContext, "刷新失败: ${e.message ?: "未知错误"}")
                    render(appContext, widgetIds, loading = false)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshAsync(context, null)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        render(context.applicationContext, appWidgetIds, loading = false)
        refreshAsync(context, null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAsync(context, goAsync())
        }
    }
}
