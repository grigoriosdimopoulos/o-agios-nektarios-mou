package gr.agiosnektarios.village.core.weather

import gr.agiosnektarios.village.core.model.WeatherSnapshot
import gr.agiosnektarios.village.core.model.Wind
import java.time.Instant
import java.time.ZoneId

/**
 * How dangerous a day this is for fire, over this village specifically.
 *
 * This is the one part of the weather feature that is not decoration. Άγιος
 * Νεκτάριος sits in pine and fir on the skirts of Kithairon; the practical
 * questions in August are "may I burn cuttings", "may I use a grinder outside"
 * and "if something starts up there, does the wind bring it here". Those have
 * answers, and the answers are computable on the phone with no server.
 *
 * ## Why the obvious approach was thrown away
 *
 * The first version used the **Chandler Burning Index** with its published
 * bands, which is what a search for "simple fire danger index" gives you. It
 * was checked against three years of hourly ERA5 reanalysis for this exact
 * point — 2023 to 2025, via Open-Meteo's archive — before being shown to
 * anyone, and it was useless here: at each day's worst hour it still called
 * **53% of fire-season days "low"**. The Ångström index with *its* published
 * bands failed in the opposite direction, calling **56% of fire-season days
 * "extreme"**.
 *
 * Neither is a bad index. Both are calibrated to climates that are not this
 * one: 30 °C at 35% humidity is a remarkable day in Sweden and an ordinary
 * Tuesday in Attica. Shipping either would have been a number that looked like
 * science and meant nothing — reassuring on the mornings it most mattered, or
 * shouting so constantly that nobody would look twice on the day it was right.
 *
 * ## What is actually computed
 *
 * The **Ångström index** — `I = RH/20 + (27 − T)/10`, lower being worse — is
 * kept as the underlying measure, because over these three years it separates
 * days here far better than Chandler does, which saturates. Its *thresholds*
 * are replaced with the 40th, 70th, 88th and 97th percentiles of the index
 * over fire-season days at this location, so a level means "worse than this
 * share of summer days here" rather than "worse than a Swedish forest".
 *
 * Regional recalibration of a fire index against local climatology is the
 * ordinary practice, not a liberty taken here. The derivation is reproducible:
 * `tools/fire_risk_calibration.py` fetches the same archive and prints these
 * numbers.
 *
 * Two aggravating factors are applied on top as **stated rules**, because the
 * index contains no wind term and no memory of drought:
 *
 *  * sustained wind of [WINDY_BEAUFORT] Beaufort, or gusts of [GUSTY_BEAUFORT],
 *    raises the level by one — 5 Bft is the threshold at which the fire service
 *    stops issuing burning permits, so it is a number the village already
 *    lives by rather than one chosen here;
 *  * [DRY_SPELL_DAYS] completed days without meaningful rain raises it by one.
 *
 * A day that has had a millimetre of rain comes back down a step and one that
 * has had five comes down two; lying snow takes it to the bottom outright. Rain
 * does **not** cancel the wind: a damp morning does not make a gale safe to
 * light a fire in, and the fire service does not issue a permit above five
 * Beaufort whatever the ground is doing.
 *
 * They are rules and not curve-fitting on purpose: a resident can be told
 * exactly why the app went red today, and can disagree with it. Over the same
 * three years the finished thing lands at 34/26/21/13/7 percent across the five
 * levels in the fire season, reaches its top level on about seven days in a
 * hundred, and says "burn nothing" on a little over a quarter of them.
 *
 * ## What this is not
 *
 * It is **not** the daily Χάρτης Πρόβλεψης Κινδύνου Πυρκαγιάς that the General
 * Secretariat for Civil Protection issues each afternoon, and it must never be
 * presented as though it were. That map is what the burning prohibitions are
 * legally keyed to.
 *
 * The detail sheet says all of that in as many words and links to the official
 * map. The badge on the map itself cannot — it is a pill the width of a word —
 * so it carries the app's own flame mark rather than reproducing the official
 * badge, and one tap on it opens the sheet that explains it. That is a
 * mitigation and not a solution. An earlier version of this comment claimed
 * every screen showing a level links to the official map, which was untrue of
 * the one screen everybody sees.
 */
object FireRisk {

    /** Sustained wind at or above this Beaufort raises the level by one. */
    const val WINDY_BEAUFORT = 5

    /** Gusts at or above this Beaufort do the same. */
    const val GUSTY_BEAUFORT = 8

    /**
     * Completed days without [WeatherSnapshot.WET_DAY_MM] of rain before
     * dryness counts.
     *
     * Must be inside the history window the forecast is asked for. It was 20
     * against a request for 7 past days, which made this rule unreachable: the
     * count could never exceed 7, so the drought step never fired — on a
     * location where 15% of fire-season days are in a spell of twenty days or
     * more. See `OpenMeteo.PAST_DAYS`.
     */
    const val DRY_SPELL_DAYS = 20

