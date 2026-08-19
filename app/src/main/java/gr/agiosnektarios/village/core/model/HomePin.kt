package gr.agiosnektarios.village.core.model

import gr.agiosnektarios.village.core.geo.GeoPoint

/**
 * Where a resident's house is, if they have pinned it.
 *
 * Forty-six houses and not one street address between them. When somebody
 * calls an ambulance the hard part is not the phone number, it is telling the
 * driver where to come — "the third turning after the church, the one with the
 * blue gate" is what people actually say, and it is why crews get lost on this
 * hill. A pinned house turns that into a coordinate the caller can read out.
 *
 * It lives at `users/{uid}/private/home` rather than on the profile document,
 * and the rules let nobody but the owner read it. That is not caution for its
 * own sake: the screen that asks for the pin says it is for the ambulance, and
 * the app never draws anybody's house but your own. Keeping it on the profile
 * would have meant every resident's phone could read every other resident's
 * exact coordinates — quietly, and for a purpose nobody had agreed to. In a
 * village of forty-six people where everyone knows every house that is a small
 * thing right up until one household wishes another did not know, and the cost
 * of getting it right was one document.
 */
data class HomePin(
    val lat: Double? = null,
    val lng: Double? = null,
    /** The pin described in the village's own words, at the time it was set. */
    val place: String = "",
) {
    val position: GeoPoint? get() = if (lat != null && lng != null) GeoPoint(lat, lng) else null
}
