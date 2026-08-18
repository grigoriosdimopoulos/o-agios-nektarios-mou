package gr.agiosnektarios.village.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.agiosnektarios.village.ui.theme.Space

/**
 * The large title every top-level screen opens with.
 *
 * Three screens each wrote their own `Text(headlineMedium)` with slightly
 * different padding, which is why the app read as a set of screens rather than
 * as one app. What makes a title feel like a platform title rather than a
 * heading in a document is specific and small:
 *
 *  - **Size with weight, not size alone.** headlineMedium at regular weight is
 *    a big, thin, floaty line. Bold at a slightly smaller size has more
 *    presence and takes less room.
 *  - **Negative tracking.** Large type set at body spacing looks loose; every
 *    system typeface tightens as it scales up, and this is the single change
 *    that most makes large text look *designed*.
 *  - **Generous space above, tight below.** The title belongs to the content
 *    under it, so the gap beneath it must be smaller than the gap above.
 *
 * [subtitle] is for a count or a status line, and [actions] for the one or two
 * controls that belong to the whole screen rather than to a row in it.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.page, end = Space.page - 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        actions()
    }
}
