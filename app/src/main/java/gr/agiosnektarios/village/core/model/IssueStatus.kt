package gr.agiosnektarios.village.core.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import gr.agiosnektarios.village.R

/**
 * Lifecycle of a report, modelled after a lightweight issue tracker.
 *
 * [isTerminal] marks the states that stop counting towards a neighbourhood's
 * open-issue badge.
 */
enum class IssueStatus(
    val id: String,
    @StringRes val labelRes: Int,
    val tint: Color,
    val isTerminal: Boolean,
) {
    OPEN("OPEN", R.string.status_open, Color(0xFFD64545), false),
    ACKNOWLEDGED("ACKNOWLEDGED", R.string.status_acknowledged, Color(0xFFE0A422), false),
    IN_PROGRESS("IN_PROGRESS", R.string.status_in_progress, Color(0xFF2E86C1), false),
    RESOLVED("RESOLVED", R.string.status_resolved, Color(0xFF2F7D32), true),
    WONT_DO("WONT_DO", R.string.status_wont_do, Color(0xFF77808B), true),
    ;

    companion object {
        fun fromId(id: String?): IssueStatus = entries.firstOrNull { it.id == id } ?: OPEN

        /** Statuses a plain resident may set on their own report. */
        val authorSelectable = listOf(RESOLVED, WONT_DO, OPEN)
    }
}
