package gr.agiosnektarios.village.core.weather

import gr.agiosnektarios.village.core.model.DayForecast
import gr.agiosnektarios.village.core.model.HourForecast
import gr.agiosnektarios.village.core.model.WeatherCondition
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import gr.agiosnektarios.village.core.model.Wind
import gr.agiosnektarios.village.data.weather.OpenMeteo
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireRiskTest {

    private val athens = ZoneId.of("Europe/Athens")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(athens).toInstant().toEpochMilli()

    private fun hour(
        time: String,
        temperature: Double,
        humidity: Int,
        wind: Double = 5.0,
        gust: Double = 10.0,
    ) = HourForecast(
        time = at(time),
        temperature = temperature,
        humidity = humidity,
        windKmh = wind,
        gustKmh = gust,
        windDirection = 0,
        precipitationMm = 0.0,
        precipitationChance = 0,
        condition = WeatherCondition.CLEAR,
    )

    private fun snapshot(
        peak: HourForecast? = hour("2026-08-19T15:00", 30.1, 35),
        now: String = "2026-08-19T08:45",
        temperature: Double = 23.6,
        humidity: Int = 64,
        precipitation: Double = 0.0,
        /** Rain that has already fallen today. */
        rainSoFar: Double = 0.0,
        /** The provider's total for the whole day, forecast hours included. */
        rainForecastToday: Double = 0.0,
        dailyMaxWindKmh: Double = 0.0,
        dailyMaxGustKmh: Double = 0.0,
        snowCm: Double = 0.0,
        dryDays: Int? = 7,
        wind: Wind = Wind(3.3, 13.0, 319),
    ) = WeatherSnapshot(
        observedAt = at(now),
        fetchedAt = at(now),
        temperature = temperature,
        feelsLike = temperature,
        humidity = humidity,
        cloudCover = 0,
        precipitation = precipitation,
        rainSoFarMm = rainSoFar,
        snowDepthCm = snowCm,
        wind = wind,
        condition = WeatherCondition.CLEAR,
        isDay = true,
        sunrise = null,
        sunset = null,
        dryDays = dryDays,
        peakToday = peak,
        today = DayForecast(
            date = at(now.substringBefore('T') + "T00:00"),
            high = peak?.temperature ?: temperature,
            low = 19.9,
            precipitationMm = rainForecastToday,
            maxWindKmh = dailyMaxWindKmh,
            maxGustKmh = dailyMaxGustKmh,
            condition = WeatherCondition.CLEAR,
        ),
        days = emptyList(),
        hours = emptyList(),
    )

    /**
     * Values computed by hand from `I = RH/20 + (27 − T)/10`, not read back
     * out of the implementation.
     */
    @Test
    fun `angstrom matches the published formula`() {
        assertEquals(1.44, FireRisk.angstrom(30.1, 35.0), 0.0001)
        assertEquals(3.54, FireRisk.angstrom(23.6, 64.0), 0.0001)
        assertEquals(0.20, FireRisk.angstrom(35.0, 20.0), 0.0001)
        // Humidity outside 0-100 is a provider fault, not a reason to produce
        // a fire level from nonsense.
        assertEquals(FireRisk.angstrom(20.0, 100.0), FireRisk.angstrom(20.0, 140.0), 0.0001)
    }

    @Test
    fun `levels sit on the calibrated breakpoints`() {
        assertEquals(FireRisk.Level.LOW, FireRisk.levelOf(2.3))
        assertEquals(FireRisk.Level.MODERATE, FireRisk.levelOf(2.2))
        assertEquals(FireRisk.Level.MODERATE, FireRisk.levelOf(1.3))
        assertEquals(FireRisk.Level.HIGH, FireRisk.levelOf(1.2))
        assertEquals(FireRisk.Level.HIGH, FireRisk.levelOf(0.71))
        assertEquals(FireRisk.Level.VERY_HIGH, FireRisk.levelOf(0.7))
        assertEquals(FireRisk.Level.VERY_HIGH, FireRisk.levelOf(0.11))
        assertEquals(FireRisk.Level.EXTREME, FireRisk.levelOf(0.1))
        assertEquals(FireRisk.Level.EXTREME, FireRisk.levelOf(-1.0))
    }

    /**
     * The dry-spell threshold has to be reachable through the window the
     * forecast is actually asked for. It was 20 against a request for 7 days
     * of history, so the rule could never fire at all.
     */
    @Test
    fun `the drought threshold fits inside the history window`() {
        assertTrue(
            "DRY_SPELL_DAYS=${FireRisk.DRY_SPELL_DAYS} exceeds PAST_DAYS=${OpenMeteo.PAST_DAYS}",
            FireRisk.DRY_SPELL_DAYS <= OpenMeteo.PAST_DAYS,
        )
    }

    /**
     * The whole reason the peak hour exists.
     *
     * Both readings are the same August day. Assessed at a quarter to nine it
     * is 23.6 degrees at 64% and every index calls it safe; the day itself
     * reaches 30.1 at 35%. If the morning's answer were the day's answer, the
     * app would have said "low" to somebody deciding at breakfast whether to
     * burn their prunings.
     */
    @Test
    fun `assesses the day's worst hour, not the current one`() {
        val morning = FireRisk.assess(snapshot())
        assertEquals(FireRisk.Level.MODERATE, morning.level)
        assertEquals(30.1, morning.temperature, 0.001)
        assertEquals(at("2026-08-19T15:00"), morning.at)

        val withoutHours = FireRisk.assess(snapshot(peak = null))
        assertEquals(FireRisk.Level.LOW, withoutHours.level)
        assertEquals(23.6, withoutHours.temperature, 0.001)
    }

    @Test
    fun `wind raises the level by one step`() {
        val calm = FireRisk.assess(snapshot())
        val windy = FireRisk.assess(
            snapshot(peak = hour("2026-08-19T15:00", 30.1, 35, wind = 32.0)),
        )
        assertEquals(FireRisk.Level.MODERATE, calm.level)
        assertEquals(FireRisk.Level.HIGH, windy.level)
        assertTrue(windy.windy)
        assertTrue(windy.raised)
        assertTrue(windy.burningForbidden)
    }

    @Test
    fun `gusts alone are enough`() {
        val gusty = FireRisk.assess(
            snapshot(peak = hour("2026-08-19T15:00", 30.1, 35, wind = 20.0, gust = 70.0)),
        )
        assertTrue(gusty.gusty)
        assertFalse(gusty.windy)
        assertEquals(FireRisk.Level.HIGH, gusty.level)
    }

    @Test
    fun `a long drought raises it too, and both can apply`() {
        val drought = FireRisk.assess(snapshot(dryDays = 25))
        assertTrue(drought.dryHere)
        assertEquals(FireRisk.Level.HIGH, drought.level)

        val both = FireRisk.assess(
            snapshot(
                peak = hour("2026-08-19T15:00", 30.1, 35, wind = 32.0),
                dryDays = 25,
            ),
        )
        assertEquals(FireRisk.Level.VERY_HIGH, both.level)
    }

    @Test
    fun `never climbs past the top level`() {
        val worst = FireRisk.assess(
            snapshot(peak = hour("2026-08-19T15:00", 40.0, 10, wind = 60.0, gust = 90.0), dryDays = 60),
        )
        assertEquals(FireRisk.Level.EXTREME, worst.level)
    }

    /**
     * A day's rain brings the level down; lying snow ends it.
     *
     * The bug this replaces was the worst in the feature. The old rule read the
     * *current hour's* rain, applied it to an assessment computed for the day's
     * worst hour, pinned the result to LOW and forced the wind flags to false —
     * so three tenths of a millimetre of drizzle at breakfast turned a
     * dangerous, gale-blown afternoon into a green pill with the burning
     * prohibition switched off. The old test asserted exactly that.
     */
    @Test
    fun `a day's rain lowers the level, lying snow ends it`() {
        val dry = FireRisk.assess(snapshot(dryDays = 25))
        assertEquals(FireRisk.Level.HIGH, dry.level)

        // A shower is one step; a soaking is two. Treating them alike is how a
        // thirty-eight degree afternoon with a millimetre of morning rain on it
        // came back two levels down.
        val shower = FireRisk.assess(snapshot(rainSoFar = 4.0, dryDays = 25))
        assertEquals(FireRisk.Level.MODERATE, shower.level)
        assertTrue(shower.wetGround)

        val soaked = FireRisk.assess(snapshot(rainSoFar = 9.0, dryDays = 25))
        assertEquals(FireRisk.Level.LOW, soaked.level)

        val snowy = FireRisk.assess(snapshot(snowCm = 6.0))
        assertEquals(FireRisk.Level.LOW, snowy.level)
        assertTrue(snowy.wetGround)
    }

    /**
     * The residual worry after the first fix: a hot, bone-dry afternoon with a
     * shower earlier in the day must still forbid burning.
     */
    @Test
    fun `a shower does not clear a dangerous afternoon`() {
        val scorcher = FireRisk.assess(
            snapshot(
                peak = hour("2026-08-19T15:00", 38.0, 12),
                rainSoFar = 1.2,
            ),
        )
        assertEquals(FireRisk.Level.VERY_HIGH, scorcher.level)
        assertTrue(scorcher.burningForbidden)
    }

    /**
     * Rain in tonight's forecast must not wet this afternoon's ground.
     *
     * `DayForecast.precipitationMm` is the provider's total for the whole
     * calendar day, forecast hours included. Relieving the level from it meant
     * a thunderstorm due at ten in the evening took two steps off a bone-dry
     * hillside at three in the afternoon — the original bug in a new coat.
     */
    @Test
    fun `rain still to come does not count as fallen`() {
        val stormLater = FireRisk.assess(
            snapshot(
                peak = hour("2026-08-19T15:00", 34.0, 22),
                rainSoFar = 0.0,
                rainForecastToday = 5.0,
            ),
        )
        assertFalse(stormLater.wetGround)
        assertEquals(FireRisk.Level.VERY_HIGH, stormLater.level)
        assertTrue(stormLater.burningForbidden)
    }

    /**
     * The permit threshold is about a day, not about the one hour the index
     * happened to pick. A fire-peak hour at 4 Beaufort on a day that reaches 6
     * in the evening is still a day nobody gets a permit for.
     */
    @Test
    fun `wind is measured over the whole day`() {
        val gustyEvening = FireRisk.assess(
            snapshot(
                peak = hour("2026-08-19T15:00", 30.1, 35, wind = 25.0, gust = 30.0),
                dailyMaxWindKmh = 42.0,
                dailyMaxGustKmh = 58.0,
            ),
        )
        assertTrue(gustyEvening.windy)
        assertTrue(gustyEvening.burningForbidden)
    }

    /** A trace in the current hour is not a day's rain and must change nothing. */
    @Test
    fun `a trace of drizzle is not wet ground`() {
        val trace = FireRisk.assess(snapshot(precipitation = 0.3, dryDays = 25))
        assertFalse(trace.wetGround)
        assertEquals(FireRisk.Level.HIGH, trace.level)
    }

    /**
     * The half of the old bug that mattered most: rain must not be able to
     * cancel a burning prohibition that the wind is responsible for.
     */
    @Test
    fun `rain does not make a gale safe to burn in`() {
        val gale = FireRisk.assess(
            snapshot(
                peak = hour("2026-08-19T15:00", 34.0, 18, wind = 40.0, gust = 75.0),
                rainSoFar = 3.0,
            ),
        )
        assertTrue(gale.wetGround)
        assertTrue(gale.windy)
        assertTrue(gale.gusty)
        assertTrue(gale.burningForbidden)
    }

    /** 1 May to 31 October, which is regulation rather than weather. */
    @Test
    fun `knows the regulated fire season`() {
        assertFalse(FireRisk.isFireSeason(at("2026-04-30T12:00")))
        assertTrue(FireRisk.isFireSeason(at("2026-05-01T00:30")))
        assertTrue(FireRisk.isFireSeason(at("2026-10-31T23:30")))
        assertFalse(FireRisk.isFireSeason(at("2026-11-01T00:30")))
    }

    /**
     * Out of season, the same weather must not produce a burning prohibition:
     * the prohibition is a rule about a period, not about a temperature.
     */
    @Test
    fun `no prohibition outside the season`() {
        val january = FireRisk.assess(
            snapshot(
                peak = hour("2026-01-15T14:00", 18.0, 20, wind = 40.0),
                now = "2026-01-15T09:00",
                dryDays = 25,
            ),
        )
        assertTrue(january.windy)
        assertFalse(january.inFireSeason)
        assertFalse(january.burningForbidden)
    }

    @Test
    fun `beaufort follows the published bands`() {
        assertEquals(0, Wind.beaufortOf(0.5))
        assertEquals(1, Wind.beaufortOf(1.0))
        assertEquals(3, Wind.beaufortOf(19.9))
        assertEquals(4, Wind.beaufortOf(20.0))
        assertEquals(5, Wind.beaufortOf(29.0))
        assertEquals(8, Wind.beaufortOf(62.0))
        assertEquals(12, Wind.beaufortOf(200.0))
    }

    /** North is 0 and wraps, so 350 degrees is northerly and not north-west. */
    @Test
    fun `wind sector wraps around north`() {
        assertEquals(0, Wind(0.0, 0.0, 0).sector)
        assertEquals(0, Wind(0.0, 0.0, 350).sector)
        assertEquals(1, Wind(0.0, 0.0, 45).sector)
        assertEquals(4, Wind(0.0, 0.0, 180).sector)
        assertEquals(7, Wind(0.0, 0.0, 319).sector)
    }
}
