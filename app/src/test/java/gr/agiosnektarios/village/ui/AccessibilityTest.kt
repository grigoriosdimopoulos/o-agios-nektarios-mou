package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.issue.FixState
import gr.agiosnektarios.village.ui.issue.IssueListContent
import gr.agiosnektarios.village.ui.issue.QuickReportSheet
import gr.agiosnektarios.village.ui.issue.QuickReportUiState
import gr.agiosnektarios.village.ui.issue.IssueListUiState
import gr.agiosnektarios.village.ui.theme.VillageTheme
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
                        onOpenFullForm = {},
                        offerFullForm = false,
                    )
                }
            }
        }
    }
}
