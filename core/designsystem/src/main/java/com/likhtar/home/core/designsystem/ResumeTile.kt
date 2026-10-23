package com.likhtar.home.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/**
 * Картка «Продовжити»: кадр 16:9, назва, серія і смужка прогресу.
 *
 * Одна на весь застосунок — ряд Кінозалу і сітка «Історії» мусять читатися як те саме,
 * тому й малюються тим самим компонентом. Ширину задає викликач: у ряду це фіксовані
 * 200dp, у сітці — частка рядка.
 */
@Composable
fun ResumeTile(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    logoUrl: String? = null,
) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .focusGlow(ProtoCardCorner)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.CardBlack,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .cachedScrim(dim = Color.Transparent) { listOf(if (logoUrl == null || hasBakedText(imageUrl)) BakedTextScrim else ResumeScrim) },
            )
            // Назва й серія разом: людина бачить «S04E05 · Серія 5» і в ряду, і в сітці.
            // .card.wide.has-sub прототипу: лого до 60 px над підписом, підпис — моно 17 px .8.
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 11.dp, bottom = 8.dp)) {
                ResumeTitle(title, logoUrl, LikhtarType.CardTitle, logoHeight = 34.dp, maxLines = 2)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Mono, fontSize = 8.5.sp),
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            progress?.takeIf { it > 0f }?.let { value ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(value.coerceIn(0f, 1f))
                            .background(LikhtarColors.Amber),
                    )
                }
            }
            FocusRing(focused, ProtoCardCorner)
        }
    }
}

/** Підкладка під назвою на кадрі. */
private val ResumeScrim = Brush.verticalGradient(
    0.42f to LikhtarColors.Shade.copy(alpha = 0f),
    1.00f to LikhtarColors.Shade.copy(alpha = 0.85f),
)

/**
 * Назва картки «Продовжити»: логотип, коли він є, інакше текст. Знак не завантажився —
 * теж текст: порожнє місце замість назви гірше за шрифт.
 */
@Composable
fun ResumeTitle(title: String, logoUrl: String?, style: TextStyle, logoHeight: Dp, maxLines: Int = 1) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl != null && !failed) {
        AsyncImage(
            model = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { failed = true },
            modifier = Modifier.fillMaxWidth(0.62f).height(logoHeight).padding(bottom = 4.dp),
        )
    } else {
        Text(
            text = titleMarkText(title),
            style = titleMarkStyle(style),
            color = LikhtarColors.TextPrimary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
