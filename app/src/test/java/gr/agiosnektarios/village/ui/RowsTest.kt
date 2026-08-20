package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.ui.admin.ResidentRow
import gr.agiosnektarios.village.ui.settings.ActionRow
import gr.agiosnektarios.village.ui.settings.RadioRow
import gr.agiosnektarios.village.ui.settings.SectionHeader
import gr.agiosnektarios.village.ui.settings.SwitchRow
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.theme.VillageTheme
import gr.agiosnektarios.village.ui.theme.errorInk
import org.junit.Rule
import org.junit.Test
import gr.agiosnektarios.village.core.model.FeatureFlags
import gr.agiosnektarios.village.ui.admin.FeatureSwitches

/**
 * The rows that make up Settings and the administrator's list.
 *
 * Neither screen had ever been rendered by anything. They are built from five
 * small row composables rather than one layout, so rendering the rows covers
 * the thing that actually breaks — a Greek label at twice the text size beside
 * a fixed-width control — without extracting five view models.
 */
class RowsTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private fun render(
        dark: Boolean = false,
        config: DeviceConfig? = null,
        name: String? = null,
        content: @Composable () -> Unit,
    ) {
        config?.let(paparazzi::unsafeUpdateConfig)
        paparazzi.snapshot(name = name) {
            VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    @Composable
    private fun Settings() {
        Column {
            SectionHeader("Εμφάνιση")
            RadioRow("Ό,τι λέει το τηλέφωνο", selected = true, onSelect = {})
            RadioRow("Πάντα φωτεινό", selected = false, onSelect = {})
            SectionHeader("Ειδοποιήσεις")
            SwitchRow("Σχόλια στις αναφορές μου", checked = true, onCheckedChange = {})
            SwitchRow("Αλλαγές κατάστασης στις αναφορές μου", checked = true, onCheckedChange = {})
            SwitchRow("Νέες ανακοινώσεις", checked = false, onCheckedChange = {})
            SectionHeader("Λογαριασμός")
            ActionRow("Αλλαγή κωδικού", onClick = {})
            ActionRow(
                "Διαγραφή του λογαριασμού μου",
                onClick = {},
                tint = MaterialTheme.colorScheme.errorInk,
            )
        }
    }

    @Test fun settings_light() = render { Settings() }

    @Test fun settings_dark() = render(dark = true) { Settings() }

    /** Greek at one and a half times the text, beside a fixed-width switch. */
    @Test
    fun settings_greek_large() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el"),
        name = "settings_large",
    ) { Settings() }

    @Test
    fun settings_greek_max() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f, locale = "el"),
        name = "settings_max",
    ) { Settings() }

    @Composable
    private fun Residents() {
        Column {
            ResidentRow(
                profile = UserProfile(
                    id = "a", firstName = "Δημήτρης", lastName = "Αναγνωστόπουλος",
                    email = "d@example.gr", blockId = "block-01", role = Role.ADMIN.id,
                ),
                onClick = {},
            )
            ResidentRow(
                profile = UserProfile(
                    id = "b", firstName = "Μαρία", lastName = "Καραγιάννη",
                    email = "m@example.gr", blockId = "block-02", role = Role.MODERATOR.id,
                ),
                onClick = {},
            )
            ResidentRow(
                profile = UserProfile(
                    id = "c", firstName = "Νίκος", lastName = "Παπαδόπουλος",
                    email = "n@example.gr", blockId = "block-02", disabled = true,
                ),
                onClick = {},
            )
        }
    }

    @Test fun residents_light() = render { Residents() }

    @Test fun residents_dark() = render(dark = true) { Residents() }

    /**
     * The switches an administrator sees, in Greek.
     *
     * Every one of them says what turning it off does, and the one that
     * publishes telephone numbers says considerably more than the rest — this
     * golden exists so that stays true, because the sentence is the only thing
     * standing between a neighbour with a passphrase and every number in the
     * village.
     */
    @Test
    fun features_greek() = render(
        config = DeviceConfig.PIXEL_5.copy(locale = "el"),
        name = "features",
    ) {
        FeatureSwitches(flags = FeatureFlags(), onChange = { _, _ -> })
    }

    @Test
    fun features_greek_large() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el"),
        name = "features_large",
    ) {
        FeatureSwitches(
            flags = FeatureFlags(mapOf("SMS_TO_ALL" to true, "WEATHER" to false)),
            onChange = { _, _ -> },
        )
    }

    @Test fun features_dark() = render(dark = true) {
        FeatureSwitches(flags = FeatureFlags(), onChange = { _, _ -> })
    }

    @Test
    fun residents_greek_max() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f, locale = "el"),
        name = "residents_max",
    ) { Residents() }
}
