package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import com.likhtar.home.core.designsystem.QualityBadge
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarChip
import com.likhtar.home.core.designsystem.VoiceBadge
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.likhtar.home.core.catalog.TitleKind
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.qualityLabel
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.cachedScrim
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.rememberProgrammeTint
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.sp

/**
 * Тло слайдера: кадр поточного слайда на весь екран і затемнення під текст.
 *
 * Кадр слайда — сцена, а не картинка в рамці: він іде під меню й під перший ряд, тому й
 * малюється поза скафолдом. Новий кадр заїжджає поверх старого збоку, а не перетікає: так
 * видно напрямок руху, і гортання читається як гортання.
 */
@Composable
internal fun SlideBackdrop(slide: CatalogItem?, forward: Boolean, art: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .cachedScrim(dim = Color.Transparent) { listOf(PageBackground) },
    )
    slide ?: return
    AnimatedContent(
        targetState = slide,
        transitionSpec = {
            val from = if (forward) 1 else -1
            slideInHorizontally(tween(HeroSlideMs, easing = FastOutSlowInEasing)) {
                it * from
            } togetherWith fadeOut(tween(HeroSlideMs))
        },
        label = "heroArt",
    ) { shown ->
        val drift = remember(shown) { Animatable(0f) }
        LaunchedEffect(shown) { drift.animateTo(1f, tween(SlideDriftMs, easing = LinearEasing)) }
        AsyncImage(
            // Порадник приходить із сервера й віддає лише постер: бекдропа в його
            // відповіді немає, і без фолбеку картка кандидата була порожньою плямою.
            // `art` — готова адреса найбільшого кадру, коли екран уже знає її сам
            // («Реаліті» добирає обкладинку проєкту). Інакше беремо кадр картки.
            model = art ?: TmdbImages.backdrop(shown.backdropPath) ?: TmdbImages.poster(shown.posterPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Кадр слайдера — по центру вшир і ВЕРХОМ до верху: на весь екран 16:9 обрізка
            // йде по висоті, і навіть зсув -0.3 різав голови в кадрах сервісів.
            alignment = Alignment.TopCenter,
            // Кадр слайда повільно наближається й пливе вбік назустріч гортанню — жива глибина,
            // як у живих кадрів сторінки тайтла. Лише шар, без перемальовування.
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = 1.04f + 0.05f * drift.value
                scaleX = s
                scaleY = s
                translationX = size.width * 0.02f * (if (forward) -1f else 1f) * drift.value
                transformOrigin = TransformOrigin(0.5f, 0.3f)
            },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .cachedScrim(dim = Color.Transparent) { listOf(HeroScrim, HeroBottomScrim) },
    )
    // .spill прототипу: світло екрана на «підлозі» — середній колір кадру, еліпс 58 % × 50 %
    // у точці (60 %, 52 %) смуги з 600 px заввишки 560 px, .42, яскравіше на чверть.
    // Міняється лише колір — дешево.
    val tone = rememberProgrammeTint(art ?: TmdbImages.backdropThumb(slide.backdropPath))
    val spill by androidx.compose.animation.animateColorAsState(tone, tween(900), label = "spill")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 300.dp)
            .height(280.dp)
            .drawBehind {
                val c = Color(
                    red = (spill.red * 1.25f).coerceAtMost(1f),
                    green = (spill.green * 1.25f).coerceAtMost(1f),
                    blue = (spill.blue * 1.25f).coerceAtMost(1f),
                )
                val center = androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.52f)
                val rx = size.width * 0.58f
                val ry = size.height * 0.5f
                scale(scaleX = rx / ry, scaleY = 1f, pivot = center) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = 0.42f), c.copy(alpha = 0f)), center = center, radius = ry),
                        radius = ry,
                        center = center,
                    )
                }
            },
    )
}

/** Скільки пливе кадр слайда: трохи довше за показ слайда, щоб рух не зупинявся на очах. */
private const val SlideDriftMs = 12_000

private val OverviewShadow = androidx.compose.ui.graphics.Shadow(
    color = Color.Black.copy(alpha = 0.8f),
    blurRadius = 6f,
)

/** М'яка синьо-сіра поверхня дає рейкам глибину, не перетворюючи Кінозал на світлу тему. */
private val RailCatalogSurface = LikhtarColors.RailFloor

