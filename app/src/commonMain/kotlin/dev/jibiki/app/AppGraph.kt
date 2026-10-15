package dev.jibiki.app

import dev.jibiki.app.resources.Res
import dev.jibiki.db.Database
import dev.jibiki.domain.ImportService
import dev.jibiki.domain.LibraryService
import dev.jibiki.domain.SearchService
import dev.jibiki.domain.Settings
import dev.jibiki.japanese.Deinflector
import dev.jibiki.radicals.RadicalIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

sealed interface Startup {
    data object Loading : Startup
    data class Ready(val inMemory: Boolean, val removedIncomplete: Int) : Startup
    data object OtherTab : Startup
    data class Failed(val message: String) : Startup
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val romaji: Boolean = true,
    val fontScale: Float = 1f,
    /** null = "All". */
    val activeProfile: Long? = 1L,
    val primaryFrequencyDict: Long? = null,
    val onboardingDone: Boolean = false,
)

/** Application-lifetime object graph. Survives Android configuration changes. */
class AppGraph(val platform: PlatformServices, val scope: CoroutineScope) {
    private val _startup = MutableStateFlow<Startup>(Startup.Loading)
    val startup: StateFlow<Startup> = _startup.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    lateinit var db: Database
        private set
    lateinit var importer: ImportService
        private set
    lateinit var search: SearchService
        private set
    lateinit var library: LibraryService
        private set

    private val radicalIndex = scope.async(start = CoroutineStart.LAZY) {
        RadicalIndex.parse(Res.readBytes("files/radkfilex.utf8").decodeToString())
    }

    suspend fun radicals(): RadicalIndex = radicalIndex.await()

    val navigator = Navigator()
    val imports = ImportController(this)
    val searchController = SearchController(this)

    fun start() {
        if (_startup.value != Startup.Loading) return
        scope.launch {
            _startup.value = try {
                if (!platform.acquireSingleInstance()) {
                    Startup.OtherTab
                } else {
                    db = Database.open(platform.driver, platform.dbName, platform.dbDispatcher)
                    importer = ImportService(db) { Clock.System.now().toEpochMilliseconds() }
                    search = SearchService(db, Deinflector(), ::radicals)
                    library = LibraryService(db)
                    val removed = importer.cleanupIncomplete()
                    _settings.value = loadSettings()
                    if (!_settings.value.onboardingDone) navigator.resetTo(Screen.Onboarding)
                    searchController.start(platform.initialQuery())
                    Startup.Ready(inMemory = isInMemory(), removedIncomplete = removed)
                }
            } catch (t: Throwable) {
                Startup.Failed(t.message ?: t.toString())
            }
        }
    }

    private suspend fun isInMemory(): Boolean =
        db.read { query("PRAGMA database_list") { it.getText(2) } }.firstOrNull().isNullOrEmpty()

    private suspend fun loadSettings(): AppSettings {
        val l = library
        val profile = l.setting(Settings.ACTIVE_PROFILE)
        return AppSettings(
            theme = l.setting(Settings.THEME)?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            romaji = l.setting(Settings.ROMAJI) != "0",
            fontScale = l.setting(Settings.FONT_SCALE)?.toFloatOrNull() ?: 1f,
            activeProfile = when (profile) {
                null -> 1L
                "all" -> null
                else -> profile.toLongOrNull()
            },
            primaryFrequencyDict = l.setting(Settings.PRIMARY_FREQUENCY_DICT)?.toLongOrNull(),
            onboardingDone = l.setting(Settings.ONBOARDING_DONE) == "1",
        )
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = change(old)
        _settings.value = new
        scope.launch {
            val l = library
            if (old.theme != new.theme) l.setSetting(Settings.THEME, new.theme.name)
            if (old.romaji != new.romaji) l.setSetting(Settings.ROMAJI, if (new.romaji) "1" else "0")
            if (old.fontScale != new.fontScale) l.setSetting(Settings.FONT_SCALE, new.fontScale.toString())
            if (old.activeProfile != new.activeProfile) l.setSetting(Settings.ACTIVE_PROFILE, new.activeProfile?.toString() ?: "all")
            if (old.primaryFrequencyDict != new.primaryFrequencyDict) {
                l.setSetting(Settings.PRIMARY_FREQUENCY_DICT, new.primaryFrequencyDict?.toString())
            }
            if (old.onboardingDone != new.onboardingDone) l.setSetting(Settings.ONBOARDING_DONE, if (new.onboardingDone) "1" else "0")
        }
    }
}
