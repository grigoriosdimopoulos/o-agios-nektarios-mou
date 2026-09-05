package gr.agiosnektarios.village.ui.auth

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.validation.Validators
import gr.agiosnektarios.village.data.auth.AuthRepository
import gr.agiosnektarios.village.data.auth.GoogleCredentialClient
import gr.agiosnektarios.village.data.auth.GoogleSignInCancelled
import gr.agiosnektarios.village.data.auth.SignInDiagnostics
import gr.agiosnektarios.village.data.auth.GoogleSignInUnavailable
import gr.agiosnektarios.village.data.user.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ sign in

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val loading: Boolean = false,
    val googleLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Preferred over [errorMessage] when set, so the text can be localized. */
    @StringRes val errorRes: Int? = null,
    /**
     * What this build actually is, shown under a failed Google sign-in.
     *
     * The failure is indistinguishable, from inside the app, between "no
     * Google account on this phone" and "this certificate is not registered
     * against the OAuth client" — and the second one is invisible to the person
     * holding the phone. Printing the package name, the signing fingerprint and
     * the exception type turns a two-round guessing game into one screenshot.
     */
    val googleDiagnostics: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleCredentialClient: GoogleCredentialClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun signIn() {
        val state = _uiState.value
        val emailError = Validators.email(state.email)
        // Length is not re-checked on sign-in: an existing account may predate a
        // stricter rule, and the server is the authority anyway.
        val passwordError = Validators.required(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val result = authRepository.signIn(state.email, state.password)
            _uiState.update {
                it.copy(
                    loading = false,
                    errorMessage = result.exceptionOrNull()?.let(::friendlyMessage),
                )
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(googleLoading = true, errorMessage = null) }
            val tokenResult = googleCredentialClient.requestIdToken(activityContext)
            val error = tokenResult.fold(
                onSuccess = { token ->
                    authRepository.signInWithGoogle(token).exceptionOrNull()
                },
                onFailure = { it },
            )
            _uiState.update {
                it.copy(
                    googleLoading = false,
                    // A dismissed sheet is a deliberate choice, not a failure.
                    errorRes = if (error is GoogleSignInUnavailable) {
                        R.string.error_google_unavailable
                    } else {
                        null
                    },
                    errorMessage = error
                        ?.takeUnless { e -> e is GoogleSignInCancelled || e is GoogleSignInUnavailable }
                        ?.let(::friendlyMessage),
                    // Set for *every* Google failure, cancellation included.
                    //
                    // A dismissal stays silent in the red line above, which is
                    // right — but it is also the shape a refused certificate
                    // arrives in, and while that is unresolved the small grey
                    // identity line has to appear either way or there is
                    // nothing to look at. It says what this build is; it does
                    // not accuse anyone of an error.
                    googleDiagnostics = error?.let { failure ->
                        SignInDiagnostics.report(
                            context = activityContext,
                            failure = failure,
                            elapsedMs = (failure as? GoogleSignInUnavailable)?.elapsedMs
                                ?: (failure as? GoogleSignInCancelled)?.elapsedMs,
                        )
                    },
                )
            }
        }
    }

    fun consumeError() =
        _uiState.update { it.copy(errorMessage = null, errorRes = null, googleDiagnostics = null) }
}

// ------------------------------------------------------------------ sign up

