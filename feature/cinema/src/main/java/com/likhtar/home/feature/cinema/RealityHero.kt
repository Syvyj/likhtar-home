package com.likhtar.home.feature.cinema

import androidx.compose.animation.AnimatedContent
import com.likhtar.home.core.designsystem.LikhtarColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.R as DsR
import com.likhtar.home.core.designsystem.VoiceBadge

/**
 * Кадр шапки Реаліті (.frame прототипу): лише ліва частина, 1360×790 px, center 30% /
 * cover, перехрестя .5s; праворуч — графіт під «програму». Градієнти статичні.
 */
@Composable
internal fun RealityFrame(art: String?) {
    Box(Modifier.width(680.dp).height(395.dp)) {
        AnimatedContent(
            targetState = art,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
            label = "realityFrame",
            modifier = Modifier.fillMaxSize(),
        ) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.4f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                // 90deg: .92 → .55 на 34 % → 0 на 58 % → .2 на 78 % → тло.
                .background(
                    Brush.horizontalGradient(
                        0f to LikhtarColors.Background.copy(alpha = 0.92f), 0.34f to LikhtarColors.Background.copy(alpha = 0.55f), 0.58f to LikhtarColors.Background.copy(alpha = 0f),
                        0.78f to LikhtarColors.Background.copy(alpha = 0.2f), 1f to LikhtarColors.Background,
                    ),
                )
                // 0deg: тло знизу → .7 на 22 % → 0 на 50 %.
                .background(Brush.verticalGradient(0.5f to LikhtarColors.Background.copy(alpha = 0f), 0.78f to LikhtarColors.Background.copy(alpha = 0.7f), 1f to LikhtarColors.Background))
                // 180deg: --deep .7 → 0 на 22 %.
                .background(Brush.verticalGradient(0f to LikhtarColors.BackgroundDeep.copy(alpha = 0.7f), 0.22f to LikhtarColors.BackgroundDeep.copy(alpha = 0f))),
        )
    }
}

/**
 * Шапка Реаліті: ліворуч — шоу, що зараз у кадрі (знак чи назва, «канал · жанр · шоу · новий
 * випуск вчора», опис), праворуч — «Нові випуски» стовпчиком дат, як телепрограма. Випуск у
 * фокусі засвічує свою цятку на лінії й міняє кадр і опис ліворуч; OK — відкрити шоу.
 * [top] — де шапка починається на екрані: розміри прототипу відраховані від верху кадру.
 */
