package gr.agiosnektarios.village.ui.announcements

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.components.BytesImage
import gr.agiosnektarios.village.ui.components.InlineSpinner
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementComposeScreen(
    onBack: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    viewModel: AnnouncementComposeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val publishedMessage = stringResource(R.string.announcement_published)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(viewModel::setImage) },
    )

    LaunchedEffect(state.saved) {
        if (state.saved) {
            showSnackbar(publishedMessage)
            onBack()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.announcement_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VillageTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = stringResource(R.string.issue_title_label),
            )
            VillageTextField(
                value = state.body,
                onValueChange = viewModel::onBody,
                label = stringResource(R.string.announcement_body_label),
                singleLine = false,
                minLines = 6,
                imeAction = ImeAction.Default,
            )

            if (state.image != null) {
                Box {
                    BytesImage(
                        bytes = state.image,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                    )
                    TextButton(
                        onClick = viewModel::clearImage,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            imagePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.uploading) {
                        InlineSpinner()
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.issue_add_photo),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.announcement_pin),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = state.pinned, onCheckedChange = viewModel::onPinned)
            }

            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