    /**
     * Ångström values at the 40th, 70th, 88th and 97th percentile of
     * fire-season days at this village, 2023-2025, measured at each day's worst
     * hour. Descending, because a lower index is a worse day.
     *
     * The "worst hour" part is load-bearing and was wrong once. The first set
     * of breakpoints was taken from the index at 13:00, which is how Ångström
     * is classically defined — but [assess] evaluates the day's minimum, which
     * is a different and systematically lower number, so the thresholds were
     * calibrated against a quantity the app never computes. Both now use the
     * daily minimum.
     */
    private val BREAKPOINTS = doubleArrayOf(2.2, 1.2, 0.7, 0.1)

    /**
     * The Greek fire season, during which burning vegetation in the countryside
     * needs a permit and is forbidden outright on the worst days.
     *
     * Dates rather than weather: this is a legal period fixed by fire-service
     * regulation, 1 May to 31 October, and it is worth stating on the screen
     * because half of what gets a fire started in a village is somebody
     * burning prunings in a month they thought was still fine.
     */
    const val SEASON_FIRST_MONTH = 5
    const val SEASON_LAST_MONTH = 10

    /** The reading, with the reasons kept alongside it. */
    data class Assessment(
        val level: Level,
        /** The Ångström index at the day's worst hour. Lower is worse. */
        val index: Double,
        /** The level the index alone would have given, before the rules below. */
        val baseLevel: Level,
        val windy: Boolean,
        val gusty: Boolean,
        val dryHere: Boolean,
        /** True when rain today, or lying snow, brought the level down. */
        val wetGround: Boolean,
        /** Whether the day falls inside the regulated fire season. */
        val inFireSeason: Boolean,
        /** The hour the reading is for, or null when today's hours were missing. */
        val at: Long?,
        val temperature: Double,
        val humidity: Int,
        /**
         * The day's strongest wind and gust, in Beaufort.
         *
         * Two integers rather than a [Wind], and deliberately: the speed here
         * is the day's maximum while the only bearing available belongs to the
         * hour the index picked, so an object carrying both would invite a
         * compass to be drawn from a number that has no direction. Anything
         * that needs a bearing takes it from the observation instead.
         */
        val windBeaufort: Int,
        val gustBeaufort: Int,
        val dryDays: Int?,
    ) {
        val raised: Boolean get() = level.ordinal > baseLevel.ordinal

        /**
         * Whether burning is off the table today.
         *
         * Two independent reasons, both of them rules rather than predictions:
         * the fire service does not issue permits above [WINDY_BEAUFORT], and
         * burning is prohibited outright at the top two risk categories.
         */
        val burningForbidden: Boolean
            get() = inFireSeason &&
                (windy || gusty || level == Level.VERY_HIGH || level == Level.EXTREME)
    }

    /** The five categories, in the wording the official scale uses. */
    enum class Level { LOW, MODERATE, HIGH, VERY_HIGH, EXTREME }

    /**
     * The Ångström fire index: `I = RH/20 + (27 − T)/10`.
     *
     * Public and unclamped so a test can check it against values computed
     * independently rather than against whatever this file happens to return.
     * Lower means a worse day, which is the opposite of every other number on
     * the screen — hence it is never shown raw.
     */
    fun angstrom(temperatureC: Double, humidityPercent: Double): Double =
        humidityPercent.coerceIn(0.0, 100.0) / 20.0 + (27.0 - temperatureC) / 10.0

    fun levelOf(index: Double): Level = when {
        index > BREAKPOINTS[0] -> Level.LOW
        index > BREAKPOINTS[1] -> Level.MODERATE
        index > BREAKPOINTS[2] -> Level.HIGH
        index > BREAKPOINTS[3] -> Level.VERY_HIGH
        else -> Level.EXTREME
    }

    /**
     * `Instant.atZone(...).toLocalDate()` rather than `LocalDate.ofInstant`,
     * which looks tidier and is API 31 — three versions above this app's
     * minimum, on exactly the old phones a village of this age is using.
     */
    fun isFireSeason(atMillis: Long, zone: ZoneId = ZoneId.of("Europe/Athens")): Boolean {
        val month = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().monthValue
        return month in SEASON_FIRST_MONTH..SEASON_LAST_MONTH
    }

