package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Something happening right now that the whole village should know about, at
 * `alerts/{id}`.
 *
 * ## What this can and cannot do
 *
 * It has to be said plainly, because a feature called "emergency" that quietly
 * under-delivers is worse than no feature. This app runs on Firebase's free
 * plan with no server of its own, and **without a server no phone can be woken
 * by another phone**. Sending a push requires a credential that cannot safely
 * live inside an app, so:
 *
 *  * a resident with the app open sees an alert **immediately**, as a takeover
 *    they cannot miss;
 *  * a resident whose app is closed sees it **when the background sync next
 *    runs**, which Android floors at fifteen minutes;
 *  * a resident whose phone is in a drawer sees nothing at all.
 *
 * Fifteen minutes is not an emergency response. So raising an alert also opens
 * the phone's own messaging app with every resident's number filled in and the
 * text written — because SMS is the one channel that reaches a phone nobody is
 * holding, needs no data connection, and belongs to the person sending it
 * rather than to this app. The screen says all of this rather than implying a
 * siren that does not exist, and 112 is one tap from the same place.
 */
data class VillageAlert(
    @DocumentId val id: String = "",
    val kind: String = AlertKind.OTHER.name,
    /** Free text: what, and where if the position is not enough. */
    val note: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    /** Where it is, in the village's own words, at the time it was raised. */
    val placeLabel: String = "",
    val raisedById: String = "",
    val raisedByName: String = "",
    @ServerTimestamp val raisedAt: Date? = null,
    /**
     * Residents who confirmed they have it too.
     *
     * The point of an outage report is the count. One person with no water has
     * a broken pipe; six have a broken main, and the sixth only says so because
     * the fifth did.
     */
    val confirmedBy: List<String> = emptyList(),
    val confirmedNames: List<String> = emptyList(),
    val resolvedAt: Date? = null,
    val resolvedById: String = "",
    val updatedAt: Date? = null,
) {
    val alertKind: AlertKind get() = AlertKind.fromId(kind)

    val isResolved: Boolean get() = resolvedAt != null

    val position: GeoPointOrNull get() = if (lat != null && lng != null) lat to lng else null

    /**
     * Whether an alert has gone stale on its own.
     *
     * Nobody comes back to close a power cut once the lights are on, so an
     * unresolved alert expires by itself. Emergencies expire fast because a
     * day-old "fire" on the map is worse than nothing; outages last a week,
     * because a week is how long a village argues with a utility.
     */
    fun isActive(now: Long): Boolean {
        if (isResolved) return false
        val age = now - (raisedAt?.time ?: now)
        return age <= alertKind.severity.lifetimeMs
    }

    companion object {
        const val MAX_NOTE = 240
        const val MAX_NAME = 80
    }
}

private typealias GeoPointOrNull = Pair<Double, Double>?

/** How loud a thing is, which decides how it is shown and how long it lives. */
enum class AlertSeverity(val lifetimeMs: Long) {
    /** Takes over the screen. Six hours, then it is history. */
    EMERGENCY(6 * 60 * 60 * 1000L),

    /** A banner and a counter. A week. */
    OUTAGE(7 * 24 * 60 * 60 * 1000L),
}

enum class AlertKind(val severity: AlertSeverity) {
    /** Smoke or flame, anywhere someone can see it. */
    FIRE(AlertSeverity.EMERGENCY),

    /** Somebody needs an ambulance and it needs to find the house. */
    MEDICAL(AlertSeverity.EMERGENCY),

    /** A person or an animal nobody can find. */
    MISSING(AlertSeverity.EMERGENCY),

    /** The lights are off. */
    POWER(AlertSeverity.OUTAGE),

    /** No water, or water nobody should drink. */
    WATER(AlertSeverity.OUTAGE),

    /** The road out is blocked — snow, a tree, a slide. */
    ROAD(AlertSeverity.OUTAGE),

    OTHER(AlertSeverity.OUTAGE),
    ;

    /** Whether "I have it too" is a useful thing to offer. */
    val takesConfirmations: Boolean get() = severity == AlertSeverity.OUTAGE

    companion object {
        fun fromId(id: String?): AlertKind = entries.firstOrNull { it.name == id } ?: OTHER
    }
}
