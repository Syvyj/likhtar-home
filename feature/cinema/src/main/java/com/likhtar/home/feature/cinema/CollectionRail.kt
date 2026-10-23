package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.CatalogPosterCard
import com.likhtar.home.core.designsystem.cardSlot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import com.likhtar.home.core.designsystem.PosterHeight
import com.likhtar.home.core.designsystem.PosterWidth
import com.likhtar.home.core.designsystem.ProtoCardCorner
import com.likhtar.home.core.designsystem.focusGlow
import com.likhtar.home.core.designsystem.R as DsR
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.SectionTitle

/**
 * Типова добірка: десять карток і «Відкрити всі» в кінці ряду.
 *
 * Десять — це рівно стільки, скільки можна пройти пультом без відчуття нескінченного ряду;
 * усе інше живе в сітці за останньою карткою.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CollectionRail(
    title: String,
    items: List<CatalogItem>,
    /** [CatalogItem.id] переглянутого — галочка в кутку картки. */
    watched: Set<String> = emptySet(),
    /**
     * Позиції з підтвердженою українською доріжкою; `null` — цей ряд про озвучку не
     * питали взагалі.
     *
     * Саме `null` відрізняє «не питали» від «питали й не знайшли»: «Закладки» — власний
     * список людини, і приглушувати в ньому все лише тому, що ми туди не ходили, було б
     * брехнею про її ж вибір.
     */
    uaConfirmed: Set<Int>? = null,
    /**
     * Позиції, чия мітка має казати `RU`: української доріжки ми в них не знайшли, а
     * російську прочитали. Порожньо — у ряду чужих доріжок немає.
     */
    russianAudio: Set<Int> = emptySet(),
    /** Підпис у кутку картки: `null` — звичайний ряд. */
    note: ((CatalogItem) -> String?)? = null,
    /** Великі картки між рядами (анонси, як «Family Business» в Apple TV). */
    tall: Boolean = false,
    /** Тайтл, з якого пішли на сторінку матеріалу: його ряд і треба відновити. */
    restoreId: String? = null,
    /**
     * Повернення після Back: з тайтла — на першу картку ряду, з «Відкрити всі» — на неї
     * ([returnPoint]). [rowIndex] — номер ряду в колонці розділу.
     */
    restore: RailRestore? = null,
    rowIndex: Int = 0,
    /**
     * Якір для ↓ з меню: чіпляється до першої картки ряду незалежно від відновлення.
     *
     * Окремо від [restore], бо це різні питання. Відновлення веде туди, звідки пішли
     * на сторінку матеріалу, а якір — туди, куди меню віддає фокус зараз. Один requester
     * на два питання давав у «Реаліті» рівно проблему: фокус осідав на
     * картці за нижнім краєм екрана.
     */
    anchor: FocusRequester? = null,
    onOpen: (CatalogItem) -> Unit,
    onFocused: (CatalogItem) -> Unit,
    /** Довге OK на картці — «Позначити» (MarkPanel); `null` — без нього. Сторінки добірок дають його через [LocalLongOpen]. */
    onLongOpen: ((CatalogItem) -> Unit)? = LocalLongOpen.current,
    /** `null` — ряд не має «Відкрити всі»: за ним немає добірки, лише ці картки. */
    onOpenAll: (() -> Unit)?,
    onExitUp: (() -> Unit)? = null,
    onAnyFocus: () -> Unit = {},
    /** Вертикальні постери замість горизонтальних карток із лого. */
    posters: Boolean = false,
    /** Номер у кутку картки — «Топ-10», хронології. */
    numbered: Boolean = false,
    /** Ширина горизонтальної картки; `null` — типова [CatalogBannerWidth]. Сторінка всесвіту рахує її від ширини екрана. */
    cardWidth: Dp? = null,
    gap: Dp = LikhtarDimens.RailGap,
    modifier: Modifier = Modifier,
) {
    // Без «Відкрити всі» за рядом нічого немає — показуємо все, що є (сторінки добірок).
    val shown = if (onOpenAll != null) items.take(RAIL_ITEMS) else items
    val exitUp = if (onExitUp == null) {
        Modifier
    } else {
        Modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                onExitUp()
                true
            } else {
                false
            }
        }
    }
    // Вхід у ряд на першу картку робить сам LikhtarRail — однаково для всіх рядів.
    val railState = rememberLazyListState()
    val allKey = "all:$title"
    // «Відкрити всі» — остання в ряду і поза вікном: без прокрутки її вузла немає, і
    // запит фокуса гинув — Back кидав на початок розділу.
    val restoreFocus = restore?.focus?.takeIf { restore.index == rowIndex && restore.tile != allKey }
    // З тайтла — на першу картку, а ряд пам'ятає прокрутку: картка, з якої пішли, далеко
    // праворуч, першої немає у вікні, і фокус падав на початок розділу.
    restore?.ScrollRailTo(railState) { key ->
        when {
            key == allKey -> shown.size
            key == null && restoreFocus != null && items.any { it.id == restoreId } -> 0
            else -> -1
        }
    }
    Column(modifier = modifier) {
        // Поверх ряду: розкрите віяло стосу колекцій у фокусі налазить на заголовок.
        SectionTitle(text = title, modifier = Modifier.zIndex(1f))
        LikhtarRail(state = railState, gap = gap) {
            itemsIndexed(shown, key = { _, item -> item.id }) { index, item ->
                // Запас під збільшення з УСІХ боків: картка росте на 10%, і без цього
                // поля вона не тільки налазила на сусідні, а й вилазила за межі свого
                // ряду — Compose підкручував список, щоб її показати, і разом із нею
                // повертався попередній ряд.
                val cardModifier = Modifier
                        .cardSlot()
                        // Повертаємось на ПЕРШУ картку ряду, а не на ту, яку відкривали:
                        // щоб стати на конкретну, довелося б перенаправляти вхід у ряд, а
                        // це ламало рух вгору-вниз — фокус стрибав на запамʼятану картку
                        // замість першої.
                        .then(
                            if (index == 0 && restoreFocus != null && items.any { it.id == restoreId }) {
                                Modifier.focusRequester(restoreFocus)
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (index == 0 && anchor != null) Modifier.focusRequester(anchor) else Modifier,
                        )
                        .then(exitUp)
                        // Пам'ять фокуса сторінки добірки; у розділах її немає — модифікатор порожній.
                        .remembered("i:${item.id}")
                        .onFocusChanged {
                            if (!it.isFocused) return@onFocusChanged
                            onFocused(item)
                            if (index == 0 && restoreFocus != null) restore.landed = true
                        }
                if (item.opens.isNotBlank()) {
                    // Колекція (стос) замість тайтла — сторінки стрімінгів і всесвітів.
                    CollectionCard(item = item, onClick = { onOpen(item) }, modifier = cardModifier)
                } else if (posters) {
                    CatalogPosterCard(
                        title = item.title,
                        year = item.year,
                        posterUrl = TmdbImages.poster(item.posterPath),
                        rating = item.rating,
                        onClick = { onOpen(item) },
                        onLongClick = onLongOpen?.takeIf { item.markable() }?.let { f -> { f(item) } },
                        order = if (numbered) index + 1 else null,
                        watched = item.id in watched,
                        uaAudio = uaConfirmed?.contains(item.tmdbId),
                        audioLabel = if (item.tmdbId in russianAudio) "RU" else "UA",
                        overlayTitle = item.logoPath == null && !hasRealPoster(item),
                        modifier = cardModifier,
                    )
                } else if (tall) {
                    CatalogTallCard(
                        item = item,
                        onClick = { onOpen(item) },
                        uaAudio = uaConfirmed?.contains(item.tmdbId),
                        note = note?.invoke(item),
                        onLongClick = onLongOpen?.takeIf { item.markable() }?.let { f -> { f(item) } },
                        modifier = cardModifier,
                    )
                } else {
                    CatalogBannerCard(
                        item = item,
                        onClick = { onOpen(item) },
                        width = cardWidth ?: CatalogBannerWidth,
                        order = if (numbered) index + 1 else null,
                        watched = item.id in watched,
                        uaAudio = uaConfirmed?.contains(item.tmdbId),
                        audioLabel = if (item.tmdbId in russianAudio) "RU" else "UA",
                        note = note?.invoke(item),
                        onLongClick = onLongOpen?.takeIf { item.markable() }?.let { f -> { f(item) } },
                        modifier = cardModifier,
                    )
                }
            }
            if (onOpenAll != null) {
                item(key = "$title-all") {
                    OpenAllCard(
                        poster = posters,
                        tall = tall,
                        width = cardWidth,
                        onClick = {
                            restore?.leave(allKey)
                            onOpenAll()
                        },
                        modifier = (if (restore != null) {
                            Modifier.returnPoint(restore, allKey, rowIndex, onAnyFocus)
                        } else {
                            Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() }
                        }).remembered(allKey),
                    )
                }
            }
        }
    }
}

