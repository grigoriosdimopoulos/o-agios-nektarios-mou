package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.GlassSurface
import gr.agiosnektarios.village.ui.components.IssueCard
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.ScreenHeader
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.components.VoteBar
import gr.agiosnektarios.village.ui.map.ControlDivider
import gr.agiosnektarios.village.ui.map.MapControl
import gr.agiosnektarios.village.ui.map.StreetNamingHint
import gr.agiosnektarios.village.ui.theme.VillageTheme
import java.util.Date
import org.junit.Rule
import org.junit.Test

/**
 * Renders the app's own surfaces to PNG so they can be *looked at*.
 *
 * This build environment has no KVM, so no emulator, so for several rounds the
 * UI was being changed without anyone — including whoever wrote the change —
 * ever seeing the result. Paparazzi renders Compose through layoutlib on a
 * plain JVM, which turns "this should look better" into something checkable.
 *
 * These are not assertions about correctness. They are eyes. The first pass
 * with them found three things that had shipped unseen: chrome tinted the
 * exact colour of the page behind it, a hairline drawn at zero height, and a
 * spinner painted in a disabled grey on a disabled grey.
 *
 * One caveat worth knowing before reading a render: a snapshot is frame zero.
 * Anything driven by an infinite animation — the indeterminate spinner, the
 * empty-state breathing — is caught at t=0, where a progress arc has no
 * length yet. Judge colour and layout from these; not motion.
 */
class LookTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false,
    )

    private fun render(dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                // Surface, not a bare Box: Surface is what publishes the
                // matching content colour, and without it every Text here
                // defaulted to black — which is why the dark render's labels
                // were invisible against the dark page.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    @Test fun buttons_light() = render { ButtonGallery() }

    @Test fun buttons_dark() = render(dark = true) { ButtonGallery() }

    @Test fun glass_light() = render { GlassGallery() }

    @Test fun glass_dark() = render(dark = true) { GlassGallery() }

    // The settled stage, not the timed wrapper: a snapshot of SplashScreen
    // catches frame zero, where every alpha is still 0 and the render is blank.
    // A fixed version string, not the real one. The build's version is derived
    // from the git commit count and SHA, so rendering it here produced a golden
    // that could never match the next commit — CI's snapshot check would fail
    // on every push, and the committed image had already drifted two builds
    // behind before anyone compared them.
    @Test fun splash_light() = render { SplashContent(stage = 3, version = FIXTURE_VERSION) }

    @Test fun splash_dark() = render(dark = true) {
        SplashContent(stage = 3, version = FIXTURE_VERSION)
    }

    /** The screen residents spend the most time on. */
    @Test fun feed_light() = render { Feed() }

    @Test fun feed_dark() = render(dark = true) { Feed() }

    @Test fun form_light() = render { FormGallery() }

    @Test fun form_dark() = render(dark = true) { FormGallery() }

    @Test fun map_chrome_light() = render { MapChrome() }

    @Test fun map_chrome_dark() = render(dark = true) { MapChrome() }

    @Test fun empty_light() = render { EmptyGallery() }

    @Test fun empty_dark() = render(dark = true) { EmptyGallery() }
}

@Composable
private fun ButtonGallery() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Primary", style = MaterialTheme.typography.titleMedium)
        PrimaryButton(text = "Submit report", onClick = {}, modifier = Modifier.fillMaxWidth())
        PrimaryButton(
            text = "Loading",
            onClick = {},
            loading = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = "Disabled",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Secondary", style = MaterialTheme.typography.titleMedium)
        SecondaryButton(text = "Cancel", onClick = {}, modifier = Modifier.fillMaxWidth())
        Text("Votes", style = MaterialTheme.typography.titleMedium)
        VoteBar(upvotes = 14, downvotes = 2, myVote = 1, onVote = {})
    }
}

