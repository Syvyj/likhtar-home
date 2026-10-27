package com.likhtar.home.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Приставка, для якої образ: назва на екрані, тека обкладинок libretro і ядро RetroArch
 * (ті самі ядра, що в ES-DE). [folders] — назви тек, якими люди самі розкладають образи.
 */
enum class RetroSystem(
    val title: String,
    val thumbnails: String,
    val core: String,
    val extensions: Set<String>,
    val folders: Set<String>,
) {
    GB("Game Boy", "Nintendo - Game Boy", "gambatte", setOf("gb", "dmg", "sgb"), setOf("gb", "gameboy")),
    GBC("Game Boy Color", "Nintendo - Game Boy Color", "gambatte", setOf("gbc", "cgb"), setOf("gbc")),
    GBA("Game Boy Advance", "Nintendo - Game Boy Advance", "mgba", setOf("gba", "agb"), setOf("gba")),
    NES("NES", "Nintendo - Nintendo Entertainment System", "fceumm", setOf("nes", "fds", "unf", "unif"), setOf("nes", "famicom", "fc")),
    SNES("SNES", "Nintendo - Super Nintendo Entertainment System", "snes9x", setOf("sfc", "smc", "fig", "swc"), setOf("snes", "sfc", "superfamicom")),
    N64("Nintendo 64", "Nintendo - Nintendo 64", "mupen64plus_next_gles3", setOf("n64", "z64", "v64"), setOf("n64")),
    MEGA_DRIVE("Mega Drive", "Sega - Mega Drive - Genesis", "genesis_plus_gx", setOf("md", "gen", "smd", "68k"), setOf("megadrive", "genesis", "md")),
    MASTER_SYSTEM("Master System", "Sega - Master System - Mark III", "genesis_plus_gx", setOf("sms"), setOf("mastersystem", "sms")),
    GAME_GEAR("Game Gear", "Sega - Game Gear", "genesis_plus_gx", setOf("gg"), setOf("gamegear", "gg")),
    PC_ENGINE("PC Engine", "NEC - PC Engine - TurboGrafx 16", "mednafen_pce_fast", setOf("pce", "sgx"), setOf("pcengine", "pce", "tg16")),
    PLAYSTATION("PlayStation", "Sony - PlayStation", "pcsx_rearmed", setOf("cue", "chd", "m3u", "pbp"), setOf("psx", "ps1", "playstation")),
    PSP("PSP", "Sony - PlayStation Portable", "ppsspp", setOf("cso"), setOf("psp"));

    companion object {
        /** Розширення, за якими приставку не вгадати: zip, iso, bin — лише з назви теки. */
        val AMBIGUOUS = setOf("zip", "7z", "iso", "bin", "img")
    }
}

/** Гра з образу: файл, приставка й обкладинка, якщо libretro її має. */
data class RetroGame(
    val path: String,
    val title: String,
    val system: RetroSystem,
    val cover: String? = null,
    /** Кадр із гри — тло шапки; є там само, де обкладинка. */
    val snap: String? = null,
)

/**
 * Ігри з образів для розділу «Ігри». Образи шукаємо в теках `ROMs` (корінь пам'яті, флешки,
 * «Завантаження»); туди ж кладе образи «Передати з телефона». Обкладинки — з
 * thumbnails.libretro.com, тієї ж бази, що в самому RetroArch: список обкладинок приставки
 * тягнемо раз на місяць і шукаємо в ньому назву файлу без регіонів і дужок, тож і
 * «tetris.gb» знаходить «Tetris (World) (Rev 1)».
 *
 * Запуск — RetroArch одразу з ядром і образом, без його меню (як у ES-DE). Ядро людина один
 * раз завантажує в самому RetroArch; лаунчер його не бачить, бо воно в приватній теці RetroArch.
 */
