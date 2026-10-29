package com.likhtar.home.core.designsystem

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
/**
 * [weatherLabel] — рядок вигляду «Нд, 24 серпня · +16°» для Головної. Коли він заданий,
 * шапка перемикається в режим Головної: ховає «Головна» та «Застосунки» з навігації,
 * прибирає годинник і показує дату з погодою поруч із логотипом.
 */
@Composable
fun LikhtarTopMenu(
    current: LikhtarSection,
    onSelect: (LikhtarSection) -> Unit,
    menuFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
    weatherLabel: String? = null,
) {
    val isHome = current == LikhtarSection.HOME
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LikhtarDimens.TopMenuHeight)
            .padding(horizontal = LikhtarDimens.ScreenPadding),
    ) {
        // Ліворуч: логотип та погода (на не-Головних екранах; на Головній це шапка лівої стрічки)
        if (!isHome) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LikhtarDimens.TopMenuGap),
            ) {
                BrandMark()
            }
        }

        // Строго по центру — так само, як і в решті розділів. Прив'язка до лівого поля
        // над віджетом Головної читалась як асиметрія, а не навмисний акцент.
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                .background(LikhtarColors.PanelFloor.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(LikhtarDimens.CornerPill))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // На Головній ховаємо «Головна» та «Застосунки» — вони з'являються лише в розділах.
            // Налаштування зі списку прибрані: вони живуть під іконкою-шестернею праворуч.
            val hiddenOnHome = setOf(LikhtarSection.HOME, LikhtarSection.APPS, LikhtarSection.SETTINGS)
            val hiddenAlways = setOf(LikhtarSection.SETTINGS)
            val hidden = if (isHome) hiddenOnHome else hiddenAlways
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

        // Праворуч: статус мережі та налаштування
        StatusBlock(
            current = current,
            onSelect = onSelect,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = contentFocusRequester,
            showClock = !isHome,
            modifier = Modifier.align(Alignment.CenterEnd),
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

    // Та сама темна пігулка, що й у меню: без підкладки час і мова тонули у світлому кадрі.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
            .background(LikhtarColors.PanelFloor.copy(alpha = 0.71f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(LikhtarDimens.CornerPill))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        // На Головній годинник не показуємо: дата й час живуть у стрічці та шапці зліва.
        if (showClock) {
            // Той самий кегль і колір, що в пунктів меню, — годинник має читатися як частина
            // меню, а не як службовий підпис. Моноширинний JetBrains Mono тут замінено на Inter,
            // тому вмикаємо табличні цифри: без них ширина рядка стрибає на кожній хвилині.
            Text(
                text = time,
                style = MenuClock,
                color = MenuInk,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(LikhtarColors.Border),
            )
        }
        NetworkStatusIcon(state = networkState)
        LocalPhoneDropAction.current?.let { open ->
            PhoneDropButton(onClick = open, modifier = Modifier.focusDown(contentFocusRequester))
        }
        SettingsButton(
            active = current == LikhtarSection.SETTINGS,
            onSelect = { onSelect(LikhtarSection.SETTINGS) },
            modifier = Modifier
                .then(
                    if (current == LikhtarSection.SETTINGS) {
                        Modifier.focusRequester(menuFocusRequester)
                    } else {
                        Modifier
                    },
                )
                .focusDown(contentFocusRequester),
        )
    }
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
@Composable
private fun SettingsButton(active: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = if (active) LikhtarColors.Glass else Color.Transparent,
        focusedBackground = LikhtarColors.GlassActive,
        borderColor = Color.Transparent,
        focusedScale = 1.04f,
    ) { focused ->
        val ink = if (focused) LikhtarColors.OnGlassActive else MenuInk
        Canvas(modifier = Modifier.size(16.dp)) {
            val s = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            // Зубці — короткі прямокутники, що сидять НА кільці, а не промені, які від нього
            // відходять: розділені промені читаються як сонце, і саме так виглядала перша
            // версія. Отвір усередині дає товсте кільце, тому шар із BlendMode.Clear не
            // потрібен — на приставці це був би зайвий offscreen-буфер.
            val ring = s * 0.25f
            val ringW = s * 0.13f
            val toothW = s * 0.12f
            val toothH = s * 0.17f
            // Зубець сидить центром на зовнішньому краї кільця, а не на його осьовій лінії:
            // інакше він коротший за товщину кільця й тоне в ньому — уся іконка стає
            // суцільним бубликом. Саме це й вийшло з першого разу.
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
}

/**
 * «Передати на приставку» з телефона (QR) — дію дає застосунок; `null` — кнопки в меню немає.
 * Через CompositionLocal, щоб меню не залежало від сервера прийому.
 */
val LocalPhoneDropAction = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
private fun PhoneDropButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.GlassActive,
        borderColor = Color.Transparent,
        focusedScale = 1.04f,
    ) { focused ->
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_solar_phone),
            contentDescription = androidx.compose.ui.res.stringResource(R.string.ds_phone_drop),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (focused) LikhtarColors.OnGlassActive else MenuInk),
            modifier = Modifier.size(16.dp),
        )
    }
}

private const val CLOCK_TICK_MS = 20_000L

/** Колір неактивного пункту меню — його ж носять годинник і шестерня. */
private val MenuInk = LikhtarColors.ChipInk.copy(alpha = 0.84f)

private val MenuClock = LikhtarType.MenuItem.copy(fontFeatureSettings = "tnum")

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
    // Біле — це курсор, а не «поточний розділ». Раніше активний пункт теж був білим, і
    // на екрані з'являлися дві білі пігулки одночасно; плюс кільце фокуса поверх білого
    // читалося сірим обідком. Тепер активний — скляна пігулка, сфокусований — матово-біла,
    // рівно як картки в каталогах і плитки доку.
    FocusableSurface(
        onClick = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = if (active) LikhtarColors.Glass else Color.Transparent,
        focusedBackground = LikhtarColors.GlassActive,
        borderColor = Color.Transparent,
        focusedScale = 1.04f,
    ) { focused ->
        Text(
            text = stringResource(section.titleRes),
            style = LikhtarType.MenuItem,
            color = if (focused) LikhtarColors.OnGlassActive else MenuInk,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}
