package gr.agiosnektarios.village.data.weather

import gr.agiosnektarios.village.core.VillageConfig
import gr.agiosnektarios.village.core.model.DayForecast
import gr.agiosnektarios.village.core.model.HourForecast
import gr.agiosnektarios.village.core.model.WeatherCondition
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import gr.agiosnektarios.village.core.weather.FireRisk
import gr.agiosnektarios.village.core.model.Wind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reading Open-Meteo's forecast, with the parsing kept away from the socket.
 *
 * Split deliberately: [parse] is pure, takes a string, and is tested against a
 * captured real response. Everything that can only be exercised on a device —
 * the connection, the timeouts, the retries — is a handful of lines in
 * [OpenMeteoClient] with no logic in it worth testing.
 *
 * The provider is free and keyless, which is the only reason this feature can
 * exist at all on a village app with no server and no billing account. Its
 * terms ask non-commercial users to stay under about ten thousand calls a day.
 * A phone here makes at most two automatic calls an hour — the reading is
 * considered current for thirty minutes — plus whatever the Refresh button is
 * pressed, which is itself floored at one a minute.
 */
object OpenMeteo {

    /**
     * Fields the request asks for.
     *
     * Kept as one string so the request and the parser cannot drift: everything
     * read below is named here, and nothing here is unread.
     */
    private const val CURRENT =
        "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
            "weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m"

    private const val HOURLY =
        "temperature_2m,relative_humidity_2m,precipitation,precipitation_probability," +
            "weather_code,snow_depth,wind_speed_10m,wind_direction_10m,wind_gusts_10m"

    private const val DAILY =
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "wind_speed_10m_max,wind_gusts_10m_max,sunrise,sunset"

    /**
     * A month back and four days forward.
     *
     * The history is not padding: it is the only way to answer "how long has it
     * been dry", which is the input a resident actually reasons with and which
     * no "current conditions" field carries.
     *
     * It was seven days, which quietly broke the thing it existed for. The
     * fire-risk rule raises the level after twenty dry days, and a window of
     * seven can never report more than seven — so the drought step was
     * unreachable, on a mountain where 15% of fire-season days fall inside a
     * spell of twenty days or more. It also meant the "since it rained" figure
     * on screen read "7 days" in the middle of a six-week drought. The window
     * must be longer than any threshold measured inside it.
     */
    const val PAST_DAYS = 31
    private const val FORECAST_DAYS = 4

    /**
     * The village's own coordinates and altitude, not the grid cell's.
     *
     * Passing `elevation` matters here. Without it the provider uses the mean
     * height of its ~11 km cell, which around Kithairon spans valley floor and
     * ridge; the village is at 640 m and a cell mean a few hundred metres out
     * is a temperature error of a couple of degrees.
     */
    fun url(): String = buildString {
        append("https://api.open-meteo.com/v1/forecast")
        append("?latitude=").append(VillageConfig.CENTER.lat)
        append("&longitude=").append(VillageConfig.CENTER.lng)
        append("&elevation=").append(VillageConfig.ELEVATION_METRES)
        append("&current=").append(CURRENT)
        append("&hourly=").append(HOURLY)
        append("&daily=").append(DAILY)
        append("&past_days=").append(PAST_DAYS)
        append("&forecast_days=").append(FORECAST_DAYS)
        append("&timezone=Europe%2FAthens")
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Turns a response body into a snapshot, or returns null if it cannot.
     *
     * Null rather than an exception because the caller's response to "the
     * provider sent something unexpected" is identical to its response to "the
     * network was down": keep showing the last good reading. A malformed
     * payload must never be able to take a screen down.
     */
    fun parse(body: String, fetchedAt: Long): WeatherSnapshot? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val offsetSeconds = root["utc_offset_seconds"]?.jsonPrimitive?.intOrNull ?: 0
        val zone = ZoneOffset.ofTotalSeconds(offsetSeconds)

        val current = root["current"]!!.jsonObject
        val observedAt = current.time("time", zone) ?: return null

        val hourly = root["hourly"]?.jsonObject
        val daily = root["daily"]?.jsonObject

        val hours = hourly.readHours(zone)
        val days = daily.readDays(zone)

        // Snow is not in the `current` block at all, only hourly, so the depth
        // has to be looked up by the hour the reading belongs to rather than
        // taken from the top of the list — which during the evening would be
        // this morning's number.
        val snow = hourly.valueAtHour("snow_depth", zone, observedAt) ?: 0.0

        val today = LocalDateTime.ofEpochSecond(observedAt / 1000, 0, zone).toLocalDate()
        val dryRun = daily.dryDaysBefore(zone, today)
        val todayIndex = days.indexOfFirst {
            LocalDateTime.ofEpochSecond(it.date / 1000, 0, zone).toLocalDate() == today
        }
        val todayForecast = days.getOrNull(todayIndex)

        WeatherSnapshot(
            observedAt = observedAt,
            fetchedAt = fetchedAt,
            temperature = current.double("temperature_2m") ?: return null,
            feelsLike = current.double("apparent_temperature")
                ?: current.double("temperature_2m") ?: 0.0,
            humidity = current.int("relative_humidity_2m") ?: 0,
            cloudCover = current.int("cloud_cover") ?: 0,
            precipitation = current.double("precipitation") ?: 0.0,
            rainSoFarMm = hours.rainFallenBy(zone, today, observedAt),
            // Open-Meteo reports snow depth in metres; the village speaks in
            // centimetres, and a "0.15" on screen would be read as none.
            snowDepthCm = snow * 100.0,
            wind = Wind(
                speedKmh = current.double("wind_speed_10m") ?: 0.0,
                gustKmh = current.double("wind_gusts_10m") ?: 0.0,
                directionDegrees = current.int("wind_direction_10m") ?: 0,
            ),
            condition = WeatherCondition.fromWmoCode(current.int("weather_code") ?: -1),
            isDay = (current.int("is_day") ?: 1) == 1,
            sunrise = daily.timeAt("sunrise", zone, todayIndex),
            sunset = daily.timeAt("sunset", zone, todayIndex),
            dryDays = dryRun?.days,
            dryDaysAtLeast = dryRun?.capped ?: false,
            peakToday = hours.peakFireHour(zone, today),
            today = todayForecast,
            // Only what is still to come. A forecast list that opens on
            // yesterday is a list nobody reads twice.
            days = days.filter {
                !LocalDateTime.ofEpochSecond(it.date / 1000, 0, zone).toLocalDate().isBefore(today)
            },
            hours = hours.filter { it.time >= observedAt }.take(24),
        )
    }.getOrNull()

