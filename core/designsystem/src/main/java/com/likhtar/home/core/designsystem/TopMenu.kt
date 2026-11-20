package com.likhtar.home.core.designsystem

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.runtime.compositionLocalOf
import com.likhtar.home.core.model.LikhtarSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Розділи, які показуються в меню та на Головній.
 *
 * Через CompositionLocal, а не параметр: список залежить від налаштувань («Смарт дім» можна
 * вимкнути), і протягувати його через кожен екран було б шумом.
 */
val LocalVisibleSections = compositionLocalOf { LikhtarSection.entries.toList() }

/** Українська назва під чужомовним знаком окремим рядком замість плашки на ньому (налаштування). */
val LocalTitleUnderLogo = compositionLocalOf { false }

/**
 * Чи лаунчер зараз «спить»: пульт давно не чіпали.
 *
 * У цьому стані Головна ховає плитки й лишає годинник, дату та погоду на шпалерах —
 * телевізор частіше стоїть увімкненим як фон, ніж активно використовується.
 */
val LocalAmbientIdle = compositionLocalOf { false }

/**
 * Верхнє меню, спільне для Кінозалу, ТВ Ефіру, Пошуку, Застосунків і Налаштувань.
 *
 * [menuFocusRequester] треба передати вниз у контент екрана як `focusProperties { up = ... }`,
 * щоб ↑ з першого рядка контенту приводило фокус саме на пункт поточного розділу.
 * [contentFocusRequester] замикає зв'язок у зворотний бік: ↓ з меню повертає фокус на контент.
 */
@Composable
fun LikhtarTopMenu(
    current: LikhtarSection,
    onSelect: (LikhtarSection) -> Unit,
    menuFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
) {
    val isHome = current == LikhtarSection.HOME
    val highContrast = LocalHighContrast.current
    val pillBg = if (highContrast) LikhtarColors.SurfaceDeep else LikhtarColors.BlockFill.copy(alpha = 0.79f)
    val pillBorder = if (highContrast) LikhtarColors.PillBorderHighContrast else LikhtarColors.PillBorder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LikhtarDimens.TopMenuHeight)
            .padding(
                start = LikhtarDimens.ScreenPadding,
                end = LikhtarDimens.ScreenPadding,
                // Безпечна зона телевізора згори (Android TV 27 dp, tvOS 60 px): на 10 dp пігулки
                // на частині телевізорів з'їдав overscan.
                top = 26.dp,
                bottom = 2.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ліворуч (по лінії контенту): скляна пігулка розділів без логотипа й без «Home»
        // (на Головну повертає Back, бренд живе в ряді на Головній).
        Row(
            modifier = Modifier
                // Пігулка бере лише те, що лишив правий блок: тісно — прокручується до фокуса, а не
                // насувається на годинник (Row(SpaceBetween) без ваги давав від'ємний проміжок).
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                .background(pillBg)
                .border(1.dp, pillBorder, RoundedCornerShape(LikhtarDimens.CornerPill))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // «Головна», «Реаліті», «Смарт дім» та «Налаштування» винесено з пігулки:
            // на Головну веде Back, Реаліті живе рядом у Кінозалі, «Смарт дім» — бічна панель, а Налаштування — ⚙.
            val hidden = setOf(LikhtarSection.HOME, LikhtarSection.REALITY, LikhtarSection.SMART_HOME, LikhtarSection.SETTINGS)
            val visibleSections = LocalVisibleSections.current.filterNot { it in hidden }
            visibleSections.forEachIndexed { index, section ->
                val isFocusTarget = if (current in hidden) index == 0 else section == current
                MenuItem(
                    section = section,
                    active = section == current,
                    onSelect = { onSelect(section) },
                    modifier = Modifier
                        .then(
                            if (isFocusTarget) Modifier.focusRequester(menuFocusRequester) else Modifier,
                        )
                        .focusDown(contentFocusRequester),
                )
            }
        }

        // Праворуч: блок у стилі Головної — годинник, погода, динамічні пігулки, шестерня.
        StatusBlock(
            current = current,
            onSelect = onSelect,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = contentFocusRequester,
            showClock = !isHome,
        )
    }
}

