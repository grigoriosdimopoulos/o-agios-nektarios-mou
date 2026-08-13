package gr.agiosnektarios.village.core.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import gr.agiosnektarios.village.R

/**
 * The kinds of real-world problem a resident can report.
 *
 * [id] is what lands in Firestore, so it must stay stable once released — the
 * label is a resource id precisely so renaming a category never touches data.
 * [tint] is used for the map pin and the category chip so a category is
 * recognisable at a glance from across the map.
 */
enum class IssueCategory(
    val id: String,
    @StringRes val labelRes: Int,
    val emoji: String,
    val tint: Color,
) {
    VEGETATION("VEGETATION", R.string.category_vegetation, "🌿", Color(0xFF4C9A5A)),
    ROAD("ROAD", R.string.category_road, "🛣️", Color(0xFF7A6650)),
    FALLEN_TREE("FALLEN_TREE", R.string.category_fallen_tree, "🌳", Color(0xFF2F7D32)),
    DANGER("DANGER", R.string.category_danger, "⚠️", Color(0xFFD64545)),
    WATER("WATER", R.string.category_water, "💧", Color(0xFF2E86C1)),
    LIGHTING("LIGHTING", R.string.category_lighting, "💡", Color(0xFFE0A422)),
    GARBAGE("GARBAGE", R.string.category_garbage, "🗑️", Color(0xFF6B7280)),
    ILLEGAL_DUMPING("ILLEGAL_DUMPING", R.string.category_illegal_dumping, "🚯", Color(0xFF8B5E3C)),
    SEWAGE("SEWAGE", R.string.category_sewage, "🚽", Color(0xFF4A6FA5)),
    STRAY_ANIMALS("STRAY_ANIMALS", R.string.category_stray_animals, "🐕", Color(0xFFB4762C)),
    POWER("POWER", R.string.category_power, "⚡", Color(0xFFCF8B12)),
    SIGNAGE("SIGNAGE", R.string.category_signage, "🚦", Color(0xFF5B6ABF)),
    PLAYGROUND("PLAYGROUND", R.string.category_playground, "🎠", Color(0xFFC2569B)),
    FIRE_RISK("FIRE_RISK", R.string.category_fire_risk, "🔥", Color(0xFFE2571E)),
    SUGGESTION("SUGGESTION", R.string.category_suggestion, "💡", Color(0xFF1F6F5C)),
    OTHER("OTHER", R.string.category_other, "📌", Color(0xFF77808B)),
    ;

    companion object {
        fun fromId(id: String?): IssueCategory = entries.firstOrNull { it.id == id } ?: OTHER
    }
}
