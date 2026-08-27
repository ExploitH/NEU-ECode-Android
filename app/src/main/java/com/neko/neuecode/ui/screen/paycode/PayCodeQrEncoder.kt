package com.neko.neuecode.ui.screen.paycode

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object PayCodeQrEncoder {
    fun encode(payload: String, sizePx: Int = 640): ImageBitmap? {
        if (payload.isBlank() || sizePx <= 0) return null
        return try {
            val matrix = QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                ),
            )
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            val black = 0xFF111111.toInt()
            val white = 0xFFFFFFFF.toInt()
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix[x, y]) black else white
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