/** Правий блок меню: годинник (не на Головній), стан інтернету і вхід у Налаштування. */
@Composable
private fun StatusBlock(
    current: LikhtarSection,
    onSelect: (LikhtarSection) -> Unit,
    menuFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester?,
    showClock: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_TICK_MS)
            time = currentTime()
        }
    }
    val networkState = rememberNetworkState()
    val highContrast = LocalHighContrast.current
    val pillBg = if (highContrast) LikhtarColors.PillGlassHighContrast else LikhtarColors.PillGlass
    val pillBorder = if (highContrast) LikhtarColors.PillBorderHighContrast else LikhtarColors.PillBorder
    val weather = LocalWeatherChip.current
    val tray = LocalNotificationsTray.current?.takeIf { it.count > 0 }
    val bell = LocalNewEpisodesBell.current?.takeIf { it.total > 0 }
    var open by remember { mutableStateOf(false) }

    // Праворуч — лише годинник і ОДНА пігулка статусу (як в Apple TV: угорі вкладки, решта за
    // однією кнопкою). Погода, «Мій дім», профіль, «Що грає», сповіщення, нові серії, телефон і
    // налаштування — у спадному меню з неї. Блок вузький і сталий: пігулка розділів вміщається
    // будь-якою мовою, а смуга не міняє ширини від того, що з'явилось сповіщення.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        if (showClock) {
            Text(text = time, style = MenuClock, color = LikhtarColors.Ink)
            Box(Modifier.width(1.dp).height(20.dp).background(LikhtarColors.GlassDivider))
        }
        if (networkState == NetworkState.DISCONNECTED) NetworkStatusIcon(state = networkState)
        Box {
            FocusableSurface(
                onClick = { open = true },
                modifier = Modifier
                    .height(36.dp)
                    .then(if (current == LikhtarSection.SETTINGS) Modifier.focusRequester(menuFocusRequester) else Modifier)
                    .focusDown(contentFocusRequester),
                shape = RoundedCornerShape(LikhtarDimens.CornerPill),
                background = pillBg,
                focusedBackground = LikhtarColors.BorderStrong,
                borderColor = pillBorder,
                focusedScale = LikhtarDimens.ControlFocusScale,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                ) {
                    weather?.let { w ->
                        LikhtarIcons.Sky(sky = w.sky, night = w.night, tint = LikhtarColors.Glow, size = 19.dp, weight = 1.2f)
                        Text(text = w.temp, style = MenuClock, color = LikhtarColors.Ink, maxLines = 1)
                    }
                    // Є нове (сповіщення чи серії) — крапка, без цифр: цифри в меню.
                    if (tray != null || (bell != null && bell.unseen > 0)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(LikhtarColors.Glow))
                    }
                    GearGlyph(LikhtarColors.Glow)
                }
            }
            if (open) {
                StatusDropdown(
                    weather = weather,
                    tray = tray,
                    bell = bell,
                    onSettings = { onSelect(LikhtarSection.SETTINGS) },
                    onDismiss = { open = false },
                )
            }
        }
    }
}

/**
 * Спадне меню пігулки статусу. Popup з фокусом: Back закриває, фокус не тікає на екран позаду,
 * після закриття система повертає його на пігулку.
 */
@Composable
private fun StatusDropdown(
    weather: WeatherChip?,
    tray: NotificationsTray?,
    bell: NewEpisodesBell?,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val smart = LocalSmartHomeAction.current
    val profile = LocalProfileChip.current
    val playing = LocalNowPlaying.current
    val phone = LocalPhoneDropAction.current
    // Дія — і закрити меню: панелі й екрани відкриваються вже без нього.
    fun act(block: () -> Unit): () -> Unit = { onDismiss(); block() }
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        LaunchedEffect(Unit) { first.requestWithRetry() }
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(LikhtarColors.Panel)
                .border(1.dp, LikhtarColors.PillBorder, RoundedCornerShape(18.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            var firstUsed = false
            @Composable
            fun item(text: String, trailing: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, icon: @Composable () -> Unit) {
                val isFirst = !firstUsed
                firstUsed = true
                StatusRow(text, trailing, onClick, onLongClick, if (isFirst) Modifier.focusRequester(first) else Modifier, icon)
            }
            weather?.let { w ->
                item(stringResource(R.string.ds_weather), w.temp, act(w.onOpen)) {
                    LikhtarIcons.Sky(sky = w.sky, night = w.night, tint = LikhtarColors.Glow, size = 18.dp, weight = 1.2f)
                }
            }
            smart?.let { open ->
                item(stringResource(R.string.ds_my_home), onClick = act(open)) { StatusIcon(R.drawable.ic_solar_home_smile) }
            }
            playing?.let { np ->
                val label = listOf(np.title, np.artist).filter { it.isNotBlank() }.joinToString(" · ")
                // OK — пауза/грати, меню лишається; довге OK — відкрити застосунок.
                item(label, if (np.playing) "❚❚" else "▶", np.onToggle, act(np.onOpen)) { StatusIcon(R.drawable.ic_solar_play) }
            }
            bell?.let { b ->
                item(stringResource(R.string.ds_new_episodes), b.total.toString(), act(b.onOpen)) { StatusIcon(R.drawable.ic_solar_bell) }
            }
            tray?.let { t ->
                item(stringResource(R.string.ds_notifications), t.count.toString(), act(t.onOpen)) { StatusIcon(R.drawable.ic_inbox) }
            }
            profile?.let { p ->
                item(stringResource(R.string.ds_profile), p.name, act(p.onOpen)) {
                    Text(p.name.take(1).uppercase(), style = MenuClock, color = LikhtarColors.Glow)
                }
            }
            phone?.let { open ->
                item(stringResource(R.string.ds_phone_drop), onClick = act(open)) { StatusIcon(R.drawable.ic_solar_phone) }
            }
            item(stringResource(R.string.ds_settings), onClick = act(onSettings)) { GearGlyph(LikhtarColors.Glow) }
        }
    }
}

