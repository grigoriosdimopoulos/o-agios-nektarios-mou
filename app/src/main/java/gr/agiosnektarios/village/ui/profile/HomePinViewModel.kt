package gr.agiosnektarios.village.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.MapBasemap
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.geo.label
import gr.agiosnektarios.village.data.location.LocationProvider
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import gr.agiosnektarios.village.data.settings.SettingsRepository
import gr.agiosnektarios.village.data.user.UserRepository
import gr.agiosnektarios.village.data.village.PlaceNamer
import gr.agiosnektarios.village.data.village.StreetNameRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomePinUiState(
    val pin: GeoPoint? = null,
    val placeLabel: String = "",
    val myPosition: GeoPoint? = null,
    val streetNames: Map<String, String> = emptyMap(),
    val basemap: MapBasemap = MapBasemap.STREETS,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class HomePinViewModel @Inject constructor(
    private val session: SessionRepository,
    private val users: UserRepository,
    private val location: LocationProvider,
    private val placeNamer: PlaceNamer,
    streetNames: StreetNameRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomePinUiState())
    val uiState: StateFlow<HomePinUiState> = _uiState.asStateFlow()

    init {
        // Opens on the house already pinned, if there is one, so correcting a
        // pin does not mean finding it again from nothing.
        val profile = (session.state.value as? SessionState.SignedIn)?.profile
        val lat = profile?.homeLat
        val lng = profile?.homeLng
        if (lat != null && lng != null) {
            _uiState.update {
                it.copy(pin = GeoPoint(lat, lng), placeLabel = profile.homePlace)
            }
        }
        viewModelScope.launch {
            streetNames.observeNamesByWay().collect { names ->
                _uiState.update { it.copy(streetNames = names) }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { current ->
                _uiState.update { it.copy(basemap = current.basemap) }
            }
        }
        viewModelScope.launch {
            location.current()?.let { point ->
                _uiState.update { state ->
                    // Only as the blue dot. Dropping the pin on the phone's
                    // position would be wrong more often than right: people set
                    // this sitting in the kitchen, but also on the bus.
                    state.copy(myPosition = point)
                }
            }
        }
    }

    fun onTap(point: GeoPoint) {
        _uiState.update { it.copy(pin = point) }
        viewModelScope.launch {
            val label = runCatching { placeNamer.describe(point) }.getOrNull().label()
            _uiState.update { if (it.pin == point) it.copy(placeLabel = label) else it }
        }
    }

    fun save() {
        val profile = (session.state.value as? SessionState.SignedIn)?.profile ?: return
        val pin = _uiState.value.pin ?: return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = users.updateHome(profile.id, pin.lat, pin.lng, _uiState.value.placeLabel)
            _uiState.update { it.copy(saving = false, saved = result.isSuccess) }
        }
    }

    fun clear() {
        val profile = (session.state.value as? SessionState.SignedIn)?.profile ?: return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = users.updateHome(profile.id, null, null, "")
            _uiState.update { it.copy(saving = false, saved = result.isSuccess, pin = null) }
        }
    }
}