data class SignUpUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val acceptedTerms: Boolean = false,
    @StringRes val firstNameError: Int? = null,
    @StringRes val lastNameError: Int? = null,
    @StringRes val emailError: Int? = null,
    @StringRes val phoneError: Int? = null,
    @StringRes val addressError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val confirmationError: Int? = null,
    @StringRes val termsError: Int? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onFirstName(value: String) = _uiState.update { it.copy(firstName = value, firstNameError = null) }
    fun onLastName(value: String) = _uiState.update { it.copy(lastName = value, lastNameError = null) }
    fun onEmail(value: String) = _uiState.update { it.copy(email = value, emailError = null) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value, phoneError = null) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value, addressError = null) }
    fun onPassword(value: String) = _uiState.update { it.copy(password = value, passwordError = null) }
    fun onPasswordConfirmation(value: String) =
        _uiState.update { it.copy(passwordConfirmation = value, confirmationError = null) }

    fun onAcceptTerms(accepted: Boolean) =
        _uiState.update { it.copy(acceptedTerms = accepted, termsError = null) }

    fun submit() {
        val state = _uiState.value
        val validated = state.copy(
            firstNameError = Validators.required(state.firstName),
            lastNameError = Validators.required(state.lastName),
            emailError = Validators.email(state.email),
            phoneError = Validators.phone(state.phone),
            addressError = Validators.required(state.address),
            passwordError = Validators.password(state.password),
            confirmationError = Validators.passwordConfirmation(
                state.password,
                state.passwordConfirmation,
            ),
            termsError = if (state.acceptedTerms) null else R.string.error_terms_required,
        )
        _uiState.value = validated
        if (validated.hasErrors) return

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val signUp = authRepository.signUp(
                email = state.email,
                password = state.password,
                displayName = "${state.firstName.trim()} ${state.lastName.trim()}",
            )
            val error = signUp.fold(
                onSuccess = { user ->
                    // The profile document is what SessionState waits for, so a
                    // failure here must surface rather than leaving a half-made
                    // account sitting on the "complete your profile" screen.
                    userRepository.createProfile(
                        userId = user.uid,
                        firstName = state.firstName,
                        lastName = state.lastName,
                        email = state.email,
                        phone = state.phone,
                        address = state.address,
                    ).exceptionOrNull()
                },
                onFailure = { it },
            )
            _uiState.update {
                it.copy(loading = false, errorMessage = error?.let(::friendlyMessage))
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}

private val SignUpUiState.hasErrors: Boolean
    get() = listOf(
        firstNameError,
        lastNameError,
        emailError,
        phoneError,
        addressError,
        passwordError,
        confirmationError,
        termsError,
    ).any { it != null }

// ------------------------------------------------------- forgot password

data class ForgotPasswordUiState(
    val email: String = "",
    @StringRes val emailError: Int? = null,
    val loading: Boolean = false,
    val sent: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmail(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }

    fun submit() {
        val error = Validators.email(_uiState.value.email)
        if (error != null) {
            _uiState.update { it.copy(emailError = error) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val result = authRepository.sendPasswordReset(_uiState.value.email)
            _uiState.update {
                it.copy(
                    loading = false,
                    // Reported as sent regardless: telling an anonymous caller
                    // whether an address is registered leaks the resident list.
                    sent = true,
                    errorMessage = result.exceptionOrNull()
                        ?.takeIf { e -> e !is com.google.firebase.auth.FirebaseAuthInvalidUserException }
                        ?.let(::friendlyMessage),
                )
            }
        }
    }
}

// ------------------------------------------------------ complete profile

data class CompleteProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val address: String = "",
    @StringRes val firstNameError: Int? = null,
    @StringRes val lastNameError: Int? = null,
    @StringRes val phoneError: Int? = null,
    @StringRes val addressError: Int? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Collects the village-specific details that Google sign-in cannot provide.
 *
 * Reached whenever an authenticated account has no `users/{uid}` document, which
 * also covers the rare case of a sign-up that created the auth account but died
 * before writing the profile.
 */
@HiltViewModel
class CompleteProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompleteProfileUiState())
    val uiState: StateFlow<CompleteProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.currentUser
            val parts = user?.displayName.orEmpty().trim().split(" ", limit = 2)
            _uiState.update {
                it.copy(
                    firstName = parts.getOrElse(0) { "" },
                    lastName = parts.getOrElse(1) { "" },
                )
            }
        }
    }

    fun onFirstName(value: String) = _uiState.update { it.copy(firstName = value, firstNameError = null) }
    fun onLastName(value: String) = _uiState.update { it.copy(lastName = value, lastNameError = null) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value, phoneError = null) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value, addressError = null) }

    fun submit() {
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

        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val result = userRepository.createProfile(
                userId = user.uid,
                firstName = state.firstName,
                lastName = state.lastName,
                email = user.email.orEmpty(),
                phone = state.phone,
                address = state.address,
            )
            _uiState.update {
                it.copy(
                    loading = false,
                    errorMessage = result.exceptionOrNull()?.let(::friendlyMessage),
                )
            }
        }
    }

    fun signOut() = authRepository.signOut()
}

/**
 * Firebase reports auth failures with a specific message ("The password is
 * invalid…") that is genuinely more useful than a generic one, so it is shown
 * as-is. Anything without a message becomes an empty string, which the screens
 * render as the localized [R.string.error_generic].
 */
internal fun friendlyMessage(throwable: Throwable): String =
    throwable.localizedMessage?.takeIf { it.isNotBlank() }.orEmpty()
