package gr.agiosnektarios.village.data.weather

import gr.agiosnektarios.village.core.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser, against a real response.
 *
 * `open_meteo_sample.json` was fetched from the live endpoint with the exact
 * query [OpenMeteo.url] builds, for this village, on 19 August 2026. Testing
 * against a hand-written fixture would only prove that the parser agrees with
 * whatever the test author imagined the provider sends.
 */
class OpenMeteoTest {

    private val body: String by lazy {
        checkNotNull(javaClass.classLoader?.getResourceAsStream("open_meteo_sample.json"))
            .bufferedReader().use { it.readText() }
    }

    private val parsed by lazy { checkNotNull(OpenMeteo.parse(body, fetchedAt = 1_787_122_900_000L)) }

    @Test
    fun `reads the current conditions`() {
        assertEquals(25.7, parsed.temperature, 0.001)
        assertEquals(26.8, parsed.feelsLike, 0.001)
        assertEquals(55, parsed.humidity)
        assertEquals(0, parsed.cloudCover)
        assertEquals(0.0, parsed.precipitation, 0.001)
        assertEquals(0.0, parsed.snowDepthCm, 0.001)
        // Rain already fallen, summed over today's hours up to the reading —
        // not the daily row, which counts hours that have not happened.
        assertEquals(0.0, parsed.rainSoFarMm, 0.001)
        assertEquals(WeatherCondition.CLEAR, parsed.condition)
        assertTrue(parsed.isDay)
        assertEquals(6.8, parsed.wind.speedKmh, 0.001)
        assertEquals(19.1, parsed.wind.gustKmh, 0.001)
        assertEquals(360, parsed.wind.directionDegrees)
    }

    /**
     * The response carries local wall-clock times and the offset separately.
     *
     * Reading `2026-08-19T10:00` as UTC is the classic way to be three hours
     * wrong all summer, which on a sunset time is worse than showing nothing.
     * 10:00 EEST is 07:00 UTC, and these are the epoch values that follow.
     */
    @Test
    fun `applies the response's own UTC offset`() {
        assertEquals(1_787_122_800_000L, parsed.observedAt)
        assertEquals(1_787_111_100_000L, parsed.sunrise) // 06:45 +03:00
        assertEquals(1_787_159_700_000L, parsed.sunset) // 20:15 +03:00
    }

    /**
     * Twenty-five completed days with under a millimetre each.
     *
     * This number is the whole argument for asking the provider for a month of
     * history instead of a week. With a seven-day window the same response
     * reported "7", the largest number it could hold — and the fire rule that
     * raises the level after twenty dry days could therefore never fire on a
     * mountain in the middle of a genuine five-week drought.
     *
     * Traces do not break the run: a day with 0.1 mm on it has not wetted
     * anything. Today is excluded, because a day four hours old has not had its
     * chance to rain.
     */
    @Test
    fun `counts the dry spell over completed days only`() {
        assertEquals(25, parsed.dryDays)
        assertFalse(parsed.dryDaysAtLeast)
    }

    /** A run that reaches the end of the window is reported as "at least". */
    @Test
    fun `says when the dry run ran out of history`() {
        val bone = body.replace(
            Regex("\"precipitation_sum\":\\[[^]]*]"),
            "\"precipitation_sum\":[" + List(35) { "0.0" }.joinToString(",") + "]",
        )
        val dry = checkNotNull(OpenMeteo.parse(bone, fetchedAt = 0L))
        assertEquals(31, dry.dryDays)
        assertTrue(dry.dryDaysAtLeast)
    }

    /**
     * The daily total counts the whole calendar day, forecast included; the
     * "so far" figure counts only hours that have been. Confusing the two let
     * an evening thunderstorm wet the ground at three in the afternoon.
     */
    @Test
    fun `separates rain fallen from rain forecast`() {
        val soaked = body.replace(
            Regex("\"precipitation\":\\[[^]]*]"),
            "\"precipitation\":[" + List(840) { "0.5" }.joinToString(",") + "]",
        )
        val wet = checkNotNull(OpenMeteo.parse(soaked, fetchedAt = 0L))
        // Ten hours: the entries stamped 01:00 through 10:00, each of which is
        // the rain of the hour *preceding* its stamp. The midnight entry is
        // yesterday's last hour and is excluded; the 10:00 entry covers
        // 09:00-10:00 and is complete, so it counts.
        assertEquals(5.0, wet.rainSoFarMm, 0.001)
        // The daily row is untouched by this and still reports the provider's
        // own sum for the day.
        assertEquals(0.0, checkNotNull(wet.today).precipitationMm, 0.001)
    }

    @Test
    fun `picks the worst hour of today rather than the current one`() {
        val peak = checkNotNull(parsed.peakToday)
        assertEquals(1_787_140_800_000L, peak.time) // 15:00 local
        assertEquals(30.1, peak.temperature, 0.001)
        assertEquals(34, peak.humidity)
        assertEquals(29.5, peak.gustKmh, 0.001)
    }

    @Test
    fun `drops days and hours already past`() {
        assertEquals(4, parsed.days.size)
        assertEquals(1_787_086_800_000L, parsed.days.first().date) // today, local midnight
        assertEquals(24, parsed.hours.size)
        assertTrue(parsed.hours.all { it.time >= parsed.observedAt })
    }

    @Test
    fun `reads today's daily figures`() {
        val today = checkNotNull(parsed.today)
        assertEquals(30.1, today.high, 0.001)
        assertEquals(19.4, today.low, 0.001)
        assertEquals(0.0, today.precipitationMm, 0.001)
        assertEquals(29.5, today.maxGustKmh, 0.001)
        assertEquals(WeatherCondition.OVERCAST, today.condition) // code 3
    }

    /**
     * A response that cannot be read must return null rather than throw.
     *
     * The caller's answer to "the provider sent something unexpected" is the
     * same as its answer to "the network was down" — keep showing the last
     * good reading — and a parse that throws would instead take the screen out.
     */
    @Test
    fun `refuses malformed input quietly`() {
        assertNull(OpenMeteo.parse("", 0L))
        assertNull(OpenMeteo.parse("not json at all", 0L))
        assertNull(OpenMeteo.parse("""{"current":{}}""", 0L))
        assertNull(OpenMeteo.parse(body.substring(0, body.length / 2), 0L))
    }

    @Test
    fun `asks for the village, at its own altitude`() {
        val url = OpenMeteo.url()
        assertTrue(url, url.contains("latitude=38.16"))
        assertTrue(url, url.contains("longitude=23.29"))
        assertTrue(url, url.contains("elevation=640"))
        assertTrue(url, url.contains("timezone=Europe%2FAthens"))
        // Every field the parser reads has to be asked for.
        assertTrue(url, url.contains("past_days=${OpenMeteo.PAST_DAYS}"))
        for (field in listOf("relative_humidity_2m", "snow_depth", "wind_gusts_10m", "precipitation_sum")) {
            assertTrue(field, url.contains(field))
        }
    }
}
