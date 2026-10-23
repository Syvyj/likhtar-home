package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.wrapContentWidth
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    /** «З медіатеки» — є свій файл (DLNA/SMB); `null` — кнопки немає. */
    localLabel: String? = null,
    onLocal: () -> Unit = {},
) {
    val item = details.item
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // .hero прототипу: колонка внизу ліворуч (880 px заввишки, 80 px знизу) — знак,
        // мітки, зачин і дії з проміжком 22 px; мітки й дії до 800 px, зачин до 760 px.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 140.dp)
                .width(400.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // .logo: 600×150 px, contain, ліворуч по центру.
            TitleLogoArea(
                logoPath = details.logoPath,
                title = item.title,
                height = 75.dp,
                localTitle = true,
                widthFraction = 0.75f,
                fallbackStyle = if (item.title.length <= 10) LikhtarType.HeroTitle.copy(fontSize = 48.sp, lineHeight = 50.sp)
                else LikhtarType.HeroTitle.copy(fontSize = 30.sp, lineHeight = 33.sp),
                alignment = Alignment.CenterStart,
            )

            // .chips: порядок лаунчера — озвучка, якість, рік, тип, вік, оцінка, статус, жанри.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (uaAudio) {
                    ProtoUaChip(
                        label = listOf("UA").plus(uaProof.takeIf { voices.isEmpty() }.orEmpty().map { it.uppercase() }).joinToString(" · "),
                    )
                }
                voices.forEach { ProtoChip(it) }
                quality.takeIf { it.isNotEmpty() }?.let { ProtoChip(it, mono = true) }
                ProtoChip(item.year, mono = true)
                val series = item.kind == TitleKind.TV
                ProtoChip(
                    kindLabel ?: stringResource(if (series) R.string.cinema_kind_series else R.string.cinema_kind_movie),
                    icon = if (series) DsR.drawable.ic_solar_tv else DsR.drawable.ic_solar_clapper,
                )
                if (details.runtimeMinutes > 0) {
                    val hours = details.runtimeMinutes / 60
                    val mins = details.runtimeMinutes % 60
                    ProtoChip(if (hours > 0) stringResource(R.string.cinema_hours_minutes_short, hours, mins) else stringResource(R.string.cinema_minutes_short, mins), mono = true)
                }
                ProtoChip(details.ageRating, mono = true)
                if (item.rating > 0) ProtoChip(String.format(java.util.Locale.US, "%.1f", item.rating), mono = true, icon = DsR.drawable.ic_solar_star)
                when (details.scan?.status) {
                    "Returning Series" -> ProtoChip(stringResource(R.string.cinema_status_returning), dot = true)
                    "Ended" -> ProtoChip(stringResource(R.string.cinema_status_ended), dot = true)
                    "Canceled" -> ProtoChip(stringResource(R.string.cinema_status_canceled), dot = true)
                }
                details.genres.take(3).forEach { ProtoChip(it) }
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

            // .actions: gap 16 px, margin-top 8 px; кнопки — .btn (76 px, поле 36, 26 px).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                // Початковий фокус — на першу кнопку, яка справді є: у публічній збірці «Дивитись»
                // немає, і сторінка стояла зовсім без фокуса — пульт нічого не робив.
                var firstTaken = false
                fun first(): Modifier = if (firstTaken) Modifier else Modifier.focusRequester(playFocus).also { firstTaken = true }
                actionsOrder.forEach { group ->
                    when (group) {
                        TitleActions.PLAY -> {
                            ProtoButton(
                                label = mainLabel,
                                onClick = onPlay,
                                onLongClick = onPick,
                                modifier = first(),
                                // .btn.main img: знак Ліхтаря 40 px.
                                leading = { focused -> LikhtarIcons.Lantern(size = 20.dp, onLight = focused) },
                            )
                            if (localLabel != null) {
                                ProtoButton(label = localLabel, onClick = onLocal, icon = DsR.drawable.ic_solar_folder)
                            }
                        }

                        TitleActions.SERVICES -> services.forEach { option ->
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
                            ProtoButton(
                                // Галочку й плюс малює значок — зі ✓/＋ у самому підписі вони двоїлись.
                                label = stringResource(if (inWatchlist) R.string.cinema_in_watchlist else R.string.cinema_add_to_watchlist).removePrefix("✓").removePrefix("＋").trim(),
                                onClick = onWatchlist,
                                modifier = first(),
                                icon = if (inWatchlist) DsR.drawable.ic_solar_check_read else DsR.drawable.ic_solar_add_circle,
                            )
                            if (uaWaitLabel != null) ProtoButton(label = uaWaitLabel, onClick = onUaWait, icon = DsR.drawable.ic_solar_bell)
                            if (mark != null) ProtoButton(
                                label = mark.second,
                                onClick = onMark,
                                leading = { focused ->
                                    MarkGlyph(mark.first, if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Glow, size = 16.dp)
                                },
                            )
                        }

                        TitleActions.FRANCHISE -> Unit
                    }
                }
            }
        }

        // Друга сцена визирає знизу (.scene2: padding 40 px, вкладки, лінія, наступна серія).
        // Нефокусоване: це вид, ↓ відкриває сцену цілком.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .offset(y = 460.dp),
        ) {
            ProtoTabsRow(
                tabs = titleTabs(details),
                active = titleTabs(details).firstOrNull(),
                modifier = Modifier.offset(x = (-11).dp),
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