private val PageBackground = Brush.linearGradient(
    0.00f to RailCatalogSurface,
    0.55f to LikhtarColors.FloorLift,
    1.00f to LikhtarColors.FloorBottom,
)

/**
 * Відповідь-пігулка.
 *
 * Обрана відповідь залита матовим білим — тим самим, що обраний канал у ТБ Ефірі. Обраність
 * і фокус тут різні речі: людина бачить свою відповідь навіть коли пішла пультом далі.
 */
@Composable
internal fun AnswerPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LikhtarChip(text = text, onClick = onClick, modifier = modifier, selected = selected)

/**
 * Затемнення під героєм: ліва третина під текст, низ — під перший ряд.
 *
 * Не суцільна шторка: у Apple кадр іде на повну яскравість, а текст читається за рахунок
 * того, що ліва частина композиції сама темна. Ми теж не глушимо кадр цілком — тільки
 * стільки, скільки треба, щоб літери не губились на світлих постерах.
 */
internal val HeroScrim = Brush.horizontalGradient(
    // .scrim прототипу: 90deg .88 → .62 на 34 % → .18 на 62 % → 0.
    0.00f to LikhtarColors.Background.copy(alpha = 0.88f),
    0.34f to LikhtarColors.Background.copy(alpha = 0.62f),
    0.62f to LikhtarColors.Background.copy(alpha = 0.18f),
    1.00f to LikhtarColors.Background.copy(alpha = 0f),
)

/** Низ кадру гасне під перший ряд, щоб картки не лежали просто на постері. */
internal val HeroBottomScrim = Brush.verticalGradient(
    // 180deg: 0 до 52 %, .72 на 74 %, суцільне тло з 86 %.
    0.52f to LikhtarColors.Background.copy(alpha = 0f),
    0.74f to LikhtarColors.Background.copy(alpha = 0.72f),
    0.86f to LikhtarColors.Background,
)

internal const val HeroSlideMs = 420

/** Текст приходить після кадру, а не разом із ним — інакше він їде разом із картинкою. */
private const val HeroTextDelayMs = 160

/** Логотип героя: він і є назва, тому місця йому дається як назві, а не як підпису. */
internal val HeroLogoHeight = 75.dp

/** Скільки екрана віддано героєві: решта лишається під перший ряд, який має визирати. */
private const val HeroFraction = 0.82f

/**
 * Верх Кінозалу: один слайд на весь кадр.
 *
 * Тут немає ані рамки, ані сусідніх блоків — кадр уже намальований на весь екран вище,
 * а це лише текст поверх нього. Саме тому герой не «картка з описом», а сцена: око має
 * одну зупинку, а не три.
 *
 * Гортання живе на кнопці, а не окремим елементом: ← з неї гортає назад, → вперед. Кнопка
 * тут одна, тож обидва краї на ній і сходяться. Крапки під нею показують, де ми в пулі.
 */
