package gr.agiosnektarios.village.core.model

import androidx.annotation.StringRes
import gr.agiosnektarios.village.R

/**
 * A part of the app an administrator can switch off for the whole village.
 *
 * Not everything is here. The map, the reports, the notice board and a
 * resident's own profile are what the app *is*; switching those off would
 * leave something that is not this app, and a switch nobody should ever press
 * is a switch that eventually gets pressed by accident. What is here is
 * everything that was added on top — each of which some villages will want and
 * some will not, and one of which should be off until somebody decides
 * otherwise.
 *
 * [defaultOn] is what applies before an administrator has touched anything,
 * including on a village that never sets a flag at all. Only [SMS_TO_ALL]
 * starts off, because turning it on publishes telephone numbers — see there.
 */
enum class Feature(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val explainRes: Int,
    val defaultOn: Boolean = true,
) {
    /** The alarm: fire, ambulance, someone missing, and the outage reports. */
    ALERTS("ALERTS", R.string.feature_alerts, R.string.feature_alerts_off),

    /**
     * Opening the phone's messaging app with every resident's number in it.
     *
     * The one feature that starts switched off, and the reason the rest of
     * this file exists. It is the only thing in the app that needs to know
     * everybody's telephone number, and while it is on, every resident's phone
     * can read every other resident's number — which is a thing to agree to
     * rather than a thing to discover. So: off unless an administrator turns
     * it on, and even then a number is only readable if its owner has said it
     * may be. Both halves are enforced by the security rules, not by hiding a
     * button.
     */
    SMS_TO_ALL("SMS_TO_ALL", R.string.feature_sms, R.string.feature_sms_off, defaultOn = false),

    /** Private messages between residents. */
    CHAT("CHAT", R.string.feature_chat, R.string.feature_chat_off),

    /** The village calendar and its reminders. */
    CALENDAR("CALENDAR", R.string.feature_calendar, R.string.feature_calendar_off),

    /** The list of useful telephone numbers — the surgery, the water board. */
    CONTACTS("CONTACTS", R.string.feature_contacts, R.string.feature_contacts_off),

    /** The weather strip, the forecast sheet and the map overlay. */
    WEATHER("WEATHER", R.string.feature_weather, R.string.feature_weather_off),

    /** The fire-risk reading, which needs the weather to be on as well. */
    FIRE_RISK("FIRE_RISK", R.string.feature_fire, R.string.feature_fire_off),

    /** Residents naming their own streets, and confirming each other's. */
    STREET_NAMES("STREET_NAMES", R.string.feature_streets, R.string.feature_streets_off),

    /** Pinning your own house so an ambulance can be told where to come. */
    HOME_PIN("HOME_PIN", R.string.feature_home, R.string.feature_home_off),

    /** Handing a report to the municipality by email. */
    COUNCIL("COUNCIL", R.string.feature_council, R.string.feature_council_off),
    ;

    companion object {
        /** The ids the security rules will accept, kept in step with them. */
        val ids: List<String> get() = entries.map { it.id }
    }
}

/**
 * What the village has switched on, at `featureFlags/village`.
 *
 * A map rather than a field per feature, so adding one later needs no
 * migration and an untouched village needs no document at all: an absent entry
 * means [Feature.defaultOn], and an absent document means every default.
 */
data class FeatureFlags(
    val enabled: Map<String, Boolean> = emptyMap(),
) {
    operator fun get(feature: Feature): Boolean = enabled[feature.id] ?: feature.defaultOn

    /**
     * The fire reading is a thing the weather says, so it cannot outlive it.
     * Expressed here rather than in the admin screen, so every caller agrees.
     */
    fun isOn(feature: Feature): Boolean = when (feature) {
        Feature.FIRE_RISK -> this[Feature.FIRE_RISK] && this[Feature.WEATHER]
        else -> this[feature]
    }

    companion object {
        const val DOCUMENT = "village"
    }
}