    // ------------------------------------------------------------- json access

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

    private fun JsonObject.time(key: String, zone: ZoneOffset): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNullSafe()?.let { parseLocal(it, zone) }

    /** JsonNull reads back as the four characters `null`, which is not a time. */
    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (!isString && content == "null") null else content

    /**
     * Local wall-clock to epoch millis.
     *
     * Every timestamp in the response is a local time with no offset on it —
     * `2026-08-19T08:30` — and the offset arrives once, separately. Parsing
     * these as UTC is the classic way to be three hours wrong all summer and
     * two all winter, which on a sunset time is the difference between useful
     * and worse than nothing.
     */
    private fun parseLocal(value: String, zone: ZoneOffset): Long? = runCatching {
        val text = if (value.length == 16) "$value:00" else value
        LocalDateTime.parse(text, DATE_TIME).toInstant(zone).toEpochMilli()
    }.getOrNull()

    private fun JsonObject?.array(key: String): JsonArray? = this?.get(key) as? JsonArray

    private fun JsonArray.doubleAt(index: Int): Double? =
        (getOrNull(index) as? JsonPrimitive)?.doubleOrNull

    private fun JsonArray.intAt(index: Int): Int? =
        (getOrNull(index) as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

    private fun JsonArray.stringAt(index: Int): String? =
        (getOrNull(index) as? JsonPrimitive)?.contentOrNullSafe()

    private fun JsonObject?.timeAt(key: String, zone: ZoneOffset, index: Int): Long? {
        if (index < 0) return null
        return array(key)?.stringAt(index)?.let { parseLocal(it, zone) }
    }

    private fun JsonObject?.valueAtHour(key: String, zone: ZoneOffset, at: Long): Double? {
        val times = array("time") ?: return null
        val values = array(key) ?: return null
        // The hour the reading falls in, not the nearest: an observation at
        // 08:30 belongs to 08:00.
        var best = -1
        for (i in times.indices) {
            val t = times.stringAt(i)?.let { parseLocal(it, zone) } ?: continue
            if (t <= at) best = i else break
        }
        return if (best >= 0) values.doubleAt(best) else null
    }

    private fun JsonObject?.readHours(zone: ZoneOffset): List<HourForecast> {
        val times = array("time") ?: return emptyList()
        val temperature = array("temperature_2m")
        val humidity = array("relative_humidity_2m")
        val precipitation = array("precipitation")
        val chance = array("precipitation_probability")
        val code = array("weather_code")
        val wind = array("wind_speed_10m")
        val gust = array("wind_gusts_10m")
        val direction = array("wind_direction_10m")
        return times.indices.mapNotNull { i ->
            val time = times.stringAt(i)?.let { parseLocal(it, zone) } ?: return@mapNotNull null
            HourForecast(
                time = time,
                temperature = temperature?.doubleAt(i) ?: return@mapNotNull null,
                humidity = humidity?.intAt(i) ?: 0,
                windKmh = wind?.doubleAt(i) ?: 0.0,
                gustKmh = gust?.doubleAt(i) ?: 0.0,
                windDirection = direction?.intAt(i) ?: 0,
                precipitationMm = precipitation?.doubleAt(i) ?: 0.0,
                precipitationChance = chance?.intAt(i) ?: 0,
                condition = WeatherCondition.fromWmoCode(code?.intAt(i) ?: -1),
            )
        }
    }

    private fun JsonObject?.readDays(zone: ZoneOffset): List<DayForecast> {
        val times = array("time") ?: return emptyList()
        val code = array("weather_code")
        val high = array("temperature_2m_max")
        val low = array("temperature_2m_min")
        val rain = array("precipitation_sum")
        val wind = array("wind_speed_10m_max")
        val gust = array("wind_gusts_10m_max")
        return times.indices.mapNotNull { i ->
            val day = times.stringAt(i)?.let {
                runCatching { LocalDate.parse(it, DATE) }.getOrNull()
            } ?: return@mapNotNull null
            DayForecast(
                date = day.atStartOfDay().toInstant(zone).toEpochMilli(),
                high = high?.doubleAt(i) ?: return@mapNotNull null,
                low = low?.doubleAt(i) ?: return@mapNotNull null,
                precipitationMm = rain?.doubleAt(i) ?: 0.0,
                maxWindKmh = wind?.doubleAt(i) ?: 0.0,
                maxGustKmh = gust?.doubleAt(i) ?: 0.0,
                condition = WeatherCondition.fromWmoCode(code?.intAt(i) ?: -1),
            )
        }
    }

    /**
     * Rain that has already fallen today, summed over the hours up to now.
     *
     * The daily row cannot answer this: `precipitation_sum` is the provider's
     * total for the whole calendar day, forecast hours included. Anything that
     * says something about the ground *right now* needs the hours that have
     * actually been.
     */
    private fun List<HourForecast>.rainFallenBy(
        zone: ZoneOffset,
        today: LocalDate,
        now: Long,
    ): Double {
        // Both edges matter, and both were wrong once in opposite directions.
        //
        // Open-Meteo documents an hourly `precipitation` as the "preceding hour
        // sum": the figure stamped 10:00 is the rain that fell between 09:00
        // and 10:00. So the entry stamped at midnight belongs to *yesterday*
        // and must not be counted as today's — a bounded error, but one that
        // sits exactly on the threshold where a second step of relief is given
        // — while the entry stamped at the current hour is already complete and
        // must be.
        val midnight = today.atStartOfDay().toInstant(zone).toEpochMilli()
        return filter { it.time > midnight && it.time <= now }
            .sumOf { it.precipitationMm }
    }

    /**
     * The hour of [today] whose temperature and humidity give the worst
     * Ångström index.
     *
     * Scanned over the whole local day rather than only the hours still to
     * come, so the answer does not change character at six in the evening —
     * "today was a category four day" stays true after the peak has passed,
     * which is when someone reading the sheet is deciding about tomorrow.
     */
    private fun List<HourForecast>.peakFireHour(zone: ZoneOffset, today: LocalDate): HourForecast? =
        filter { LocalDateTime.ofEpochSecond(it.time / 1000, 0, zone).toLocalDate() == today }
            .minByOrNull { FireRisk.angstrom(it.temperature, it.humidity.toDouble()) }

    /**
     * Completed days since the last wet one.
     *
     * Today is excluded on purpose: a day that is four hours old has not had
     * its chance to rain yet, and counting it would make every morning look one
     * day drier than it is. Returns null when the response carried no history
     * to count, so the caller can say "unknown" rather than "zero".
     */
    private fun JsonObject?.dryDaysBefore(zone: ZoneOffset, today: LocalDate): DryRun? {
        val times = array("time") ?: return null
        val rain = array("precipitation_sum") ?: return null
        val past = times.indices.mapNotNull { i ->
            val day = times.stringAt(i)?.let { runCatching { LocalDate.parse(it, DATE) }.getOrNull() }
                ?: return@mapNotNull null
            if (day.isBefore(today)) day to (rain.doubleAt(i) ?: 0.0) else null
        }.sortedByDescending { it.first }
        if (past.isEmpty()) return null
        var count = 0
        for ((_, mm) in past) {
            if (mm >= WeatherSnapshot.WET_DAY_MM) break
            count++
        }
        // Whether the run simply ran out of history. A drought longer than the
        // window is reported as "31 or more" rather than as a flat 31, because
        // the two are different facts and the screen should not claim the
        // smaller one.
        return DryRun(days = count, capped = count == past.size)
    }

    private data class DryRun(val days: Int, val capped: Boolean)
}
