package gr.agiosnektarios.village.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's horizontal rhythm, in one place.
 *
 * Before this the page margin was 16dp on the issue list, 16dp on the profile's
 * stat row, 20dp in the screen header and 12dp on the map sheets — close enough
 * that no single screen looked wrong, and different enough that moving between
 * them felt like moving between apps. A consistent left edge is the cheapest
 * thing there is and it is most of what "designed" means: every title, every
 * card and every section label should start on the same vertical line.
 *
 * [page] is that line. Everything else is measured from it.
 */
object Space {
    /** The margin from the screen edge to any content. One value, everywhere. */
    val page = 20.dp

    /** Between sibling cards in a vertical list. */
    val gutter = 12.dp

    /**
     * Where a separator starts in a list whose rows lead with a 48dp avatar.
     *
     * page + avatar + the gap after it, so the rule begins under the text
     * rather than under the picture — the detail that makes a list read as
     * rows of something rather than as stripes.
     */
    val separatorInset = 80.dp
}
