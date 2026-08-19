package gr.agiosnektarios.village.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import gr.agiosnektarios.village.core.weather.FireRisk
import gr.agiosnektarios.village.data.settings.SettingsRepository
import gr.agiosnektarios.village.data.weather.WeatherRepository
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeatherUiState(
    val snapshot: WeatherSnapshot? = null,
    val fire: FireRisk.Assessment? = null,
    val loading: Boolean = false,
    val stale: Boolean = false,
    val animateOnMap: Boolean = false,
    /**
     * Whether the fire reading is for the day it is being read on.
     *
     * A cached response restored after a few days offline still parses, and
     * [FireRisk] will happily assess the day it was taken — so without this a
     * phone that had been out of signal since Sunday would show Sunday's purple
     * "alert" badge over Wednesday's map, with nothing on the badge to say so.
     * The level is still shown in the sheet, where it can be dated; the badge
     * on the map is suppressed, because a colour with no date on it is a claim
     * about now.
     */
    val fireIsToday: Boolean = false,
    /**
     * Whether the reading is recent enough to animate over the map.
     *
     * Rain drawn across the village is a statement about this minute. Half an
     * hour old is fine; three hours is not, and a reading from a day the phone
     * spent in a drawer is a lie told with animation.
     */
    val fresh: Boolean = false,
)

/**
 * The weather, as the map and its sheet need it.
 *
 * The assessment is derived here rather than in the repository because it is a
 * presentation concern with a policy in it: nothing outside the UI has any use
 * for a fire level. The repository's job stops at "what is the weather".
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weather: WeatherRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /**
     * A minute hand.
     *
     * `fireIsToday` and `fresh` are answers about *now*, and computing them
     * only when the repository emits meant they were frozen at the moment the
     * app was opened: leave it running across midnight and yesterday's badge
     * was still labelled today's, and the animated rain kept falling from a
     * reading hours old despite the freshness gate that was supposed to stop
     * exactly that.
     */
    private val minute = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    val uiState: StateFlow<WeatherUiState> = combine(
        weather.state,
        settings.settings.map { it.showWeatherLayer },
        minute,
    ) { state, animate, now ->
        val snapshot = state.snapshot
        WeatherUiState(
            snapshot = snapshot,
            fire = snapshot?.let(FireRisk::assess),
            loading = state.loading,
            stale = state.stale,
            animateOnMap = animate,
            fireIsToday = snapshot != null && isSameLocalDay(snapshot.observedAt, now),
            fresh = snapshot != null && now - snapshot.observedAt <= FRESH_MS,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    init {
        refresh()
    }

    /**
     * Safe to call on every resume. The repository decides whether the reading
     * is old enough to be worth the radio; the caller does not have to know.
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch { weather.refresh(force) }
    }

    fun setAnimateOnMap(enabled: Boolean) {
        viewModelScope.launch { settings.setShowWeatherLayer(enabled) }
    }

    private companion object {
        /** How recent an observation has to be before the map may animate it. */
        const val FRESH_MS = 3 * 60 * 60 * 1000L
    }
}

/**
 * Same calendar day in the device's zone.
 *
 * Not `(a / DAY) == (b / DAY)`, which is a comparison of UTC days and is
 * therefore wrong for three hours of every Greek summer night.
 */
private fun isSameLocalDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
