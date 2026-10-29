package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.LocalTitleUnderLogo
import com.likhtar.home.core.designsystem.BrandColors
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import com.likhtar.home.core.catalog.TitleKind
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.qualityLabel
import com.likhtar.home.core.catalog.MoodTile
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.titleMarkStyle
import com.likhtar.home.core.designsystem.titleMarkText
import com.likhtar.home.core.designsystem.NoteBadge
import com.likhtar.home.core.designsystem.BakedTextScrim
import com.likhtar.home.core.designsystem.hasBakedText
import com.likhtar.home.core.designsystem.QualityBadge
import com.likhtar.home.core.designsystem.VoiceBadge
import com.likhtar.home.core.designsystem.CardStack
import androidx.compose.ui.graphics.lerp
import com.likhtar.home.core.designsystem.rememberProgrammeTint
import com.likhtar.home.core.designsystem.StackFocusScale
import com.likhtar.home.core.designsystem.StackOrigin
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.FocusRing
import com.likhtar.home.core.designsystem.ProtoCardCorner
import com.likhtar.home.core.designsystem.focusGlow
import com.likhtar.home.core.designsystem.FaceSafeCrop
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType

// CatalogPosterCard переїхала в core:designsystem (PosterCard.kt): нею користується
// і Пошук, а модуль фічі не має бути залежністю іншої фічі.

/**
 * Плитка-перехід: сервіс, хронологія, настрій. Постера немає навмисно — за нею стоїть
 * не фільм, а добірка, і випадковий кадр із неї обіцяв би не те.
 */
