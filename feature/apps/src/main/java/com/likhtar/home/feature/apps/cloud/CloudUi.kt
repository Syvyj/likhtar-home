package com.likhtar.home.feature.apps.cloud
import com.likhtar.home.core.designsystem.OutlineBadge
import com.likhtar.home.core.designsystem.CloudColors


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.data.cloud.CloudGame
import com.likhtar.home.core.data.cloud.CloudService
import com.likhtar.home.core.data.cloud.mainService
import com.likhtar.home.core.data.cloud.services
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.titleMarkStyle
import com.likhtar.home.core.designsystem.titleMarkText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.feature.apps.R

/** Кольори марок (макет: --b, --g, --x). */
fun brand(service: CloudService): Color = when (service) {
    CloudService.BOOSTEROID -> CloudColors.Boosteroid
    CloudService.GEFORCE_NOW -> CloudColors.GeForceNow
    CloudService.XBOX -> CloudColors.Xbox
}

/** Ретро (макет: --r): мітка приставки замість крапок сервісів. */
val RetroColor = CloudColors.Retro

/** Колір марки на темній кнопці (макет: .gb .br.b/g/x). */
fun brandOnDark(service: CloudService): Color = when (service) {
    CloudService.BOOSTEROID -> CloudColors.BoosteroidTint
    CloudService.GEFORCE_NOW -> CloudColors.GeForceNowTint
    CloudService.XBOX -> CloudColors.XboxTint
}

@Composable
fun genreLabel(key: String): String = stringResource(
    when (key) {
        "action" -> R.string.cloud_g_action
        "adventure" -> R.string.cloud_g_adventure
        "rpg" -> R.string.cloud_g_rpg
        "strategy" -> R.string.cloud_g_strategy
        "simulation" -> R.string.cloud_g_simulation
        "shooter" -> R.string.cloud_g_shooter
        "racing" -> R.string.cloud_g_racing
        "sports" -> R.string.cloud_g_sports
        "puzzle" -> R.string.cloud_g_puzzle
        "fighting" -> R.string.cloud_g_fighting
        "platformer" -> R.string.cloud_g_platformer
        "mmo" -> R.string.cloud_g_mmo
        "casual" -> R.string.cloud_g_casual
        "family" -> R.string.cloud_g_family
        "indie" -> R.string.cloud_g_indie
        "horror" -> R.string.cloud_g_horror
        "classics" -> R.string.cloud_g_classics
        "retro" -> R.string.cloud_g_retro
        else -> R.string.cloud_b_other
    },
)

@Composable
fun blockTitle(key: String): String = when (key) {
    "popular" -> stringResource(R.string.cloud_b_popular)
    "split" -> stringResource(R.string.cloud_b_split)
    "coop" -> stringResource(R.string.cloud_b_coop)
    "free" -> stringResource(R.string.cloud_b_free)
    "other" -> stringResource(R.string.cloud_b_other)
    else -> genreLabel(key)
}

/** Форми карток макета (px ÷ 2): постер 220×330, горизонтальна 409×230, велика 556×313; збільшення — як у Кінозалі. */
enum class CardShape(val width: Dp, val height: Dp, val scale: Float, val markSize: TextUnit) {
    POSTER(110.dp, 165.dp, 1.07f, 13.sp),
    WIDE(204.dp, 115.dp, 1.05f, 15.sp),
    FEAT(278.dp, 156.dp, 1.05f, 19.sp),

    /** Сітка «Відкрити всі» (макет: #grid .card 150×225). */
    SMALL(75.dp, 112.dp, 1.07f, 10.sp),
}

private val Placeholder = { title: String ->
    val hue = title.sumOf { it.code } % 360
    Brush.linearGradient(listOf(Color.hsl(hue.toFloat(), 0.45f, 0.36f), Color.hsl(((hue + 40) % 360).toFloat(), 0.40f, 0.17f)))
}

/**
 * Картка гри. Постер — обкладинка 2:3; горизонтальна й велика — кадр із назвою внизу. Без
 * картинки — кольорова плашка з назвою (як ретро-картки). Крапки — сервіси, мітка FREE.
 */
