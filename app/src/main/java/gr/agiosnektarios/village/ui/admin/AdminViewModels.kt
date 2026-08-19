package gr.agiosnektarios.village.ui.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.UserMessages
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.admin.AdminRepository
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.user.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import gr.agiosnektarios.village.core.model.Feature
import gr.agiosnektarios.village.core.model.FeatureFlags
import gr.agiosnektarios.village.data.settings.FeatureRepository

data class AdminUiState(
    val query: String = "",
    val residents: List<UserProfile> = emptyList(),
    val issues: List<Issue> = emptyList(),
    /** False for anyone who reaches this screen without the role. */
    val authorized: Boolean = false,
    val flags: FeatureFlags = FeatureFlags(),
    val loading: Boolean = true,
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val featureRepository: FeatureRepository,
    issueRepository: IssueRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AdminUiState> = combine(
        query.debounce(220).flatMapLatest { text ->
            userRepository.searchResidents(text, limit = 60).catch { emit(emptyList()) }
        },
        issueRepository.observeIssues(limit = 200),
        sessionRepository.profile,
        query,
        featureRepository.flags,
    ) { residents, issues, profile, currentQuery, flags ->
        AdminUiState(
            query = currentQuery,
            residents = residents,
            issues = issues,
            authorized = profile?.isAdmin == true,
            flags = flags,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setFeature(feature: Feature, on: Boolean) {
        viewModelScope.launch { featureRepository.set(feature, on) }
    }
}

data class AdminUserUiState(
    val profile: UserProfile? = null,
    val issues: List<Issue> = emptyList(),
    val working: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val deleted: Boolean = false,
)

/**
 * Every action here goes through a Cloud Function; nothing on this screen
 * writes to Firestore directly, because "delete this resident" means deleting
 * an auth account and cascading their content, which no client can do.
 */
@HiltViewModel
class AdminUserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    userRepository: UserRepository,
    issueRepository: IssueRepository,
    private val adminRepository: AdminRepository,
    private val messages: UserMessages,
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("userId").orEmpty()
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<AdminUserUiState> = combine(
        userRepository.observeProfile(userId).catch { emit(null) },
        issueRepository.observeIssuesByAuthor(userId),
        local,
    ) { profile, issues, localState ->
        AdminUserUiState(
            profile = profile,
            issues = issues,
            working = localState.working,
            message = localState.message,
            errorMessage = localState.errorMessage,
            deleted = localState.deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUserUiState())

    fun setRole(role: Role) = perform { adminRepository.setRole(userId, role) }

    fun setDisabled(disabled: Boolean) = perform { adminRepository.setDisabled(userId, disabled) }

    fun rename(firstName: String, lastName: String) =
        perform { adminRepository.renameUser(userId, firstName, lastName) }

    /**
     * [successMessage] is passed in already localized by the screen.
     *
     * The address comes from the loaded profile because, without a server,
     * this is the ordinary "forgot password" call and it needs an email
     * rather than a uid.
     */
    fun sendPasswordReset(successMessage: String) = perform(successMessage) {
        adminRepository.sendPasswordReset(uiState.value.profile?.email.orEmpty())
    }

    fun deleteUser() {
        viewModelScope.launch {
            local.update { it.copy(working = true, errorMessage = null) }
            val result = adminRepository.deleteUser(userId)
            local.update {
                it.copy(
                    working = false,
                    deleted = result.isSuccess,
                    errorMessage = messages.of(result.exceptionOrNull()),
                )
            }
        }
    }

    private fun perform(
        successMessage: String? = null,
        block: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            local.update { it.copy(working = true, errorMessage = null, message = null) }
            val result = block()
            local.update {
                it.copy(
                    working = false,
                    message = successMessage?.takeIf { _ -> result.isSuccess },
                    errorMessage = messages.of(result.exceptionOrNull()),
                )
            }
        }
    }

    fun consumeMessages() = local.update { it.copy(message = null, errorMessage = null) }

    private data class LocalState(
        val working: Boolean = false,
        val message: String? = null,
        val errorMessage: String? = null,
        val deleted: Boolean = false,
    )
}
