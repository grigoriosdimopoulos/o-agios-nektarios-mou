package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.data.settings.ThemeMode
import androidx.compose.runtime.Composable
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.ui.components.IssueTimeline
import gr.agiosnektarios.village.ui.issue.FixState
import gr.agiosnektarios.village.ui.issue.IssueListContent
import gr.agiosnektarios.village.ui.issue.QuickReportSheet
import gr.agiosnektarios.village.ui.issue.QuickReportUiState
import gr.agiosnektarios.village.ui.issue.IssueListUiState
import gr.agiosnektarios.village.ui.theme.VillageTheme
import java.util.Date
import org.junit.Rule
import org.junit.Test

/**
 * The app at the text size older residents actually use.
 *
 * Android's font scale goes to 2.0 in Accessibility settings, and in a village
 * where a good share of the users are over sixty it is not an edge case — it
 * is the common configuration. Everything else in this suite renders at 1.0,
 * which is the one setting that never exposes a layout that cannot cope.
 */
class AccessibilityTest {

    @get:Rule
    val paparazzi = Paparazzi(
        // Greek, because that is the locale this village reads in and it is
        // where the words are longest — the layout that survives at 1.5x in
        // English can still collapse in Greek, which is exactly how the quick
        // report's location line reached a state with the send button pushed
        // off the bottom of the screen.
        deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el"),
        showSystemUi = false,
    )

    @Test
    fun issue_list_large_text() {
        paparazzi.snapshot {
            VillageTheme(themeMode = ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    IssueListContent(
                        state = IssueListUiState(issues = sampleIssues, loading = false),
                        onOpenIssue = {},
                        onQueryChange = {},
                        onSortChange = {},
                        onToggleStatus = {},
                        onToggleCategory = {},
                    )
                }
            }
        }
    }

    /**
     * The state this whole path exists to rescue, at the text size the village
     * actually uses. Send must be on screen and the status must be readable.
     */
    @Test
    fun quick_report_stuck_large_text() {
        paparazzi.snapshot {
            VillageTheme(themeMode = ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    QuickReportSheet(
                        state = QuickReportUiState(
                            photo = ByteArray(64),
                            text = "Πεσμένο δέντρο",
                            position = null,
                            fix = FixState.UNAVAILABLE,
                        ),
                        onTextChange = {},
                        onRetakePhoto = {},
                        onRetryLocation = {},
                        onPickOnMap = {},
                        onSubmit = {},
                        onCategoryChange = {},
                    )
                }
            }
        }
    }

    /**
     * The screens this session added, at the largest scale Android offers.
     *
     * 2.0 is the end of the accessibility slider, and it is where a layout
     * that merely wraps at 1.5 tends to break instead. `unsafeUpdateConfig`
     * is the supported way to change the device for one snapshot; the rule's
     * own config stays at 1.5, which is the size worth guarding by default.
     */
    private fun atMaxText(name: String, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_5.copy(fontScale = 2.0f, locale = "el"),
        )
        paparazzi.snapshot(name = name) {
            VillageTheme(themeMode = ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    @Test
    fun quick_report_max_text() = atMaxText("quick_report") {
        QuickReportSheet(
            state = QuickReportUiState(
                photo = ByteArray(64),
                text = "Πεσμένο δέντρο",
                position = null,
                fix = FixState.UNAVAILABLE,
            ),
            onTextChange = {},
            onRetakePhoto = {},
            onRetryLocation = {},
            onPickOnMap = {},
            onSubmit = {},
            onCategoryChange = {},
        )
    }

    @Test
    fun timeline_max_text() = atMaxText("timeline") {
        IssueTimeline(
            issue = Issue(
                id = "1",
                title = "Πεσμένο δέντρο",
                authorName = "Μαρία Καραγιάννη",
                assigneeId = "d",
                assigneeName = "Δημήτρης Αναγνωστόπουλος",
                createdAt = Date(1_755_000_000_000L),
                assignedAt = Date(1_755_003_000_000L),
            ),
        )
    }
}
