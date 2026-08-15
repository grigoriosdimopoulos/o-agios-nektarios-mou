package gr.agiosnektarios.village.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Draws an image held as JPEG bytes rather than fetched from a URL.
 *
 * Every picture in this app lives inside a Firestore document, because Cloud
 * Storage needs a billing account and this village does not have one. There is
 * no URL to hand an image loader, so decoding happens here.
 *
 * The decode is [remember]ed against the byte array's identity: bitmap decoding
 * is far too expensive to repeat on every recomposition, and these arrays are
 * replaced wholesale when the document changes rather than mutated in place.
 */
@Composable
fun BytesImage(
    bytes: ByteArray?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable (() -> Unit)? = null,
) {
    val bitmap: ImageBitmap? = remember(bytes) {
        bytes?.takeIf { it.isNotEmpty() }?.let { source ->
            runCatching {
                BitmapFactory.decodeByteArray(source, 0, source.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    if (bitmap == null) {
        // A picture that will not decode is a damaged document, not a crash.
        if (fallback != null) Box(modifier) { fallback() }
        return
    }

    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
