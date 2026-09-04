package com.neko.neuecode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.neko.neuecode.R
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import com.neko.neuecode.domain.model.Result
import com.neko.neuecode.ui.screen.paycode.PayCodeQrEncoder
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class ECodeWidgetProvider : AppWidgetProvider() {
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refreshAll(context: Context) {
            context.sendBroadcast(
                Intent(context, ECodeWidgetProvider::class.java).apply {
                    action = ECodeWidgetPresentation.ACTION_REFRESH_QR
                },
            )
        }

        fun notifyViews(context: Context) {
            val appContext = context.applicationContext
            render(appContext, allWidgetIds(appContext), loading = false)
        }

        private fun pendingAction(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, ECodeWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
            val qr = ECodeWidgetStore.loadQrBitmap(context)
            val showBalance = ECodeWidgetPresentation.showBalances(snapshot.showBalance)
            widgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.ecode_widget)
                views.setTextViewText(R.id.widget_title, "e码通")
                views.setTextViewText(
                    R.id.widget_status,
                    when {
                        loading -> "刷新中…"
                        else -> snapshot.status
                    },
                )
                if (qr != null) {
                    views.setImageViewBitmap(R.id.widget_qr, qr)
                } else {
                    views.setImageViewResource(R.id.widget_qr, R.drawable.widget_qr_placeholder)
                }
                val balanceVisibility = if (showBalance) View.VISIBLE else View.GONE
                views.setViewVisibility(R.id.widget_balances, balanceVisibility)
                views.setTextViewText(
                    R.id.widget_card_balance,
                    if (snapshot.cardBalance.isNotBlank()) "校园卡  ${snapshot.cardBalance}" else "校园卡  --",
                )
                views.setTextViewText(
                    R.id.widget_network_balance,
                    if (snapshot.networkBalance.isNotBlank()) "网费  ${snapshot.networkBalance}" else "网费  --",
                )
                views.setTextViewText(R.id.widget_updated_at, formatTimestamp(snapshot.updatedAt))
                views.setOnClickPendingIntent(
                    R.id.widget_qr,
                    pendingAction(context, ECodeWidgetPresentation.bodyClickAction(), 1001),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_refresh,
                    pendingAction(context, ECodeWidgetPresentation.refreshClickAction(), 1002),
                )
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

        private fun refreshQrAsync(context: Context, pendingResult: PendingResult?) {
            val appContext = context.applicationContext
            val widgetIds = allWidgetIds(appContext)
            if (!com.neko.neuecode.domain.ecode.EcodeModuleAvailability.shouldFetchPayCode()) {
                ECodeWidgetStore.saveStatus(
                    appContext,
                    com.neko.neuecode.domain.ecode.EcodeModuleAvailability.PAUSE_NOTICE,
                )
                render(appContext, widgetIds, loading = false)
                pendingResult?.finish()
                return
            }
            render(appContext, widgetIds, loading = true)
            scope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        appContext,
                        ECodeWidgetEntryPoint::class.java,
                    )
                    if (!entryPoint.userPreferences().isPayCodeFetchEnabled() ||
                        entryPoint.userPreferences().isPayCodeSmsLocked()
                    ) {
                        ECodeWidgetStore.saveStatus(appContext, "取码开关已关闭，打开 App 后再取码")
                        render(appContext, widgetIds, loading = false)
                        pendingResult?.finish()
                        return@launch
                    }
                    when (val result = entryPoint.eCodePayCodeRepository().fetchPayCode()) {
                        is PayCodeParseResult.Success -> {
                            val bitmap = PayCodeQrEncoder.encodeBitmap(result.code.payload, sizePx = 512)
                            if (bitmap != null) {
                                ECodeWidgetStore.saveQrBitmap(appContext, bitmap)
                                ECodeWidgetStore.saveStatus(
                                    appContext,
                                    ECodeWidgetPresentation.qrStatus(true, result.code.ttlSeconds),
                                )
                            } else {
                                ECodeWidgetStore.saveStatus(appContext, "付款码已取到，绘制失败")
                            }
                        }
                        is PayCodeParseResult.Failure -> {
                            if (result.reason == PayCodeFailure.NeedSms) {
                                ECodeWidgetStore.saveStatus(
                                    appContext,
                                    "付款码需要短信验证，已停止自动刷新",
                                )
                            } else {
                                ECodeWidgetStore.saveStatus(
                                    appContext,
                                    ECodeWidgetPresentation.qrStatus(false, null),
                                )
                            }
                        }
                    }
                    render(appContext, widgetIds, loading = false)
                } catch (e: Exception) {
                    Timber.e(e, "Widget QR refresh failed")
                    ECodeWidgetStore.saveStatus(
                        appContext,
                        ECodeWidgetPresentation.qrStatus(false, null),
                    )
                    render(appContext, widgetIds, loading = false)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private fun refreshBalanceAsync(context: Context, pendingResult: PendingResult?) {
            val appContext = context.applicationContext
            val widgetIds = allWidgetIds(appContext)
            render(appContext, widgetIds, loading = true)
            scope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        appContext,
                        ECodeWidgetEntryPoint::class.java,
                    )
                    when (val balanceResult = entryPoint.personalRepository().getBalance()) {
                        is Result.Success -> {
                            ECodeWidgetStore.saveBalances(
                                appContext,
                                balanceResult.data.cardBalance,
                                balanceResult.data.networkBalance,
                                balanceResult.data.lastUpdate,
                                status = "余额已更新",
                            )
                        }
                        is Result.Error -> {
                            ECodeWidgetStore.saveStatus(
                                appContext,
                                balanceResult.message ?: "余额刷新失败",
                            )
                        }
                        else -> Unit
                    }
                    render(appContext, widgetIds, loading = false)
                } catch (e: Exception) {
                    Timber.e(e, "Widget balance refresh failed")
                    ECodeWidgetStore.saveStatus(appContext, "余额刷新失败")
                    render(appContext, widgetIds, loading = false)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshQrAsync(context, null)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        render(context.applicationContext, appWidgetIds, loading = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ECodeWidgetPresentation.ACTION_REFRESH_QR -> refreshQrAsync(context, goAsync())
            ECodeWidgetPresentation.ACTION_REFRESH_BALANCE -> refreshBalanceAsync(context, goAsync())
        }
    }
}
