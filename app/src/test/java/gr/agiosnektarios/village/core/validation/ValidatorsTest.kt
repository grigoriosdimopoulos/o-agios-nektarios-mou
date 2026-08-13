package gr.agiosnektarios.village.core.validation

import gr.agiosnektarios.village.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the validators that are pure Kotlin.
 *
 * [Validators.email] is deliberately absent: it delegates to
 * `android.util.Patterns`, which is a stub in local unit tests and would only
 * be testable under Robolectric or on a device.
 */
class ValidatorsTest {

    @Test
    fun `required rejects blank and whitespace`() {
        assertEquals(R.string.error_field_required, Validators.required(""))
        assertEquals(R.string.error_field_required, Validators.required("   "))
        assertNull(Validators.required("Marathonos 12"))
    }

    @Test
    fun `password enforces the minimum length`() {
        assertEquals(R.string.error_field_required, Validators.password(""))
        assertEquals(R.string.error_password_short, Validators.password("short"))
        assertEquals(R.string.error_password_short, Validators.password("1234567"))
        assertNull(Validators.password("12345678"))
    }

    @Test
    fun `password confirmation must match exactly`() {
        assertEquals(
            R.string.error_field_required,
            Validators.passwordConfirmation("correct-horse", ""),
        )
        assertEquals(
            R.string.error_password_mismatch,
            Validators.passwordConfirmation("correct-horse", "correct-Horse"),
        )
        assertNull(Validators.passwordConfirmation("correct-horse", "correct-horse"))
    }

    @Test
    fun `phone accepts the ways a resident actually writes their number`() {
        assertNull(Validators.phone("2109876543"))
        assertNull(Validators.phone("6971234567"))
        assertNull(Validators.phone("+30 697 123 4567"))
        assertNull(Validators.phone("+30-697-123-4567"))
        assertNull(Validators.phone("(210) 987 6543"))
        assertNull(Validators.phone("00306971234567"))
    }

    @Test
    fun `phone rejects blanks and obvious nonsense`() {
        assertEquals(R.string.error_field_required, Validators.phone(""))
        assertEquals(R.string.error_phone_invalid, Validators.phone("12345"))
        assertEquals(R.string.error_phone_invalid, Validators.phone("not a number"))
    }
}
