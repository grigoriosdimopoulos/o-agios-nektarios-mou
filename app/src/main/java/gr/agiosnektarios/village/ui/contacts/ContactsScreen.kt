package gr.agiosnektarios.village.ui.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.ContactKind
import gr.agiosnektarios.village.core.model.NationalContacts
import gr.agiosnektarios.village.core.model.VillageContact
import gr.agiosnektarios.village.ui.components.ScreenHeader
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.raisedContainer
import gr.agiosnektarios.village.ui.theme.raisedOutline

/**
 * The numbers worth having when something is wrong.
 *
 * The national half of this list is drawn from constants and renders before
 * anything has loaded — no account, no network, no rule evaluation between a
 * resident and 199. That is the entire reason those numbers are not in
 * Firestore with the rest.
 *
 * Dialling goes through `ACTION_DIAL` rather than `ACTION_CALL`, so the number
 * lands in the dialler with the call not yet placed. It needs no permission,
 * and it means a mis-tap in a pocket cannot ring the fire service.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.contacts_copied)
    var pendingDelete by remember { mutableStateOf<VillageContact?>(null) }

    val dial: (String) -> Unit = { number ->
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.filter { !it.isWhitespace() }}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // A snackbar rather than nothing.
    //
    // Copying a number used to be entirely silent: the icon was tapped, the
    // clipboard changed, and the screen gave no sign that anything had
    // happened — so the natural response is to tap it again, and on a phone
    // that is how somebody ends up calling instead.
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        ContactsContent(
            state = state,
            onBack = onBack,
            onAdd = viewModel::startAdding,
            onEdit = viewModel::startEditing,
            onDelete = { pendingDelete = it },
            onCall = dial,
            onCopy = { number ->
                clipboard.setText(AnnotatedString(number))
                scope.launch { snackbar.showSnackbar("$copied · $number") }
            },
            modifier = Modifier.padding(padding),
        )
    }

    if (editor.open) {
        ContactEditorDialog(
            state = editor,
            onName = viewModel::onName,
            onNumber = viewModel::onNumber,
            onNote = viewModel::onNote,
            onKind = viewModel::onKind,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }

    pendingDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(contact.name) },
            text = { Text(stringResource(R.string.contacts_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(contact.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** The list itself, with nothing injected — so it can be rendered and looked at. */
