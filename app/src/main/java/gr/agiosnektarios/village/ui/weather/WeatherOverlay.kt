package gr.agiosnektarios.village.ui.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.core.model.WeatherCondition
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The weather, drawn over the map.
 *
 * ## Why this is in screen space and not a map layer
 *
 * The obvious build is a MapLibre layer, and it would be wrong. The settlement
 * is 1.2 km across: there is no meaningful variation in rainfall or wind over
 * it, nothing to reference a geographic layer *to*, and pinning the drops to
 * the ground would mean rain that slides sideways when you pan the map — which
 * is exactly what real rain does not do. Weather happens between the sky and
 * the observer, so it belongs in the plane of the glass.
 *
 * ## What is drawn
 *
 * Light first, and it is the part that does most of the work: a warm wash from
 * the direction the sun is in, thinned by cloud cover, going cool and flat
 * after sunset. Then whatever is falling, at the angle the wind is pushing it.
 * Then the wind itself, as slow streaks, but only once there is enough of it to
 * be worth saying — a permanent drift across the map would be decoration, and
 * what makes this useful is that it is *not* on most days.
 *
 * ## Restraint
 *
 * This runs over the screen a resident is trying to read reports on. Every
 * count is capped, every alpha is low, and the whole thing is behind a switch
 * that is off until someone turns it on. [phase] is a parameter rather than
 * only an internal animation so a single frame can be rendered and looked at
 * in a test.
 */
@Composable
fun WeatherOverlay(
    snapshot: WeatherSnapshot,
    modifier: Modifier = Modifier,
    /**
     * Whether the map underneath is the dark basemap.
     *
     * Not cosmetic. The first version drew wind as white streaks at six
     * percent alpha and rain in pale blue, which over the light basemap was
     * literally nothing — the same invisible-chrome mistake this codebase has
     * now made four times, and it was invisible until the frame was rendered
     * and looked at. Weather has to be drawn in whatever contrasts with the
     * ground it falls on.
     */
    dark: Boolean = false,
    phase: Float? = null,
) {
    val drift by rememberInfiniteTransition(label = "weather").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing)),
        label = "weatherPhase",
    )
    val t = phase ?: drift

    // Particles are placed once and then moved by the phase, so the rain does
    // not reshuffle itself on every recomposition.
    val seeds = remember { List(MAX_PARTICLES) { Random(it * 7919).nextFloat() to Random(it * 104729).nextFloat() } }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawLight(snapshot, t)
        when {
            snapshot.condition == WeatherCondition.SNOW || snapshot.snowDepthCm > 0.0 ->
                drawSnow(snapshot, seeds, t, dark)
            snapshot.condition.isWet || snapshot.precipitation > 0.0 ->
                drawRain(snapshot, seeds, t, dark)
            snapshot.condition == WeatherCondition.FOG -> drawFog(t)
            else -> Unit
        }
        drawWind(snapshot, seeds, t, dark)
    }
}

/**
 * The sky's light, as a wash over the map.
 *
 * Cloud cover thins it rather than replacing it with grey: an overcast day is
 * not a grey filter over the world, it is the same world with the warmth taken
 * out of it. After sunset the wash goes cool and slightly darker, which is the
 * cheapest possible way to make the map know what time it is.
 */
