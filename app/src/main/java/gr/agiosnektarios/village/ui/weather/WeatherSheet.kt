package gr.agiosnektarios.village.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.DayForecast
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import gr.agiosnektarios.village.core.weather.FireRisk
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.raisedContainer
import java.util.Date

/**
 * Everything the phone knows about the weather here, on one pane.
 *
 * Ordered by what a resident actually came for. The temperature and the sky are
 * at the top because that is the glance; the fire risk is next because on this
 * mountain between May and October it is the reason the rest of it matters; the
 * plain facts follow; the forecast is last, because a four-day outlook is the
 * part you scroll to rather than the part you open for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSheet(
    state: WeatherUiState,
    onDismiss: () -> Unit,
    onCallFireService: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenOfficialMap: () -> Unit,
    onToggleMapWeather: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        WeatherSheetContent(
            state = state,
            onCallFireService = onCallFireService,
            onOpenContacts = onOpenContacts,
            onOpenOfficialMap = onOpenOfficialMap,
            onToggleMapWeather = onToggleMapWeather,
            onRefresh = onRefresh,
        )
    }
}

/**
 * The pane's contents, without the pane.
 *
 * Split out for one reason: a `ModalBottomSheet` renders into its own window
 * and a snapshot test cannot see inside it. Everything this session added is
 * meant to be looked at before it ships, and a screen that cannot be rendered
 * is a screen that will not be.
 */
@Composable
fun WeatherSheetContent(
    state: WeatherUiState,
    onCallFireService: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenOfficialMap: () -> Unit,
    onToggleMapWeather: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.page)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val snapshot = state.snapshot
        if (snapshot == null) {
            Unavailable(loading = state.loading, onRefresh = onRefresh)
            return@Column
        }

        Headline(snapshot)
        state.fire?.let { fire ->
            FireCard(
                fire = fire,
                // Dated whenever it is not today's. A phone that has been out
                // of signal since Sunday still has Sunday's numbers, and
                // showing them undated would make Sunday's danger look like
                // this morning's.
                forDay = if (state.fireIsToday) null else longDate(snapshot.observedAt),
                onCallFireService = onCallFireService,
                onOpenContacts = onOpenContacts,
                onOpenOfficialMap = onOpenOfficialMap,
            )
        }
        Facts(snapshot)
        if (snapshot.days.size > 1) Forecast(snapshot.days)
        Footer(
            state = state,
            onToggleMapWeather = onToggleMapWeather,
            onRefresh = onRefresh,
        )
    }
}

