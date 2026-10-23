package com.likhtar.home.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.model.Channel
import com.likhtar.home.core.model.ContentItem
import com.likhtar.home.core.model.LauncherApp

/** Заголовок секції: uppercase, розріджений — як `.section-title` у прототипі. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // Прототипи: заголовок ряду — звичайним регістром, 30 px / 600, білий (.row-title).
    Text(
        text = text,
        style = RowTitle,
        color = androidx.compose.ui.graphics.Color.White,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 7.dp),
    )
}

/** .row-title прототипів: 30 px, 600, −.01em. */
val RowTitle = androidx.compose.ui.text.TextStyle(
    fontFamily = LikhtarType.Body,
    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    fontSize = 15.sp,
    letterSpacing = (-0.15).sp,
)

/**
 * Плитка застосунку в сітці. Числа — з макета «Застосунки — редизайн» (канва 1920 → dp = px/2):
 * плитка 232×132, скруглення 26, поле 14, іконка 104 зі скругленням 22, підпис 20 під нею.
 *
 * Фокус: підйом на 10px і масштаб 1.04, підкладка стає матово-білою. Кільця немає —
 * той самий закон, що в доці Головної.
 *
 * TV-банер (16:9) вписується в плитку цілком, квадратна іконка лягає в центр на підкладку
 * власного домінантного кольору. Розтягувати не можна ні те, ні те.
 */
@Composable
fun AppTile(
    app: LauncherApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
    dimmed: Boolean = false,
    tags: List<String> = emptyList(),
) {
    val artwork = rememberAppArtwork(app.packageName, app.activityName)
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(LikhtarDimens.AppTileWidth)
            .onFocusChanged { focused = it.hasFocus },
        horizontalAlignment = Alignment.Start,
    ) {
        FocusableSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(LikhtarDimens.AppTileIconHeight),
            shape = RoundedCornerShape(TileCorner),
            background = TileFill,
            focusedBackground = TileFillActive,
            borderColor = TileLine,
            focusedScale = 1.04f,
            focusedLift = 5.dp,
        ) { _ ->
            Box(
                modifier = Modifier.fillMaxSize().alpha(if (dimmed) 0.4f else 1f).padding(7.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    artwork == null -> AppLetter(app)
                    // Банер вписуємо в ту саму висоту, що й квадратну іконку: інакше 16:9
                    // на 16:9-плитці розтягується від краю до краю, скло зникає, і сітка
                    // перетворюється на стіну наліпок. У макеті графіка займає близько
                    // половини ширини плитки.
                    artwork.banner -> Image(
                        bitmap = artwork.image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(AppIconSide).clip(RoundedCornerShape(LikhtarDimens.CornerSmall)),
                    )
                    else -> AppIcon(artwork)
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = app.label,
                // Підпис лежить під плиткою просто на шпалерах — тінь тримає його
                // читабельним і на світлому кадрі. Див. [onWallpaper].
                style = LikhtarType.AppLabel.onWallpaper(),
                color = if (focused) LikhtarColors.TextPrimary else TileLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // Замок і мітки — маленькі квадратики біля підпису, а не поверх іконки:
            // на банері вони перекривали логотип застосунку.
            if (locked) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(LikhtarDimens.CornerTiny))
                        .background(LockFill)
                        .border(1.dp, LockLine, RoundedCornerShape(LikhtarDimens.CornerTiny)),
                )
            }
            tags.forEach { tag ->
                Text(
                    text = tag,
                    style = LikhtarType.MonoTiny,
                    color = LikhtarColors.TextTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(LikhtarDimens.CornerTiny))
                        .background(LikhtarColors.Glass)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** Квадратна іконка: центр плитки, підкладка кольору самої іконки. */
@Composable
private fun AppIcon(artwork: AppArtwork) {
    val density = LocalDensity.current
    // Не збільшуємо понад 1.5× власного розміру: дрібна іконка, розтягнута на всю плитку,
    // перетворюється на кашу з пікселів.
    val natural = with(density) { maxOf(artwork.widthPx, artwork.heightPx).toDp() }
    val side = minOf(AppIconSide, natural * 1.5f)

    Box(
        modifier = Modifier
            .size(side)
            .clip(RoundedCornerShape(AppIconCorner))
            .background(artwork.backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = artwork.image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Немає ні банера, ні іконки — лишається перша літера назви. */
@Composable
private fun AppLetter(app: LauncherApp) {
    Box(
        modifier = Modifier
            .size(AppIconSide)
            .clip(RoundedCornerShape(AppIconCorner))
            .background(Color(app.accent).copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = app.glyph.ifBlank { app.label.take(1).uppercase() },
            style = LikhtarType.AppLetter,
            color = LikhtarColors.TextPrimary,
        )
    }
}

/** Квадрат іконки 104px макета → 52dp, скруглення 22px → 11dp. */
private val AppIconSide = 52.dp
private val AppIconCorner = LikhtarDimens.CornerLarge

/** Плитка: скруглення 26px → 13dp; заливки й підпис — з макета. */
private val TileCorner = LikhtarDimens.CornerLarge
private val TileFill = Color.White.copy(alpha = 0.07f)
private val TileFillActive = Color.White.copy(alpha = 0.92f)
private val TileLine = Color.White.copy(alpha = 0.11f)
private val TileLabel = LikhtarColors.ChipInk.copy(alpha = 0.62f)
private val LockFill = LikhtarColors.Warn.copy(alpha = 0.18f)
private val LockLine = LikhtarColors.Warn.copy(alpha = 0.4f)

