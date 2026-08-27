package com.neko.neuecode.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object ECodeWidgetStore {
    private const val PREFS = "ecode_widget_store"
    private const val KEY_CARD = "card_balance"
    private const val KEY_NETWORK = "network_balance"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_STATUS = "status"
    private const val KEY_SHOW_BALANCE = "show_balance"
    private const val QR_FILE = "ecode_widget_qr.png"

    data class Snapshot(
        val cardBalance: String = "",
        val networkBalance: String = "",
        val updatedAt: Long = 0L,
        val status: String = "点码刷新付款码",
        val hasQr: Boolean = false,
        val showBalance: Boolean = true,
    )

    fun load(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = File(context.filesDir, QR_FILE)
        return Snapshot(
            cardBalance = prefs.getString(KEY_CARD, "") ?: "",
            networkBalance = prefs.getString(KEY_NETWORK, "") ?: "",
            updatedAt = prefs.getLong(KEY_UPDATED, 0L),
            status = prefs.getString(KEY_STATUS, "点码刷新付款码") ?: "点码刷新付款码",
            hasQr = file.exists() && file.length() > 0,
            showBalance = prefs.getBoolean(KEY_SHOW_BALANCE, true),
        )
    }

    fun saveBalances(context: Context, card: String, network: String, updatedAt: Long, status: String? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CARD, card)
            .putString(KEY_NETWORK, network)
            .putLong(KEY_UPDATED, updatedAt)
            .apply {
                if (status != null) putString(KEY_STATUS, status)
            }
            .apply()
    }

    fun saveStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATUS, status)
            .apply()
    }

    fun saveShowBalance(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_BALANCE, show)
            .apply()
    }

    fun saveQrBitmap(context: Context, bitmap: Bitmap) {
        val file = File(context.filesDir, QR_FILE)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun clearQr(context: Context) {
        File(context.filesDir, QR_FILE).delete()
    }

    fun loadQrBitmap(context: Context): Bitmap? {
        val file = File(context.filesDir, QR_FILE)
        if (!file.exists() || file.length() == 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}