private fun DrawScope.drawLight(snapshot: WeatherSnapshot, phase: Float) {
    val clear = 1f - (snapshot.cloudCover / 100f)
    if (snapshot.isDay) {
        val warmth = 0.08f * clear
        if (warmth > 0.01f) {
            // Where the sun actually is, from the snapshot's own sunrise and
            // sunset, rather than a corner chosen once and described as "the
            // direction the sun is in". It travels left to right across the day
            // and rides highest at noon — a screen-space abstraction of an
            // arc, not a projection, but one that is at least a function of the
            // hour instead of a constant.
            val day = snapshot.sunrise?.let { rise ->
                snapshot.sunset?.let { set ->
                    if (set > rise) {
                        ((snapshot.observedAt - rise).toFloat() / (set - rise)).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                }
            } ?: 0.5f
            val elevation = sin(day * Math.PI.toFloat())
            // A slow wander, so the light is never quite static without ever
            // asking to be watched.
            val wander = sin(phase * 2f * Math.PI.toFloat()) * size.minDimension * 0.02f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(SUN.copy(alpha = warmth), Color.Transparent),
                    center = Offset(
                        size.width * (0.15f + 0.70f * day) + wander,
                        size.height * (0.34f - 0.22f * elevation),
                    ),
                    radius = size.maxDimension * 0.75f,
                ),
            )
        }
        val greyness = 0.06f * (1f - clear)
        if (greyness > 0.005f) drawRect(color = OVERCAST.copy(alpha = greyness))
    } else {
        drawRect(color = NIGHT.copy(alpha = 0.16f))
    }
}

/**
 * Rain, leaning the way the wind is pushing it.
 *
 * Length and count both come from the rate, so a drizzle and a downpour do not
 * look the same — which is the whole of what the village asked for when they
 * said "and how much".
 */
