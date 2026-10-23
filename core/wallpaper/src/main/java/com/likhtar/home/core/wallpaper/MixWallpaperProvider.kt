package com.likhtar.home.core.wallpaper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Мікс усіх джерел одразу: Bing, теми, віддалений набір і вбудовані разом.
 *
 * Зроблено ще одним провайдером, а не окремою гілкою в репозиторії, бо репозиторій уже
 * влаштований так, що «додати джерело — це один `@Binds @IntoSet`». Мікс у ньому — таке саме
 * джерело, просто його набір збирається з інших.
 *
 * Інші провайдери беруться через `Provider`, а не напряму: мікс лежить у тому самому наборі,
 * що й вони, тож пряма ін'єкція дала б цикл у графі. Себе з набору прибираємо за id — інакше
 * перший же виклик пішов би в нескінченну рекурсію.
 */
@Singleton
class MixWallpaperProvider internal constructor(
    private val all: Provider<Set<@JvmSuppressWildcards WallpaperProvider>>,
    private val label: () -> String,
) : WallpaperProvider {

    @Inject constructor(
        all: Provider<Set<@JvmSuppressWildcards WallpaperProvider>>,
        @ApplicationContext context: Context,
    ) : this(all, { context.getString(R.string.wallpaper_provider_mix) })

    override val id = ID
    override val name: String get() = label()
    override val updateMode = WallpaperUpdateMode.TIME_ELAPSED

    override suspend fun wallpapers(): List<Wallpaper> {
        val sources = all.get()
            .filter { it.id != ID && it.id !in SKIP }
            .sortedBy { it.id }
        // Перебираємо джерела по колу, а не зшиваємо списки поспіль: у Bing сім фото на добу,
        // а у віддаленого набору бувають десятки, і при простому склеюванні людина півдня
        // дивилась би на один і той самий набір, перш ніж дійти до наступного.
        val sets = sources.map { runCatching { it.wallpapers() }.getOrDefault(emptyList()) }
        val mixed = mutableListOf<Wallpaper>()
        var index = 0
        while (sets.any { index < it.size }) {
            sets.forEach { items -> items.getOrNull(index)?.let(mixed::add) }
            index++
        }
        return mixed
    }

    companion object {
        const val ID = "mix"

        /**
         * «Без шпалер» у мікс не входить: це не набір, а свідома відмова від фону, і його
         * поява раз на коло виглядала б як збій завантаження.
         */
        private val SKIP = setOf("plain")
    }
}
