package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.wrapContentWidth
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.designsystem.QualityBadge
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.VoiceBadge
import com.likhtar.home.core.designsystem.ChipKind
import com.likhtar.home.core.designsystem.MetaChip
import com.likhtar.home.core.catalog.FranchiseRef
import com.likhtar.home.core.catalog.TitleDetails
import com.likhtar.home.core.data.TitleActions
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.WatchOption
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.HeroBackdrop
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.R as DsR
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.tv.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border

/**
 * Шапка картки: постер ліворуч, усе про фільм праворуч.
 *
 * Постер — не прикраса, а головний об'єкт: це готова графіка прокатника, вона читається
 * за будь-якої яскравості, тоді як кадр із фільму щоразу різний і текст на ньому доводиться
 * рятувати підкладками. Кадр лишається тлом (див. [HeroBackdrop]) і навмисно не несе змісту.
 */
@Composable
internal fun PosterHead(
    details: TitleDetails,
    mainLabel: String,
    actionsOrder: List<String>,
    appOptions: List<WatchOption>,
    inWatchlist: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onPick: () -> Unit,
    onWatchlist: () -> Unit,
    onOpenFranchise: (FranchiseRef) -> Unit,
    onOpenElsewhere: (WatchOption) -> Unit,
    modifier: Modifier = Modifier,
    /** Чим назвати матеріал у плашці. «Реаліті» кладе позиції в модель Кінозалу, але шоу
     *  не серіал, і «Серіал» під назвою шоу — неправда, якої видно з дивана. */
    kindLabel: String? = null,
    /** Ми САМІ бачили українську доріжку. `false` — або немає, або не питали: мітки не буде. */
    uaAudio: Boolean = false,
    /** Чим підтверджена мітка: сервіс або названа озвучка. Порожньо — сама мітка. */
    uaProof: List<String> = emptyList(),
    /** Українські озвучки — рядком під мітками, щоб видно було одразу, чия вона. */
    voices: List<String> = emptyList(),
    /** Радар української: підпис кнопки «Чекаю українську»; `null` — кнопки немає. */
    uaWaitLabel: String? = null,
    onUaWait: () -> Unit = {},
    /** «Позначити»: переглянуто / пропустити / не рекомендувати (MarkPanel). */
    mark: Pair<MarkIcon, String>? = null,
    onMark: () -> Unit = {},
    quality: String = "",
    /** Наступна серія, що визирає з другої сцени: кадр і підпис; `null` — без неї. */
    peekEpisode: Pair<String?, String>? = null,
    /** Дотик по вкладці, що визирає знизу: відкрити другу сцену з нею (на пульті це ↓). */
    onPeekTab: ((TitleTab) -> Unit)? = null,
    /** «З медіатеки» — є свій файл (DLNA/SMB); `null` — кнопки немає. */
    localLabel: String? = null,
    onLocal: () -> Unit = {},
) {
    val item = details.item
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // .hero прототипу: знак, мітки, зачин і дії з проміжком 22 px; мітки й дії до
        // 800 px, зачин до 760 px. Колонка — по центру зони над вкладками; коли кнопок стільки, що
        // вона не вміщається, — від верху (обмежена висота: Column тоді починає згори). Раніше
        // стояла на фіксованих 140 dp від низу: три ряди кнопок упирали знак у край екрана, а під
        // кнопками лишалась порожнеча.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .height(maxHeight - HeroTabsPeek)
                .wrapContentHeight(Alignment.CenterVertically)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // .logo: 600×150 px, contain, ліворуч по центру.
            TitleLogoArea(
                logoPath = details.logoPath,
                title = item.title,
                height = 75.dp,
                localTitle = true,
                widthFraction = 0.75f,
                modifier = Modifier.width(400.dp),
                fallbackStyle = if (item.title.length <= 10) LikhtarType.HeroTitle.copy(fontSize = 48.sp, lineHeight = 50.sp)
                else LikhtarType.HeroTitle.copy(fontSize = 30.sp, lineHeight = 33.sp),
                alignment = Alignment.CenterStart,
            )

            // Мета одним рядком, як в Apple TV: рік · тип · тривалість · жанри · оцінка · статус.
            val series = item.kind == TitleKind.TV
            val runtime = if (details.runtimeMinutes > 0) {
                val hours = details.runtimeMinutes / 60
                val mins = details.runtimeMinutes % 60
                if (hours > 0) stringResource(R.string.cinema_hours_minutes_short, hours, mins) else stringResource(R.string.cinema_minutes_short, mins)
            } else ""
            val status = when (details.scan?.status) {
                "Returning Series" -> stringResource(R.string.cinema_status_returning)
                "Ended" -> stringResource(R.string.cinema_status_ended)
                "Canceled" -> stringResource(R.string.cinema_status_canceled)
                else -> ""
            }
            Text(
                text = listOf(
                    item.year,
                    kindLabel ?: stringResource(if (series) R.string.cinema_kind_series else R.string.cinema_kind_movie),
                    runtime,
                    details.genres.take(2).joinToString(", "),
                    if (item.rating > 0) "★ " + String.format(java.util.Locale.US, "%.1f", item.rating) else "",
                    status,
                ).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 14.5.sp),
                color = LikhtarColors.WarmStone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(460.dp),
            )
            // Плашки — монохромні контури (вік, якість), українське — бурштином: озвучка видна одразу.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(460.dp),
            ) {
                com.likhtar.home.core.designsystem.OutlineBadge(details.ageRating)
                quality.split("·").map { it.trim() }.filter { it.isNotEmpty() }.forEach { com.likhtar.home.core.designsystem.OutlineBadge(it) }
                if (uaAudio) {
                    ProtoUaChip(
                        label = listOf("UA").plus(uaProof.takeIf { voices.isEmpty() }.orEmpty().map { it.uppercase() }).joinToString(" · "),
                    )
                }
                voices.forEach { com.likhtar.home.core.designsystem.OutlineBadge(it, amber = true) }
            }

            // .lead: 28 px / 1.42, білий з тінню, до 760 px.
            Text(
                text = leadSentences(details.card.summary.ifBlank { item.overview }),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = LikhtarType.Body,
                    fontSize = 14.sp,
                    lineHeight = 19.9.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 24f),
                ),
                color = Color.White,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(380.dp),
            )

            val services = (appOptions + details.watchOptions)
                .sortedBy { it.kind.ordinal }
                .distinctBy { it.launch?.packageName ?: it.label }

            // Дії як в Apple TV: одна велика головна кнопка, решта — круглі значки з підписом у фокусі;
            // сервіси — рядком «Також у …» під ними (коли є своя головна дія).
            val hasPlay = TitleActions.PLAY in actionsOrder
            var roundHint by remember { mutableStateOf<String?>(null) }
            HeroActions(narrow = 460.dp, gap = 12.dp, modifier = Modifier.padding(top = 6.dp)) {
                // Початковий фокус — на першу кнопку, яка справді є: у публічній збірці «Дивитись»
                // немає, і сторінка стояла зовсім без фокуса — пульт нічого не робив.
                var firstTaken = false
                fun first(): Modifier = if (firstTaken) Modifier else Modifier.focusRequester(playFocus).also { firstTaken = true }
                // Свій файл (медіатека, Jellyfin) — і там, де групи «Дивитись» немає (публічна збірка):
                // інакше кнопка жила лише поруч із «Дивитись» і в публічній збірці не з'являлась.
                if (localLabel != null && TitleActions.PLAY !in actionsOrder) {
                    ProtoButton(label = localLabel, onClick = onLocal, icon = DsR.drawable.ic_solar_folder, modifier = first())
                }
                actionsOrder.forEach { group ->
                    when (group) {
                        TitleActions.PLAY -> {
                            ProtoButton(
                                label = mainLabel,
                                onClick = onPlay,
                                onLongClick = onPick,
                                modifier = first(),
                                height = 48.dp,
                                padding = 24.dp,
                                fontSize = 16.5.sp,
                                bold = true,
                                // .btn.main img: знак Ліхтаря.
                                leading = { focused -> LikhtarIcons.Lantern(size = 22.dp, onLight = focused) },
                            )
                            if (localLabel != null) {
                                ProtoButton(label = localLabel, onClick = onLocal, icon = DsR.drawable.ic_solar_folder)
                            }
                        }

                        TitleActions.SERVICES -> if (!hasPlay) services.forEach { option ->
                            val serviceName = option.service.ifBlank { option.label }
                            val brand = StreamingBrand.fromTitle(serviceName)
                            ProtoButton(
                                label = brand?.title ?: serviceName,
                                onClick = { onOpenElsewhere(option) },
                                enabled = option.kind != WatchOption.OptionKind.INFO,
                                modifier = if (option.kind != WatchOption.OptionKind.INFO) first() else Modifier,
                                icon = DsR.drawable.ic_solar_play_circle,
                                bold = true,
                                trailing = when (option.audio) {
                                    "uk" -> { { VoiceBadge() } }
                                    "subs" -> { { QualityBadge(stringResource(R.string.cinema_badge_subs)) } }
                                    else -> null
                                },
                            )
                        }

                        TitleActions.SOURCES -> Unit

                        TitleActions.WATCHLIST -> {
                            val listLabel = stringResource(if (inWatchlist) R.string.cinema_in_watchlist else R.string.cinema_add_to_watchlist).removePrefix("✓").removePrefix("＋").trim()
                            RoundAction(
                                label = listLabel,
                                onClick = onWatchlist,
                                onHint = { roundHint = it },
                                modifier = first(),
                            ) { focused ->
                                Icon(painterResource(if (inWatchlist) DsR.drawable.ic_solar_check_read else DsR.drawable.ic_solar_add_circle), null,
                                    tint = if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Paper, modifier = Modifier.size(20.dp))
                            }
                            if (uaWaitLabel != null) RoundAction(label = uaWaitLabel, onClick = onUaWait, onHint = { roundHint = it }) { focused ->
                                Icon(painterResource(DsR.drawable.ic_solar_bell), null,
                                    tint = if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Paper, modifier = Modifier.size(20.dp))
                            }
                            if (mark != null) RoundAction(label = mark.second, onClick = onMark, onHint = { roundHint = it }) { focused ->
                                MarkGlyph(mark.first, if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Paper, size = 20.dp)
                            }
                        }

                        TitleActions.FRANCHISE -> Unit
                    }
                }
            }
            // Підпис круглої дії, на якій фокус: значок без слова на ТБ не читається.
            Text(
                text = roundHint.orEmpty(),
                style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.sp),
                color = LikhtarColors.WarmStone,
                maxLines = 1,
            )
            if (hasPlay && services.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cinema_also_on), style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp), color = LikhtarColors.WarmMuted)
                    services.forEach { option ->
                        val serviceName = option.service.ifBlank { option.label }
                        ProtoButton(
                            label = StreamingBrand.fromTitle(serviceName)?.title ?: serviceName,
                            onClick = { onOpenElsewhere(option) },
                            enabled = option.kind != WatchOption.OptionKind.INFO,
                            icon = null,
                            height = 32.dp,
                            padding = 14.dp,
                            fontSize = 13.sp,
                            bold = true,
                            trailing = when (option.audio) {
                                "uk" -> { { VoiceBadge() } }
                                "subs" -> { { QualityBadge(stringResource(R.string.cinema_badge_subs)) } }
                                else -> null
                            },
                        )
                    }
                }
            }
        }

        // Друга сцена визирає знизу (.scene2: padding 40 px, вкладки, лінія, наступна серія).
        // Нефокусоване: це вид, ↓ відкриває сцену цілком. Відступ — від низу екрана, як і в
        // колонки вище: при щільності 240 екран «вищий» (720 dp), і фіксовані 460 від верху
        // клали вкладки й кадр серії поверх опису й кнопок (ATV9, 1080p).
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .offset(y = maxHeight - HeroTabsPeek),
        ) {
            ProtoTabsRow(
                tabs = titleTabs(details),
                active = titleTabs(details).firstOrNull(),
                modifier = Modifier.offset(x = (-11).dp),
                onTap = onPeekTab,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(LikhtarColors.Paper.copy(alpha = 0.2f)))
            peekEpisode?.let { (still, title) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    AsyncImage(
                        model = still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(176.dp, 99.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LikhtarColors.Surface),
                    )
                    Text(
                        title,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * Дія-пігулка: один підпис, без другого рядка.
 *
 * Головна дія залита акцентом і без фокуса — на картку заходять, щоб натиснути саме її,
 * і вона має бути видною ще до того, як користувач ворухне пультом.
 */
@Composable
internal fun ActionPill(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    /** Без підкладки, поки не обране й не сфокусоване: так стоять неактивні сезони. */
    quiet: Boolean = false,
    /** Знак ліхтаря перед підписом — лише в головної дії. */
    lantern: Boolean = false,
    /** Знак позначки перед підписом — кнопка «Позначити». */
    markIcon: MarkIcon? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // Спільна кнопка лаунчера (LikhtarButton); тут лише значки сторінки тайтла.
    LikhtarButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        primary = primary,
        enabled = enabled,
        quiet = quiet,
        onLongClick = onLongClick,
        leading = when {
            lantern -> { _, onLight -> LikhtarIcons.Lantern(size = 20.dp, onLight = onLight) }
            markIcon != null -> { _, onLight ->
                MarkGlyph(markIcon, if (onLight) LikhtarColors.LanternAmberDeep else LikhtarColors.LanternAmber, size = 20.dp)
            }
            else -> null
        },
    )
}

/** Вік у рамці — рівно як в Apple: цифра в тонкій плашці, без слова «рейтинг». */
/**
 * Зачин синопсису — два перші речення.
 *
 * TMDB віддає один `overview` абзацом, окремого короткого опису в нього немає. Ріжемо по
 * крапці з пробілом, а не по `.`: інакше «2 год 9 хв» і скорочення на кшталт «т. зв.»
 * рвали б фразу на початку. Двох речень вистачає, щоб думка закінчилась; якщо крапок
 * менше — віддаємо як є, обріже вже `maxLines`.
 */
internal fun leadSentences(overview: String, count: Int = 2): String {
    val text = overview.trim()
    var end = -1
    repeat(count) {
        val next = text.indexOf(". ", startIndex = end + 1)
        if (next > 0) end = next
    }
    return if (end > 0) text.substring(0, end + 1) else text
}

/**
 * Статус тайтла словами. Порожньо — TMDB не дав, і плашку не малюємо.
 *
 * `Canceled` перекладаємо не як «скасовано», а як «закрито»: скасувати можуть анонс, а
 * тут ідеться про серіал, який обірвали, і людині це треба знати до першої серії.
 */

/**
 * Дії шапки: перші [NARROW_ROWS] ряди — у ширину колонки ([narrow]), і лише останній — повною
 * шириною: широкий другий ряд лишав під собою порожнє місце, а кнопки тяглись через увесь
 * кадр. Без межі рядів FlowRow на 400 dp ріс угору третім-четвертим рядом і
 * виштовхував знак назви за край екрана.
 */
private const val NARROW_ROWS = 2

@Composable
private fun HeroActions(narrow: Dp, gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = mutableListOf(mutableListOf<Placeable>())
        var x = 0
        placeables.forEach { p ->
            val limit = if (rows.size <= NARROW_ROWS) narrow.roundToPx().coerceAtMost(constraints.maxWidth) else constraints.maxWidth
            if (rows.last().isNotEmpty() && x + gapPx + p.width > limit) {
                rows += mutableListOf<Placeable>()
                x = 0
            }
            x += (if (rows.last().isEmpty()) 0 else gapPx) + p.width
            rows.last() += p
        }
        val filled = rows.filter { it.isNotEmpty() }
        val width = filled.maxOfOrNull { row -> row.sumOf { it.width } + gapPx * (row.size - 1) } ?: 0
        val height = filled.sumOf { row -> row.maxOf { it.height } } + gapPx * (filled.size - 1).coerceAtLeast(0)
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            filled.forEach { row ->
                var rx = 0
                row.forEach { it.placeRelative(rx, y); rx += it.width + gapPx }
                y += row.maxOf { it.height } + gapPx
            }
        }
    }
}

/** Скільки знизу займає друга сцена, що визирає (вкладки й лінія): шапка живе над нею. */
private val HeroTabsPeek = 80.dp

/** Кругла дія сторінки тайтлу (tvOS): значок у колі; підпис віддає наверх у фокусі ([onHint]). */
@Composable
internal fun RoundAction(
    label: String,
    onClick: () -> Unit,
    onHint: (String?) -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (focused: Boolean) -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .onFocusChanged { if (it.isFocused) onHint(label) else onHint(null) },
        shape = CircleShape,
        background = LikhtarColors.Surface.copy(alpha = 0.85f),
        focusedBackground = LikhtarColors.Paper,
        // Контур — щоб коло читалось і на темному кадрі, не лише значок у порожнечі.
        borderColor = LikhtarColors.PillBorderHighContrast,
        focusedScale = 1.08f,
        instantFill = true,
    ) { focused ->
        Box(Modifier.align(Alignment.Center)) { icon(focused) }
    }
}

