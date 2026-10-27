package com.likhtar.home

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.likhtar.home.core.data.HomeTakeover
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class LikhtarApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var settings: SettingsRepository


    @Inject lateinit var homeTakeover: HomeTakeover

    override fun onCreate() {
        super.onCreate()
        installCrashSafetyNet()
        homeTakeover.watch()
        // HTTP-кеш на диску для всіх запитів лаунчера, за їхніми ж Cache-Control. Android вбиває
        // лаунчер, поки працює плеєр чи YouTube, і кеш у пам'яті гинув разом із ним: Кінозал
        // після повернення знову йшов по ~25 запитів і 2–3 с стояв порожній. Шлюз віддає TMDB
        // з max-age доби — тепер ці відповіді беруться з диска.
        runCatching { android.net.http.HttpResponseCache.install(java.io.File(cacheDir, "http"), HTTP_CACHE_BYTES) }
    }

    /**
     * Запобіжник, коли домашнім Ліхтар зробили через ADB телевізора: стоковий лаунчер
     * вимкнено, і лаунчер, що падає щоразу, лишив би телевізор без домашнього екрана. Третє
     * падіння за 5 хв — повертаємо стоковий (як у Free My TV), далі падіння йде як завжди.
     */
    private fun installCrashSafetyNet() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val log = java.io.File(filesDir, "home-crashes.txt")
                val now = System.currentTimeMillis()
                val recent = log.takeIf { it.exists() }?.readLines().orEmpty()
                    .mapNotNull(String::toLongOrNull).filter { now - it < CRASH_WINDOW_MS } + now
                log.writeText(recent.joinToString("\n"))
                if (recent.size >= CRASH_LIMIT && homeTakeover.isActive) {
                    // ADB — мережа: не з головного потоку, і не довше кількох секунд.
                    Thread { homeTakeover.revertQuietly() }.apply { start(); join(8_000) }
                    log.delete()
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Мова інтерфейсу з майстра — і для рядків, які беруть контекст застосунку. */
    override fun attachBaseContext(base: android.content.Context) {
        // Збірка для магазину — без суфікса `.personal` (app/build.gradle.kts).
        com.likhtar.home.core.model.Distribution.store = !base.packageName.endsWith(".personal")
        super.attachBaseContext(com.likhtar.home.core.data.AppLanguage.wrap(base))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            // Картка не з'являється стрибком, а проявляється. Двісті мілісекунд — те саме
            // вікно, що й у зросту під фокусом, тож ряд не має двох різних темпів.
            //
            // Виняток один і він свідомий: повнокадровий превʼю ТВ Ефіру вимикає crossfade
            // у своєму запиті. Там анімуються два великі бітмапи, і на слабкій приставці це коштує
            // 12 мс на кадр. Дрібні картки рядів такої ціни не мають.
            // Заголовки кешу сервера Coil 3 і так не читає (це окремий модуль) — як і було
            // з respectCacheHeaders(false) у Coil 2.
            .crossfade(CrossfadeMs)
            .build()
}

/** Скільки проявляється картинка картки. */
private const val CrossfadeMs = 200

private const val CRASH_LIMIT = 3
private const val CRASH_WINDOW_MS = 5 * 60_000L

private const val HTTP_CACHE_BYTES = 30L * 1024 * 1024