@Composable
internal fun CinemaHero(
    item: CatalogItem,
    pool: List<CatalogItem>,
    watchFocus: FocusRequester,
    onWatch: (CatalogItem) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    kindLabel: String? = null,
    /** Назва без логотипа — фірмовим знаком; ТВ Ефір вимикає: там назви програм. */
    titleMark: Boolean = true,
    /** Ми САМІ бачили українську доріжку. `false` — або немає, або не питали. */
    uaAudio: Boolean = false,
    /** Куди веде ↑ з головної дії: пункт ПОТОЧНОГО розділу в меню, а не перший-ліпший. */
    menuFocus: FocusRequester? = null,
    /** Рядок під назвою замість «★ рейтинг · рік · тип» — ТВ Ефір 2 пише там канал і час. */
    meta: ((CatalogItem) -> String)? = null,
    watchLabel: ((CatalogItem) -> String)? = null,
) {
    val inPool = pool.indexOf(item)
    // Мітка приходить окремим запитом, пізніше за слайд, а AnimatedContent запам'ятовує
    // вміст слайда в момент появи. Звичайний параметр у нього не доходив, і «UA»
    // з'являлось лише після зміни слайда. Стан, прочитаний усередині, — доходить.
    val ua by rememberUpdatedState(uaAudio)

    // .hero прототипу: текстова колонка 860 px унизу ліворуч, 92 px від низу героя.
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(HeroFraction)) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 32.dp).width(430.dp),
        ) {
            // Текст приходить після кадру: якщо пустити разом, він їде з картинкою й
            // читається як зсув сторінки, а не як зміна слайда.
            AnimatedContent(
                targetState = item,
                transitionSpec = {
                    fadeIn(tween(HeroSlideMs, delayMillis = HeroTextDelayMs)) togetherWith
                        fadeOut(tween(HeroTextDelayMs))
                },
                label = "heroText",
            ) { shown ->
                Column {
                    // .mark: до 620×150 px, margin-bottom 26 px; чужий знак — з назвою плашкою.
                    TitleLogoArea(
                        logoPath = shown.logoPath,
                        title = shown.title,
                        height = HeroLogoHeight,
                        localTitle = true,
                        mark = titleMark,
                        widthFraction = 0.72f,
                        fallbackStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = LikhtarType.Body,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 38.sp,
                            lineHeight = 38.sp,
                            letterSpacing = (-0.76).sp,
                        ),
                    )
                    // .meta: 24 px / 500, приглушений; gap 12 px; UA і якість — у рядку.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 13.dp),
                    ) {
                        Text(
                            text = meta?.invoke(shown) ?: itemMeta(shown, kindLabel),
                            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 12.sp),
                            color = LikhtarColors.TextDim,
                        )
                        if (ua) VoiceBadge()
                        shown.qualityLabel.takeIf { it.isNotEmpty() }?.let { QualityBadge(it) }
                    }
                    // p: 26 px / 1.42, .9, 3 рядки, margin 18 px / 30 px.
                    Text(
                        text = shown.overview,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp, lineHeight = 18.5.sp, shadow = OverviewShadow),
                        color = LikhtarColors.TextPrimary.copy(alpha = 0.9f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 9.dp, bottom = 15.dp),
                    )
                }
            }

            ProtoButton(
                label = watchLabel?.invoke(item) ?: stringResource(R.string.cinema_watch),
                onClick = { onWatch(item) },
                modifier = Modifier
                    .focusRequester(watchFocus)
                    // Без цього пошук фокуса брав найближчий угорі пункт — «Головна», — і
                    // ↑ з героя виводило з розділу, замість повернути на його ж пункт.
                    .then(
                        if (menuFocus != null) {
                            Modifier.focusProperties { up = menuFocus }
                        } else {
                            Modifier
                        },
                    )
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionRight -> { onNext(); true }
                            Key.DirectionLeft -> { onPrev(); true }
                            else -> false
                        }
                    },
            )

            // Крапки лише коли ми справді в слайдері: під підбором радника вони показували б
            // позицію, якої немає.
            if (pool.size > 1 && inPool >= 0) HeroDots(count = pool.size, active = inPool)
        }
    }
}

/** Де ми в пулі слайдів. Активна крапка ширша, а не яскравіша: це видно з дивана. */
@Composable
internal fun HeroDots(count: Int, active: Int) {
    // .dots: margin-top 30 px, gap 9 px; крапка 7 px, активна — 30 px теплим білим.
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 15.dp),
    ) {
        repeat(count) { index ->
            val on = index == active
            val w by androidx.compose.animation.core.animateDpAsState(if (on) 15.dp else 3.5.dp, tween(300), label = "dot")
            Box(
                modifier = Modifier
                    .height(3.5.dp)
                    .width(w)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) LikhtarColors.Paper else LikhtarColors.TextPrimary.copy(alpha = 0.32f)),
            )
        }
    }
}

@Composable
internal fun itemMeta(item: CatalogItem, kindLabel: String? = null): String = listOfNotNull(
    item.rating.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "★ %.1f", it) },
    item.year.takeIf { it.isNotBlank() },
    // Підпис типу перевизначається з того ж міркування, що і в шапці рядів: у «Реаліті»
    // позиції лежать у моделі Кінозалу, але серіалами не є.
    kindLabel ?: stringResource(if (item.kind == TitleKind.TV) R.string.cinema_kind_series_lower else R.string.cinema_kind_movie_lower),
).joinToString(" · ")
