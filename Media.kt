@file:Suppress("DEPRECATION")

package com.nearchat.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object Media {
    private val IMG = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VID = setOf("mp4", "mkv", "3gp", "webm", "mov", "avi", "m4v")

    private fun ext(n: String) = n.substringAfterLast('.', "").lowercase()
    fun isImage(n: String) = ext(n) in IMG
    fun isVideo(n: String) = ext(n) in VID

    fun previewOf(m: Msg): String = m.text ?: when {
        Media.isImage(m.fileName ?: "") -> "\uD83D\uDCF7 Photo"
        Media.isVideo(m.fileName ?: "") -> "\uD83C\uDFAC Video"
        else -> "\uD83D\uDCC4 ${m.fileName ?: "File"}"
    }

    private fun sampleFor(o: BitmapFactory.Options, maxDim: Int): Int {
        var s = 1
        while (o.outWidth / (s * 2) >= maxDim && o.outHeight / (s * 2) >= maxDim) s *= 2
        return s
    }

    private fun rotationOf(orientation: Int): Float = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    private fun rotate(b: Bitmap?, deg: Float): Bitmap? {
        if (b == null || deg == 0f) return b
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)
    }

    fun decodeFile(path: String, maxDim: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) null else {
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleFor(o, maxDim) })
            val rot = runCatching { rotationOf(ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) }.getOrDefault(0f)
            rotate(bmp, rot)
        }
    } catch (e: Throwable) {
        null
    }

    fun decodeStream(open: () -> InputStream?, maxDim: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) null else {
            val bmp = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleFor(o, maxDim) }) }
            val rot = runCatching {
                open()?.use { rotationOf(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) } ?: 0f
            }.getOrDefault(0f)
            rotate(bmp, rot)
        }
    } catch (e: Throwable) {
        null
    }

    fun videoFrame(setSource: (android.media.MediaMetadataRetriever) -> Unit): Bitmap? = try {
        val r = android.media.MediaMetadataRetriever()
        try {
            setSource(r)
            r.getFrameAtTime(0)
        } finally {
            r.release()
        }
    } catch (e: Throwable) {
        null
    }

    fun saveThumb(dir: File, id: Long, bmp: Bitmap): String {
        dir.mkdirs()
        val f = File(dir, "$id.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return f.absolutePath
    }
}
