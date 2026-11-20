package com.example.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object ImageUtils {

    fun decodeUriToSoftwareBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024
    ): Bitmap? {
        return try {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val largest = max(width, height)
                    if (largest > maxDimension && largest > 0) {
                        val scale = maxDimension.toFloat() / largest.toFloat()
                        decoder.setTargetSize(
                            (width * scale).toInt().coerceAtLeast(1),
                            (height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                var sampleSize = 1
                val largest = max(boundsOpts.outWidth, boundsOpts.outHeight)
                while (largest / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
            }
            rawBitmap?.let { bmp ->
                if (bmp.config == Bitmap.Config.HARDWARE) {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bmp
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val filename = "QA_AI_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/ArabicQA"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false

            resolver.openOutputStream(imageUri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return false
                }
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, values, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

class ImageQuotaManager(context: Context) {

    companion object {
        const val DAILY_IMAGE_LIMIT = 8
        private const val PREFS_NAME = "qa_image_quota_prefs"
        private const val KEY_DATE = "quota_date"
        private const val KEY_USED = "quota_used"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun todayKey(): String = dateFormat.format(Date())

    fun remainingCount(dailyLimit: Int = DAILY_IMAGE_LIMIT): Int {
        val effectiveLimit = dailyLimit.coerceIn(1, 50)
        val today = todayKey()
        val storedDate = prefs.getString(KEY_DATE, null)
        if (storedDate != today) {
            prefs.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_USED, 0)
                .apply()
            return effectiveLimit
        }
        val used = prefs.getInt(KEY_USED, 0)
        return (effectiveLimit - used).coerceAtLeast(0)
    }

    fun consumeOne(dailyLimit: Int = DAILY_IMAGE_LIMIT): Int {
        val effectiveLimit = dailyLimit.coerceIn(1, 50)
        val today = todayKey()
        val storedDate = prefs.getString(KEY_DATE, null)
        val currentUsed = if (storedDate == today) prefs.getInt(KEY_USED, 0) else 0
        val newUsed = (currentUsed + 1).coerceAtMost(effectiveLimit)
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_USED, newUsed)
            .apply()
        return (effectiveLimit - newUsed).coerceAtLeast(0)
    }
}
