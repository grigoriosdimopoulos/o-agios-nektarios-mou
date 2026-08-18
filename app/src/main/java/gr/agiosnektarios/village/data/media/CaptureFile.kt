package gr.agiosnektarios.village.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A scratch file for the camera to write one photo into.
 *
 * The camera app writes here, [ImageCodec] reads it back and re-encodes it
 * into the report document, and the file is then rubbish. It lives in the
 * cache directory so the system can reclaim it, and old captures are swept on
 * the way in rather than on the way out — an app that crashes between capture
 * and upload would otherwise leave the frame behind for good.
 */
object CaptureFile {

    fun create(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        sweep(dir)
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.captures", file)
    }

    /** Deletes anything left by an earlier capture. There is only ever one live. */
    private fun sweep(dir: File) {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