@Composable
fun CatalogTileCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    brand: StreamingBrand? = null,
) {
    FocusableSurface(
        onClick = onClick,
        // Один розмір для всіх плиток: плитка з wordmark і текстова («Всі стрімінги»,
        // канали без бренду) стояли в ряду різними прямокутниками.
        // .svc прототипу: 228×102 px, тло #171513 з тонкою рамкою; фокус — тепла біла
        // заливка, 1.07 і тепла тінь.
        modifier = modifier
            .width(114.dp)
            .height(51.dp)
            .focusGlow(ProtoCardCorner),
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.TileFloor,
        focusedBackground = LikhtarColors.Paper,
        borderColor = LikhtarColors.Faint,
        focusedScale = LikhtarDimens.CardFocusScale,
        instantFill = true,
    ) { focused ->
        if (brand != null) {
            StreamingBrandMark(
                brand = brand,
                subtitle = subtitle,
                focused = focused,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = (-0.22).sp),
                    color = if (focused) LikhtarColors.Background else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 8.5.sp),
                        color = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/** Впізнавані wordmark-и сервісів без важких растрових підкладок чи сторонніх CDN. */
enum class StreamingBrand(
    val title: String,
    val wordmark: String,
    val color: Color,
    val fontSize: Int = 18,
) {
    NETFLIX("Netflix", "NETFLIX", BrandColors.Netflix, 18),
    APPLE_TV("Apple TV+", "Apple TV+", Color.White, 18),
    HBO("HBO", "HBO", Color.White, 20),
    PRIME("Prime Video", "prime video", BrandColors.Prime, 17),
    DISNEY("Disney+", "Disney+", BrandColors.Disney, 19),
    PARAMOUNT("Paramount+", "Paramount+", BrandColors.Paramount, 16),

    // Реаліті: сервіси, мовники й пізнавальні мережі — тими самими плитками.
    MEGOGO("MEGOGO", "MEGOGO", Color.White, 18),
    SWEET("SWEET.TV", "sweet.tv", BrandColors.Sweet, 19),
    YOUTUBE("YouTube", "YouTube", BrandColors.YouTube, 18),
    PLUS1("1+1 video", "1+1", Color.White, 22),
    STB("СТБ", "СТБ", BrandColors.Stb, 22),
    NOVYY("Новий канал", "НОВИЙ", BrandColors.Novyy, 18),
    DISCOVERY("Discovery", "Discovery", BrandColors.Discovery, 17),
    NATGEO("National Geographic", "NAT GEO", BrandColors.NatGeo, 17),
    NATGEO_WILD("Nat Geo Wild", "NAT GEO WILD", BrandColors.NatGeo, 15),
    ANIMAL("Animal Planet", "Animal Planet", BrandColors.AnimalPlanet, 15),
    BBC("BBC", "BBC", Color.White, 22),
    HISTORY("History", "HISTORY", BrandColors.History, 18),
    TLC("TLC", "TLC", BrandColors.Tlc, 22),
    ;

    companion object {
        fun fromTitle(title: String): StreamingBrand? {
            val normalized = title.lowercase()
            return when {
                "netflix" in normalized -> NETFLIX
                "apple" in normalized && "tv" in normalized -> APPLE_TV
                normalized.startsWith("hbo") || normalized == "max" -> HBO
                "prime" in normalized || "amazon video" in normalized -> PRIME
                // Hulu і FX — усередині застосунку Disney+: одна марка.
                "disney" in normalized || "hulu" in normalized -> DISNEY
                "paramount" in normalized -> PARAMOUNT
                "megogo" in normalized -> MEGOGO
                "sweet" in normalized -> SWEET
                "ютуб" in normalized || "youtube" in normalized -> YOUTUBE
                normalized.startsWith("1+1") -> PLUS1
                normalized == "стб" -> STB
                normalized.startsWith("новий") -> NOVYY
                "discovery" in normalized -> DISCOVERY
                // Wild — раніше за загальне «nat geo»: інакше дві плитки однакові.
                "nat geo wild" in normalized -> NATGEO_WILD
                "national geographic" in normalized || "nat geo" in normalized -> NATGEO
                "animal planet" in normalized -> ANIMAL
                normalized == "bbc" -> BBC
                normalized == "history" -> HISTORY
                normalized == "tlc" -> TLC
                else -> null
            }
        }
    }
}

@Composable
private fun StreamingBrandMark(
    brand: StreamingBrand,
    subtitle: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = brand.wordmark,
            // Кеглі BRAND прототипу: Netflix 27 px, HBO 30, Prime 26, Disney+ 29, Paramount+ 24.
            style = TextStyle(
                fontFamily = LikhtarType.Body,
                fontSize = (brand.fontSize * 0.75f).sp,
                lineHeight = (brand.fontSize * 0.75f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02f * brand.fontSize * 0.75f).sp,
            ),
            // На білому focus-стані світлі wordmark-и губляться, тому інвертуємо лише їх.
            color = if (focused && brand.color == Color.White) LikhtarColors.Background else brand.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = LikhtarType.MonoSmall,
                color = if (focused) LikhtarColors.OnGlassActiveMuted else LikhtarColors.TextMuted,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Банерна картка ряду: лише кадр із логотипом фільму, без підпису під ним.
 *
 * Назву несе не картка, а кадр на весь екран над рядами — тому дублювати її текстом
 * унизу немає сенсу, і чотири з половиною картки в ряду лишаються великими.
 */
@Composable
fun CatalogBannerCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = CatalogBannerWidth,
    /** Профіль це вже додивився. */
    watched: Boolean = false,
    /**
     * Чи знаємо ми тут українську доріжку: `true` — мітка UA; `false` і `null` — без мітки.
     *
     * `false` трапляється лише за увімкненим «Показувати без озвучки»: інакше такі
     * позиції в ряд не потрапляють узагалі.
     */
    uaAudio: Boolean? = null,
    /**
     * Короткий підпис у кутку — дата для «Незабаром українською».
     *
     * Без нього ряд анонсів не відрізнити від ряду того, що вже грає, а картка обіцяла б
     * перегляд, якого ще немає.
     */
    note: String? = null,
    /**
     * Що написано на мітці: `UA` або `RU`.
     *
     * Мова не ховається за спільним значком: у неігровому дозволена й російська доріжка,
     * і глядач має бачити, що вмикає, ДО натискання, а не після.
     */
    audioLabel: String = "UA",
    /**
     * Номер у хронології чи франшизі — великою цифрою в лівому куті, як у топах Apple TV.
     * Тоді мітки озвучки переїжджають у правий кут, а галочка переглянутого — униз.
     */
    order: Int? = null,
    /** Підпис під лого: жанр, як на картках Apple TV. */
    caption: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(width).focusGlow(ProtoCardCorner),
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.CardBlack,
        focusedBackground = LikhtarColors.CardBlack,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        // Сервер підміняє «portal-img:» на повну адресу /api/portal-img/ — ловимо обидві форми.
        val portalBanner = item.backdropPath?.let { "portal-img" in it } == true
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            AsyncImage(
                model = TmdbImages.backdropThumb(item.backdropPath)
                    ?: TmdbImages.posterThumb(item.posterPath),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                // Центр по горизонталі, зсув до верху по вертикалі: обличчя в кадрах лежать
                // у верхній третині, і центрована обрізка їх зрізала. Банер порталу (2,6:1) —
                // чистий кадр проєкту: темне поле ліворуч, люди праворуч, тож ріжемо
                // з правого краю — у картку йдуть люди, а не порожнеча.
                alignment = if (portalBanner) Alignment.CenterEnd else FaceSafeCrop,
                // Без приглушення: різницю каже мітка UA, а не блідість.
                modifier = Modifier.fillMaxSize(),
            )
            // Підпис несе картка, а не лише кадр: у портальних позицій логотипа немає
            // за природою (немає TMDB-id — нізвідки його брати), і ряд «Суто українське»
            // виглядав як набір безіменних кадрів. Немає лого — стоїть назва тим самим
            // накресленням; є лого — усе як було.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Назва текстом (логотипа немає) — щільна смуга: на кадрах українських творів
                    // часто вже надрукований напис, і наш налазив на нього.
                    .background(if (item.logoPath == null || hasBakedText(item.backdropPath)) BakedTextScrim else BannerScrim),
            )
            // .card.wide .logo прототипу: 22 px від краю, до 70 px заввишки, до 64 % ширини.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(11.dp),
            ) {
                // Банер порталу тепер чистий — назву несе наш знак, як на будь-якій картці.
                TitleLogoArea(
                    logoPath = item.logoPath,
                    title = item.title,
                    height = 35.dp,
                    widthFraction = 0.64f,
                    fallbackStyle = LikhtarType.Label,
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = LikhtarType.Body2,
                        color = LikhtarColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (order != null) {
                Text(
                    text = order.toString(),
                    style = OrderStyle,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 4.dp),
                )
            }
            // Озвучка й якість — одним рядком у кутку: з дивана це одна відповідь на «що
            // ввімкнеться», і дві плашки в різних кутах читались би як дві різні речі.
            val quality = item.qualityLabel
            val topNote = note.takeUnless { it.isNullOrBlank() || order != null }
            if (uaAudio == true || quality.isNotEmpty() || topNote != null) {
                // Один рядок угорі: мітки зліва, підпис справа (внизу він
                // налазив на лого). Підпис бере, що лишилось, і обрізається — інакше на
                // «Нових серіях» «S02E04 · ще 2» налазило на «4K · DV+HDR10+».
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(if (order != null) Alignment.TopEnd else Alignment.TopStart)
                        .then(if (order != null) Modifier else Modifier.fillMaxWidth())
                        .padding(6.dp),
                ) {
                    if (uaAudio == true) VoiceBadge(label = audioLabel)
                    if (quality.isNotEmpty()) QualityBadge(quality)
                    if (topNote != null) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                            NoteBadge(text = topNote)
                        }
                    }
                }
            }
            if (!note.isNullOrBlank() && order != null) {
                NoteBadge(text = note, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            if (watched) {
                Text(
                    text = "✓",
                    style = LikhtarType.MonoSmall,
                    color = LikhtarColors.OnAccent,
                    modifier = Modifier
                        // Верхній правий кут зайнятий підписом — галочка тоді внизу.
                        .align(if (order != null || !note.isNullOrBlank()) Alignment.BottomEnd else Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                        .background(LikhtarColors.Accent)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            FocusRing(focused, ProtoCardCorner)
        }
    }
}

/**
 * Логотип фільму в СПІЛЬНІЙ області — однаковій на картці ряду й на боковій панелі.
 *
 * Логотипи TMDB бувають будь-яких пропорцій: від вузького напису до майже квадратного
 * знака. Тому область фіксована, а лого вписується в неї (`Fit`) і тулиться до лівого
 * низу — так різні фільми лягають однаково, а не стрибають розміром. Немає логотипа —
 * лишається назва тим самим накресленням, що й решта заголовків екрана.
 */
@Composable
fun TitleLogoArea(
    logoPath: String?,
    title: String,
    height: Dp,
    modifier: Modifier = Modifier,
    fallbackStyle: TextStyle = LikhtarType.HeroTitle,
    widthFraction: Float = LogoWidthFraction,
    /** Куди тулиться логотип — і зона, і сам знак у ній. Сцена серій центрує його. */
    alignment: Alignment = Alignment.BottomStart,
    /**
     * Підписати чужомовний знак українською назвою. Лише для великих шапок: на картці
     * ряду другий рядок під знаком не вміщається.
     */
    localTitle: Boolean = false,
    /** Без логотипа — фірмовий знак; `false` — простий текст (ТВ Ефір: там назви програм, а не шоу). */
    mark: Boolean = true,
) {
    val subtitle = localTitle && TmdbImages.needsLocalTitle(logoPath, title)
    if (subtitle) {
        // Оригінальний знак — як є, під ним назва нашою мовою плашкою, по центру САМОГО
        // знака. По центру зони було б не те: знак вписується в
        // неї по висоті й буває вдвічі вужчий, і плашка висіла б збоку від нього.
        val prepared = TmdbImages.logo(logoPath)?.let { rememberPreparedLogo(it) }
        val aspect = prepared?.let { it.bitmap.width.toFloat() / it.bitmap.height }
        val boost = if (aspect != null && aspect < TallLogoAspect) TallLogoBoost else 1f
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth(widthFraction),
            contentAlignment = if (alignment == Alignment.Center) Alignment.TopCenter else Alignment.TopStart,
        ) {
            val logoWidth = aspect?.let { minOf(maxWidth, height * boost * it) } ?: maxWidth
            val text = title.uppercase()
            // Кегль — найбільший, з яким назва вміщається в ширину зони одним рядком.
            // Не за кількістю літер: «НАЙКРАЩІ З НАЙКРАЩИХ» при тих самих двадцяти символах
            // ширша за «RESIDENT EVIL», і правило «до 20 — 22sp» обрізало її трикрапкою.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val room = with(density) { (maxWidth - OverlayPillChrome).roundToPx() }
            val style = remember(text, room) {
                OverlaySizes.map { overlayStyle(it) }.firstOrNull { st ->
                    measurer.measure(text, st, maxLines = 1).size.width <= room
                } ?: overlayStyle(OverlaySizes.last())
            }
            val pillWidth = with(density) {
                measurer.measure(text, style, maxLines = 1).size.width.toDp()
            } + OverlayPillChrome
            // Плашка може бути ширшою за знак: коротка назва центрується під ним, довга —
            // виходить за його межі, але починається не лівіше за сам знак.
            val shift = ((logoWidth - pillWidth) / 2).coerceAtLeast(0.dp)
            // За замовчуванням плашка НАКЛАДАЄТЬСЯ на низ знака: так вони читаються як одна
            // назва. Відступ знизу — половина плашки, щоб вона не лізла на метарядок. «Назва під
            // знаком» у налаштуваннях ставить її окремим рядком: дехто хоче бачити оригінальний
            // знак цілим.
            val below = LocalTitleUnderLogo.current
            val logo: @Composable () -> Unit = {
                Box(Modifier.width(logoWidth)) {
                    TitleLogoArea(
                        logoPath = logoPath, title = title, height = height,
                        fallbackStyle = fallbackStyle, widthFraction = 1f, alignment = Alignment.Center,
                    )
                }
            }
            // Поки знак вантажиться, зона показує назву текстом — плашка під нею
            // повторила б ту саму назву вдруге («Фрірен»).
            val pill: @Composable (Modifier) -> Unit = { pillModifier ->
                if (prepared != null) TitlePill(
                    text = text,
                    style = style,
                    verticalPadding = 5.dp,
                    tight = true,
                    maxLines = 1,
                    modifier = pillModifier,
                )
            }
            if (below) {
                Column(
                    modifier = Modifier.width(maxOf(logoWidth, pillWidth)),
                    verticalArrangement = Arrangement.spacedBy(OverlayPillGap),
                ) {
                    logo()
                    pill(Modifier.offset(x = shift))
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(maxOf(logoWidth, pillWidth))
                        .padding(bottom = OverlayPillHalf),
                ) {
                    logo()
                    pill(Modifier.align(Alignment.BottomStart).offset(x = shift, y = OverlayPillHalf))
                }
            }
        }
        return
    }
    val logo = TmdbImages.logo(logoPath)
    val artwork = logo?.let { rememberPreparedLogo(it) }
    // Вертикальні знаки («ВОЛОДАР ПЕРСНІВ / ПЕРСНІ ВЛАДИ») вписуються в зону по висоті й
    // виходять помітно дрібнішими за горизонтальні: та сама смуга, а знака в ній удвічі
    // менше. Тому вузьким даємо більшу смугу й ставимо їх по центру, а не на дно.
    val tall = artwork != null &&
        artwork.bitmap.width.toFloat() / artwork.bitmap.height < TallLogoAspect
    val bandHeight = if (tall) height * TallLogoBoost else height
    val bandAlignment = when {
        !tall -> alignment
        alignment == Alignment.BottomStart -> Alignment.CenterStart
        else -> alignment
    }
    Box(
        modifier = modifier.height(bandHeight).fillMaxWidth(widthFraction),
        contentAlignment = bandAlignment,
    ) {
        if (logo != null) {
            // TMDB часто додає до PNG великі прозорі поля. Обрізаємо їх один раз при
            // завантаженні — тоді різні пропорції не перетворюються на «маленькі» лого.
            if (artwork != null) {
                AsyncImage(
                    model = artwork.bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    // Те саме вирівнювання, що й у зони: `Fit` лишає порожній залишок
                    // усередині слота, і власне зашите `BottomStart` саджало знак на дно
                    // навіть тоді, коли зону просили центрувати.
                    alignment = bandAlignment,
                    // Кольорові брендові лого не чіпаємо. У світлий перетворюємо тільки
                    // справді темний монохромний знак, якому на hero бракує контрасту.
                    colorFilter = if (artwork.isDarkMonochrome) {
                        ColorFilter.tint(LikhtarColors.TextPrimary)
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxHeight(),
                )
            } else {
                // Поки Coil підготує PNG або якщо TMDB поверне битий файл, зона не порожня.
                FallbackTitle(title, fallbackStyle, mark)
            }
        } else {
            FallbackTitle(title, fallbackStyle, mark)
        }
    }
}

@Composable
private fun FallbackTitle(title: String, style: TextStyle, mark: Boolean = true) {
    Text(
        text = if (mark) titleMarkText(title) else title,
        style = if (mark) titleMarkStyle(style) else style,
        color = LikhtarColors.TextPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Назва плашкою: той самий напівпрозорий фон і обводка, що в мітках «UA» і якості.
 *
 * Голий текст на кадрі губився — світла сцена з'їдала його, і назва читалась гірше за
 * мітку поруч. Плашка тримає контраст на будь-якому кадрі.
 */
@Composable
internal fun TitlePill(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    verticalPadding: Dp = 5.dp,
    /**
     * Висота плашки за висотою літер, а не за шрифтовими полями. Oswald тримає великий
     * запас над і під літерами, і навіть без `includeFontPadding` плашка виходила вдвічі
     * вищою за текст — налазила на знак. Текст центрується й може трохи виходити за межі.
     */
    tight: Boolean = false,
) {
    val shape = RoundedCornerShape(LikhtarDimens.CornerMedium)
    val base = modifier
        .clip(shape)
        // Легка прозорість: знак під плашкою має просвічувати,
        // інакше вона читається як латка поверх логотипа, а не частина назви. Контраст
        // тексту тримає тінь, а не щільність фону.
        .background(LikhtarColors.ChipFloor.copy(alpha = 0.4f))
        .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
    if (tight) {
        val letters = with(LocalDensity.current) { style.fontSize.toDp() } * TightPillLetters
        Box(
            modifier = base.height(letters + verticalPadding * 2).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = style,
                color = LikhtarColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.wrapContentHeight(unbounded = true),
            )
        }
        return
    }
    Text(
        text = text,
        style = style,
        color = LikhtarColors.TextPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = base.padding(horizontal = 12.dp, vertical = verticalPadding),
    )
}

/** Кеглі, з яких підбирається найбільший, що вміщається. */
private val OverlaySizes = listOf(28, 26, 24, 22, 20, 18, 16, 14)

/** Поля плашки по горизонталі й рамка — те, що до тексту додається завжди. */
private val OverlayPillChrome = 26.dp

/** Стиль назви під знаком: Oswald, розріджений, з тінню, рядок по висоті літер. */
private fun overlayStyle(size: Int) = TextStyle(
    fontFamily = LikhtarType.Poster,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    letterSpacing = 1.5.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f),
    // У Oswald великий запас над і під літерами, і плашка виходила вдвічі вищою за сам
    // текст — налазила на знак. Рядок по висоті літер, без шрифтових полів.
    lineHeight = 1.em,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Висота великих літер Oswald разом із «Й» і «Ї» — частка кегля. */
private const val TightPillLetters = 0.98f

/** Наскільки плашка з українською назвою звисає нижче знака. */
private val OverlayPillHalf = 14.dp

/** Проміжок між знаком і назвою, коли вона стоїть під ним. */
private val OverlayPillGap = 6.dp

/** Ширина банерної картки ряду — та сама для «Відкрити всі» в кінці ряду. */
/** Нижче цього співвідношення сторін знак вважаємо вертикальним. */
private const val TallLogoAspect = 2.2f

/** Наскільки більшу смугу дістає вертикальний знак. */
private const val TallLogoBoost = 1.45f

// 250, а не 200: на сторінках студій і франшиз картка рахується «3,3 на ряд» і виходить
// ~250dp — Кінозал з 200 виглядав дрібнішим за них.
// 200 — .card.wide прототипів (400×225 px); 242 — +20 %.
val CatalogBannerWidth = 242.dp

/**
 * Номер на картці — невеликий і напівпрозорий, як у топах Apple TV: підказка порядку,
 * а не головне на картці. Легка тінь — щоб не губився на світлому кадрі.
 */
private val OrderStyle = TextStyle(
    fontFamily = LikhtarType.DisplayFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 22.sp,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 1f), blurRadius = 4f),
)

/**
 * Картка колекції у стилі Disney+: арт без напису, наше лого й рознесене «КОЛЕКЦІЯ».
 *
 * Постери колекцій TMDB несуть власний напис, і то англійською чи німецькою, — ряд
 * виглядав збіркою різних афіш. Однаковий низ робить з нього один ряд.
 */
@Composable
fun CollectionCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Колекція — стосом: за нею кілька фільмів, а не один. Задні картки —
    // у середньому кольорі постера (сіре всюди «не прикольно»); темне трохи висвітлюємо,
    // інакше за чорним постером «Чужого» шари зникали.
    val tint = rememberProgrammeTint(TmdbImages.poster(item.posterPath))
    CardStack(tone = lerp(tint, Color.White, 0.22f), modifier = modifier, corner = LikhtarDimens.CornerMedium) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(CollectionCardWidth),
        shape = RoundedCornerShape(LikhtarDimens.CornerMedium),
        background = LikhtarColors.CardBlack,
        focusedBackground = LikhtarColors.SurfaceAlt,
        focusedScale = StackFocusScale,
        focusOrigin = StackOrigin,
    ) { _ ->
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            AsyncImage(
                model = TmdbImages.poster(item.posterPath),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WideScrim),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TitleLogoArea(
                    logoPath = item.logoPath,
                    title = item.title,
                    height = 44.dp,
                    fallbackStyle = LikhtarType.Label,
                    widthFraction = 0.9f,
                    alignment = Alignment.BottomCenter,
                )
                Text(
                    text = stringResource(R.string.cinema_collection_kicker),
                    style = LikhtarType.MonoSmall.copy(letterSpacing = 3.sp),
                    color = LikhtarColors.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    }
}


