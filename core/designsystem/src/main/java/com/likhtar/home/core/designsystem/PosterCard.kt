package com.likhtar.home.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import coil3.compose.AsyncImage

/** Постер 2:3 — пропорція TMDB. Ширші картки 16:9 різали б обкладинки навпіл. */
// Великий вертикальний постер, як у рядах Apple TV: арт на постері і є підписом,
// тому назва під карткою потрібна лише коли постера немає.
/** .card.poster прототипів 236×354 px (118×177 dp) + 20 % (картки замалі). */
val PosterWidth = 143.dp
val PosterHeight = 215.dp

/**
 * Картка каталогу: постер, назва під ним і рік.
 *
 * Приймає готовий URL постера, а не CatalogItem: designsystem не може залежати від
 * core:catalog (той має flavor-и й BuildConfig), а картка потрібна і Кінозалу, і Пошуку.
 *
 * Рейтинг показуємо лише на сфокусованій: у рядку з двадцяти карток двадцять цифр —
 * це шум, а не інформація.
 */
@Composable
fun CatalogPosterCard(
    title: String,
    year: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rating: Double = 0.0,
    onLongClick: (() -> Unit)? = null,
    /** Номер у списку — для хронологій і франшиз, де порядок і є змістом рядка. */
    order: Int? = null,
    /** Профіль це вже додивився. Галочка, а не сірий фільтр: картка мусить лишитись читною. */
    watched: Boolean = false,
    /**
     * Українська доріжка: `true` — мітка UA; `false` і `null` — без мітки.
     *
     * Те саме правило, що на банерній картці ряду: сітка «Відкрити всі» не має відрізнятись
     * від ряду, з якого в неї зайшли.
     */
    uaAudio: Boolean? = null,
    /**
     * Що написано на мітці: `UA` або `RU`.
     *
     * Мова не ховається за спільним значком: у неігровому дозволена й російська доріжка,
     * і глядач має бачити, що вмикає, ДО натискання, а не після.
     */
    audioLabel: String = "UA",
    /**
     * Назва поверх постера. Для кадрів порталів (1+1, Телепортал): на них назви немає, і
     * ряд із таких карток читався як «суцільні ноунейми».
     */
    overlayTitle: Boolean = false,
    /** Розмір постера: у Ліхтарику ряди менші (160 px прототипу). */
    width: androidx.compose.ui.unit.Dp = PosterWidth,
    height: androidx.compose.ui.unit.Dp = PosterHeight,
) {
    val reduceMotion = LocalReduceMotion.current
    var cardFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(width)
            .onFocusChanged { cardFocused = it.hasFocus },
    ) {
        FocusableSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .focusGlow(ProtoCardCorner),
            shape = RoundedCornerShape(ProtoCardCorner),
            background = LikhtarColors.CardBlack,
            focusedScale = LikhtarDimens.CardFocusScale,
        ) { focused ->
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    // 150dp на 1080p — це 300px: w185 тут уже замилюється, беремо w342.
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Без приглушення: картка без української — повноцінна, різницю каже
                    // лише мітка UA.
                    // Трохи щільніше кадрування: частина постерів у TMDB завантажена з білою
                    // рамкою («Люди Ікс '97»), і на ряду вона читалась як контур фокуса.
                    // Шар масштабу — під вузлом фокуса, не над ним: D-pad не зачіпає.
                    modifier = Modifier
                        // Без clipToBounds: обрізає поверхня картки, а він ставив ще один шар.
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = PosterTrim
                            scaleY = PosterTrim
                        },
                )
                if (uaAudio == true) {
                    VoiceBadge(
                        label = audioLabel,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }
                if (overlayTitle && posterUrl != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0.5f to androidx.compose.ui.graphics.Color.Transparent,
                                    1.0f to com.likhtar.home.core.designsystem.LikhtarColors.Background.copy(alpha = 0.9f),
                                ),
                            ),
                    )
                    Text(
                        text = title,
                        style = LikhtarType.Label,
                        color = LikhtarColors.TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                if (posterUrl == null) {
                    Text(
                        text = title.take(1).uppercase(),
                        style = LikhtarType.HeroTitle,
                        color = LikhtarColors.TextMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (order != null) {
                    OutlineBadge(text = "$order", filled = true, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                if (watched) {
                    OutlineBadge(text = "✓", filled = true, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
                FocusRing(focused, ProtoCardCorner)
                // Оцінка — лише під фокусом, як усюди: постійна плашка в кутку накривала назву,
                // надруковану на самому постері («Зникла безвісти»).
                if (focused && rating > 0) {
                    // Та сама плашка, що «УКР» навпроти, — інакше рейтинг виглядав більшим і старим.
                    QualityBadge(
                        label = String.format(java.util.Locale.US, "%.1f", rating),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                }
            }
        }
        if (posterUrl == null) {
            Text(
                text = title,
                style = LikhtarType.Body2,
                color = LikhtarColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = year,
                style = LikhtarType.MonoSmall,
                color = LikhtarColors.TextMuted,
                maxLines = 1,
            )
        } else if (!overlayTitle) {
            // Apple tvOS Lockup Footer: на вертикальних постерах назва й рік плавно
            // проявляються під карткою з відступом від фокус-кільця й легким збільшенням.
            val footerProgress by animateFloatAsState(
                targetValue = if (cardFocused) 1f else 0f,
                animationSpec = tween(if (reduceMotion) 0 else 180),
                label = "posterFooter",
            )
            // Під підпис резервуємо лише 20 dp із ~42: решта лягає в проміжок між рядами (до заголовка
            // наступного ~37 dp) — він порожній, коли картка не у фокусі. Ряди постерів стоять так само
            // щільно, як широкі.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .padding(top = 9.dp)
                    .graphicsLayer {
                        alpha = footerProgress
                        translationY = (-4f * (1f - footerProgress)) * density
                        // Підпис росте разом із карткою.
                        val s = 0.94f + 0.16f * footerProgress
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    },
            ) {
                Text(
                    text = title,
                    style = LikhtarType.Body2.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (year.isNotBlank()) {
                    Text(
                        text = year,
                        style = LikhtarType.MonoSmall.copy(fontSize = 11.5.sp),
                        color = LikhtarColors.WarmMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** На скільки збільшуємо постер, щоб рамка з оригіналу (до ~3% з кожного боку) пішла за край. */
private const val PosterTrim = 1.07f
