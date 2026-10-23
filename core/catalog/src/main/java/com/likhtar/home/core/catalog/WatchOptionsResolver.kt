package com.likhtar.home.core.catalog

import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Куди веде кнопка «Дивитися в…».
 *
 * Два джерела. **Власний сервер** (`ResolvedOption`, див. `ResolverClient`) дає посилання на
 * конкретну картку разом із кандидатами в застосунок — одним запитом `/api/watch`, уже
 * впорядкованим. Це єдиний спосіб відкрити саме цей фільм у чужому OTT-застосунку, бо всі
 * вони приймають лише свої внутрішні id. **TMDB `watch/providers`**
 * каже, де фільм є, навіть коли посилання немає; таблиця [com.likhtar.home.core.model.StreamingService] каже, **чим** його
 * тоді відкрити на цьому пристрої (зібрана розбором манфестів, див.
 * `docs/internal/app-deeplinks.md`).
 *
 * Пакети приходять від сервера, тому мап «сервіс → застосунок» і чинення адрес тут більше
 * немає: сервер віддає лише ті адреси, які застосунки оголошують у манфестах, і сам список
 * пакетів-кандидатів. Заміряно `pm query-activities`: `app.primevideo.com` бере
 * Prime, а `www.primevideo.com` — **лише** вбудований браузер Sweet.TV, яка ловить будь-який
 * http(s) без обмеження хоста; `play.max.com` і `play.hbomax.com` обидва бере `com.wbd.stream`,
 * тому переписування адрес Max, яке тут колись жило, виявилося непотрібним.
 */
private const val TAG = "LikhtarCatalog"