@Composable
fun ContactsContent(
    state: ContactsUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (VillageContact) -> Unit,
    onDelete: (VillageContact) -> Unit,
    onCall: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            ScreenHeader(
                title = stringResource(R.string.contacts_title),
                modifier = Modifier.weight(1f),
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = onAdd) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.contacts_add),
                            )
                        }
                    }
                },
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = Space.page,
                end = Space.page,
                top = Space.gutter,
                bottom = BottomBarDefaults.contentPadding() + Space.page,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.gutter),
        ) {
            // Grouped by what you would be doing when you reach for it, not
            // alphabetically. In an emergency the order of a list is the whole
            // of its usability.
            val bundledByKind = NationalContacts.all.groupBy { it.kind }
            bundledByKind.forEach { (kind, entries) ->
                item(key = "head-${kind.name}") {
                    SectionLabel(
                        text = kind.sectionLabel(),
                        // The caption belongs to these numbers only. It used to
                        // be the screen's subtitle, which put "these work with
                        // no signal" over a section fed by Firestore — and the
                        // "no account" half was never true of any of it, since
                        // this screen lives inside the signed-in graph.
                        caption = if (kind == ContactKind.EMERGENCY) {
                            stringResource(R.string.contacts_subtitle)
                        } else {
                            null
                        },
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    ContactRow(
                        name = stringResource(entry.nameRes),
                        number = entry.number,
                        note = entry.noteRes?.let { stringResource(it) },
                        emphasised = kind == ContactKind.EMERGENCY,
                        onCall = { onCall(entry.number) },
                        onCopy = { onCopy(entry.number) },
                    )
                }
            }

            if (state.local.isEmpty()) {
                item(key = "head-local") { SectionLabel(stringResource(R.string.contacts_local)) }
                item(key = "empty-local") {
                    Text(
                        text = stringResource(R.string.contacts_local_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            // Grouped by section, which is what the editor's Section picker is
            // for. It had none: every local number landed under one heading
            // whatever was chosen, so the picker changed nothing a resident
            // could see.
            state.local.groupBy { it.contactKind }.forEach { (kind, entries) ->
                item(key = "head-local-${kind.name}") { SectionLabel(kind.sectionLabel()) }
                items(entries, key = { it.id }) { contact ->
                    ContactRow(
                        name = contact.name,
                        number = contact.number,
                        note = contact.note.ifBlank { null },
                        emphasised = false,
                        onCall = { onCall(contact.number) },
                        onCopy = { onCopy(contact.number) },
                        onEdit = if (state.canEdit) {
                            { onEdit(contact) }
                        } else {
                            null
                        },
                        onDelete = if (state.canEdit) {
                            { onDelete(contact) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactKind.sectionLabel(): String = stringResource(
    when (this) {
        ContactKind.EMERGENCY -> R.string.contacts_emergency
        ContactKind.UTILITY -> R.string.contacts_utility
        ContactKind.HEALTH -> R.string.contacts_health
        ContactKind.LOCAL -> R.string.contacts_local
    },
)

@Composable
private fun SectionLabel(text: String, caption: String? = null) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One number.
 *
 * The whole row dials. A small "call" button beside a large inert row is the
 * arrangement that makes someone tap three times in a hurry and reach nothing;
 * here the target is the row, and the secondary actions are the small ones.
 */
@Composable
private fun ContactRow(
    name: String,
    number: String,
    note: String?,
    emphasised: Boolean,
    onCall: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val callLabel = stringResource(R.string.contacts_call_of, name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(raisedContainer)
            // The hairline is what holds a white card's edge against the cream
            // page in the light theme; in the dark one the fill separates by
            // itself and raisedOutline is null. See Surfaces.kt.
            .then(
                raisedOutline?.let { Modifier.border(it, RoundedCornerShape(14.dp)) } ?: Modifier,
            )
            .clickable(onClickLabel = callLabel, onClick = onCall)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = number,
                // The number itself is the point of the row, so on the
                // emergency ones it is the largest thing in it — big enough to
                // read off a screen and dial from a landline if the phone is
                // the thing that is broken.
                style = if (emphasised) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // A visible telephone, because the row's only icon used to be a copy
        // glyph — so a card whose whole surface dials read, correctly enough,
        // as a card that copies. The row is still the target; this says what
        // tapping it does.
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.contacts_copy),
                modifier = Modifier.size(18.dp),
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.contacts_edit),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactEditorDialog(
    state: ContactEditorState,
    onName: (String) -> Unit,
    onNumber: (String) -> Unit,
    onNote: (String) -> Unit,
    onKind: (ContactKind) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (state.id == null) R.string.contacts_add else R.string.contacts_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VillageTextField(
                    value = state.name,
                    onValueChange = onName,
                    label = stringResource(R.string.contacts_name),
                )
                VillageTextField(
                    value = state.number,
                    onValueChange = onNumber,
                    label = stringResource(R.string.contacts_number),
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                    error = if (state.invalid) stringResource(R.string.contacts_invalid) else null,
                )
                VillageTextField(
                    value = state.note,
                    onValueChange = onNote,
                    label = stringResource(R.string.contacts_note),
                )
                Text(
                    text = stringResource(R.string.contacts_kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Flowing rather than a fixed row: four Greek section names in
                // a dialog at one-and-a-half times the text size do not fit on
                // one line, and a clipped pill is an option nobody can pick.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContactKind.entries.forEach { kind ->
                        KindPill(
                            label = kind.sectionLabel(),
                            selected = kind == state.kind,
                            onClick = { onKind(kind) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.saving) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun KindPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