/** Як постер у рядах: 150dp. */
val CollectionCardWidth = 182.dp

/** Скільки ширини контейнера віддано логотипу: далі він починає тиснути на решту. */
private const val LogoWidthFraction = 0.82f

/**
 * Підготовлений TMDB logo: без зовнішніх прозорих полів і з чесною характеристикою кольору.
 */
@Composable
private fun rememberPreparedLogo(url: String): PreparedLogo? {
    val context = LocalContext.current
    var artwork by remember(url) { mutableStateOf(LogoArtworkCache[url]) }
    LaunchedEffect(url) {
        LogoArtworkCache[url]?.let { artwork = it; return@LaunchedEffect }
        val computed = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        // Для logo-зони на 1080p цього досить, а D-pad не декодує
                        // оригінальний PNG на кожній новій картці.
                        .size(Size(640, 320))
                        .allowHardware(false)
                        // Сирий software-бітмап після обрізки не потрібен, а в пам'яті Coil
                        // він лежав би поруч із нашою копією — двічі на кожен логотип.
                        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                        .build(),
                )
                (result.image as? coil3.BitmapImage)?.bitmap?.let(::prepareLogo)
            }.getOrNull()
        }
        computed?.let { LogoArtworkCache[url] = it }
        artwork = computed
    }
    return artwork
}

