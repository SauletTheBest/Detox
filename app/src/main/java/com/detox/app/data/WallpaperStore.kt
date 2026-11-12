package com.detox.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * The strict home's own wallpaper photo, kept as ONE small JPEG inside Detox's private folder.
 *
 * Why copy it instead of remembering the photo's address (Uri)? The photo picker only lets us
 * read the photo for a short time. A private copy keeps working after a restart, and the
 * launcher never needs any storage permission.
 *
 * Why shrink it? A 12-megapixel camera photo is ~48 MB in memory. 1440 px on the long side is
 * sharp on this phone's screen and only ~8 MB, so the home screen opens fast.
 */
object WallpaperStore {

    private const val FILE_NAME = "wallpaper.jpg"
    private const val MAX_SIDE = 1440          // px, long side
    private const val JPEG_QUALITY = 85        // 0..100; 85 looks the same as 100 at a third of the size
    private const val TAG = "Detox"

    /** Where the photo lives: filesDir/wallpaper.jpg. May not exist (no photo chosen yet). */
    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * Copy the picked photo into [file]: read -> shrink -> turn upright -> save as JPEG.
     * Runs on a background thread (Dispatchers.IO), so the screen never freezes.
     * @return false if the photo couldn't be read (deleted, unknown format, too big...).
     */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // 1) Read only the photo's size. inJustDecodeBounds = "don't load pixels yet".
            //    (In this mode decodeStream always returns null; the size lands in `bounds`.)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val sizeStream = resolver.openInputStream(uri) ?: return@withContext false
            sizeStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            // 2) Load a smaller copy right away. inSampleSize = 4 keeps every 4th pixel in each
            //    direction = 1/16 of the memory. Android only does powers of two, so it's "roughly right".
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight))
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext false

            // 3) Exact final size + upright (phones often store portrait photos sideways
            //    and only write "rotate 90°" into the photo's EXIF notes).
            val upright = scaleAndRotate(decoded, readRotation(context, uri))
            if (upright !== decoded) decoded.recycle()   // free the bigger copy now, not "some day"

            // 4) Write to a temp file, then rename. Rename is all-or-nothing, so the launcher
            //    never reads a half-written photo.
            val temp = File(context.filesDir, "$FILE_NAME.tmp")
            val written = temp.outputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            upright.recycle()
            if (!written) {
                temp.delete()
                return@withContext false
            }
            val target = file(context)
            if (!temp.renameTo(target)) {
                // Very rare: rename refused to replace the old file. Remove the old one and retry.
                target.delete()
                if (!temp.renameTo(target)) return@withContext false
            }
            Log.i(TAG, "Wallpaper saved (${target.length() / 1024} KB)")
            true
        } catch (e: Exception) {
            Log.i(TAG, "Could not save wallpaper: $e")
            false
        } catch (e: OutOfMemoryError) {
            // A giant panorama could still be too big; just say "couldn't use it".
            Log.i(TAG, "Wallpaper too big: $e")
            false
        }
    }

    /** "Remove photo": delete our private copy. The original in your gallery is untouched. */
    fun clear(context: Context) {
        file(context).delete()
    }

    /** Biggest power of two that still leaves the long side at least MAX_SIDE (we scale down exactly later). */
    private fun sampleSizeFor(longSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    private fun scaleAndRotate(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        if (scale == 1f && rotationDegrees == 0) return bitmap      // already perfect
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        }
        // filter = true: smooth scaling instead of blocky pixels.
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * How far the photo must be turned to stand upright, from its EXIF notes.
     * Uses Android's built-in ExifInterface (works from Android 7; we need 8+), so no extra library.
     */
    private fun readRotation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0                     // normal, unknown, or mirrored (rare; ignored)
            }
        } ?: 0
    } catch (e: Exception) {
        0                                     // no EXIF notes (e.g. a PNG screenshot) -> keep as is
    }
}
