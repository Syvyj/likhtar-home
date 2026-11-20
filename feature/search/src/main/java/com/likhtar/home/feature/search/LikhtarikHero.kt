package com.likhtar.home.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.R as DsR

/**
 * Герой Ліхтарика (макет «Новий Ліхтарик», екран 1): кадр праворуч, ліворуч — кікер, назва,
 * серія, рядок Ліхтарика й дві кнопки. Що саме — вирішує [pickHero].
 */
@Composable
internal fun LikhtarikHero(
    pick: HeroPick,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    playFocus: FocusRequester,
    logoOf: suspend (CatalogItem) -> String?,
    modifier: Modifier = Modifier,
    /** Кадр іде від верху екрана під меню й пошуком; текст — нижче на стільки. */
    contentTop: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val item: CatalogItem = when (pick) {
        is HeroPick.NewEpisode -> pick.show.item
        is HeroPick.Continue -> pick.item.asCatalogItem()
        is HeroPick.Advice -> pick.item
    }
    HeroScene(
        item = item,
        kicker = stringResource(
            when (pick) {
                is HeroPick.NewEpisode -> R.string.search_hero_new
                is HeroPick.Continue -> R.string.search_hero_continue
                is HeroPick.Advice -> R.string.search_hero_advice
            },
        ),
        meta = when (pick) {
            is HeroPick.NewEpisode -> stringResource(R.string.search_hero_episode, pick.show.season, pick.show.episode)
            is HeroPick.Continue -> if (pick.item.season > 0) stringResource(R.string.search_hero_episode, pick.item.season, pick.item.nextEpisode) else ""
            is HeroPick.Advice -> listOf(item.year, item.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
        },
        line = when (pick) {
            is HeroPick.NewEpisode -> stringResource(R.string.search_hero_new_line)
            is HeroPick.Continue -> stringResource(R.string.search_hero_continue_line)
            is HeroPick.Advice -> pick.why
        },
        playLabel = when (pick) {
            is HeroPick.NewEpisode -> stringResource(R.string.search_hero_play, pick.show.season, pick.show.episode)
            is HeroPick.Continue -> stringResource(R.string.search_hero_resume)
            is HeroPick.Advice -> stringResource(R.string.search_open)
        },
        // Опис тайтлу заповнює ліву колонку змістом, а не порожнечею під кнопкою.
        overview = item.overview,
        onPlay = onPlay,
        secondaryLabel = if (pick !is HeroPick.Advice) stringResource(R.string.search_hero_all_episodes) else null,
        onSecondary = onOpen,
        playFocus = playFocus,
        logoOf = logoOf,
        contentTop = contentTop,
        modifier = modifier,
    )
}

/**
 * Сцена героя Ліхтарика: кадр на весь верх (від краю екрана під меню й пошуком), ліворуч — кікер,
 * знак назви (як у Кінозалі; поки знака немає — назва текстом), мета, опис, рядок Ліхтарика й кнопки.
 * Нею малюється і «нове для вас», і «найкращий збіг» пошуку, і порада радника.
 */
@Composable
internal fun HeroScene(
    item: CatalogItem,
    kicker: String,
    meta: String,
    line: String,
    playLabel: String,
    onPlay: () -> Unit,
    playFocus: FocusRequester,
    logoOf: suspend (CatalogItem) -> String?,
    modifier: Modifier = Modifier,
    overview: String = "",
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    onLongPlay: (() -> Unit)? = null,
    contentTop: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // Знак назви: у розкладі TMDB і в порадах його немає — догружаємо, як для заставки й Кінозалу.
    // Стан — на тайтл: інакше при зміні героя лишався знак попереднього («THE EXPANSE» над «Ліхтарями»).
    var logoPath by androidx.compose.runtime.remember(item.tmdbId, item.kind) { androidx.compose.runtime.mutableStateOf(item.logoPath) }
    androidx.compose.runtime.LaunchedEffect(item.tmdbId, item.kind) {
        if (logoPath == null) logoPath = runCatching { logoOf(item) }.getOrNull()
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // Вільна зона ліворуч: від відступу екрана до місця, де кадр уже проявився (~35 % ширини).
        val freeWidth = maxWidth * FREE_ZONE - 48.dp
        // Кадр розчиняється сам — маскою прозорості, а не плямою кольору поверх: тло сторінки має
        // своє сяйво, і кольорова «шторка» лишала різкі межі зліва й знизу.
        AsyncImage(
            model = TmdbImages.hero(item.backdropPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterStart,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.7f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.42f to Color.Black), blendMode = BlendMode.DstIn)
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.3f to Color.Black, 0.62f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        // Ліворуч — лише головне, по центру вільного місця між пошуком і рядами.
        Column(
            // Від пошуку — невеликий відступ, решта висоти йде в повітря між рядами.
            modifier = Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = contentTop + 16.dp).offset(x = -maxWidth * SHIFT_LEFT).width(freeWidth),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            // Усе — по одній осі посередині вільної зони, щоб нічого не лізло на кадр.
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(kicker.uppercase(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 12.5.sp, letterSpacing = 0.9.sp), color = LikhtarColors.Glow, maxLines = 1)
            val logo = TmdbImages.logo(logoPath)
            if (logo != null) {
                AsyncImage(model = logo, contentDescription = item.title, contentScale = ContentScale.Fit, alignment = Alignment.Center, modifier = Modifier.heightIn(max = 84.dp).widthIn(max = freeWidth))
            } else {
                Text(item.title, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 40.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = LikhtarColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // Рік і рейтинг, а поруч — чому Ліхтарик це радить («Нове на Netflix»): один рядок, один стиль.
            if (meta.isNotBlank() || line.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (meta.isNotBlank()) Text(meta, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp), color = LikhtarColors.Paper, maxLines = 1)
                    if (meta.isNotBlank() && line.isNotBlank()) Text("·", style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp), color = LikhtarColors.Paper.copy(alpha = 0.6f))
                    if (line.isNotBlank()) {
                        LikhtarIcons.Lantern(size = 14.dp)
                        Text(line, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = LikhtarColors.Glow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 6.dp)) {
                ProtoButton(label = playLabel, onClick = onPlay, onLongClick = onLongPlay, height = 36.dp, modifier = Modifier.focusRequester(playFocus))
                if (secondaryLabel != null) ProtoButton(label = secondaryLabel, onClick = onSecondary, icon = null, height = 36.dp)
            }
        }
        // Опис — на самому кадрі, на підкладці, внизу праворуч.
        if (overview.isNotBlank()) {
            Text(
                overview,
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp, lineHeight = 18.sp),
                color = LikhtarColors.Paper,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 36.dp).widthIn(max = 470.dp)
                    .clip(RoundedCornerShape(12.dp)).background(LikhtarColors.BackgroundDeep.copy(alpha = 0.66f))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

/** Частка ширини сцени ліворуч, де кадру ще не видно: там стоїть колонка з назвою й кнопкою. */
private const val FREE_ZONE = 0.355f

/** Колонку трохи ліворуч від середини вільної зони — на око так рівніше біля кадру. */
private const val SHIFT_LEFT = 0.035f