private data class PreparedLogo(
    val bitmap: Bitmap,
    val isDarkMonochrome: Boolean,
)

private fun prepareLogo(source: Bitmap): PreparedLogo? {
    var left = source.width
    var top = source.height
    var right = -1
    var bottom = -1
    var luma = 0L
    var colorfulPixels = 0
    var visiblePixels = 0

    // Один getPixels замість getPixel у подвійному циклі: JNI-виклик на кожен піксель
    // робив розбір логотипа помітно дорожчим за його завантаження.
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        if ((pixel ushr 24) < LogoVisibleAlpha) continue
        val x = index % source.width
        val y = index / source.width
        left = minOf(left, x)
        top = minOf(top, y)
        right = maxOf(right, x)
        bottom = maxOf(bottom, y)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        luma += (r * 299 + g * 587 + b * 114) / 1000
        if (maxOf(r, g, b) - minOf(r, g, b) >= LogoColorChroma) colorfulPixels++
        visiblePixels++
    }
    if (visiblePixels == 0 || right < left || bottom < top) return null

    val padding = maxOf(2, minOf(right - left + 1, bottom - top + 1) / 24)
    val cropLeft = (left - padding).coerceAtLeast(0)
    val cropTop = (top - padding).coerceAtLeast(0)
    val cropRight = (right + padding + 1).coerceAtMost(source.width)
    val cropBottom = (bottom + padding + 1).coerceAtMost(source.height)
    val bitmap = Bitmap.createBitmap(source, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)

    val averageLuma = luma / visiblePixels
    val isDarkMonochrome = averageLuma < 96 && colorfulPixels * 10 < visiblePixels
    return PreparedLogo(bitmap, isDarkMonochrome)
}

