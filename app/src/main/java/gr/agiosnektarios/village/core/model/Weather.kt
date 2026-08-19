package gr.agiosnektarios.village.core.model

/**
 * The weather over the village, as one value.
 *
 * The whole settlement is 1.2 km across. At that size weather has no spatial
 * variation worth modelling — it is not raining on one street and dry on the
 * next — so this is a single reading for the place rather than a field over it.
 * That one fact decides most of the design downstream: the map's animated
 * weather is drawn in screen space rather than as a geo-referenced layer,
 * because there is nothing to reference it to.
 *
 * Everything here comes from Open-Meteo, which needs no key and no account.
 * That matters: this app runs on Firebase's free plan with no server of its
 * own, so a forecast that required a secret could not be fetched at all.
 */
data class WeatherSnapshot(
    /** When the provider says this reading is for, in epoch millis. */
    val observedAt: Long,
    /** When this device actually fetched it. Drives the "as of" line. */
    val fetchedAt: Long,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    /** Cloud cover, 0-100. */
    val cloudCover: Int,
    /** Rain in the last hour, mm. */
    val precipitation: Double,
    /**
     * Rain that has actually fallen today, mm, up to and including this hour.
     *
     * Distinct from [DayForecast.precipitationMm], which is the provider's sum
     * for the whole calendar day and therefore includes hours that have not
     * happened. Confusing the two is a live safety bug and was one: an evening
     * thunderstorm in the forecast made the ground "wet" at three in the
     * afternoon and took two steps off the fire level while the hillside was
     * still bone dry. Anything that makes a present-tense claim — the level,
     * the "rain today" tile — has to use this one.
     */
    val rainSoFarMm: Double = 0.0,
    /** Lying snow, in centimetres. Zero for most of the year. */
    val snowDepthCm: Double,
    val wind: Wind,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val sunrise: Long?,
    val sunset: Long?,
    /**
     * How many days since the village last had meaningful rain.
     *
     * `null` when the history needed to answer is not in the response. Counted
     * over completed days only, and "meaningful" is [WET_DAY_MM] — a trace of
     * 0.1 mm on a 34-degree day has not wetted anything.
     */
    val dryDays: Int?,
    /**
     * Whether [dryDays] hit the end of the history that was fetched.
     *
     * True means "at least this many", not "exactly this many" — a six-week
     * drought read through a one-month window is thirty-one days as far as the
     * response can say, and the screen has to show it as `31+`.
     */
    val dryDaysAtLeast: Boolean = false,
    /**
     * The hour of today the fire-risk reading is taken from.
     *
     * Fire danger is a property of a day, not of a moment. At 08:45 a Greek
     * August morning is 23 degrees at 64% humidity and any index will call it
     * safe; the same day at 15:00 is 31 degrees at 28% and is when things
     * actually start. Assessing "now" would therefore have told a resident it
     * was fine to burn cuttings on exactly the mornings it was not — so the
     * hour with the worst conditions in the day is picked here and the reading
     * says which hour it is.
     */
    val peakToday: HourForecast?,
    val today: DayForecast?,
    val days: List<DayForecast>,
    val hours: List<HourForecast>,
) {
    companion object {
        /** Rain below this in a day does not count as the ground having been wetted. */
        const val WET_DAY_MM = 1.0
    }
}

/**
 * Wind, kept as one value because its two halves are never useful apart.
 *
 * [directionDegrees] follows the meteorological convention and is the direction
 * the wind blows **from**: 0 is a northerly. An arrow drawn to show where it is
 * going therefore points at `directionDegrees + 180`, and getting that backwards
 * is the single most likely way to make this feature actively harmful on a
 * mountain in August.
 */
data class Wind(
    val speedKmh: Double,
    val gustKmh: Double,
    val directionDegrees: Int,
) {
    val beaufort: Int get() = beaufortOf(speedKmh)
    val gustBeaufort: Int get() = beaufortOf(gustKmh)

    /** The eight-point compass sector the wind comes from, as an index from north. */
    val sector: Int get() = (((directionDegrees % 360) + 360) % 360 + 22) / 45 % 8

    companion object {
        /**
         * The Beaufort scale, by its published km/h bands.
         *
         * Greek weather is spoken in μποφόρ, not in km/h — a forecast that says
         * "7 μποφόρ" is understood by everyone in the village, and one that says
         * "54 km/h" is understood by nobody. Both are shown; this is the one
         * that leads.
         */
        fun beaufortOf(speedKmh: Double): Int = when {
            speedKmh < 1.0 -> 0
            speedKmh < 6.0 -> 1
            speedKmh < 12.0 -> 2
            speedKmh < 20.0 -> 3
            speedKmh < 29.0 -> 4
            speedKmh < 39.0 -> 5
            speedKmh < 50.0 -> 6
            speedKmh < 62.0 -> 7
            speedKmh < 75.0 -> 8
            speedKmh < 89.0 -> 9
            speedKmh < 103.0 -> 10
            speedKmh < 118.0 -> 11
            else -> 12
        }
    }
}

data class DayForecast(
    /** Local midnight of the day, epoch millis. */
    val date: Long,
    val high: Double,
    val low: Double,
    val precipitationMm: Double,
    val maxWindKmh: Double,
    val maxGustKmh: Double,
    val condition: WeatherCondition,
)

data class HourForecast(
    val time: Long,
    val temperature: Double,
    val humidity: Int,
    val windKmh: Double,
    val gustKmh: Double,
    val windDirection: Int,
    val precipitationMm: Double,
    val precipitationChance: Int,
    val condition: WeatherCondition,
) {
    val wind: Wind get() = Wind(windKmh, gustKmh, windDirection)
}

/**
 * WMO code 4677, collapsed to the handful of states worth drawing.
 *
 * The provider reports 28 distinct codes. Rendering 28 states means 28 things
 * to get wrong and 28 icons to draw badly; what actually changes what a
 * resident does is the group.
 */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN,
    ;

    val isWet: Boolean get() = this == DRIZZLE || this == RAIN || this == HEAVY_RAIN || this == THUNDERSTORM

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 66, 80, 81 -> RAIN
            65, 67, 82 -> HEAVY_RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}
