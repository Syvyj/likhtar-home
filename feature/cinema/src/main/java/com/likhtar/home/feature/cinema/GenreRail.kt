package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.SectionGenre
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.CardStack
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.StackFocusScale
import com.likhtar.home.core.designsystem.StackOrigin
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.SectionTitle
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.likhtar.home.core.designsystem.FocusRing
import com.likhtar.home.core.designsystem.focusGlow

/**
 * «Жанри», як в Apple TV: високі плитки — кадр жанру під його кольором і
 * назва внизу. Стоїть між рядами Кінозалу й Реаліті; плитка веде на сторінку жанру
 * добірками.
 */
@Composable
internal fun GenreRail(
    genres: List<SectionGenre>,
    onOpen: (SectionGenre) -> Unit,
    onFocused: (SectionGenre) -> Unit,
    modifier: Modifier = Modifier,
    /** Повернення після Back на плитку, з якої пішли на сторінку жанру ([returnPoint]). */
    restore: RailRestore? = null,
    rowIndex: Int = 0,
    /** Сторінка без [RailRestore] (сторінка сервісу) — своя пам'ять фокуса на плитку. */
    tileModifier: @Composable (SectionGenre) -> Modifier = { Modifier },
    /** Плитки 16:9 кольором жанру (.genre прототипу reality) замість високого стосу. */
    wide: Boolean = false,
) {
    val rail = rememberLazyListState()
    restore?.ScrollRailTo(rail) { key -> genres.indexOfFirst { "genre:${it.key}" == key } }
    Column(modifier = modifier) {
        // Поверх ряду: розкрите віяло стосу у фокусі налазить на заголовок.
        SectionTitle(text = stringResource(R.string.cinema_genres), modifier = Modifier.zIndex(1f))
        LikhtarRail(state = rail) {
            itemsIndexed(genres, key = { _, g -> g.key }) { i, genre ->
                if (wide) WideGenreCard(
                    genre = genre,
                    onClick = {
                        restore?.leave("genre:${genre.key}")
                        onOpen(genre)
                    },
                    modifier = Modifier
                        .cardSlot()
                        .then(tileModifier(genre))
                        .then(
                            if (restore != null) Modifier.returnPoint(restore, "genre:${genre.key}", rowIndex) { onFocused(genre) }
                            else Modifier.onFocusChanged { if (it.isFocused) onFocused(genre) },
                        ),
                ) else GenreCard(
                    genre = genre,
                    onClick = {
                        restore?.leave("genre:${genre.key}")
                        onOpen(genre)
                    },
                    // Ті самі поля, що в картки CollectionRail: інакше проміжок між жанрами
                    // (9dp) удвічі вужчий, ніж між постерами над ними (23dp), і колонки
                    // двох рядів не збігаються.
                    modifier = Modifier
                        .cardSlot()
                        .then(tileModifier(genre))
                        .then(
                            if (restore != null) Modifier.returnPoint(restore, "genre:${genre.key}", rowIndex) { onFocused(genre) }
                            else Modifier.onFocusChanged { if (it.isFocused) onFocused(genre) },
                        ),
                )
            }
        }
    }
}

/**
 * .genre прототипу: плитка 216×316 px стосом — дві задні картки кольором жанру
 * (brightness .62 і .42, вище й вужче); фокус — 1.06 від верхнього краю, кільце й тепла тінь,
 * а задні розходяться віялом: ліва −4°, права +5°.
 */