private const val LogoVisibleAlpha = 40
private const val LogoColorChroma = 36

/**
 * Обрізані логотипи — software-бітмапи до ~800 КБ. 128 штук важили до 100 МБ на приставці з
 * 1 ГБ; 48 — це кілька екранів рядів, решту Coil дістане з диска.
 */
private val LogoArtworkCache = object : LinkedHashMap<String, PreparedLogo>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreparedLogo>?) = size > 48
}

/**
 * Велика картка між рядами, як в Apple TV («Family Business»): високий кадр, знак назви,
 * що це й коли. Для анонсів у Кінозалі — їх небагато, і звичайний ряд їх губив.
 */
@Composable
fun CatalogTallCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    uaAudio: Boolean? = null,
    /** «вийде 7 жовтня» — квитком у правому куті: «вийде», велике «7», «жовтня». */
    note: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    // .card.tall прототипу: 380×480 px, кадр згори, фокус — 1.05 з кільцем і теплою тінню.
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(TallCardWidth, TallCardHeight).focusGlow(ProtoCardCorner),
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.CardBlack,
        focusedBackground = LikhtarColors.CardBlack,
        focusedScale = 1.05f,
    ) { focused ->
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = TmdbImages.backdrop(item.backdropPath) ?: TmdbImages.poster(item.posterPath),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(TallScrim),
            )
            if (uaAudio == true) VoiceBadge(label = "UA", modifier = Modifier.align(Alignment.TopStart).padding(7.dp))
            // .when: «вийде» 14 px, число моно 44 px, місяць 16 px бурштином.
            val ticket = note?.let { TicketNote.find(it.trim()) }
            if (ticket != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .widthIn(min = 46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(LikhtarColors.BadgeFloor.copy(alpha = 0.72f))
                        .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 5.dp),
                ) {
                    Text(ticket.groupValues[1], style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 7.sp), color = LikhtarColors.TextMuted, maxLines = 1)
                    Text(
                        ticket.groupValues[2],
                        style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 22.sp),
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                    Text(ticket.groupValues[3], style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 8.sp), color = LikhtarColors.Glow, maxLines = 1)
                }
            }
            // .foot: 22 px від країв; лого до 80 % × 84 px, інакше назва 30 px; під ним «Фільм · жанр · жанр» 19 px.
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(11.dp),
            ) {
                TitleLogoArea(
                    logoPath = item.logoPath,
                    title = item.title,
                    height = 42.dp,
                    fallbackStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 16.sp),
                    widthFraction = 0.8f,
                )
                val meta = listOf(stringResource(if (item.kind == TitleKind.TV) R.string.cinema_kind_series else R.string.cinema_kind_movie)) + item.genres.take(2)
                Text(
                    text = meta.joinToString(" · "),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp),
                    color = LikhtarColors.TextPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            FocusRing(focused, ProtoCardCorner)
        }
    }
}

/** «вийде 20 жовтня» → «вийде», «20», «жовтня» (як /^(.*?)\s*(\d+)\s+(\S+)$/ у прототипі). */
private val TicketNote = Regex("""^(.*?)\s*(\d+)\s+(\S+)$""")

val TallCardWidth = 230.dp
val TallCardHeight = 290.dp

// Підкладки під підпис на кадрі — один Brush на процес, а не новий на кожну картку ряду
// при кожній композиції.
// .card .shade прототипу: 0deg, rgba(10,9,8,.85) → 0 на 58 %.
private val BannerScrim = Brush.verticalGradient(0.42f to Color.Transparent, 1.00f to LikhtarColors.Shade.copy(alpha = 0.85f))
private val WideScrim = Brush.verticalGradient(0.45f to Color.Transparent, 1.00f to LikhtarColors.Background.copy(alpha = 0.9f))
private val TallScrim = Brush.verticalGradient(0.45f to Color.Transparent, 1f to LikhtarColors.Background.copy(alpha = 0.96f))
