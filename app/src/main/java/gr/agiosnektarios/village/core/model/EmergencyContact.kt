package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.DocumentId

/**
 * One resident's agreement to be texted in an emergency, at
 * `emergencyContacts/{uid}`.
 *
 * The document existing *is* the consent. There is no `shared: false` — a
 * resident who has not agreed has no document here at all, so there is nothing
 * for anyone to read and nothing to get wrong.
 */
data class EmergencyContact(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
) {
    /** Digits and a leading plus, which is all an `smsto:` URI can carry. */
    val dialable: String get() = phone.filter { it.isDigit() || it == '+' }

    companion object {
        const val MAX_NAME = 80
        const val MAX_PHONE = 20
    }
}