@Composable
private fun GlassGallery() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(12) {
                Text(
                    "Content scrolling beneath the chrome",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        ) {
            Text(
                "Glass bar",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun Feed() {
    Column {
        ScreenHeader(title = "Αναφορές", subtitle = "4 ανοιχτές στο χωριό")
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sampleIssues.forEach { issue ->
                IssueCard(issue = issue, onClick = {})
            }
        }
    }
}

@Composable
private fun FormGallery() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VillageTextField(value = "", onValueChange = {}, label = "Τίτλος", placeholder = "Τι συμβαίνει;")
        VillageTextField(value = "Σπασμένος στύλος στη γωνία", onValueChange = {}, label = "Τίτλος")
        VillageTextField(
            value = "bad@",
            onValueChange = {},
            label = "Email",
            error = "Δεν είναι έγκυρη διεύθυνση",
        )
        VillageTextField(
            value = "Ο στύλος γέρνει πάνω από τον δρόμο και το φως δεν ανάβει εδώ και δύο βδομάδες.",
            onValueChange = {},
            label = "Περιγραφή",
            singleLine = false,
            minLines = 4,
        )
    }
}

@Composable
private fun EmptyGallery() {
    EmptyState(
        emoji = "🗺️",
        title = "Καμία αναφορά ακόμη",
        subtitle = "Όταν κάποιος αναφέρει κάτι στο χωριό, θα εμφανιστεί εδώ.",
    )
}

internal fun at(minutesAgo: Long) = Date(1_755_000_000_000L - minutesAgo * 60_000L)

internal val sampleIssues = listOf(
    Issue(
        id = "1",
        title = "Σπασμένος στύλος φωτισμού στην κεντρική",
        description = "Ο στύλος γέρνει πάνω από τον δρόμο και το φως δεν ανάβει εδώ και δύο βδομάδες.",
        categoryId = IssueCategory.LIGHTING.id,
        statusId = IssueStatus.OPEN.id,
        authorName = "Γιώργος Π.",
        upvotes = 14,
        commentCount = 5,
        createdAt = at(40),
    ),
    Issue(
        id = "2",
        title = "Πεσμένο δέντρο κλείνει τον δρόμο",
        description = "Μετά τον αέρα της Κυριακής. Δεν περνάει αυτοκίνητο.",
        categoryId = IssueCategory.FALLEN_TREE.id,
        statusId = IssueStatus.IN_PROGRESS.id,
        authorName = "Μαρία Κ.",
        upvotes = 31,
        commentCount = 12,
        createdAt = at(60 * 20),
    ),
    Issue(
        id = "3",
        title = "Σκουπίδια έξω από τους κάδους",
        description = "",
        categoryId = IssueCategory.GARBAGE.id,
        statusId = IssueStatus.RESOLVED.id,
        authorName = "Δημήτρης Α.",
        upvotes = 8,
        commentCount = 2,
        createdAt = at(60 * 24 * 3),
    ),
    Issue(
        id = "4",
        title = "Ξερά χόρτα δίπλα στο γήπεδο — κίνδυνος πυρκαγιάς",
        description = "Πολύ ψηλά και ξερά, ακριβώς πλάι στα σπίτια.",
        categoryId = IssueCategory.FIRE_RISK.id,
        statusId = IssueStatus.ACKNOWLEDGED.id,
        authorName = "Ελένη Β.",
        upvotes = 47,
        commentCount = 9,
        createdAt = at(60 * 24 * 9),
    ),
)

/**
 * The map's own chrome, rendered off the map.
 *
 * These float over a live MapView, which no snapshot can draw, so they were
 * never actually looked at — the glass cluster and the naming hint are the two
 * things a resident sees first on the screen the app opens with.
 */
@Composable
internal fun MapChrome() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.End,
    ) {
        GlassSurface(
            modifier = Modifier.width(48.dp),
            shape = RoundedCornerShape(16.dp),
            alpha = 0.92f,
            edge = false,
        ) {
            Column {
                MapControl(
                    icon = Icons.Filled.FilterList,
                    contentDescription = "filter",
                    active = true,
                    onClick = {},
                )
                ControlDivider()
                MapControl(
                    icon = Icons.Filled.Map,
                    contentDescription = "basemap",
                    active = false,
                    onClick = {},
                )
                ControlDivider()
                MapControl(
                    icon = Icons.Filled.Layers,
                    contentDescription = "blocks",
                    active = false,
                    onClick = {},
                )
            }
        }
        StreetNamingHint(onDismiss = {}, modifier = Modifier.width(280.dp))
    }
}

/** Shape-accurate and never changing, which is the whole point. */
private const val FIXTURE_VERSION = "1.0.0 (0000000)"
