package gr.agiosnektarios.village

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gr.agiosnektarios.village.data.settings.AppLanguage
import gr.agiosnektarios.village.ui.MainViewModel
import gr.agiosnektarios.village.ui.VillageApp
import gr.agiosnektarios.village.ui.theme.VillageTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * The deep link the UI has not consumed yet.
     *
     * The activity is `singleTask`, so a notification tapped while the app is
     * already running arrives through [onNewIntent] rather than a fresh
     * [onCreate]. Reading `intent.data` once at composition would silently drop
     * every one of those, which is most of them.
     */
    private var pendingDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDeepLink = intent?.data?.toString()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val session by viewModel.session.collectAsStateWithLifecycle()

            // AppCompat owns the per-app locale so it survives process death and
            // is honoured by resources loaded outside Compose (notifications,
            // the launcher label). Setting it recreates the activity, so this
            // only fires when the stored preference actually differs.
            LaunchedEffect(settings.language) {
                val desired = when (settings.language) {
                    AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    else -> LocaleListCompat.forLanguageTags(settings.language.tag)
                }
                if (AppCompatDelegate.getApplicationLocales() != desired) {
                    AppCompatDelegate.setApplicationLocales(desired)
                }
            }

            VillageTheme(themeMode = settings.themeMode) {
                VillageApp(
                    session = session,
                    deepLink = pendingDeepLink,
                    onDeepLinkHandled = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.data?.toString()
    }
}
