package gr.agiosnektarios.village.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.core.MapBasemap
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "village_settings")

enum class ThemeMode(val id: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class AppLanguage(val id: String, val tag: String) {
    /** Follow the device locale, falling back to Greek via the default resources. */
    SYSTEM("SYSTEM", ""),
    GREEK("EL", "el"),
    ENGLISH("EN", "en"),
    ;

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Device-local preferences. Notification *toggles* also live on the user
 * document (Cloud Functions read them before sending), but they are mirrored
 * here so the settings screen renders instantly and works offline.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val notifyComments: Boolean = true,
    val notifyStatus: Boolean = true,
    val notifyVotes: Boolean = true,
    val notifyAnnouncements: Boolean = true,
    val notifyChat: Boolean = true,
    val showBlocksLayer: Boolean = true,
    val basemap: MapBasemap = MapBasemap.STREETS,
    val hasSeenOnboarding: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFY_COMMENTS = booleanPreferencesKey("notify_comments")
        val NOTIFY_STATUS = booleanPreferencesKey("notify_status")
        val NOTIFY_VOTES = booleanPreferencesKey("notify_votes")
        val NOTIFY_ANNOUNCEMENTS = booleanPreferencesKey("notify_announcements")
        val NOTIFY_CHAT = booleanPreferencesKey("notify_chat")
        val BLOCKS_LAYER = booleanPreferencesKey("blocks_layer")
        val BASEMAP = stringPreferencesKey("basemap")
        val ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        // A corrupt preferences file must not brick the app on launch.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            AppSettings(
                themeMode = ThemeMode.fromId(prefs[Keys.THEME]),
                language = AppLanguage.fromId(prefs[Keys.LANGUAGE]),
                notifyComments = prefs[Keys.NOTIFY_COMMENTS] ?: true,
                notifyStatus = prefs[Keys.NOTIFY_STATUS] ?: true,
                notifyVotes = prefs[Keys.NOTIFY_VOTES] ?: true,
                notifyAnnouncements = prefs[Keys.NOTIFY_ANNOUNCEMENTS] ?: true,
                notifyChat = prefs[Keys.NOTIFY_CHAT] ?: true,
                showBlocksLayer = prefs[Keys.BLOCKS_LAYER] ?: true,
                basemap = MapBasemap.fromId(prefs[Keys.BASEMAP]),
                hasSeenOnboarding = prefs[Keys.ONBOARDING] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.id }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.id }

    suspend fun setShowBlocksLayer(show: Boolean) = edit { it[Keys.BLOCKS_LAYER] = show }

    suspend fun setBasemap(basemap: MapBasemap) = edit { it[Keys.BASEMAP] = basemap.id }

    suspend fun setOnboardingSeen() = edit { it[Keys.ONBOARDING] = true }

    suspend fun setNotificationPref(pref: NotificationPref, enabled: Boolean) = edit {
        it[pref.key] = enabled
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    enum class NotificationPref(internal val key: Preferences.Key<Boolean>) {
        COMMENTS(Keys.NOTIFY_COMMENTS),
        STATUS(Keys.NOTIFY_STATUS),
        VOTES(Keys.NOTIFY_VOTES),
        ANNOUNCEMENTS(Keys.NOTIFY_ANNOUNCEMENTS),
        CHAT(Keys.NOTIFY_CHAT),
    }
}