@Composable
private fun GenreCard(genre: SectionGenre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tone = Color(genre.color)
    var focused by remember { mutableStateOf(false) }
    // Щоразу інший кадр: розділ відкривається з чистого стану, а Back зі сторінки жанру
    // повертає той самий (rememberSaveable).
    val art = rememberSaveable(genre.key) { genre.backdrops.randomOrNull() ?: genre.backdropPath.orEmpty() }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "fan")
    val shape = RoundedCornerShape(GenreCorner)
    Box(
        modifier
            .padding(top = 16.dp)
            .size(GenreWidth, GenreHeight)
            .onFocusChanged { focused = it.hasFocus }
            .graphicsLayer {
                val k = 1f + 0.06f * t
                scaleX = k
                scaleY = k
                transformOrigin = StackOrigin
            },
    ) {
        // ::after — translateY(−32 px) scale(.88) → translate(28, −30 px) rotate(6°) scale(.9).
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val k = 0.88f + 0.02f * t
                    scaleX = k
                    scaleY = k
                    translationX = (14.dp * t).toPx()
                    translationY = -(16.dp + (-1).dp * t).toPx()
                    rotationZ = 6f * t
                    // Від верхнього краю, як і сам стос: з центру верх ховався за передньою.
                    transformOrigin = StackOrigin
                }
                .clip(shape)
                .background(tone.dim(0.55f)),
        )
        // ::before — translateY(−18 px) scale(.94) → translate(−26, −18 px) rotate(−5°) scale(.95).
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val k = 0.94f + 0.01f * t
                    scaleX = k
                    scaleY = k
                    translationX = -(13.dp * t).toPx()
                    translationY = -(9.dp + 0.dp * t).toPx()
                    rotationZ = -5f * t
                    transformOrigin = StackOrigin
                }
                .clip(shape)
                .background(tone.dim(0.75f)),
        )
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.matchParentSize().focusGlow(GenreCorner),
            shape = shape,
            background = tone,
            focusedBackground = tone,
            borderColor = Color.Transparent,
            focusedScale = 1f,
        ) { hasFocus ->
            Box(modifier = Modifier.fillMaxSize().clip(shape)) {
                AsyncImage(
                    // w1280: плитка 460×580 px ріже 16:9 по висоті — w500 тут розтягувало вдвічі.
                    model = TmdbImages.backdrop(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // .face::after: color-mix(тон 35 %) → 55 % на 55 % → 92 %.
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to tone.copy(alpha = 0.35f),
                            0.55f to tone.copy(alpha = 0.55f),
                            1f to tone.copy(alpha = 0.92f),
                        ),
                    ),
                )
                // b: 27 px / 600, 18 px зліва, 14 справа, 18 знизу, з тінню.
                Text(
                    text = genre.title,
                    style = TextStyle(
                        fontFamily = LikhtarType.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 14.85.sp,
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), blurRadius = 8f),
                    ),
                    color = Color.White,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 9.dp, bottom = 12.dp),
                )
                FocusRing(hasFocus, GenreCorner)
            }
        }
    }
}

/**
 * .genre прототипу reality: плитка 340 px 16:9 — кадр під кольором жанру (90deg: колір → .7 на
 * 40 % → темне .25), знизу тінь, назва 28 px / 700. Фокус — 1.07, кільце й тепла тінь.
 */
@Composable
private fun WideGenreCard(genre: SectionGenre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tone = Color(genre.color)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(213.dp).aspectRatio(16f / 9f).focusGlow(9.dp),
        shape = RoundedCornerShape(9.dp),
        background = tone,
        focusedBackground = tone,
        borderColor = Color.Transparent,
        focusedScale = 1.07f,
    ) { focused ->
        AsyncImage(
            model = TmdbImages.still(genre.backdropPath) ?: genre.backdropPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.88f }
                .background(Brush.horizontalGradient(0f to tone, 0.4f to tone.copy(alpha = 0.7f), 1f to LikhtarColors.ShadeDeep.copy(alpha = 0.25f))),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to LikhtarColors.ShadeDeep.copy(alpha = 0.55f))))
        Text(
            genre.title,
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 14.7.sp),
            color = Color.White,
            maxLines = 2,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 11.dp, bottom = 9.dp),
        )
        FocusRing(focused, 9.dp)
    }
}

private fun Color.dim(k: Float) = Color(red * k, green * k, blue * k, alpha)

/** Як анонси ([TallCardWidth] × [TallCardHeight]): жанри губились. */
private val GenreWidth = TallCardWidth
private val GenreHeight = TallCardHeight
private val GenreCorner = 11.dp