@Singleton
class WatchOptionsResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    /** Порожній у стор-збірці — див. [ExternalPlayer]. */
    private val externalPlayers: Set<@JvmSuppressWildcards ExternalPlayer>,
) {
    fun resolve(
        details: TitleDetails,
        resolved: List<ResolvedOption> = emptyList(),
    ): List<WatchOption> {
        val item = details.item
        val options = mutableListOf<WatchOption>()
        val used = mutableSetOf<String>()

        // 0) Готові посилання на картку від власного сервера (`/api/watch`). Порядок його —
        // клієнт нічого не пересортовує: `order` уже враховує і групи, і те, що megogo вище
        // за міжнародну підписку. Наш `sortedBy { kind.ordinal }` нижче стабільний, тому
        // всередині DIRECT цей порядок доживає до екрана.
        resolved.forEach { option ->
            if (com.likhtar.home.core.model.Distribution.store && option.group != "official") return@forEach
            // Перший встановлений із кандидатів сервера: на частині приставок Rakuten стоїть як
            // `tv.wuaki.apptv`, а не як `tv.rakuten.android`.
            val pkg = option.packages.firstOrNull(::isInstalled)
            if (pkg == null) {
                Log.i(TAG, "${option.service}: жоден із ${option.packages} не встановлений")
                return@forEach
            }
            if (!used.add(pkg)) return@forEach
            options += WatchOption(
                label = context.getString(R.string.catalog_watch_in, option.label),
                service = option.label,
                kind = WatchOption.OptionKind.DIRECT,
                launch = LaunchAction(packageName = pkg, uri = option.deeplink),
                audio = option.audio,
            )
        }

        // 2) Зовнішні плеєри, які відкривають картку за TMDB id. Набір задає збірка
        // (див. [ExternalPlayer]) — у стор-версії він порожній, і цей блок нічого не додає.
        // ACTION_SEARCH до них не шлемо: на приставках інтент не резолвиться взагалі
        // («unable to resolve»), хоч `android.app.searchable` у манфесті й оголошено.
        externalPlayers.forEach { player ->
            val pkg = player.packages.firstOrNull(::isInstalled) ?: return@forEach
            if (!used.add(pkg)) return@forEach
            options += WatchOption(
                label = context.getString(R.string.catalog_open_in, player.label),
                service = player.label,
                kind = WatchOption.OptionKind.EXTERNAL_PLAYER,
                launch = LaunchAction(
                    packageName = pkg,
                    uri = player.uriFor(item.kind.tmdbPath, item.tmdbId),
                ),
            )
        }

        // 3) Сервіси, які TMDB бачить у регіоні UA, але прямого посилання ми не маємо.
        // Що там українською, сервер знає й тоді, коли застосунку тут немає: «є в Netflix
        // українською» — теж відповідь на вечір, тож мітку беремо з його відповіді.
        val audioBy = resolved.filter { it.audio.isNotBlank() }
            .associate { letters(it.service) to it.audio }
        details.providerNames.forEach { provider ->
            val service = com.likhtar.home.core.model.StreamingService.byProvider(provider) ?: return@forEach
            val target = Target(service.title, service.packages)
            val pkg = target.packages.firstOrNull(::isInstalled)
            if (pkg == null) {
                options += WatchOption(
                    label = context.getString(R.string.catalog_available_on, provider),
                    service = provider,
                    kind = WatchOption.OptionKind.INFO,
                    audio = audioBy.entries.firstOrNull { letters(provider).startsWith(it.key) }?.value.orEmpty(),
                )
                return@forEach
            }
            if (!used.add(pkg)) return@forEach
            options += searchOrOpen(target, item.searchQuery, pkg)
        }

        // 4) Універсальні шукачі, які не залежать від providers: якщо стоять — завжди корисні.
        // Список — у джерелах flavor-а; у збірці для магазину він порожній.
        UNIVERSAL_SEARCH.forEach { target ->
            val pkg = target.packages.firstOrNull(::isInstalled) ?: return@forEach
            if (!used.add(pkg)) return@forEach
            options += searchOrOpen(target, item.searchQuery, pkg)
        }

        return options.sortedBy { it.kind.ordinal }
    }

    /**
     * Або справжній пошук за фразою, або чесне «відкриє лише застосунок».
     *
     * Фраза — **оригінальна** назва (`CatalogItem.searchQuery`), а не українська з TMDB:
     * каталоги онлайн-кінотеатрів індексують оригінал, а «Ірландець» у них не
     * знаходиться. Заразом це прибирає кирилицю з адреси схеми.
     */
    private fun searchOrOpen(target: Target, query: String, pkg: String): WatchOption =
        if (target.searchScheme != null) {
            WatchOption(
                label = context.getString(R.string.catalog_search_in, target.title),
                service = target.title,
                kind = WatchOption.OptionKind.SEARCH,
                launch = LaunchAction(pkg, target.searchScheme + encodePathSegment(query)),
            )
        } else {
            WatchOption(
                label = context.getString(R.string.catalog_open_app, target.title),
                service = target.title,
                kind = WatchOption.OptionKind.OPEN,
                launch = LaunchAction(pkg),
            )
        }

    /** Назва сервісу без пробілів і знаків: «Netflix Basic» і «netflix» зводяться докупи. */
    private fun letters(v: String) = v.lowercase().filter(Char::isLetterOrDigit)

    private fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        true
    }.getOrDefault(false)

    /** Пробіли в схемі ламають `Uri.parse`, а кирилиця в шляху — ні. */
    private fun encodePathSegment(value: String): String = value.trim().replace(" ", "%20")

    internal data class Target(
        val title: String,
        val packages: List<String>,
        val searchScheme: String? = null,
    )

    // Список плеєрів звідси прибрано: сервер віддає `packages` для кожного джерела тим самим
    // правилом «перший встановлений», і його список актуальніший — він упорядкований за
    // широтою claim'ів. Наш `queryIntentActivities` лишається другою половиною правила:
    // сервер дає кандидатів, приставка вирішує, хто з них живий. Універсальні шукачі —
    // [UNIVERSAL_SEARCH] у джерелах flavor-а.
}