@Composable
private fun StatusRow(
    text: String,
    trailing: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    icon: @Composable () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(12.dp),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.BorderStrong,
        borderColor = Color.Transparent,
        focusedScale = 1.02f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
            Text(text, style = LikhtarType.MenuItem, color = LikhtarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, style = MenuClock, color = LikhtarColors.WarmMuted, maxLines = 1) }
        }
    }
}

@Composable
private fun StatusIcon(icon: Int) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LikhtarColors.Glow),
        modifier = Modifier.size(18.dp),
    )
}

private enum class NetworkState { WIFI, ETHERNET, CELLULAR, DISCONNECTED }

@Composable
private fun rememberNetworkState(): NetworkState {
    val context = LocalContext.current
    val cm = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var state by remember { mutableStateOf(getNetworkState(cm)) }

    DisposableEffect(cm) {
        if (cm == null) return@DisposableEffect onDispose {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                state = getNetworkState(cm)
            }
            override fun onLost(network: Network) {
                state = getNetworkState(cm)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state = getNetworkState(cm)
            }
        }
        val request = NetworkRequest.Builder().build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Безпечний fallback для середовищ без дозволу на callback
        }
        onDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }
    return state
}

private fun getNetworkState(cm: ConnectivityManager?): NetworkState {
    if (cm == null) return NetworkState.DISCONNECTED
    return runCatching {
        val active = cm.activeNetwork ?: return NetworkState.DISCONNECTED
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkState.DISCONNECTED
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkState.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.CELLULAR
            else -> NetworkState.DISCONNECTED
        }
    }.getOrDefault(NetworkState.DISCONNECTED)
}

@Composable
private fun NetworkStatusIcon(state: NetworkState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val ink = if (state == NetworkState.DISCONNECTED) LikhtarColors.Live else MenuInk

        when (state) {
            NetworkState.ETHERNET -> {
                // Монітор / вузол Ethernet
                val pad = w * 0.15f
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(pad, pad),
                    size = Size(w - pad * 2, h * 0.55f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = 1.3.dp.toPx()),
                )
                // Ніжка підставки
                drawLine(
                    color = ink,
                    start = Offset(w / 2f, pad + h * 0.55f),
                    end = Offset(w / 2f, h - pad),
                    strokeWidth = 1.3.dp.toPx(),
                )
                // База
                drawLine(
                    color = ink,
                    start = Offset(w * 0.3f, h - pad),
                    end = Offset(w * 0.7f, h - pad),
                    strokeWidth = 1.3.dp.toPx(),
                )
            }
            NetworkState.WIFI, NetworkState.CELLULAR -> {
                // Точка внизу
                drawCircle(color = ink, radius = 1.5.dp.toPx(), center = Offset(w / 2f, h - 2.5.dp.toPx()))
                // Дуги Wi-Fi
                val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = ink,
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.25f, h * 0.35f),
                    size = Size(w * 0.5f, h * 0.5f),
                    style = stroke,
                )
                drawArc(
                    color = ink,
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.08f, h * 0.12f),
                    size = Size(w * 0.84f, h * 0.84f),
                    style = stroke,
                )
            }
            NetworkState.DISCONNECTED -> {
                // Wi-Fi з перекресленням
                val stroke = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = ink,
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.1f, h * 0.15f),
                    size = Size(w * 0.8f, h * 0.8f),
                    style = stroke,
                )
                drawCircle(color = ink, radius = 1.4.dp.toPx(), center = Offset(w / 2f, h - 2.5.dp.toPx()))
                // Діагональна лінія перекреслення
                drawLine(
                    color = ink,
                    start = Offset(w * 0.15f, h * 0.15f),
                    end = Offset(w * 0.85f, h * 0.85f),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Вхід у Налаштування — іконка замість пункту меню.
 *
 * Шестерня намальована вручну: заводити material-icons заради однієї іконки — це залежність
 * на кільце й вісім зубців.
 */
/** Погода в правому блоці шапки — як на Головній. */
data class WeatherChip(
    val temp: String,
    val sky: com.likhtar.home.core.model.Sky,
    val night: Boolean,
    val onOpen: () -> Unit,
)

val LocalWeatherChip = androidx.compose.runtime.compositionLocalOf<WeatherChip?> { null }


/** Відкриття бічної панелі «Мій дім» поверх будь-якого розділу. */
val LocalSmartHomeAction = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Шестерня, намальована вручну: заводити material-icons заради однієї іконки — зайва залежність. */
@Composable
private fun GearGlyph(ink: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val s = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val ring = s * 0.25f
        val ringW = s * 0.13f
        val toothW = s * 0.12f
        val toothH = s * 0.17f
        val toothCenter = ring + ringW / 2f
        repeat(8) { index ->
            rotate(degrees = index * 45f, pivot = center) {
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(center.x - toothW / 2f, center.y - toothCenter - toothH / 2f),
                    size = Size(toothW, toothH),
                    cornerRadius = CornerRadius(toothW * 0.35f),
                )
            }
        }
        drawCircle(color = ink, radius = ring, center = center, style = Stroke(width = ringW))
    }
}