    fun assess(weather: WeatherSnapshot): Assessment {
        // The day's worst hour when the forecast carried one, and the current
        // reading when it did not — a snapshot restored from a cache written
        // yesterday has no hours for today left in it.
        //
        // The hour matters. A Greek August morning is 23 degrees at 64%
        // humidity and reads as perfectly safe on any index; the same day at
        // three in the afternoon is 30 degrees at 35% and is when things start.
        // Assessing "now" would have given a reassuring answer at exactly the
        // time somebody was deciding whether to burn their prunings.
        val peak = weather.peakToday
        val temperature = peak?.temperature ?: weather.temperature
        val humidity = peak?.humidity ?: weather.humidity

        // The day's strongest wind, not the wind at the hour the index picked.
        //
        // Those are different hours and the difference decides a prohibition: a
        // day whose fire peak blows 4 Beaufort at three but reaches 6 at six in
        // the evening is a day the fire service will not write a permit for,
        // and reading only the peak hour reported it as calm. The threshold is
        // a statement about a day, so it has to be measured over one.
        val hourly = peak?.wind ?: weather.wind
        val windBeaufort = Wind.beaufortOf(
            maxOf(hourly.speedKmh, weather.today?.maxWindKmh ?: 0.0),
        )
        val gustBeaufort = Wind.beaufortOf(
            maxOf(hourly.gustKmh, weather.today?.maxGustKmh ?: 0.0),
        )

        val index = angstrom(temperature, humidity.toDouble())
        val base = levelOf(index)
        val season = isFireSeason(peak?.time ?: weather.observedAt)

        val windy = windBeaufort >= WINDY_BEAUFORT
        val gusty = gustBeaufort >= GUSTY_BEAUFORT
        val dryHere = (weather.dryDays ?: 0) >= DRY_SPELL_DAYS

        // Wet ground, measured over the day rather than over this minute.
        //
        // This was the worst bug in the feature and it read as a safety
        // measure. The test was `precipitation > 0.2` — the *current* hour —
        // applied to an assessment computed for the day's worst hour, and it
        // returned Level.LOW outright while also forcing `windy` and `gusty`
        // to false. So three tenths of a millimetre of drizzle at eight in the
        // morning turned a 34-degree, 18%-humidity, 7-Beaufort afternoon into
        // a green pill reading "the ground is wet, which settles it", with the
        // burning prohibition cancelled. A test asserted that behaviour.
        //
        // A day's rain is what wets fuel, so the day's total is what counts,
        // against the same millimetre threshold the dry-spell run already uses.
        // It lowers the level rather than pinning it, and it does not touch the
        // wind: no amount of morning drizzle makes a gale safe to light a fire
        // in, and above five Beaufort the fire service does not issue a permit
        // whatever the ground is doing.
        // How much rain, not merely whether. A single millimetre is a shower
        // that fine fuel shrugs off in an afternoon at twelve percent humidity,
        // and treating it the same as a proper wetting is how a 38-degree day
        // with a shower on it could come back two steps down. One step for a
        // millimetre, two for [SOAKING_MM].
        // What has fallen, not what is forecast. `today.precipitationMm` is the
        // provider's total for the whole calendar day, so a thunderstorm due at
        // ten in the evening was already making the ground "wet" at three in
        // the afternoon and taking two steps off a bone-dry hillside. That is
        // the original bug in a new coat: a forward-looking number used for a
        // present-tense claim.
        val rainToday = maxOf(weather.rainSoFarMm, weather.precipitation)
        val relief = when {
            rainToday >= SOAKING_MM -> 2
            rainToday >= WeatherSnapshot.WET_DAY_MM -> 1
            else -> 0
        }
        val snowLying = weather.snowDepthCm > 0.0

        var step = base.ordinal
        // Each aggravating factor is worth one step, and both can apply — a
        // long drought with a gale is genuinely two things wrong rather than
        // one thing said twice.
        if (windy || gusty) step++
        if (dryHere) step++
        step -= relief
        // Snow on the ground is the one thing that does end the argument.
        if (snowLying) step = Level.LOW.ordinal

        return Assessment(
            level = Level.entries[step.coerceIn(Level.LOW.ordinal, Level.EXTREME.ordinal)],
            index = index,
            baseLevel = base,
            windy = windy,
            gusty = gusty,
            dryHere = dryHere,
            wetGround = relief > 0 || snowLying,
            inFireSeason = season,
            at = peak?.time,
            temperature = temperature,
            humidity = humidity,
            windBeaufort = windBeaufort,
            gustBeaufort = gustBeaufort,
            dryDays = weather.dryDays,
        )
    }

    /** Rain at or above this in a day counts as having properly wetted the ground. */
    const val SOAKING_MM = 5.0

    /** Where the official daily map lives. Shown beside every reading. */
    const val OFFICIAL_MAP_URL = "https://civilprotection.gov.gr/"
}
