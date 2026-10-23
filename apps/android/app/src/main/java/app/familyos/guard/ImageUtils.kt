package app.familyos.guard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun toBase64(context: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 80): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return null
                val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
                val bitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt().coerceAtLeast(1),
                        (original.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    original
                }
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        }.getOrNull()
    }
}