/**
 * «Передати на приставку» з телефона (QR) — дію дає застосунок; `null` — кнопки в меню немає.
 * Через CompositionLocal, щоб меню не залежало від сервера прийому.
 */
val LocalPhoneDropAction = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Дзвіночок «Нові серії»: [total] — скільки нових, [unseen] — ще не бачених (цифра на значку). */
data class NewEpisodesBell(val total: Int, val unseen: Int, val onOpen: () -> Unit)

/**
 * «Що грає» — музика чи подкаст у фоні (free-my-tv, lounge): OK — пауза/грати, довге OK —
 * відкрити застосунок. [playing] — що робить OK: грає → значок паузи.
 */
data class NowPlayingChip(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val onToggle: () -> Unit,
    val onOpen: () -> Unit,
)

val LocalNowPlaying = androidx.compose.runtime.compositionLocalOf<NowPlayingChip?> { null }

/** «Хто дивиться» (Nottilo «Chi guarda»): ім'я активного профілю, OK — панель профілів. */
data class ProfileChip(val name: String, val onOpen: () -> Unit)

val LocalProfileChip = androidx.compose.runtime.compositionLocalOf<ProfileChip?> { null }

/** Сповіщення Android (lounge): [count] — скільки зараз у панелі; OK — бічна панель. */
data class NotificationsTray(val count: Int, val onOpen: () -> Unit)

val LocalNotificationsTray = androidx.compose.runtime.compositionLocalOf<NotificationsTray?> { null }

val LocalNewEpisodesBell = androidx.compose.runtime.compositionLocalOf<NewEpisodesBell?> { null }

private const val CLOCK_TICK_MS = 20_000L

/** Колір неактивного пункту меню — його ж носять годинник і шестерня. */
private val MenuInk = LikhtarColors.ChipInk.copy(alpha = 0.88f)

private val MenuClock = LikhtarType.MenuItem.copy(fontSize = 15.5.sp, fontFeatureSettings = "tnum")

private fun currentTime(): String = clockTime()

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.likhtar_lantern),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(text = "LIKHTAR HOME", style = LikhtarType.Brand, color = LikhtarColors.TextPrimary)
    }
}

@Composable
private fun MenuItem(
    section: LikhtarSection,
    active: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Активний розділ — тепла біла пігулка (#FFFAF2) з темним текстом, добре помітна з 3 м;
    // сфокусований інший розділ — тепла підкладка #49433A + біле кільце фокуса зовні.
    FocusableSurface(
        onClick = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = if (active) LikhtarColors.GlassActive else Color.Transparent,
        focusedBackground = if (active) LikhtarColors.GlassActive else LikhtarColors.BorderStrong,
        borderColor = Color.Transparent,
        focusedScale = 1.02f,
    ) { focused ->
        Text(
            text = stringResource(section.titleRes),
            style = LikhtarType.MenuItem,
            color = when {
                active -> LikhtarColors.OnGlassActive
                focused -> LikhtarColors.GlassActive
                else -> LikhtarColors.WarmInkSoft
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
