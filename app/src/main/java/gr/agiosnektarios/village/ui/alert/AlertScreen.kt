package gr.agiosnektarios.village.ui.alert

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.ui.theme.rememberHaptics
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.AlertKind
import gr.agiosnektarios.village.core.model.AlertSeverity
import gr.agiosnektarios.village.ui.components.ErrorBanner
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import gr.agiosnektarios.village.ui.theme.Motion
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.raisedContainer
import gr.agiosnektarios.village.ui.theme.raisedOutline
import gr.agiosnektarios.village.ui.theme.primaryInk
import androidx.compose.material3.minimumInteractiveComponentSize

/**
 * Telling the village something is wrong.
 *
 * Two states, not a wizard. The first is six large targets and nothing else,
 * because the person using this screen is frightened or in a hurry and every
 * field between them and the point is a field they have to read. The second is
 * the one they picked, a place, an optional note, and the actions.
 *
 * For the three emergencies the *first* thing on the second state is the
 * telephone. That ordering is the whole ethic of the screen: this app tells
 * neighbours, and neighbours are not a fire brigade. Saying so in the layout is
 * more honest than saying so in a paragraph nobody reads.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertScreen(
    onDone: () -> Unit,
    showSnackbar: (String) -> Unit,
    viewModel: AlertViewModel = hiltViewModel(),
) {
    val state by viewModel.raise.collectAsStateWithLifecycle()
    val live by viewModel.active.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()

    // Closes at once rather than holding a confirmation screen: somebody who
    // has just raised a fire alarm has somewhere else to be, and the map they
    // land on shows the banner they created. The snackbar is there because
    // "the screen went away" is not the same message as "it went out", and on
    // this screen of all screens the difference matters.
    val raised = stringResource(R.string.alert_raised)
    LaunchedEffect(state.raisedId) {
        if (state.raisedId != null) {
            onDone()
            showSnackbar(raised)
        }
    }

    val dial: (String) -> Unit = { number ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    AlertRaiseContent(
        state = state,
        numbersAvailable = live.residentNumbers.isNotEmpty(),
        onPick = viewModel::pick,
        onNote = viewModel::onNote,
        onPlace = viewModel::onPlace,
        onBack = viewModel::reset,
        onSend = viewModel::send,
        onDial = dial,
        onSms = { body -> sendSms(context, live.residentNumbers, body) },
        onClose = onDone,
    )
}

/**
 * The screen without its view model, so it can be rendered and looked at.
 *
 * The ordering inside it — telephone above "tell the village", for the three
 * kinds that cannot wait — is the ethic of the whole feature, and the sort of
 * thing that gets quietly rearranged by a later hand unless there is an image
 * of it in the repository.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertRaiseContent(
    state: RaiseAlertState,
    numbersAvailable: Boolean,
    onPick: (AlertKind) -> Unit,
    onNote: (String) -> Unit,
    onPlace: (AlertPlace) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onDial: (String) -> Unit,
    onSms: (String) -> Unit,
    onClose: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.alert_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = Space.page).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ErrorBanner(message = state.errorMessage)

            // Slower than anything else in the app moves.
            //
            // Speed is read before words are, and this is the one screen where
            // a mistake costs something. Everything else here settles in about
            // a fifth of a second; this takes its time arriving, so the hand
            // pauses with it. See Motion.deliberate.
            AnimatedContent(
                targetState = state.kind,
                transitionSpec = {
                    (fadeIn(Motion.deliberate()) + slideInVertically(Motion.offsetSpring()) { it / 8 })
                        .togetherWith(fadeOut(Motion.quick()))
                },
                label = "alertStage",
            ) { chosen ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (chosen == null) {
                Text(
                    text = stringResource(R.string.alert_pick),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AlertKind.entries.forEach { kind ->
                    // A tick, not a warning. Haptics.kt reserves the doubled
                    // buzz for "something is wrong, or about to be
                    // irreversible", and choosing between seven options —
                    // "power cut" and "something else" among them — is
                    // neither. The warning belongs on sending, and is there.
                    KindButton(kind = kind, onClick = { haptics.tick(); onPick(kind) })
                }
            } else {
                Chosen(
                    state = state,
                    onNote = onNote,
                    onPlace = onPlace,
                    onBack = onBack,
                    onSend = onSend,
                    onDial = onDial,
                    onSms = onSms,
                    numbersAvailable = numbersAvailable,
                )
            }
            }
            }
        }
    }
}

@Composable
private fun KindButton(kind: AlertKind, onClick: () -> Unit) {
    val emergency = kind.severity == AlertSeverity.EMERGENCY
    val container = if (emergency) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        raisedContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .then(
                if (emergency) {
                    Modifier
                } else {
                    raisedOutline?.let { Modifier.border(it, RoundedCornerShape(16.dp)) } ?: Modifier
                },
            )
            .clickable(onClick = onClick)
            // Deliberately tall. This is the one screen in the app where a
            // mis-tap costs something, and where the hands using it may be
            // unsteady.
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = kind.icon(),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (emergency) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.primaryInk
            },
        )
        Text(
            text = kind.label(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (emergency) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chosen(
    state: RaiseAlertState,
    onNote: (String) -> Unit,
    onPlace: (AlertPlace) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onDial: (String) -> Unit,
    onSms: (String) -> Unit,
    numbersAvailable: Boolean,
) {
    val kind = state.kind ?: return
    val haptics = rememberHaptics()
    val emergency = kind.severity == AlertSeverity.EMERGENCY
    val label = kind.label()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = kind.icon(),
            contentDescription = null,
            tint = if (emergency) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primaryInk
            },
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.action_back),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primaryInk,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(onClick = onBack)
                .padding(8.dp),
        )
    }

    // The telephone first, for the three that cannot wait.
    if (emergency) {
        Text(
            text = stringResource(R.string.alert_first),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        PrimaryButton(
            text = stringResource(
                when (kind) {
                    AlertKind.FIRE -> R.string.alert_call_199
                    AlertKind.MEDICAL -> R.string.alert_call_166
                    else -> R.string.alert_call_112
                },
            ),
            onClick = {
                onDial(
                    when (kind) {
                        AlertKind.FIRE -> "199"
                        AlertKind.MEDICAL -> "166"
                        else -> "112"
                    },
                )
            },
            icon = Icons.Filled.Call,
            container = MaterialTheme.colorScheme.error,
            content = MaterialTheme.colorScheme.onError,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    WhereRow(state = state, onPlace = onPlace)

    VillageTextField(
        value = state.note,
        onValueChange = onNote,
        label = stringResource(R.string.alert_note),
        singleLine = false,
        minLines = 2,
    )

    PrimaryButton(
        text = stringResource(R.string.alert_raise),
        onClick = { haptics.committed(); onSend() },
        enabled = state.canRaise,
        loading = state.raising,
        modifier = Modifier.fillMaxWidth(),
    )

    val body = stringResource(
        R.string.alert_sms_body,
        label,
        listOfNotNull(
            state.placeLabel.takeIf { it.isNotBlank() },
            state.note.takeIf { it.isNotBlank() },
            state.position?.let { "https://maps.google.com/?q=${it.lat},${it.lng}" },
        ).joinToString(" · "),
    )
    SecondaryButton(
        text = stringResource(R.string.alert_sms),
        onClick = { onSms(body) },
        enabled = numbersAvailable,
        icon = Icons.Filled.Sms,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!numbersAvailable) {
        Text(
            text = stringResource(R.string.alert_no_numbers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        // The full version ends "so if it cannot wait, send the text message
        // too". With no numbers on file there is no text message to send, and
        // telling someone in an emergency to do a thing the app has just
        // greyed out is worse than telling them nothing.
        text = stringResource(
            if (numbersAvailable) R.string.alert_reach else R.string.alert_reach_no_sms,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhereRow(state: RaiseAlertState, onPlace: (AlertPlace) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.alert_where),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlacePill(
                label = stringResource(R.string.alert_where_me),
                selected = state.place == AlertPlace.HERE,
                onClick = { onPlace(AlertPlace.HERE) },
            )
            PlacePill(
                label = stringResource(R.string.alert_where_home),
                selected = state.place == AlertPlace.HOME,
                onClick = { onPlace(AlertPlace.HOME) },
            )
        }
        Text(
            text = when {
                state.locating -> stringResource(R.string.alert_where_locating)
                state.placeLabel.isNotBlank() -> state.placeLabel
                // Locale.US, not the device's. Under el-GR this printed
                // "38,16472, 23,29216" — four comma-separated numbers — and
                // when OpenStreetMap has no name for the way you are standing
                // on, which is the normal case here, this line IS the location
                // somebody reads down the telephone to 166.
                state.position != null -> String.format(
                    java.util.Locale.US,
                    "%.5f, %.5f",
                    state.position.lat,
                    state.position.lng,
                )
                // "My house" with no house pinned is not the same failure as
                // the phone not knowing where it is, and being told the
                // generic one sends people looking for a signal problem
                // instead of a setting.
                state.place == AlertPlace.HOME -> stringResource(R.string.alert_where_no_home)
                else -> stringResource(R.string.alert_where_unknown)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PlacePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .minimumInteractiveComponentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Hands the message to the phone's own SMS app, addressed to everyone.
 *
 * `ACTION_SENDTO` with an `smsto:` list opens the composer with the recipients
 * and the text filled in and sends nothing by itself — the person still presses
 * send. That is the right division: this app has no business sending messages
 * from somebody's number without them seeing what goes out, and SMS is the only
 * channel here that reaches a phone that is not running the app.
 */
private fun sendSms(context: android.content.Context, numbers: List<String>, body: String) {
    if (numbers.isEmpty()) return
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";")))
        .putExtra("sms_body", body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
internal fun AlertKind.label(): String = stringResource(labelRes)

internal fun AlertKind.icon(): ImageVector = when (this) {
    AlertKind.FIRE -> Icons.Filled.LocalFireDepartment
    AlertKind.MEDICAL -> Icons.Filled.MedicalServices
    AlertKind.MISSING -> Icons.Filled.PersonSearch
    AlertKind.POWER -> Icons.Filled.PowerOff
    AlertKind.WATER -> Icons.Filled.WaterDrop
    AlertKind.ROAD -> Icons.Filled.Construction
    AlertKind.OTHER -> Icons.Filled.Campaign
}