@Composable
private fun Headline(snapshot: WeatherSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DateLine(snapshot.observedAt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = snapshot.temperature.asDegrees(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = snapshot.condition.icon(snapshot.isDay),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = snapshot.condition.label() + " · " +
                stringResource(R.string.weather_feels_like, snapshot.feelsLike.asDegrees()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The fire reading, with its reasons and its limits on the same card.
 *
 * A coloured level on its own is an assertion. What makes this usable is that
 * every line under it is a fact the resident can check against their own
 * window — the wind is up, it has not rained in three weeks — and that the card
 * says plainly, every time, that the binding answer is the state's map and not
 * this one.
 */
@Composable
private fun FireCard(
    fire: FireRisk.Assessment,
    forDay: String?,
    onCallFireService: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenOfficialMap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(raisedContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = fire.level.color(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.fire_risk_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            FireLevelPill(level = fire.level)
        }

        if (forDay != null) {
            Text(
                text = stringResource(R.string.weather_not_today, forDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = if (fire.at != null) {
                stringResource(
                    R.string.fire_risk_at,
                    clock(fire.at),
                    fire.temperature.asDegrees(),
                    fire.humidity,
                )
            } else {
                stringResource(
                    R.string.fire_risk_now,
                    fire.temperature.asDegrees(),
                    fire.humidity,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The reasons, each one checkable from a window.
        val gust = stringResource(R.string.weather_beaufort, fire.gustBeaufort)
        val sustained = stringResource(R.string.weather_beaufort, fire.windBeaufort)
        val reasons = buildList {
            if (fire.wetGround) add(stringResource(R.string.fire_because_wet))
            if (fire.windy) add(stringResource(R.string.fire_because_wind, sustained))
            if (fire.gusty) add(stringResource(R.string.fire_because_gusts, gust))
            if (fire.dryHere) {
                add(stringResource(R.string.fire_because_dry, fire.dryDays ?: 0))
            }
        }
        reasons.forEach { reason ->
            Text(
                text = "· $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (fire.burningForbidden) {
            Text(
                text = stringResource(R.string.fire_burning_forbidden),
                style = MaterialTheme.typography.titleSmall,
                color = fire.level.color(),
            )
        }
        Text(
            text = if (fire.inFireSeason) {
                stringResource(R.string.fire_season)
            } else {
                stringResource(R.string.fire_out_of_season)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.fire_official),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Stacked rather than side by side. SecondaryButton is a fixed 54dp
        // tall, and two of these labels sharing a phone's width in Greek at
        // one-and-a-half times the text size is a pair of clipped buttons.
        SecondaryButton(
            text = stringResource(R.string.fire_official_link),
            onClick = onOpenOfficialMap,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        )
        // Dials, rather than opening a list somebody then has to read. From the
        // map this is the second tap; routing it through the contacts screen
        // made the fire service five taps away from the screen the app opens
        // on, which is the wrong number for the one call that cannot wait.
        SecondaryButton(
            text = stringResource(R.string.fire_call),
            onClick = onCallFireService,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Call,
        )
        TextButton(
            onClick = onOpenContacts,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.contacts_title))
        }
    }
}

@Composable
private fun Facts(snapshot: WeatherSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Intrinsic height, so a wind tile that runs to two lines does not
        // leave the humidity beside it floating half a tile short. Two tiles
        // in a row are one object; they have to share an edge.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WindTile(snapshot, modifier = Modifier.weight(1f).fillMaxHeight())
            FactTile(
                label = stringResource(R.string.weather_humidity),
                value = "${snapshot.humidity}%",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FactTile(
                label = stringResource(R.string.weather_rain_today),
                // What has fallen, not the day's forecast total — the tile
                // says "rain today", and a figure that counts tonight's storm
                // at three in the afternoon is answering a different question.
                value = stringResource(
                    R.string.weather_millimetres,
                    snapshot.rainSoFarMm.asMillimetres(),
                ),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            FactTile(
                label = stringResource(R.string.weather_dry_spell),
                // "31+" when the run reached the end of the history that was
                // fetched. A six-week drought read through a one-month window
                // is not a thirty-one-day drought, and the tile should not
                // claim the smaller number.
                value = snapshot.dryDays?.let {
                    val days = pluralStringResource(R.plurals.weather_dry_days, it, it)
                    if (snapshot.dryDaysAtLeast) "$days+" else days
                } ?: "—",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FactTile(
                label = stringResource(R.string.weather_sunrise),
                value = snapshot.sunrise?.let { clock(it) } ?: "—",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            FactTile(
                label = stringResource(R.string.weather_sunset),
                value = snapshot.sunset?.let { clock(it) } ?: "—",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        // Only when there is any. A "0 cm" tile every day of the year would
        // make the one week it matters unremarkable.
        if (snapshot.snowDepthCm > 0.0) {
            FactTile(
                label = stringResource(R.string.weather_snow),
                value = stringResource(
                    R.string.weather_centimetres,
                    snapshot.snowDepthCm.asMillimetres(),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Speed, direction and gusts in one tile.
 *
 * The gust figure used to sit under the humidity, which is where it landed
 * when the tiles were laid out and where it stayed until somebody looked at a
 * render: "Humidity 64%, gusting 13 km/h" is two unrelated facts wearing one
 * label.
 */
@Composable
private fun WindTile(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    val calm = snapshot.wind.beaufort == 0
    val detail = buildList {
        if (!calm) add(stringResource(R.string.wind_from, snapshot.wind.sectorLabel()))
        if (snapshot.wind.gustBeaufort > snapshot.wind.beaufort) {
            add(
                stringResource(
                    R.string.weather_gusts,
                    stringResource(R.string.weather_beaufort, snapshot.wind.gustBeaufort),
                ),
            )
        }
    }
    FactTile(
        label = stringResource(R.string.weather_wind),
        value = if (calm) {
            stringResource(R.string.wind_calm)
        } else {
            stringResource(R.string.weather_beaufort, snapshot.wind.beaufort)
        },
        detail = detail.joinToString(" · ").ifBlank { null },
        modifier = modifier,
    )
}

@Composable
private fun Forecast(days: List<DayForecast>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.weather_forecast),
            style = MaterialTheme.typography.titleSmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(days, key = { it.date }) { day ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(raisedContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = shortDay(day.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Icon(
                        imageVector = day.condition.icon(),
                        contentDescription = day.condition.label(),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = day.high.asDegrees(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = day.low.asDegrees(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer(
    state: WeatherUiState,
    onToggleMapWeather: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val snapshot = state.snapshot ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleMapWeather(!state.animateOnMap) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.weather_animate),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = state.animateOnMap,
                onCheckedChange = onToggleMapWeather,
            )
        }

        if (state.stale) {
            Text(
                text = stringResource(R.string.weather_stale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.weather_updated,
                    gr.agiosnektarios.village.ui.components.relativeTime(Date(snapshot.fetchedAt)),
                ) + " · " + stringResource(R.string.weather_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.weather_refresh),
                onClick = onRefresh,
                enabled = !state.loading,
            )
        }
    }
}

@Composable
private fun Unavailable(loading: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.weather_unavailable),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.weather_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        SecondaryButton(
            text = stringResource(R.string.weather_refresh),
            onClick = onRefresh,
            enabled = !loading,
        )
    }
}