@Composable
fun CloudCard(
    game: CloudGame,
    shape: CardShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    services: Set<CloudService> = CloudService.entries.toSet(),
) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(shape.width, shape.height),
        shape = RoundedCornerShape(7.dp),
        background = LikhtarColors.Surface,
        focusedBackground = LikhtarColors.Surface,
        borderColor = Color.Transparent,
        focusedScale = shape.scale,
    ) { focused ->
        Box(Modifier.fillMaxSize().background(Placeholder(game.title)))
        // Як у макеті (tryImg): не завантажилась перша — пробуємо наступну, далі — плашка з назвою.
        val urls = (if (shape == CardShape.POSTER || shape == CardShape.SMALL) listOf(game.cover, game.cover2).map { it?.let(::posterSized) }
        else listOf(game.hero?.let(::wideSized), game.cover, game.cover2)).filterNotNull().distinct()
        var noImage by remember(urls) { mutableStateOf(urls.isEmpty()) }
        // Без картинки — назва фірмовим знаком посередині, як у Кінозалі там, де немає логотипа.
        if (noImage) {
            GameTitle(
                game.title,
                LikhtarType.HeroTitle.copy(fontSize = shape.markSize, lineHeight = shape.markSize * 1.05f),
                maxLines = 4,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
            )
        }
        FallbackImage(urls, game.title, Modifier.fillMaxSize(), onExhausted = { noImage = true })
        if (!noImage && (shape == CardShape.WIDE || shape == CardShape.FEAT)) {
            Text(
                game.title,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = if (shape == CardShape.FEAT) 15.sp else 12.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 9.dp, end = 9.dp, top = 20.dp, bottom = 7.dp),
            )
        }
        Row(Modifier.align(Alignment.TopEnd).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
            game.services(services).forEach { s ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(brand(s)).border(1.dp, Color.Black.copy(alpha = 0.50f), CircleShape))
            }
        }
        val rom = game.rom
        if (rom != null && !rom.missing) {
            // Плашки карток — один стиль на весь лаунчер (OutlineBadge), як «УКР» у Кінозалі.
            OutlineBadge(rom.system, filled = true, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        if (rom?.missing == true) {
            // Файлу вже немає (витягли флешку): картка лишається у списку тьмяною, а не зникає.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)))
            Text(
                stringResource(R.string.cloud_no_file),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 9.sp),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        }
        if (game.free) {
            OutlineBadge("FREE", amber = true, filled = true, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        // Зовнішнє кільце фокуса малює сам FocusableSurface.
    }
}

/** «Відкрити всі N» у кінці ряду — тієї ж форми, що й картки ряду (макет: .card.empty.more). */
@Composable
fun OpenAllCard(count: Int, shape: CardShape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(shape.width, shape.height),
        shape = RoundedCornerShape(7.dp),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.Surface,
        borderColor = LikhtarColors.BorderStrong,
        focusedScale = shape.scale,
    ) { focused ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.cloud_open_all), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = if (focused) LikhtarColors.TextPrimary else LikhtarColors.TextDim)
            Text(count.toString(), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 22.sp), color = LikhtarColors.TextPrimary, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Сервіси (головний — першим), free-to-play, кооп / спліт-скрін, магазини — тими самими контурними
 * плашками, що мета в героях Кінозалу: один стиль на весь лаунчер; колір сервісу — у крапках картки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudChips(game: CloudGame, modifier: Modifier = Modifier, services: Set<CloudService> = CloudService.entries.toSet()) {
    val main = game.mainService(services)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        game.rom?.let { OutlineBadge(if (it.missing) stringResource(R.string.cloud_no_file) else "RetroArch · ${it.system}") }
        game.services(services).sortedBy { it != main }.forEach { OutlineBadge(serviceName(it)) }
        if (game.free) OutlineBadge(stringResource(R.string.cloud_free), amber = true)
        when {
            'l' in game.coop -> OutlineBadge(stringResource(R.string.cloud_split))
            'c' in game.coop -> OutlineBadge(stringResource(R.string.cloud_coop))
        }
        game.stores.forEach { OutlineBadge(it) }
    }
}

/** Картинка з запасними адресами: збій першої — наступна; усі впали — нічого (видно плашку під нею). */
@Composable
fun FallbackImage(urls: List<String>, description: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center, onExhausted: () -> Unit = {}) {
    var index by androidx.compose.runtime.remember(urls) { androidx.compose.runtime.mutableIntStateOf(0) }
    val url = urls.getOrNull(index) ?: return
    val last = index == urls.lastIndex
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        onError = { if (last) onExhausted(); index += 1 },
        modifier = modifier,
    )
}

/*
 * Картинки під розмір картки, а не оригінали: «герой» Steam — 3840×1240, обкладинки Microsoft — у
 * повному розмірі, і приставка тягнула й розпаковувала їх заради картки на 200 dp — ряди каталогу
 * гортались ривками (медіана кадру 42 мс проти 32 у Кінозалі). Кінозал так само
 * бере TMDB під розмір картки.
 */
private const val STEAM_HERO = "/library_hero.jpg"

/** Горизонтальна картка: Steam — капсула 616×353; Microsoft — ?w=; решта як є. */
internal fun wideSized(url: String): String = when {
    url.endsWith(STEAM_HERO) -> url.removeSuffix(STEAM_HERO) + "/capsule_616x353.jpg"
    "store-images.s-microsoft.com" in url && '?' !in url -> "$url?w=640&h=360"
    else -> url
}

/** Постер: Microsoft — ?w=; Steam 600×900 і Boosteroid (вже з width=300) — як є. */
internal fun posterSized(url: String): String =
    if ("store-images.s-microsoft.com" in url && '?' !in url) "$url?w=330&h=495" else url

/**
 * Назва гри фірмовим знаком — той самий, що в Кінозалі там, де немає логотипа (titleMarkStyle):
 * Oswald великими літерами з тінню. Логотипів ігор у нас немає, тож так скрізь.
 */
@Composable
fun GameTitle(title: String, base: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 2, textAlign: TextAlign? = null) {
    Text(
        titleMarkText(title),
        style = titleMarkStyle(base),
        color = LikhtarColors.TextPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}
