package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.core.model.AlertKind
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.VillageAlert
import gr.agiosnektarios.village.core.weather.FireRisk
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.alert.AlertRaiseContent
import gr.agiosnektarios.village.ui.alert.EmergencyBanner
import gr.agiosnektarios.village.ui.alert.OutageCard
import gr.agiosnektarios.village.ui.alert.UrgentButton
import gr.agiosnektarios.village.ui.alert.RaiseAlertState
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.LocalClock
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.issue.IssueDetailContent
import gr.agiosnektarios.village.ui.issue.IssueListContent
import gr.agiosnektarios.village.ui.issue.IssueListUiState
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.VillageTheme
import gr.agiosnektarios.village.ui.weather.FireLevelPill
import org.junit.Rule
import org.junit.Test

/**
 * States nobody had looked at.
 *
 * Everything in here renders a screen or a control in the configuration the
 * village actually runs it in — Greek, and the text size an eighty-year-old
 * sets on a phone — and every one of them was previously unrendered. Added by
 * a review whose whole point was that a person should look at the pixels.
 */
private const val NOW = 1_787_120_700_000L

class StressTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val greekLarge = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el")
    private val greekMax = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f, locale = "el")

    private fun render(
        dark: Boolean = false,
        config: DeviceConfig? = null,
        name: String? = null,
        content: @Composable () -> Unit,
    ) {
        config?.let(paparazzi::unsafeUpdateConfig)
        paparazzi.snapshot(name = name) {
            CompositionLocalProvider(LocalClock provides { NOW }) {
                VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) { content() }
                }
            }
        }
    }

    /**
     * The twenty-one filter chips, which live behind a modal sheet and so have
     * never appeared in a golden. This is their contents, laid out exactly as
     * IssueFilterSheet lays them out, at the text size the village runs.
     */
    @Test
    fun filter_sheet_greek_large() = render(config = greekLarge, name = "filters") { FilterSheetBody() }

    @Test
    fun filter_sheet_greek_max() = render(config = greekMax, name = "filters_max") { FilterSheetBody() }

    /** The same chips in the dark theme, where their tinted fills are 10% ink on near-black. */
    @Test
    fun filter_sheet_dark() = render(dark = true) { FilterSheetBody() }

    /**
     * The alarm picker at twice the text — the screen a frightened person of
     * eighty opens. Seven targets on a scrolling column; the question is how
     * many of them are above the fold.
     */
    @Test
    fun alert_pick_greek_max() = render(config = greekMax, name = "pick_max") {
        AlertRaiseContent(
            state = RaiseAlertState(),
            numbersAvailable = true,
            onPick = {}, onNote = {}, onPlace = {}, onBack = {}, onSend = {}, onDial = {}, onSms = {},
        )
    }

    /** The chosen state for a fire, in Greek at twice the text. */
    @Test
    fun alert_fire_greek_max() = render(config = greekMax, name = "fire_max") {
        AlertRaiseContent(
            state = RaiseAlertState(
                kind = AlertKind.FIRE,
                note = "Καπνός πάνω από το ρέμα.",
                placeLabel = "Άνω γειτονιά",
            ),
            numbersAvailable = true,
            onPick = {}, onNote = {}, onPlace = {}, onBack = {}, onSend = {}, onDial = {}, onSms = {},
        )
    }

    /** The report detail — council row, vote bar, timeline — in Greek at 1.5. */
    @Test
    fun issue_detail_greek_large() = render(config = greekLarge, name = "detail") {
        IssueDetailContent(
            issue = sampleIssues[1].copy(
                assigneeId = "dimitris",
                assigneeName = "Δημήτρης Αναγνωστόπουλος",
                assignedAt = java.util.Date(NOW - 3 * 60 * 60 * 1000L),
                reportedToCouncilAt = java.util.Date(NOW - 6L * 24 * 60 * 60 * 1000L),
                councilReference = "ΑΠ 4412/2026",
            ),
            comments = emptyList(),
            photos = emptyList(),
            viewer = UserProfile(
                id = "me",
                firstName = "Γρηγόρης",
                lastName = "Δημόπουλος",
                email = "g@example.gr",
                role = Role.USER.id,
            ),
            myVote = 0,
            onVote = {},
            onDeleteComment = {},
            contentPadding = PaddingValues(top = 0.dp, bottom = 0.dp),
        )
    }

    /** An outage card at twice the text, with a moderator's "it is over" beside the confirm. */
    @Test
    fun outage_card_greek_max() = render(config = greekMax, name = "outage_max") {
        Column {
            OutageCard(
                alert = VillageAlert(
                    id = "a",
                    kind = AlertKind.WATER.name,
                    note = "Από χθες το βράδυ, σε όλη την πάνω γειτονιά.",
                    raisedById = "d",
                    raisedByName = "Δημήτρης Αναγνωστόπουλος",
                    raisedAt = java.util.Date(NOW - 40 * 60_000L),
                    confirmedBy = listOf("d", "m"),
                ),
                userId = "m",
                canResolve = true,
                onConfirm = {},
                onResolve = {},
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    /** The five fire-risk pills side by side, light and dark, at the village's text size. */
    @Test
    fun fire_pills_greek_large() = render(config = greekLarge, name = "fire_pills") { FirePills() }

    @Test
    fun fire_pills_dark() = render(dark = true) { FirePills() }

    /** The reports list when a filter has hidden everything — the empty state nobody rendered. */
    @Test
    fun issue_list_filtered_empty_greek() = render(config = greekLarge, name = "empty_filtered") {
        IssueListContent(
            state = IssueListUiState(
                issues = emptyList(),
                loading = false,
                statuses = setOf(IssueStatus.RESOLVED),
                categories = setOf(IssueCategory.WATER),
            ),
            onOpenIssue = {}, onQueryChange = {}, onSortChange = {},
            onToggleStatus = {}, onToggleCategory = {},
        )
    }

    /**
     * The whole alarm feature in the dark theme, which had no golden at all.
     * In dark, colorScheme.error is a pale salmon (#FF8A80), so every surface
     * that was a red warning in the light theme inverts.
     */
    @Test fun alert_pick_dark() = render(dark = true) {
        AlertRaiseContent(
            state = RaiseAlertState(),
            numbersAvailable = true,
            onPick = {}, onNote = {}, onPlace = {}, onBack = {}, onSend = {}, onDial = {}, onSms = {},
        )
    }

    @Test fun alert_fire_chosen_dark() = render(dark = true) {
        AlertRaiseContent(
            state = RaiseAlertState(
                kind = AlertKind.FIRE,
                note = "Καπνός πάνω από το ρέμα.",
                placeLabel = "Άνω γειτονιά",
            ),
            numbersAvailable = true,
            onPick = {}, onNote = {}, onPlace = {}, onBack = {}, onSend = {}, onDial = {}, onSms = {},
        )
    }

    /** The emergency banner and the map's urgent button, dark and light, side by side. */
    @Test fun emergency_chrome_dark() = render(dark = true) { EmergencyChrome() }

    @Test fun emergency_chrome_light() = render { EmergencyChrome() }

    /** The outage card in the dark theme — the raised card nobody rendered dark. */
    @Test fun outage_card_dark() = render(dark = true) {
        Column {
            OutageCard(
                alert = VillageAlert(
                    id = "a",
                    kind = AlertKind.POWER.name,
                    note = "Από χθες το βράδυ, σε όλη την πάνω γειτονιά.",
                    raisedById = "d",
                    raisedByName = "Δημήτρης Α.",
                    raisedAt = java.util.Date(NOW - 40 * 60_000L),
                    confirmedBy = listOf("d", "m", "n"),
                ),
                userId = "x",
                canResolve = true,
                onConfirm = {},
                onResolve = {},
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetBody() {
    Column(
        modifier = Modifier.padding(horizontal = Space.page).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Κατάσταση", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IssueStatus.entries.forEach { status ->
                StatusChip(status = status, selected = status == IssueStatus.OPEN, onClick = {})
            }
        }
        Text(text = "Κατηγορία", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IssueCategory.entries.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = category == IssueCategory.WATER,
                    onClick = {},
                )
            }
        }
        SecondaryButton(text = "Καθαρισμός", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FirePills() {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FireRisk.Level.entries.forEach { level -> FireLevelPill(level = level) }
    }
}
@Composable
private fun EmergencyChrome() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EmergencyBanner(
            alerts = listOf(
                VillageAlert(
                    id = "e",
                    kind = AlertKind.FIRE.name,
                    note = "Καπνός πάνω από το ρέμα.",
                    placeLabel = "Άνω γειτονιά",
                    raisedById = "d",
                    raisedByName = "Δημήτρης Α.",
                    raisedAt = java.util.Date(NOW - 4 * 60_000L),
                ),
            ),
            onOpen = {},
        )
        UrgentButton(onClick = {})
    }
}
