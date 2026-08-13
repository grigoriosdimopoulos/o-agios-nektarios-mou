package gr.agiosnektarios.village.core.validation

import android.util.Patterns
import androidx.annotation.StringRes
import gr.agiosnektarios.village.R

/**
 * Form validation, returning a string resource for the message or null when the
 * value is fine.
 *
 * Resource ids rather than strings so validation lives in the view model
 * (testable, no Context) while the message is still rendered in the user's
 * chosen language at draw time.
 */
object Validators {

    const val MIN_PASSWORD_LENGTH = 8

    @StringRes
    fun email(value: String): Int? = when {
        value.isBlank() -> R.string.error_field_required
        !Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches() -> R.string.error_email_invalid
        else -> null
    }

    @StringRes
    fun password(value: String): Int? = when {
        value.isBlank() -> R.string.error_field_required
        value.length < MIN_PASSWORD_LENGTH -> R.string.error_password_short
        else -> null
    }

    @StringRes
    fun passwordConfirmation(password: String, confirmation: String): Int? = when {
        confirmation.isBlank() -> R.string.error_field_required
        password != confirmation -> R.string.error_password_mismatch
        else -> null
    }

    @StringRes
    fun required(value: String): Int? =
        if (value.isBlank()) R.string.error_field_required else null

    /**
     * Accepts Greek mobile and landline formats with or without the +30 country
     * code, and tolerates spaces, dashes and parentheses, since residents type
     * their number however they usually write it.
     */
    @StringRes
    fun phone(value: String): Int? {
        if (value.isBlank()) return R.string.error_field_required
        val digits = value.filter { it.isDigit() || it == '+' }
        val normalized = digits.removePrefix("+30").removePrefix("0030")
        return if (normalized.length in 10..15 && normalized.all { it.isDigit() }) {
            null
        } else {
            R.string.error_phone_invalid
        }
    }
}