private fun DrawScope.drawRain(
    snapshot: WeatherSnapshot,
    seeds: List<Pair<Float, Float>>,
    phase: Float,
    dark: Boolean,
) {
    val heavy = snapshot.condition == WeatherCondition.HEAVY_RAIN ||
        snapshot.condition == WeatherCondition.THUNDERSTORM
    val rate = when {
        heavy -> 1f
        snapshot.condition == WeatherCondition.DRIZZLE -> 0.25f
        else -> min(1f, max(0.35f, snapshot.precipitation.toFloat() / 4f))
    }
    val count = (MAX_PARTICLES * rate).toInt().coerceAtLeast(12)
    val length = size.height * (0.02f + 0.05f * rate)
    // Wind pushes the fall off vertical. Capped, because rain at 45 degrees
    // over a map reads as scratches rather than as weather.
    val lean = (snapshot.wind.speedKmh.toFloat() / 60f).coerceIn(0f, 0.45f) *
        sin(Math.toRadians(snapshot.wind.arrowRotation.toDouble())).toFloat()
    val stroke = if (heavy) 1.7.dp.toPx() else 1.1.dp.toPx()

    for (i in 0 until count) {
        val (sx, sy) = seeds[i]
        // Three speeds, so the fall has depth instead of moving as one sheet.
        val speed = 1f + (i % 3) * 0.45f
        val y = ((sy + phase * speed) % 1f) * (size.height + length) - length
        val x = sx * size.width + lean * y
        drawLine(
            color = (if (dark) RAIN_ON_DARK else RAIN_ON_LIGHT)
                .copy(alpha = if (heavy) 0.42f else 0.30f),
            start = Offset(x, y),
            end = Offset(x + lean * length, y + length),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Snow: slow, wandering, and with the ground gone pale underneath if any of it
 * is lying.
 */
private fun DrawScope.drawSnow(
    snapshot: WeatherSnapshot,
    seeds: List<Pair<Float, Float>>,
    phase: Float,
    dark: Boolean,
) {
    if (snapshot.snowDepthCm > 0.0) {
        // Cool blue-grey rather than white, and the reason is the flakes.
        // Whitening the ground and then dropping white flakes on it made both
        // disappear — the render showed a pale wash with faint rings floating
        // in it. Lying snow in daylight *is* blue-grey in the shade, so the
        // honest colour is also the one that leaves the flakes somewhere to
        // land. Deepens with depth, and stops mattering past a hand's width.
        val cover = (snapshot.snowDepthCm.toFloat() / 15f).coerceIn(0.12f, 0.38f)
        drawRect(color = SNOW_GROUND.copy(alpha = cover))
    }
    if (snapshot.condition != WeatherCondition.SNOW) return

    val lean = (snapshot.wind.speedKmh.toFloat() / 50f).coerceIn(0f, 0.5f) *
        sin(Math.toRadians(snapshot.wind.arrowRotation.toDouble())).toFloat()
    for (i in 0 until SNOW_PARTICLES) {
        val (sx, sy) = seeds[i]
        val speed = 0.35f + (i % 4) * 0.12f
        val y = ((sy + phase * speed) % 1f) * size.height
        // A sideways sway, which is what tells snow from falling dots.
        val sway = sin((phase * 2f + sx * 6f) * Math.PI.toFloat()) * size.width * 0.02f
        val x = (sx * size.width + lean * y + sway + size.width) % size.width
        drawCircle(
            color = Color.White.copy(alpha = if (dark) 0.75f else 0.95f),
            radius = (1.4f + (i % 3) * 0.8f).dp.toPx(),
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawFog(phase: Float) {
    val slide = (phase % 1f) * size.height * 0.2f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                FOG.copy(alpha = 0.22f),
                FOG.copy(alpha = 0.10f),
                FOG.copy(alpha = 0.24f),
            ),
            startY = -slide,
            endY = size.height + slide,
        ),
    )
}

/**
 * The wind, as streaks running the way it blows.
 *
 * Silent below 3 Beaufort. A village on a ridge has some air moving almost
 * always, and drawing it always would make the display mean nothing on the day
 * it matters. Above that the streaks lengthen, quicken and multiply with the
 * scale.
 *
 * Each streak lives on a wrapped grid and fades in and out across its own
 * travel, which is what stops the wrap from being visible: a streak is at zero
 * opacity at the moment it jumps. The first attempt instead spread them along
 * one perpendicular axis a screen and a half wide, and all but six of the
 * sixteen landed off the edge — the render showed a handful of scratches
 * rather than moving air.
 */
private fun DrawScope.drawWind(
    snapshot: WeatherSnapshot,
    seeds: List<Pair<Float, Float>>,
    phase: Float,
    dark: Boolean,
) {
    val beaufort = snapshot.wind.beaufort
    if (beaufort < 3) return

    val strength = ((beaufort - 2) / 6f).coerceIn(0.15f, 1f)
    val radians = Math.toRadians(snapshot.wind.arrowRotation.toDouble() - 90.0)
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    val length = size.minDimension * (0.10f + 0.16f * strength)
    val travel = size.maxDimension * 0.5f
    val colour = if (dark) WIND_ON_DARK else WIND_ON_LIGHT
    val count = (WIND_STREAKS * (0.5f + 0.5f * strength)).toInt().coerceAtLeast(10)

    for (i in 0 until count) {
        val (sx, sy) = seeds[(i + SNOW_PARTICLES) % seeds.size]
        val speed = 0.7f + (i % 3) * 0.3f
        // Staggered starts, so they are not all born and all dying together.
        val life = ((phase * speed * (0.6f + strength) + i / count.toFloat()) % 1f)
        val x = (sx * size.width + dx * life * travel).mod(size.width)
        val y = (sy * size.height + dy * life * travel).mod(size.height)
        val fade = sin(life * Math.PI.toFloat())
        drawLine(
            color = colour.copy(alpha = (0.14f + 0.16f * strength) * fade),
            start = Offset(x, y),
            end = Offset(x + dx * length, y + dy * length),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private const val CYCLE_MS = 4200
private const val MAX_PARTICLES = 90
private const val SNOW_PARTICLES = 44
private const val WIND_STREAKS = 40

private val SUN = Color(0xFFFFC46B)
private val OVERCAST = Color(0xFF5B6670)
private val NIGHT = Color(0xFF0B1220)
private val RAIN_ON_DARK = Color(0xFFBFD8EA)
private val RAIN_ON_LIGHT = Color(0xFF3E6E93)
private val FOG = Color(0xFFD3D8DC)
private val WIND_ON_DARK = Color(0xFFFFFFFF)
private val WIND_ON_LIGHT = Color(0xFF2F4858)
private val SNOW_GROUND = Color(0xFF9FB4C7)
