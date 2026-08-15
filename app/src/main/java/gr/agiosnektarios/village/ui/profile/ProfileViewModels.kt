package gr.agiosnektarios.village.ui.profile

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.VillageBlock
import gr.agiosnektarios.village.core.validation.Validators
import gr.agiosnektarios.village.data.auth.AuthRepository
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.media.ImageCodec
import gr.agiosnektarios.village.data.media.ImageSpec
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.user.UserRepository
import gr.agiosnektarios.village.data.village.VillageBlockRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val myIssues: List<Issue> = emptyList(),
    val blockNameEl: String = "",
    val blockNameEn: String = "",
    val loading: Boolean = true,
) {
    /** Counted from the live list rather than the stored counter, which lags. */
    val resolvedCount: Int get() = myIssues.count { it.status.isTerminal }
    val upvotesReceived: Int get() = myIssues.sumOf { it.upvotes }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    issueRepository: IssueRepository,
    private val blockRepository: VillageBlockRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val blocks = MutableStateFlow<List<VillageBlock>>(emptyList())

    init {
        viewModelScope.launch { blocks.value = blockRepository.blocks() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileUiState> = sessionRepository.profile
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(ProfileUiState(loading = false))
            } else {
                combine(
                    issueRepository.observeIssuesByAuthor(profile.id),
                    blocks,
                ) { issues, allBlocks ->
                    val block = allBlocks.firstOrNull { it.id == profile.blockId }
                    ProfileUiState(
                        profile = profile,
                        myIssues = issues,
                        blockNameEl = block?.nameEl.orEmpty(),
                        blockNameEn = block?.nameEn.orEmpty(),
                        loading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun signOut() = authRepository.signOut()
}

data class EditProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val address: String = "",
    val blockId: String = "",
    val avatar: ByteArray? = null,
    val blocks: List<VillageBlock> = emptyList(),
    @StringRes val firstNameError: Int? = null,
    @StringRes val lastNameError: Int? = null,
    @StringRes val phoneError: Int? = null,
    @StringRes val addressError: Int? = null,
    val uploadingPhoto: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val imageCodec: ImageCodec,
    private val authRepository: AuthRepository,
    blockRepository: VillageBlockRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val blocks = blockRepository.blocks()
            val profile = sessionRepository.currentProfile
            _uiState.update {
                it.copy(
                    blocks = blocks,
                    firstName = profile?.firstName.orEmpty(),
                    lastName = profile?.lastName.orEmpty(),
                    phone = profile?.phone.orEmpty(),
                    address = profile?.address.orEmpty(),
                    blockId = profile?.blockId.orEmpty(),
                    avatar = profile?.avatarBytes,
                )
            }
        }
    }

    fun onFirstName(value: String) = _uiState.update { it.copy(firstName = value, firstNameError = null) }
    fun onLastName(value: String) = _uiState.update { it.copy(lastName = value, lastNameError = null) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value, phoneError = null) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value, addressError = null) }
    fun onBlock(blockId: String) = _uiState.update { it.copy(blockId = blockId) }

    fun changePhoto(uri: Uri) {
        val userId = sessionRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingPhoto = true) }
            val result = imageCodec.encode(uri, ImageSpec.AVATAR)
            result.onSuccess { bytes ->
                userRepository.updateAvatar(userId, bytes)
                _uiState.update { it.copy(avatar = bytes) }
            }
            _uiState.update {
                it.copy(
                    uploadingPhoto = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun save() {
        val userId = sessionRepository.currentUserId ?: return
        val state = _uiState.value
        val validated = state.copy(
            firstNameError = Validators.required(state.firstName),
            lastNameError = Validators.required(state.lastName),
            phoneError = Validators.phone(state.phone),
            addressError = Validators.required(state.address),
        )
        _uiState.value = validated
        if (listOf(
                validated.firstNameError,
                validated.lastNameError,
                validated.phoneError,
                validated.addressError,
            ).any { it != null }
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorMessage = null) }
            val result = userRepository.updateProfile(
                userId = userId,
                firstName = state.firstName,
                lastName = state.lastName,
                phone = state.phone,
                address = state.address,
                blockId = state.blockId,
            )
            if (result.isSuccess) {
                authRepository.updateDisplayName(
                    "${state.firstName.trim()} ${state.lastName.trim()}",
                )
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    saved = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
