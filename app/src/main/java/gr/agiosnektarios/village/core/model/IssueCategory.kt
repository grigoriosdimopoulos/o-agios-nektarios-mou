package gr.agiosnektarios.village.core.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import gr.agiosnektarios.village.R

/**
 * The kinds of real-world problem a resident can report.
 *
 * [id] is what lands in Firestore, so it must stay stable once released — the
 * label is a resource id precisely so renaming a category never touches data.
 * [tint] is used for the map pin and the category chip so a category is
 * recognisable at a glance from across the map.
 */
/*
 * Two things were wrong in this table and both are visible on the feed.
 *
 * LIGHTING and SUGGESTION were both "💡", so two of sixteen categories could
 * not be told apart on a card, a chip or a map pin. And GARBAGE (#6B7280) and
 * OTHER (#77808B) were cool blue-greys — the only cold objects anywhere in a
 * palette built on cream, pine and terracotta, which is why a rubbish report
 * looked like it belonged to a different app. Both greys are now warm.
 */
enum class IssueCategory(
    val id: String,
    @StringRes val labelRes: Int,
    val emoji: String,
    val tint: Color,
    /**
     * How urgent this kind of thing is, which decides how much weight its card
     * carries in a list.
     *
     * Every report used to look identical: "dry grass beside the playground —
     * fire risk" and "bins not emptied" had the same size, the same colour and
     * the same everything except a small emoji. A list where nothing stands out
     * is a list where the one thing that matters does not either.
     */
    val urgency: Urgency = Urgency.ORDINARY,
) {
    VEGETATION("VEGETATION", R.string.category_vegetation, "🌿", Color(0xFF4C9A5A)),
    ROAD("ROAD", R.string.category_road, "🛣️", Color(0xFF7A6650)),
    FALLEN_TREE("FALLEN_TREE", R.string.category_fallen_tree, "🌳", Color(0xFF2F7D32), Urgency.RAISED),
    DANGER("DANGER", R.string.category_danger, "⚠️", Color(0xFFD64545), Urgency.URGENT),
    WATER("WATER", R.string.category_water, "💧", Color(0xFF2E86C1), Urgency.RAISED),
    LIGHTING("LIGHTING", R.string.category_lighting, "💡", Color(0xFFE0A422)),
    GARBAGE("GARBAGE", R.string.category_garbage, "🗑️", Color(0xFF8A7F6E)),
    ILLEGAL_DUMPING("ILLEGAL_DUMPING", R.string.category_illegal_dumping, "🚯", Color(0xFF8B5E3C)),
    SEWAGE("SEWAGE", R.string.category_sewage, "🚽", Color(0xFF4A6FA5), Urgency.RAISED),
    STRAY_ANIMALS("STRAY_ANIMALS", R.string.category_stray_animals, "🐕", Color(0xFFB4762C)),
    POWER("POWER", R.string.category_power, "⚡", Color(0xFFCF8B12), Urgency.RAISED),
    SIGNAGE("SIGNAGE", R.string.category_signage, "🚦", Color(0xFF5B6ABF)),
    PLAYGROUND("PLAYGROUND", R.string.category_playground, "🎠", Color(0xFFC2569B)),
    FIRE_RISK("FIRE_RISK", R.string.category_fire_risk, "🔥", Color(0xFFE2571E), Urgency.URGENT),
    SUGGESTION("SUGGESTION", R.string.category_suggestion, "✨", Color(0xFF1F6F5C)),
    OTHER("OTHER", R.string.category_other, "📌", Color(0xFF9A8E7C)),
    ;

    /**
     * A drawn icon rather than an emoji.
     *
     * The emoji are kept for anywhere text has to travel — a notification, a
     * shared message — but they are the wrong thing on a screen: they are a
     * second visual language beside a serif display face and a restrained
     * palette, and they render differently on every device and font. These are
     * one weight, one style, and they take the category's own colour.
     */
    val icon: ImageVector
        get() = when (this) {
            VEGETATION -> Icons.Filled.Grass
            ROAD -> Icons.Filled.Route
            FALLEN_TREE -> Icons.Filled.Park
            DANGER -> Icons.Filled.ReportProblem
            WATER -> Icons.Filled.WaterDrop
            LIGHTING -> Icons.Filled.Lightbulb
            GARBAGE -> Icons.Filled.DeleteSweep
            ILLEGAL_DUMPING -> Icons.Filled.Forest
            SEWAGE -> Icons.Filled.Plumbing
            STRAY_ANIMALS -> Icons.Filled.Pets
            POWER -> Icons.Filled.Bolt
            SIGNAGE -> Icons.Filled.Traffic
            PLAYGROUND -> Icons.Filled.Attractions
            FIRE_RISK -> Icons.Filled.LocalFireDepartment
            SUGGESTION -> Icons.Filled.AutoAwesome
            OTHER -> Icons.Filled.PushPin
        }

    companion object {
        fun fromId(id: String?): IssueCategory = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** How much room a report of this kind is allowed to take in the eye. */
enum class Urgency { ORDINARY, RAISED, URGENT }
