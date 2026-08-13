package gr.agiosnektarios.village.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.runCatchingUnit
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Uploads user-supplied images to Cloud Storage.
 *
 * Photos are downscaled and re-encoded before upload: phone cameras produce
 * 4–8 MB frames, and a report only ever displays one at card or full-width
 * size. This keeps uploads usable on village mobile data and keeps storage
 * costs proportional to the number of reports, not megapixels.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun uploadIssuePhoto(userId: String, uri: Uri): Result<String> =
        upload("issues/$userId/${UUID.randomUUID()}.jpg", uri, MAX_PHOTO_EDGE)

    suspend fun uploadAvatar(userId: String, uri: Uri): Result<String> =
        upload("avatars/$userId/${UUID.randomUUID()}.jpg", uri, MAX_AVATAR_EDGE)

    suspend fun uploadChatImage(chatId: String, userId: String, uri: Uri): Result<String> =
        upload("chats/$chatId/${userId}_${UUID.randomUUID()}.jpg", uri, MAX_PHOTO_EDGE)

    suspend fun uploadAnnouncementImage(userId: String, uri: Uri): Result<String> =
        upload("announcements/$userId/${UUID.randomUUID()}.jpg", uri, MAX_PHOTO_EDGE)

    private suspend fun upload(path: String, uri: Uri, maxEdge: Int): Result<String> =
        withContext(io) {
            runCatching {
                val bytes = compress(uri, maxEdge)
                val ref = storage.reference.child(path)
                ref.putBytes(bytes).await()
                ref.downloadUrl.await().toString()
            }
        }

    suspend fun delete(downloadUrl: String): Result<Unit> = withContext(io) {
        runCatchingUnit { storage.getReferenceFromUrl(downloadUrl).delete().await() }
    }

    private fun compress(uri: Uri, maxEdge: Int): ByteArray {
        // Two passes: measure with inJustDecodeBounds, then decode subsampled so
        // a large photo never has to exist in memory at full size.
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

        // Each helper recycles the bitmap it replaces, so only the final one is
        // still alive by the time we encode.
        val scaled = scaleToFit(applyExifRotation(uri, decoded), maxEdge)

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            scaled.recycle()
            out.toByteArray()
        }
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

    private fun scaleToFit(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** Portrait photos otherwise arrive sideways, since JPEG stores orientation in EXIF. */
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private companion object {
        const val MAX_PHOTO_EDGE = 1600
        const val MAX_AVATAR_EDGE = 512
        const val JPEG_QUALITY = 82
    }
}