@Singleton
class RomLibrary @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs get() = context.getSharedPreferences("likhtar_roms", Context.MODE_PRIVATE)

    private val _games = MutableStateFlow<List<RetroGame>>(emptyList())
    val games: StateFlow<List<RetroGame>> = _games.asStateFlow()

    private val _recent = MutableStateFlow(readRecent())
    /** Шляхи нещодавно запущених образів, свіжі першими. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val indexes = mutableMapOf<RetroSystem, CoverIndex>()
    private val scanLock = Mutex()

    /** Чи бачить лаунчер чужі файли (флешку, скопійоване з комп'ютера). Свої — бачить завжди. */
    val canReadAll: Boolean
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            else -> context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** Перечитати теки й підтягнути обкладинки. Дешево: кілька тек, без читання самих образів. */
    suspend fun refresh() = scanLock.withLock {
        val started = System.currentTimeMillis()
        val found = withContext(Dispatchers.IO) { scan() }
        val known = _games.value.filter { it.cover != null }.associateBy { it.path }
        var current = found.map { known[it.path] ?: it }.sortedWith(ORDER)
        _games.value = current
        Log.i(TAG, "образів ${found.size} за ${System.currentTimeMillis() - started} мс")
        // Обкладинки — приставка за приставкою: кожна з'являється, щойно готова.
        found.map { it.system }.distinct().forEach { system ->
            val t = System.currentTimeMillis()
            val index = withContext(Dispatchers.IO) {
                runCatching { index(system) }.onFailure { Log.w(TAG, "обкладинки ${system.title}", it) }.getOrNull()
            } ?: return@forEach
            // Знайшли обкладинку — і назву беремо з бази: «tetris.gb» стає «Tetris (World)».
            current = withContext(Dispatchers.Default) {
                current.map { game ->
                    if (game.system != system || game.cover != null) game
                    else index.pick(game.title)?.let { game.copy(title = it, cover = coverUrl(system, it), snap = coverUrl(system, it, SNAPS)) } ?: game
                }.sortedWith(ORDER)
            }
            _games.value = current
            Log.i(TAG, "${system.title}: обкладинки за ${System.currentTimeMillis() - t} мс")
        }
    }

    /** Теки, де шукати: `ROMs`/`Roms` у корені кожного сховища та в «Завантаженнях». */
    private fun roots(): List<File> {
        val volumes = context.getExternalFilesDirs(null).filterNotNull().map { File(it.path.substringBefore("/Android/")) }
        val bases = (volumes + Environment.getExternalStorageDirectory()).distinct()
        return bases.flatMap { base -> listOf(File(base, "ROMs"), File(base, "Roms"), File(base, "roms"), File(base, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")) }
            .plus(legacyDir())
            .filter { it.isDirectory }
            // Пам'ять Android регістру не розрізняє: ROMs, Roms і roms — одна тека.
            .distinctBy { key(it) }
    }

    /** До Android 10 «Передати з телефона» пише у власну теку лаунчера — її теж показуємо. */
    fun legacyDir(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads"), FOLDER)

    private fun scan(): List<RetroGame> = roots().flatMap { root ->
        root.walkTopDown().maxDepth(3).filter { it.isFile }.mapNotNull { file ->
            val system = systemOf(file.name, file.parentFile?.name) ?: return@mapNotNull null
            // Доріжки .bin до .cue — не окремі ігри.
            if (file.extension.equals("bin", true) && file.parentFile?.listFiles()?.any { it.extension.equals("cue", true) } == true) return@mapNotNull null
            RetroGame(file.path, titleOf(file.name), system)
        }.toList()
    }.distinctBy { key(File(it.path)) }

    private fun key(file: File) = runCatching { file.canonicalPath }.getOrDefault(file.path).lowercase()

    /** Назви обкладинок приставки: з кешу (місяць) або зі списку на сервері libretro. */
    private fun index(system: RetroSystem): CoverIndex {
        indexes[system]?.let { return it }
        val cache = File(context.cacheDir, "covers/${system.name}.txt")
        val names = if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < INDEX_MS) {
            cache.readLines()
        } else {
            val t = System.currentTimeMillis()
            val html = http("$THUMBS/${enc(system.thumbnails)}/Named_Boxarts/", TIMEOUT_MS, INDEX_TIMEOUT_MS) { it.text() }
            Log.i(TAG, "${system.title}: список ${html.length / 1024} КБ за ${System.currentTimeMillis() - t} мс")
            parseIndex(html).also { cache.parentFile?.mkdirs(); cache.writeText(it.joinToString("\n")) }
        }
        return CoverIndex(names).also { indexes[system] = it }
    }

    /** Запустити гру. `null` — запущено; інакше — що сказати людині. */
    fun launch(game: RetroGame): String? {
        val pm = context.packageManager
        val retroArch = RETROARCH.firstOrNull { pkg -> runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess }
        val intent = if (retroArch != null) {
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(retroArch, "com.retroarch.browser.retroactivity.RetroActivityFuture"))
                .putExtra("ROM", game.path)
                .putExtra("LIBRETRO", "/data/data/$retroArch/cores/${game.system.core}_libretro_android.so")
                .putExtra("CONFIGFILE", "${Environment.getExternalStorageDirectory()}/Android/data/$retroArch/files/retroarch.cfg")
        } else {
            // Lemuroid образів ззовні не приймає — відкриваємо його бібліотеку.
            pm.getLaunchIntentForPackage(LEMUROID) ?: pm.getLeanbackLaunchIntentForPackage(LEMUROID)
        } ?: return context.getString(R.string.data_roms_no_emulator)
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            remember(game.path)
            null
        }.getOrElse { Log.w(TAG, "запуск ${game.title}", it); context.getString(R.string.data_roms_launch_failed) }
    }

    /** Системний екран доступу до всіх файлів: без нього лаунчер бачить лише образи з телефона. */
    fun openFileAccessSettings() {
        val pkg = Uri.parse("package:${context.packageName}")
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
                add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
        intents.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }

    private fun remember(path: String) {
        val list = (listOf(path) + _recent.value.filterNot { it == path }).take(RECENT_MAX)
        prefs.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
        _recent.value = list
    }

    private fun readRecent(): List<String> = prefs.getString(KEY_RECENT, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        private const val TAG = "LikhtarRoms"
        private val ORDER = compareBy<RetroGame>({ it.system.ordinal }, { it.title.lowercase() })
        /** Тека образів у «Завантаженнях»: Download/ROMs. */
        const val FOLDER = "ROMs"
        private const val THUMBS = "https://thumbnails.libretro.com"
        private const val TIMEOUT_MS = 10_000
        private const val INDEX_TIMEOUT_MS = 30_000
        private const val INDEX_MS = 30L * 24 * 60 * 60_000
        private const val RECENT_MAX = 10
        private const val KEY_RECENT = "recent"
        private const val LEMUROID = "com.swordfish.lemuroid"
        /** Збірки RetroArch: з retroarch.com (64 і 32 біти) та з магазинів і F-Droid. */
        private val RETROARCH = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")

        /** Приставка за розширенням, а для zip/iso/bin — за назвою теки. */
        fun systemOf(fileName: String, folder: String?): RetroSystem? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val byFolder = folder?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.let { f -> RetroSystem.entries.firstOrNull { f in it.folders } }
            if (ext in RetroSystem.AMBIGUOUS) return byFolder
            val byExt = RetroSystem.entries.filter { ext in it.extensions }
            return byExt.firstOrNull { it == byFolder } ?: byExt.firstOrNull()
        }

        /** Назва для екрана: без розширення й підкреслень. Дужки лишаємо — це регіон і версія. */
        fun titleOf(fileName: String): String = fileName.substringBeforeLast('.').replace('_', ' ').trim()

        /**
         * «Tetris (World) (Rev 1)» → «tetris»: так порівнюємо файл із назвами обкладинок. Прохід по
         * символах, а не регулярки: 13 тисяч назв NES приставка інакше нормалізувала б секунди.
         */
        fun normalize(name: String): String {
            val out = StringBuilder(name.length)
            var depth = 0
            var space = true
            for (c in name) {
                when {
                    c == '(' || c == '[' -> depth++
                    c == ')' || c == ']' -> if (depth > 0) depth--
                    depth > 0 -> {}
                    c == '&' -> { if (!space) out.append(' '); out.append("and "); space = true }
                    c.isLetterOrDigit() -> { out.append(c.lowercaseChar()); space = false }
                    !space -> { out.append(' '); space = true }
                }
            }
            return out.toString().trim().removePrefix("the ")
        }

        fun pickCover(title: String, index: List<String>): String? = CoverIndex(index).pick(title)

        /**
         * Назви файлів зі сторінки-списку теки (`href="Name%20(USA).png"`). Без `Regex.findAll`:
         * на Android кожен наступний збіг заново копіює весь рядок у нативну пам'ять, і список NES
         * (4 МБ, 13 тисяч назв) приставка розбирала б хвилинами.
         */
        fun parseIndex(html: String): List<String> {
            val names = ArrayList<String>()
            var at = 0
            while (true) {
                val start = html.indexOf("href=\"", at).takeIf { it >= 0 }?.plus(6) ?: break
                val end = html.indexOf('"', start).takeIf { it >= 0 } ?: break
                at = end + 1
                val href = html.substring(start, end)
                if (href.endsWith(".png") && '/' !in href) names += URLDecoder.decode(href.removeSuffix(".png").replace("+", "%2B"), "UTF-8")
            }
            return names
        }

        /** libretro замінює в іменах файлів зірочку, скісні, двокрапку, `&`, `<>`, `?`, `|` на `_`. */
        fun coverUrl(system: RetroSystem, name: String, kind: String = BOXARTS): String =
            "$THUMBS/${enc(system.thumbnails)}/$kind/${enc(name.replace(Regex("""[&*/:`<>?\\|]"""), "_"))}.png"

        private const val BOXARTS = "Named_Boxarts"
        private const val SNAPS = "Named_Snaps"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

/**
 * Назви обкладинок приставки, згруповані за [RomLibrary.normalize] — один раз на список: у NES
 * їх ~30 тисяч, і нормалізувати їх заново для кожної гри приставка не встигала б.
 */
class CoverIndex(names: List<String>) {
    private val exact = names.toHashSet()
    private val byKey = names.groupBy(RomLibrary::normalize)

    /** Найкраща обкладинка: точна назва, інакше та сама гра — спершу США/світ/Європа. */
    fun pick(title: String): String? {
        if (title in exact) return title
        val key = RomLibrary.normalize(title)
        if (key.isEmpty()) return null
        return byKey[key]?.minByOrNull { name ->
            REGIONS.indexOfFirst { name.contains("($it") }.let { if (it < 0) REGIONS.size else it } * 10 + name.length / 40
        }
    }

    private companion object {
        val REGIONS = listOf("USA", "World", "Europe")
    }
}