/**
 * «Відкрити всі» (.more прототипу): розміром картки свого ряду, значок widget-4 бурштином і
 * підпис 24 px; фокус — тепла біла заливка, 1.07 і тепла тінь.
 */
@Composable
internal fun OpenAllCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.cinema_open_all),
    /** У ряду постерів — форма постера. */
    poster: Boolean = false,
    tall: Boolean = false,
    /** Ширина горизонтальної картки ряду, коли вона не типова. */
    width: Dp? = null,
) {
    FocusableSurface(
        onClick = onClick,
        // Розмір і поля рівно як у картки ряду: інакше остання плитка випадала з рядка
        // і вужчим прямокутником, і своєю висотою.
        modifier = modifier
            .cardSlot()
            .then(
                if (tall) Modifier.width(TallCardWidth).height(TallCardHeight)
                else if (poster) Modifier.width(PosterWidth).height(PosterHeight)
                else Modifier.width(width ?: CatalogBannerWidth).aspectRatio(16f / 9f),
            )
            .focusGlow(ProtoCardCorner),
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.BackgroundDeep,
        focusedBackground = LikhtarColors.Paper,
        borderColor = LikhtarColors.Faint,
        focusedScale = LikhtarDimens.CardFocusScale,
        instantFill = true,
    ) { focused ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
        ) {
            Icon(
                painterResource(DsR.drawable.ic_solar_widget),
                contentDescription = null,
                tint = if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Glow,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = if (focused) LikhtarColors.Background else LikhtarColors.TextPrimary,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Палітра прототипів (shared.css): --deep, --white, --glow, --text, --bg, --faint.

/** Скільки кадрів чекати на картку, поки LazyColumn скомпонує потрібний ряд. */

internal const val RAIL_ITEMS = 10

/** Кадр, а не постер: назви на ньому немає (1+1, Телепортал, реле StarLight, ютуб). */
internal fun isPortalFrame(path: String?): Boolean =
    path != null && ("1plus1" in path || ":8100/" in path || "/api/img?" in path || "ytimg.com" in path)

/** Постер картки — справжній вертикальний, а не широкий кадр, підставлений на його місце. */
internal fun hasRealPoster(item: CatalogItem): Boolean =
    !item.posterPath.isNullOrBlank() && item.posterPath != item.backdropPath && !isPortalFrame(item.posterPath)

/**
 * «Топ-10» (.top прототипу): велика цифра 300 px стоїть за постером — контуром кольору
 * тла, а коли постер у фокусі, «запалюється» бурштином з теплим відсвітом.
 */
@Composable
internal fun TopTenRail(
    title: String,
    items: List<CatalogItem>,
    uaConfirmed: Set<Int>,
    onOpen: (CatalogItem) -> Unit,
    onFocused: (CatalogItem) -> Unit,
    onLongOpen: ((CatalogItem) -> Unit)? = null,
) {
    val railState = rememberLazyListState()
    Column {
        SectionTitle(text = title)
        LikhtarRail(state = railState) {
            itemsIndexed(items.take(10), key = { _, item -> item.id }) { index, item ->
                var lit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                val glow by androidx.compose.animation.animateColorAsState(if (lit) LikhtarColors.Glow else LikhtarColors.Background, androidx.compose.animation.core.tween(250), label = "num")
                val stroke by androidx.compose.animation.animateColorAsState(if (lit) LikhtarColors.Glow else LikhtarColors.Paper.copy(alpha = 0.32f), androidx.compose.animation.core.tween(250), label = "stroke")
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.cardSlot(),
                ) {
                    // .num: 700 300 px / .74, −.08em, margin-right −46 px; контур 3 px.
                    val num = TextStyle(
                        fontFamily = LikhtarType.Body,
                        fontWeight = FontWeight.Bold,
                        fontSize = 150.sp,
                        lineHeight = 111.sp,
                        letterSpacing = (-12).sp,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    )
                    // margin-right −46 px: постер заходить на цифру; низ цифри — урівень з низом постера.
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .layout { measurable, constraints ->
                                val p = measurable.measure(constraints)
                                layout(p.width - 23.dp.roundToPx(), p.height) { p.place(0, 0) }
                            }
                            .offset(y = 41.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            style = num.copy(shadow = if (lit) androidx.compose.ui.graphics.Shadow(LikhtarColors.Amber.copy(alpha = 0.45f), blurRadius = 120f) else null),
                            color = glow,
                        )
                        Text(
                            "${index + 1}",
                            style = num.copy(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)),
                            color = stroke,
                        )
                    }
                    com.likhtar.home.core.designsystem.CatalogPosterCard(
                        title = item.title,
                        year = item.year,
                        posterUrl = TmdbImages.poster(item.posterPath),
                        onClick = { onOpen(item) },
                        onLongClick = onLongOpen?.takeIf { item.markable() }?.let { f -> { f(item) } },
                        uaAudio = item.tmdbId in uaConfirmed,
                        modifier = Modifier
                            .onFocusChanged {
                                lit = it.hasFocus
                                if (it.isFocused) onFocused(item)
                            },
                    )
                }
            }
        }
    }
}
