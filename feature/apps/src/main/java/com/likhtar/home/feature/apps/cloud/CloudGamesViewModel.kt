package com.likhtar.home.feature.apps.cloud

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.data.cloud.CloudCatalogRepository
import com.likhtar.home.core.data.cloud.CloudCatalogState
import com.likhtar.home.core.data.cloud.CloudGame
import com.likhtar.home.core.data.cloud.CloudLaunch
import com.likhtar.home.core.data.cloud.CloudService
import com.likhtar.home.core.data.cloud.cloudLink
import com.likhtar.home.core.data.cloud.mainLaunch
import com.likhtar.home.core.data.cloud.mainService
import com.likhtar.home.core.data.cloud.packages
import com.likhtar.home.feature.apps.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import com.likhtar.home.core.data.cloud.toCloudGame
import kotlinx.coroutines.launch

/** Свій порядок і сховані блоки сторінки каталогу (за ключем блоку). */
data class CloudBlockPrefs(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet())

/**
 * Хмарні ігри на екрані: ряд у «Іграх», сторінка каталогу й картка гри. Каталог — спільний
 * [CloudCatalogRepository]; тут — запуск гри й налаштування блоків.
 */
@HiltViewModel
class CloudGamesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CloudCatalogRepository,
    private val libraryRepository: com.likhtar.home.core.data.cloud.CloudLibraryRepository,
    private val romLibrary: com.likhtar.home.core.data.RomLibrary,
) : ViewModel() {
    val catalog: StateFlow<CloudCatalogState> = repository.state

    /**
     * Фільтр каталогу живе тут, а не в `remember` екрана: картка гри знищує композицію каталогу, і
     * після Back фільтр скидався — разом із ним зникала й картка, на яку треба повернути фокус.
     */
    var catalogFilter: androidx.compose.runtime.MutableState<com.likhtar.home.core.data.cloud.CloudFilter>? = null

    /** Хмарні ігри й ретро з теки ROMs — в одних списках (docs/cloud-games-design.md, «Ретро-ігри»). */
    val games: StateFlow<List<CloudGame>> = kotlinx.coroutines.flow.combine(repository.state, romLibrary.games) { c, roms ->
        c.games + roms.map { it.toCloudGame() }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** Мої сервіси, Game Pass, «Граю зараз», «Улюблені», «У мене є». */
    val library: StateFlow<com.likhtar.home.core.data.cloud.CloudLibrary> = libraryRepository.library

    fun updateLibrary(change: (com.likhtar.home.core.data.cloud.CloudLibrary) -> com.likhtar.home.core.data.cloud.CloudLibrary) = libraryRepository.update(change)

    private val prefs get() = context.getSharedPreferences("cloud_games", Context.MODE_PRIVATE)
    private val blockPrefs = MutableStateFlow(readBlocks())
    val blocks: StateFlow<CloudBlockPrefs> = blockPrefs.asStateFlow()

    init {
        viewModelScope.launch { repository.load() }
        viewModelScope.launch { romLibrary.refresh() }
    }

    fun game(key: String): CloudGame? = catalog.value.games.firstOrNull { it.key == key }

    /**
     * Запустити гру: у застосунку сервісу, без нього — у браузері (GeForce NOW і Xbox працюють і там,
     * Boosteroid — сторінкою гри). Нічим не відкрилось — кажемо, а не мовчимо (анти-ціль «битий запуск»).
     */
    fun play(game: CloudGame, service: CloudService? = game.mainService(library.value.services), launch: CloudLaunch? = service?.let(game::mainLaunch)) {
        game.rom?.let { rom ->
            // Ретро: RetroArch з ядром, як і в ряду образів «Ігор»; «Граю зараз» — спільний.
            val retro = romLibrary.games.value.firstOrNull { it.path == rom.path }
            val error = retro?.let(romLibrary::launch) ?: if (retro == null) context.getString(R.string.cloud_no_file) else null
            if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show() else libraryRepository.update { it.played(game.key) }
            return
        }
        if (service == null || launch == null) return
        // Запуск піднімає гру на початок «Граю зараз» (макет: launch()).
        libraryRepository.update { it.played(game.key) }
        val link = cloudLink(service, launch)
        val pkg = service.packages.firstOrNull(::installed)
        val app = pkg?.let {
            Intent(Intent.ACTION_VIEW, link.app.toUri()).setPackage(it)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (link.clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
        }
        if (app != null && start(app)) return
        if (start(Intent(Intent.ACTION_VIEW, link.web.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        Toast.makeText(context, context.getString(R.string.cloud_no_app, serviceName(service)), Toast.LENGTH_LONG).show()
    }

    /** «↗ У браузері»: справжнє посилання сервісу. */
    fun openWeb(game: CloudGame) {
        val service = game.mainService(library.value.services) ?: game.mainService() ?: return
        val launch = game.mainLaunch(service) ?: return
        start(Intent(Intent.ACTION_VIEW, cloudLink(service, launch).web.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** ↑ з кнопок картки — трейлер в офіційному YouTube, як на сторінці фільму в Кінозалі. */
    fun openTrailer(game: CloudGame) {
        if (game.trailer.isEmpty()) return
        start(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${game.trailer}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun moveBlock(visible: List<String>, key: String, by: Int) {
        val i = visible.indexOf(key)
        val j = i + by
        if (i < 0 || j !in visible.indices) return
        val order = visible.toMutableList().apply { this[i] = this[j]; this[j] = key }
        saveBlocks(blockPrefs.value.copy(order = order + blockPrefs.value.hidden))
    }

    fun setHidden(key: String, hidden: Boolean) {
        val now = blockPrefs.value
        saveBlocks(now.copy(hidden = if (hidden) now.hidden + key else now.hidden - key))
    }

    fun resetBlocks() = saveBlocks(CloudBlockPrefs())

    private fun saveBlocks(value: CloudBlockPrefs) {
        blockPrefs.value = value
        prefs.edit {
            putString("order", value.order.joinToString(","))
            putStringSet("hidden", value.hidden)
        }
    }

    private fun readBlocks() = CloudBlockPrefs(
        order = prefs.getString("order", "").orEmpty().split(',').filter { it.isNotEmpty() },
        hidden = prefs.getStringSet("hidden", emptySet()).orEmpty().toSet(),
    )

    private fun installed(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, PackageManager.GET_META_DATA) }.isSuccess

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Назва сервісу для людей. */
fun serviceName(service: CloudService): String = when (service) {
    CloudService.BOOSTEROID -> "Boosteroid"
    CloudService.GEFORCE_NOW -> "GeForce NOW"
    CloudService.XBOX -> "Xbox Cloud"
}