@Composable
internal fun RealityGuideHero(
    pool: List<CatalogItem>,
    current: CatalogItem?,
    top: Dp,
    focus: FocusRequester,
    menuFocus: FocusRequester,
    onShow: (Int) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    ua: (CatalogItem) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height((395.dp - top).coerceAtLeast(0.dp))) {
        // .about: 900×500 px з 190 px, низом.
        current?.let { item ->
            Box(
                Modifier
                    .offset(x = LikhtarDimens.ScreenPadding, y = 95.dp - top)
                    .width(450.dp)
                    .height(250.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                AnimatedContent(
                    targetState = item,
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 140)) + slideInVertically(tween(180, delayMillis = 140)) { 6 }) togetherWith fadeOut(tween(140))
                    },
                    contentKey = { it.id },
                    label = "realityAbout",
                ) { shown -> RealityAbout(shown, ua(shown)) }
            }
        }
        // .guide: з 1232 px, 150 px від верху, 600 px завширшки.
        Column(
            Modifier
                .offset(x = 616.dp, y = 75.dp - top)
                .width(300.dp),
        ) {
            Text(
                stringResource(R.string.cinema_reality_new_episodes),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = Color.White,
                modifier = Modifier.padding(start = 22.dp, bottom = 7.dp),
            )
            Box {
                // Лінії розкладу з цятками більше немає: список і без неї читається.
                Column {
                    pool.forEachIndexed { i, item ->
                        GuideRow(
                            item = item,
                            onFocused = { onShow(i) },
                            onClick = { onOpen(item) },
                            modifier = Modifier
                                .then(if (item == current) Modifier.focusRequester(focus) else Modifier)
                                .focusProperties {
                                    left = FocusRequester.Cancel
                                    if (i == 0) up = menuFocus
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealityAbout(item: CatalogItem, ua: Boolean) {
    Column {
        val logo = item.logoPath?.let { TmdbImages.logo(it) ?: it }
        if (logo != null) {
            // .mark: до 560×190 px, низом ліворуч; англійський знак — з назвою Oswald 40 px.
            AsyncImage(
                model = logo,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier.widthIn(max = 280.dp).height(95.dp),
            )
            if (TmdbImages.needsLocalTitle(item.logoPath, item.title)) {
                Text(
                    item.title,
                    style = TextStyle(fontFamily = LikhtarType.Poster, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
                    color = Color.White,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        } else {
            // h1: 92 px / 700, .98, −.035em.
            Text(
                item.title,
                style = TextStyle(
                    fontFamily = LikhtarType.Body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 46.sp,
                    lineHeight = 45.sp,
                    letterSpacing = (-1.6).sp,
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 48f),
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // .meta: «<b>канал</b> · жанр · шоу · новий випуск вчора», 24 px приглушено; UA поруч.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(top = 13.dp),
        ) {
            val genre = item.genres.firstOrNull().orEmpty()
            val showLabel = stringResource(R.string.cinema_kind_show_lower).takeIf { item.kind == TitleKind.TV }.orEmpty()
            val newEpisode = if (item.channel.isNotBlank() && item.availabilityDate.isNotBlank()) {
                stringResource(R.string.cinema_new_episode_on, whenLabel(item.availabilityDate))
            } else {
                ""
            }
            val lead = item.channel.ifBlank { item.rating.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.1f", it) }.orEmpty() }
            Text(
                buildAnnotatedString {
                    if (lead.isNotBlank()) withStyle(SpanStyle(color = LikhtarColors.TextPrimary, fontWeight = FontWeight.SemiBold)) { append(lead) }
                    listOf(genre, showLabel, newEpisode).filter(String::isNotBlank).forEach { part ->
                        if (length > 0) append(" · ")
                        append(part)
                    }
                },
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.sp),
                color = LikhtarColors.TextDim,
                maxLines = 1,
            )
            if (ua) VoiceBadge()
        }
        // p: 25 px / 1.42, .82, 3 рядки, margin 18 px.
        Text(
            item.overview,
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.5.sp, lineHeight = 17.75.sp),
            color = LikhtarColors.TextPrimary.copy(alpha = 0.82f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

/**
 * Рядок «Нових випусків» (.ep прототипу): дата моно, назва 27 px і канал 18 px; у фокусі —
 * тепла біла пігулка з «▶ Дивитись».
 */
@Composable
private fun GuideRow(
    item: CatalogItem,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 18.dp, bottom = 2.dp).height(38.dp)) {
        FocusableSurface(
            onClick = onClick,
            modifier = modifier
                .fillMaxHeight()
                .width(282.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = RoundedCornerShape(LikhtarDimens.CornerPill),
            background = Color.Transparent,
            focusedBackground = LikhtarColors.Paper,
            borderColor = Color.Transparent,
            focusedScale = 1.03f,
            instantFill = true,
        ) { hasFocus ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp),
            ) {
                Text(
                    whenLabel(item.availabilityDate),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                    color = if (hasFocus) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                    modifier = Modifier.width(48.dp),
                    maxLines = 1,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                        color = if (hasFocus) LikhtarColors.Background else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.channel.ifBlank { item.genres.firstOrNull() ?: stringResource(R.string.cinema_kind_show_lower) },
                        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.sp),
                        color = if (hasFocus) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                        maxLines = 1,
                    )
                }
                if (hasFocus) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(DsR.drawable.ic_solar_play), null, tint = LikhtarColors.AmberOnPaper, modifier = Modifier.size(13.dp))
                        Text(
                            stringResource(R.string.cinema_watch),
                            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            color = LikhtarColors.Background,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** «сьогодні», «вчора» або «16.10» — як `when()` у прототипі. */
@Composable
private fun whenLabel(iso: String): String {
    val date = runCatching { java.time.LocalDate.parse(iso.take(10)) }.getOrNull() ?: return ""
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.cinema_today)
        today.minusDays(1) -> stringResource(R.string.cinema_yesterday)
        else -> String.format(java.util.Locale.US, "%02d.%02d", date.dayOfMonth, date.monthValue)
    }
}

