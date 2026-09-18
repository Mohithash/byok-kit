package __PKG__.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/** A meal photo ready for the model: downscaled, orientation-corrected JPEG. */
class MealPhoto(val bitmap: Bitmap) {
    val base64: String by lazy {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

object Photo {
    private const val MAX_EDGE = 1024

    fun fromBitmap(b: Bitmap): MealPhoto = MealPhoto(scale(b))

    fun fromUri(ctx: Context, uri: Uri): MealPhoto? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_EDGE && bounds.outHeight / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = ctx.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright = if (rotation == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
        return MealPhoto(scale(upright))
    }

    private fun scale(b: Bitmap): Bitmap {
        val longest = maxOf(b.width, b.height)
        if (longest <= MAX_EDGE) return b
        val f = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }
}
