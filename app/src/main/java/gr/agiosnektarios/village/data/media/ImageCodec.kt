package gr.agiosnektarios.village.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.core.di.IoDispatcher
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * How large an image is allowed to be once encoded.
 *
 * [maxBytes] is a hard ceiling, not a hope: [ImageCodec] keeps reducing quality
 * and then dimensions until the encoded image is under it. That matters because
 * these images are stored inside Firestore documents, and a document that
 * exceeds 1 MiB is rejected outright by the server — a limit that must never be
 * discovered by a resident standing in the road with a photo of a pothole.
 */
data class ImageSpec(
    val maxEdge: Int,
    val maxBytes: Int,
) {
    companion object {
        /**
         * A report's full photo, opened one at a time on the detail screen.
         *
         * 1280 px is more than a phone shows full-width, and half a megabyte
         * leaves the rest of the 1 MiB document budget untouched.
         */
        val ISSUE_PHOTO = ImageSpec(maxEdge = 1280, maxBytes = 500_000)

        /**
         * The postage stamp on a report's card.
         *
         * Deliberately tiny. This one rides *inside* the issue document, and the
         * map reads every issue in the village on every launch — at 100 KB a
         * piece that would be a hundred megabytes of thumbnails per open. Seven
         * kilobytes is legible at card size and costs nothing.
         */
        val ISSUE_THUMBNAIL = ImageSpec(maxEdge = 220, maxBytes = 7_000)

        /** Rides inside the user document, which is read wherever a name appears. */
        val AVATAR = ImageSpec(maxEdge = 192, maxBytes = 12_000)

        /** Announcements are rare and read as a capped list, so this can be larger. */
        val ANNOUNCEMENT = ImageSpec(maxEdge = 1100, maxBytes = 90_000)

        /** One per message, in a conversation paged 300 at a time. */
        val CHAT = ImageSpec(maxEdge = 1100, maxBytes = 90_000)
    }
}

/**
 * Turns a photo the resident picked into bytes small enough to live in a
 * Firestore document.
 *
 * This exists instead of Cloud Storage because Cloud Storage requires a billing
 * account, and this village runs entirely on the free tier. Firestore stores
 * bytes natively, so an image is written as a `Blob` field rather than
 * base64 text — the same trick, without the third of the size that base64 adds
 * on top for nothing.
 *
 * The cost of that choice is honest and worth stating: images here are smaller
 * than a photo-sharing app's, and the ceilings above are the whole reason a
 * report with a picture works at all without a card on file.
 */
@Singleton
class ImageCodec @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun encode(uri: Uri, spec: ImageSpec): Result<ByteArray> = withContext(io) {
        runCatching {
            var bitmap = decodeDownsampled(uri, spec.maxEdge)
            try {
                var edge = spec.maxEdge
                // Quality first — it costs detail but keeps the picture's size.
                // Only when the cheapest quality still will not fit does the
                // image get physically smaller, which is the more visible loss.
                while (true) {
                    for (quality in QUALITY_LADDER) {
                        val encoded = bitmap.toJpeg(quality)
                        if (encoded.size <= spec.maxBytes) return@runCatching encoded
                    }
                    edge /= 2
                    check(edge >= MIN_EDGE) {
                        "cannot fit this image under ${spec.maxBytes} bytes"
                    }
                    bitmap = bitmap.scaledToFit(edge)
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Decodes at roughly the size wanted rather than at full resolution.
     *
     * A modern phone camera produces frames that are tens of megabytes once
     * decoded; loading one whole to shrink it is how an image picker runs a
     * device out of memory.
     */
    private fun decodeDownsampled(uri: Uri, maxEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: error("Cannot open image")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Cannot decode image")

        return decoded.rotatedByExif(uri).scaledToFit(maxEdge)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private fun Bitmap.scaledToFit(maxEdge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return this
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== this) recycle()
        return scaled
    }

    /** Portrait photos otherwise arrive sideways, since JPEG stores orientation in EXIF. */
    private fun Bitmap.rotatedByExif(uri: Uri): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return this
        }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated !== this) recycle()
        return rotated
    }

    private companion object {
        val QUALITY_LADDER = intArrayOf(85, 75, 65, 55, 45, 35, 25)

        /** Below this the picture has stopped being worth keeping. */
        const val MIN_EDGE = 120
    }
}
