package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A number worth having when something is wrong.
 *
 * There are two kinds and they behave differently on purpose.
 *
 * **National numbers are compiled into the app.** 199, 166, 100, 112 and the
 * electricity fault line do not change, and the moment they are wanted is the
 * moment there is a fire on the hill and no signal in the valley. A list that
 * needs a Firestore round trip to render is a list that is blank exactly then,
 * so those live in [NationalContacts] and are drawn before anything has
 * loaded — no account, no network, no permission.
 *
 * **Local numbers come from the village.** The rural surgery at Vilia, the
 * municipality's works desk, whoever has the key to the water tank: these are
 * real and none of them is in any dataset this app could ship. They are not
 * invented here — an app that guesses a doctor's telephone number is worse
 * than one that has none — so an administrator enters them and they live at
 * `contacts/{id}`.
 */
data class VillageContact(
    @DocumentId val id: String = "",
    val name: String = "",
    /** As dialled. Kept as a string: leading zeros and spacing are meaningful. */
    val number: String = "",
    /** Optional line under the name — opening hours, or who this reaches. */
    val note: String = "",
    val kind: String = ContactKind.LOCAL.name,
    /** Manual ordering within a section; ties fall back to the name. */
    val order: Int = 0,
    val createdById: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val contactKind: ContactKind get() = ContactKind.fromId(kind)

    companion object {
        const val MAX_NAME = 60
        const val MAX_NUMBER = 24
        const val MAX_NOTE = 120

        /** Digits, spaces, and the punctuation a real Greek number is written with. */
        private val ALLOWED = Regex("^[0-9+()\\s.-]{3,$MAX_NUMBER}$")

        fun isDialable(number: String): Boolean =
            ALLOWED.matches(number.trim()) && number.count { it.isDigit() } >= 3
    }
}

enum class ContactKind {
    /** Answers in seconds and is free from any phone. */
    EMERGENCY,

    /** Power, water, the things that break. */
    UTILITY,

    /** Doctors, pharmacy, the surgery down in Vilia. */
    HEALTH,

    /** The municipality, the association, the neighbour with the tractor. */
    LOCAL,
    ;

    companion object {
        fun fromId(id: String?): ContactKind =
            entries.firstOrNull { it.name == id } ?: LOCAL
    }
}

/**
 * The numbers that ship with the app.
 *
 * Every one of these was checked against the authority that owns it rather
 * than recalled: 112, 100, 199 and 166 against the state's own emergency-number
 * page, and both electricity fault lines against ΔΕΔΔΗΕ's faults page. Nothing
 * local is in this list, because nothing local could be verified the same way,
 * and a wrong number for the surgery is worse than an empty section that tells
 * the village to fill it in.
 *
 * Names are resource ids, not strings: this list renders in Greek or English
 * like everything else.
 */
object NationalContacts {

    data class Bundled(
        val id: String,
        val number: String,
        val nameRes: Int,
        val noteRes: Int?,
        val kind: ContactKind,
    )

    val all: List<Bundled> = listOf(
        Bundled(
            id = "112",
            number = "112",
            nameRes = gr.agiosnektarios.village.R.string.contact_112,
            noteRes = gr.agiosnektarios.village.R.string.contact_112_note,
            kind = ContactKind.EMERGENCY,
        ),
        Bundled(
            id = "199",
            number = "199",
            nameRes = gr.agiosnektarios.village.R.string.contact_fire,
            noteRes = gr.agiosnektarios.village.R.string.contact_fire_note,
            kind = ContactKind.EMERGENCY,
        ),
        Bundled(
            id = "166",
            number = "166",
            nameRes = gr.agiosnektarios.village.R.string.contact_ambulance,
            noteRes = null,
            kind = ContactKind.EMERGENCY,
        ),
        Bundled(
            id = "100",
            number = "100",
            nameRes = gr.agiosnektarios.village.R.string.contact_police,
            noteRes = null,
            kind = ContactKind.EMERGENCY,
        ),
        Bundled(
            id = "11500",
            number = "11500",
            nameRes = gr.agiosnektarios.village.R.string.contact_power,
            noteRes = gr.agiosnektarios.village.R.string.contact_power_note,
            kind = ContactKind.UTILITY,
        ),
        Bundled(
            id = "8004004000",
            number = "800 400 4000",
            nameRes = gr.agiosnektarios.village.R.string.contact_power_free,
            noteRes = gr.agiosnektarios.village.R.string.contact_power_free_note,
            kind = ContactKind.UTILITY,
        ),
    )
}
