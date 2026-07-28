package com.biliaudio.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * 用 zxing 把字符串编码成二维码 Bitmap。
 *
 * bilibili 的 /x/passport-login/web/qrcode/generate 返回的 url 是
 * 一段登录链接（不是图片 URL），需要本地把它编码成二维码图片才能被
 * B站客户端扫描。之前用 Coil 的 AsyncImage 直接加载该 URL 当然显示不出。
 */
object QrCodeGenerator {

    fun generate(content: String, size: Int = 600): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix: BitMatrix = MultiFormatWriter()
                .encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
