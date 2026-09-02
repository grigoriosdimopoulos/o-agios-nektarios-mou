package gr.agiosnektarios.village.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.toArgb
import gr.agiosnektarios.village.core.model.IssueCategory

/**
 * Map pins, drawn straight to bitmaps.
 *
 * A GL map renders icons from a texture atlas, not from a view hierarchy, so
 * these cannot be composables — the previous Google Maps screen could pass
 * arbitrary Compose content per marker, and that is the one thing that did not
 * survive the move. Drawing them by hand is the honest replacement: one bitmap
 * per (category, open) pair, uploaded to the style once, then referenced by
 * thousands of features for free.
 *
 * The count that used to live inside a composable is now a text field on the
 * symbol layer, which the map renders and collision-avoids itself.
 */
object MapPins {

    /** Icon id for a report's pin, and the key it is registered under in the style. */
    fun iconId(category: IssueCategory, open: Boolean): String =
        "pin-${category.id}-${if (open) "open" else "done"}"

    const val PENDING_ICON = "pin-pending"

    /**
     * Every pin the app can show. Registered in one pass when the style loads,
     * because adding an image to a live style forces a texture re-upload.
     */
    fun allPins(metrics: DisplayMetrics): Map<String, Bitmap> = buildMap {
        for (category in IssueCategory.entries) {
            put(iconId(category, open = true), pin(metrics, category, open = true))
            put(iconId(category, open = false), pin(metrics, category, open = false))
        }
        put(PENDING_ICON, pin(metrics, IssueCategory.OTHER, open = true, pending = true))
    }

    /**
     * A coloured disc with the category's emoji and a white ring.
     *
     * Resolved reports are drawn washed out rather than hidden: "this was dealt
     * with" is information the village wants on the map, but it should not
     * compete with what is still open.
     */
    private fun pin(
        metrics: DisplayMetrics,
        category: IssueCategory,
        open: Boolean,
        pending: Boolean = false,
    ): Bitmap {
        val density = metrics.density
        val size = (44 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = center - 3f * density

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33000000
        }
        canvas.drawCircle(center, center + 1.5f * density, radius, shadow)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.tint.toArgb()
            alpha = if (open) 255 else 120
        }
        canvas.drawCircle(center, center, radius, fill)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = if (pending) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt()
            alpha = if (open) 255 else 160
        }
        canvas.drawCircle(center, center, radius, ring)

        val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f * density
            textAlign = Paint.Align.CENTER
            alpha = if (open) 255 else 170
        }
        // Centre the glyph on its own metrics rather than the box, or emoji with
        // descenders sit visibly low in the circle.
        val baseline = center - (emoji.descent() + emoji.ascent()) / 2f
        canvas.drawText(category.emoji, center, baseline, emoji)

        return bitmap
    }
}
