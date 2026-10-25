package com.likhtar.home.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.PreparingScrim
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.open
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.designsystem.BakedTextScrim
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.hasBakedText
import com.likhtar.home.core.designsystem.ResumeTitle
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.focusDirections
import com.likhtar.home.core.designsystem.focusDown
import com.likhtar.home.core.designsystem.focusUp
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.LocalAmbientIdle
import com.likhtar.home.core.designsystem.rememberAppArtwork
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.designsystem.requestWithRetry
import androidx.compose.runtime.LaunchedEffect
import com.likhtar.home.core.data.RecentChannel
import com.likhtar.home.core.smarthome.SmartDevice
import com.likhtar.home.core.model.LauncherApp
import com.likhtar.home.core.catalog.ContinueItem
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.model.SearchLaunch
import com.likhtar.home.core.model.HourForecast
import com.likhtar.home.core.model.Sky
import com.likhtar.home.core.model.WeatherSnapshot

/**
 * Головна: час, привітання, погода і док розділів — у стилі Apple-скла, тому самому,
 * що на сторінках каталогів.
 *
 * Ліва колонка — провідник, а не окрема «картка в картці»: вона стоїть просто на шпалерах.
 * Контраст дають лише локальні сюжети стрічки, без важкої загальної рамки чи blur.
 *
 * Шпалери та вінетки малює навігація (`WallpaperBackground`, профіль `HOME`) — тут лише вміст.
 */
@Composable
fun HomeScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    onOpenWeather: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTitle: (com.likhtar.home.core.catalog.CatalogItem) -> Unit = {},
    /** Сторінка шоу Реаліті за ext-ключем проєкту. */
    onOpenShow: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    val updateError by viewModel.updateError.collectAsStateWithLifecycle()
    var updateOpen by remember { mutableStateOf(false) }
    update?.takeIf { updateOpen }?.let { release ->
        com.likhtar.home.core.designsystem.UpdateDialog(
            version = release.version,
            notes = release.notes,
            progress = updateProgress,
            error = updateError,
            onUpdate = { viewModel.installUpdate(release) },
            onDismiss = { updateOpen = false },
        )
    }

    // «Продовжити» перечитуємо на кожному поверненні на екран. Раніше цим займався
    // п'ятихвилинний цикл у ViewModel — рівно та затримка, з якою ряд оновлювався після
    // виходу з YouTube чи MEGOGO.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshResume() }

    // Дата живе з годинником, а місто — тільки всередині погодного віджета.
    // Початковий фокус Головної свідомо на погоді: це перше, що користувач бачить,
    // але після будь-якої дії з пульта асинхронне оновлення не має його красти.
    val firstTileFocusRequester = remember { FocusRequester() }
    // Один реквестер, а не два. Раніше на картці погоди висіли `weatherFocusRequester` і
    // окремий «початковий», два `focusRequester` підряд на одному вузлі — і ↑ з доку
    // мовчки гинуло в `runCatching`, лишаючи екран узагалі без фокуса.
    val weatherShown = state.showWeather && state.weather != null
    val weatherFocusRequester = rememberInitialFocusRequester(weatherShown)
    // Без погоди (вимкнена чи ще не приїхала) запит вище гине, і Головна лишалась зовсім без
    // фокуса: стрілкам нема від чого рухатись, «нічого не клацається». Тоді — перша
    // плитка доку.
    LaunchedEffect(weatherShown) { if (!weatherShown) firstTileFocusRequester.requestWithRetry() }
    val shelfFocusRequester = remember { FocusRequester() }
    val contextFocusRequester = remember { FocusRequester() }
    var focusedDockSection by remember { mutableStateOf<LikhtarSection?>(null) }
    var lightAt by remember { mutableStateOf(Offset.Unspecified) }
    var dockHasFocus by remember { mutableStateOf(true) }
    // Чи є в поточному ряду хоч одна картка, яка бере фокус.
    val contextHasCards = when (focusedDockSection) {
        LikhtarSection.HOME -> true
        LikhtarSection.CINEMA -> state.resume.isNotEmpty()
        LikhtarSection.REALITY -> state.realityResume.isNotEmpty()
        LikhtarSection.LIVE_TV -> state.recentChannels.isNotEmpty()
        LikhtarSection.SMART_HOME -> state.smartDevices.isNotEmpty()
        // «Новий пошук» стоїть завжди, навіть коли історія порожня.
        LikhtarSection.SEARCH -> true
        LikhtarSection.APPS -> state.pinnedApps.isNotEmpty()
        else -> false
    }
    // Кожна картка ряду мусить знати вихід угору й донизу: між зонами пів екрана
    // порожнечі, і геометричний пошук фокуса там глухне.
    val contextCross = Modifier.focusUp(weatherFocusRequester).focusDown(firstTileFocusRequester)

    // У ambient-режимі плитки не просто ховаються, а згасають: різке зникнення на великому
    // екрані читається як збій, а не як задум. Годинник і погода лишаються — телевізор
    // частіше стоїть увімкненим як фон, ніж активно використовується.
    val idle = LocalAmbientIdle.current
    val tilesAlpha by animateFloatAsState(
        targetValue = if (idle) 0f else 1f,
        animationSpec = tween(AMBIENT_FADE_MS),
        label = "ambientTiles",
    )

    // OLED Pixel Shift: у режимі очікування годинник щохвилини мікро-зсувається на кілька
    // пікселів, інакше статичні цифри випалюють матрицю.
    val pixelShiftX = if (idle) (((state.time.hashCode() % 5) - 2) * 2).dp else 0.dp
    val pixelShiftY = if (idle) ((((state.time.hashCode() / 5) % 5) - 2) * 2).dp else 0.dp

    // Уночі ambient-годинник пригашений: о третій ранку повна яскравість світить у кімнату.
    val currentHour = remember(state.time) { state.time.substringBefore(':').toIntOrNull() ?: 12 }
    val isNight = currentHour >= 23 || currentHour < 6
    val ambientClockAlpha by animateFloatAsState(
        targetValue = if (idle && isNight) 0.72f else 1f,
        animationSpec = tween(AMBIENT_FADE_MS),
        label = "ambientClockAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()

    ) {
        // Без затінення шпалер: темні смуги згори й знизу та плями під годинником і погодою
        // сприймаються як поломка телевізора — фото як є.

        // Верхня зона: ліворуч ідентичність і годинник, праворуч віджет погоди.
        // Верхнього меню на Головній немає навмисно — навігація живе в доці внизу.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = HomePadding, end = HomePadding, top = ContentTop),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ClockBlock(
                time = state.time,
                date = state.date,
                modifier = Modifier
                    .offset(x = pixelShiftX, y = pixelShiftY)
                    // graphicsLayer, а не alpha(): згасання читається у фазі малювання, і
                    // Головна не перекомпоновується на кожен кадр анімації.
                    .graphicsLayer { alpha = ambientClockAlpha },
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.weather?.takeIf { state.showWeather }?.let { weather ->
                    WeatherCard(
                        weather = weather,
                        onOpen = onOpenWeather,
                        modifier = Modifier
                            .offset(x = -pixelShiftX, y = pixelShiftY)
                            .graphicsLayer { alpha = tilesAlpha }
                            .focusRequester(weatherFocusRequester)
                            // Фокус на погоді — ряд над доком ховається: інакше «Ліхтарик» і
                            // «Налаштування» висіли без фокуса на «Ліхтар Хом». Саме тут,
                            // а не «нижня зона втратила фокус»: під час переходу плитка → ряд
                            // зона на мить без фокуса, ряд зникав, і фокус падав на погоду.
                            .onFocusChanged { if (it.isFocused) focusedDockSection = null }
                            // Явний вихід униз — на ПЕРШУ плитку доку. Між погодою й доком
                            // пів екрана порожнечі, і геометричний пошук фокуса кидав туди,
                            // куди ближче по прямій: з правого верхнього кута це виявлявся
                            // останній застосунок, тобто кінець ряду замість його початку.
                            .focusDown(firstTileFocusRequester),
                    )
                }

            }
        }

        // Обидва ряди «Продовжити» ведуть однаково — тим самим шляхом, що й інші екрани.
        val openResume: (ResumeCard) -> Unit = { card ->
            card.open(
                onShow = onOpenShow,
                onTitle = { title ->
                    viewModel.arrivalSeen(card)
                    onOpenTitle(title)
                },
                onLaunch = { viewModel.openInApp(it) },
                onSection = onSelectSection,
            )
        }

        // Світло під тим, що у фокусі — пунктом смуги чи карткою полиці (прототип):
        // одна тепла пляма, що лише їде вбік; у «тиші» лишається приглушеною.
        // Пляма — еліпс 550×450 (як closest-side у прототипі): коло, розтягнуте вшир.
        val light by animateOffsetAsState(
            if (lightAt.isSpecified) lightAt else Offset.Zero, tween(450, easing = LightEase), label = "light",
        )
        if (lightAt.isSpecified) Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(450.dp)
                .graphicsLayer {
                    translationX = light.x - size.width / 2f
                    translationY = light.y - size.height / 2f
                    scaleX = 550f / 450f
                    alpha = 0.45f + 0.55f * tilesAlpha
                }
                .drawBehind {
                    drawOval(
                        brush = Brush.radialGradient(
                            0f to LikhtarColors.Spill.copy(alpha = 0.3f), 0.55f to LikhtarColors.Amber.copy(alpha = 0.1f), 1f to Color.Transparent,
                            center = center, radius = size.minDimension / 2f,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Screen,
                    )
                },
        )

        // Нижня зона: полиця «Продовжити» над доком розділів.
        CompositionLocalProvider(LocalLight provides { at -> lightAt = at }) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer { alpha = tilesAlpha }
                .padding(start = HomePadding, end = HomePadding, bottom = DockBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = focusedDockSection,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(tween(220, delayMillis = 60)) + androidx.compose.animation.slideInVertically(tween(220, delayMillis = 60)) { it / 12 })
                        .togetherWith(androidx.compose.animation.fadeOut(tween(120)))
                },
                label = "shelf",
            ) { section ->
            when (section) {
                LikhtarSection.HOME -> HomeContextRow(
                    showTorch = state.showTorch,
                    update = update,
                    onUpdate = { updateOpen = true },
                    onOpenSettings = { onSelectSection(LikhtarSection.SETTINGS) },
                    onOpenTorch = { onSelectSection(LikhtarSection.SEARCH) },
                    onAsk = { query ->
                        SearchLaunch.query = query
                        onSelectSection(LikhtarSection.SEARCH)
                    },
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.CINEMA -> CinemaContextRow(
                    cards = state.resume,
                    onOpen = openResume,
                    onOpenHistory = onOpenHistory,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.REALITY -> RealityContextRow(
                    cards = state.realityResume,
                    onOpen = openResume,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.LIVE_TV -> ChannelsContextRow(
                    channels = state.recentChannels,
                    pinned = state.pinnedChannels,
                    onOpen = viewModel::openChannel,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.SMART_HOME -> SmartHomeContextRow(
                    devices = state.smartDevices,
                    onOpen = { onSelectSection(LikhtarSection.SMART_HOME) },
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.APPS -> AppsContextRow(
                    apps = state.pinnedApps,
                    onLaunch = viewModel::launch,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.SEARCH -> SearchContextRow(
                    queries = state.recentSearches,
                    // Запит із пігулки — одразу шукати; «Новий пошук» — у поле.
                    onSearch = { query ->
                        if (query.isBlank()) SearchLaunch.focusField = true else SearchLaunch.query = query
                        onSelectSection(LikhtarSection.SEARCH)
                    },
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                else -> Unit
            }
            }
            Dock(
                tiles = state.tiles,
                onSelectSection = { section ->
                    if (section == LikhtarSection.SEARCH) SearchLaunch.focusField = true
                    onSelectSection(section)
                },
                onFocused = { focusedDockSection = it },
                firstTileFocusRequester = firstTileFocusRequester,
                // ↑ з доку — у власний контекстний ряд. Якщо ряду немає або він
                // порожній, перестрибуємо одразу на погоду: підвисати ні на чому.
                upTarget = if (contextHasCards) contextFocusRequester else weatherFocusRequester,
                selected = focusedDockSection,
                hasFocus = dockHasFocus,
                onFocusInside = { dockHasFocus = it },
            )
        }
        }

        // Прогрів роздачі: сервер шукає джерело, яке реально тягне, і це секунди. Відсотків
        // тут немає навмисно — показувати прочитані байти як прогрес фільму нечесно.
        PreparingScrim(state.preparing)

    }
}

/**
 * Ідентичність і час — цілісний блок лівої частини верхньої зони.
 *
 * Вордмарк, годинник і дата стоять одним стовпцем за спільною вертикальною лінією: саме
 * вона й робить із трьох рядків один блок, тому окремої підкладки під ними немає.
 * Привітання («Добрий день») пішло — воно повторювало те, що вже каже годинник.
 */
@Composable
private fun ClockBlock(
    time: String,
    date: String,
    modifier: Modifier = Modifier,
) {
    // Без бурштинової лінії збоку (прототип): світло на Головній одне — під фокусом.
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = time, style = HomeClockStyle, color = LikhtarColors.Ink)
        Text(text = date, style = LikhtarType.Lead.copy(shadow = TextShade.copy(blurRadius = 10f)), color = LikhtarColors.Ink)
    }
}

/**
 * Віджет погоди: місто, температура, символ стану й три найближчі позначки часу.
 *
 * Тексту стану («Ясно», «Мінлива хмарність») тут навмисно немає — його несе символ.
 * Українські формулювання довгі, вони обрізалися рівно на швидкості вітру, тобто на
 * половині сенсу, а з дивана однаково швидше читається піктограма.
 *
 * Картка натискна: OK відкриває детальну погоду. Фокус не інвертує її в біле — на
 * щільному блоці з цифрами це читалося б як інший екран; замість цього світла рамка.
 */
@Composable
private fun WeatherCard(weather: WeatherSnapshot, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // Погода текстом, без скляної картки (прототип): шпалери видно цілком. Фокусована
    // вона лише як вхід на сторінку погоди — тоді під нею м'яка тепла підкладка.
    FocusableSurface(
        onClick = onOpen,
        modifier = modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerXLarge),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.Background.copy(alpha = 0.7f),
        borderColor = Color.Transparent,
        focusedScale = 1.03f,
    ) { focused ->
        // Без фокуса підкладки немає — текст на самому фото, тож тінь під ним, як під годинником.
        val shade = if (focused) null else TextShade
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                LikhtarIcons.Sky(
                    sky = weather.hourly.firstOrNull()?.sky ?: Sky.CLEAR,
                    night = weather.hourly.firstOrNull()?.isNight() ?: false,
                    tint = LikhtarColors.Glow,
                    size = 34.dp,
                    weight = 1.2f,
                )
                Text(text = plainTemperature(weather.temperature), style = WeatherTempStyle.copy(shadow = shade), color = LikhtarColors.Ink)
            }
            Text(
                text = weather.city + weather.condition.substringBefore(" · ").takeIf { it.isNotBlank() }?.let { ", " + it.lowercase() }.orEmpty(),
                style = WeatherCityStyle.copy(shadow = shade?.copy(blurRadius = 10f)),
                color = LikhtarColors.InkMuted,
            )
            if (weather.hourly.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                    weather.hourly.take(CARD_HOURS).forEach { HourCell(it, shade?.copy(blurRadius = 10f)) }
                }
            }
        }
    }
}

@Composable
private fun HourCell(hour: HourForecast, shade: androidx.compose.ui.graphics.Shadow?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(text = hour.time, style = HourTimeStyle.copy(shadow = shade), color = LikhtarColors.InkMuted)
        Text(text = plainTemperature(hour.temperature), style = HourTempStyle.copy(shadow = shade), color = LikhtarColors.Ink)
    }
}

/**
 * Ніч рахуємо з самої години прогнозу: у моделі погоди ознаки дня й ночі немає, а без неї
 * о 21:00 у картці світило б сонце.
 */
/**
 * Температура без «плюса»: у макеті стоїть «18°», а не «+18°».
 *
 * Мінус лишається — без нього −5° і 5° стали б однією й тією ж цифрою.
 */
internal fun plainTemperature(raw: String): String = raw.removePrefix("+")

internal fun isNightHour(time: String?): Boolean {
    val hour = time?.substringBefore(':')?.trim()?.toIntOrNull() ?: return false
    return hour >= 21 || hour < 6
}

/**
 * Ніч цієї години: за Open-Meteo (`is_day`), а годинник — лише запасний. Правило «до 6 —
 * ніч» малювало сонце о 06:00, коли схід о 06:40, а влітку — місяць о 21:00.
 */
internal fun HourForecast.isNight(): Boolean = night ?: isNightHour(time)

/**
 * Док розділів — навігація Головної.
 *
 * Свій FocusRequester на кожну плитку, щоб замкнути ряд у кільце: ← з першої веде на
 * останню, → з останньої на першу. Дефолтний пошук фокуса цього не вміє — він просто
 * впирається в край.
 */
@Composable
private fun Dock(
    tiles: List<HomeTile>,
    onSelectSection: (LikhtarSection) -> Unit,
    onFocused: (LikhtarSection) -> Unit,
    firstTileFocusRequester: FocusRequester,
    upTarget: FocusRequester?,
    modifier: Modifier = Modifier,
    /** Розділ, чия полиця зараз відкрита: фокус пішов на полицю — пункт лишається підсвіченим. */
    selected: LikhtarSection? = null,
    hasFocus: Boolean = true,
    onFocusInside: (Boolean) -> Unit = {},
) {
    val requesters = remember(tiles.size) { List(tiles.size) { FocusRequester() } }

    // Одна смуга-«пігулка», як верхнє меню розділів (прототип): навігація однакова
    // скрізь у лаунчері. Фокус — тепла біла пігулка.
    // Смуга — за вмістом і по центру екрана: розпірка після LIKHTAR HOME відганяла розділи
    // до правого краю, і між ними зяяла дірка.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .height(DockHeight)
            .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
            .background(LikhtarColors.BackgroundDeep.copy(alpha = 0.74f))
            .border(0.75.dp, LikhtarColors.Paper.copy(alpha = 0.08f), RoundedCornerShape(LikhtarDimens.CornerPill))
            .onFocusChanged { onFocusInside(it.hasFocus) }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tiles.forEachIndexed { index, tile ->
            val previous = requesters[(index - 1 + tiles.size) % tiles.size]
            val next = requesters[(index + 1) % tiles.size]
            NavTile(
                tile = tile,
                onClick = { onSelectSection(tile.section) },
                onFocused = onFocused,
                marked = !hasFocus && tile.section == selected,
                modifier = Modifier
                    .focusRequester(requesters[index])
                    .then(if (index == 0) Modifier.focusRequester(firstTileFocusRequester) else Modifier)
                    .focusDirections {
                        left = previous
                        right = next
                    }
                    .focusUp(upTarget),
            )
            if (tile.section == LikhtarSection.HOME) Spacer(Modifier.width(DockHomeGap))
        }
    }
}

@Composable
private fun NavTile(
    tile: HomeTile,
    onClick: () -> Unit,
    onFocused: (LikhtarSection) -> Unit,
    modifier: Modifier = Modifier,
    marked: Boolean = false,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .height(DockItemHeight)
            .lightSource()
            .onFocusChanged { if (it.isFocused) onFocused(tile.section) },
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = if (marked) LikhtarColors.Paper.copy(alpha = 0.14f) else Color.Transparent,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusRingColor = Color.Transparent,
        focusedScale = 1.04f,
        instantFill = true,
    ) { focused ->
        val ink = if (focused) LikhtarColors.OnWarmWhite else LikhtarColors.Ink
        Row(
            // Поля 12, не 15: з «Ліхтариком» замість «Пошуку» в доці «Застосунки» обрізались.
            modifier = Modifier.padding(start = if (tile.section == LikhtarSection.HOME) 8.dp else 12.dp, end = 12.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (tile.section == LikhtarSection.HOME) {
                LikhtarIcons.Lantern(size = BrandIconSize, onLight = focused)
                Text(text = "LIKHTAR HOME", style = DockLabelStyle, color = ink, maxLines = 1)
            } else {
                DockGlyph(tile = tile, focused = focused)
                Text(
                    text = tile.labelRes?.let { stringResource(it) } ?: tile.label,
                    style = DockLabelStyle,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Значок пункту смуги: лінійний набір [LikhtarIcons] бурштином — світлом, як у прототипі;
 * на фокусі темніє, бо стоїть на теплій білій пігулці.
 */
@Composable
private fun DockGlyph(tile: HomeTile, focused: Boolean) {
    // Заливні іконки Solar бурштином — світлом, як у прототипі; на білій пігулці темніють.
    val icon = when (tile.section) {
        LikhtarSection.CINEMA -> R.drawable.ic_dock_cinema
        LikhtarSection.LIVE_TV -> R.drawable.ic_dock_tv
        LikhtarSection.REALITY -> R.drawable.ic_dock_reality
        LikhtarSection.SMART_HOME -> R.drawable.ic_dock_smart
        LikhtarSection.SEARCH -> R.drawable.ic_dock_search
        else -> R.drawable.ic_dock_apps
    }
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Glow),
        modifier = Modifier.size(DockIconSize),
    )
}

private val DockHeight = 52.dp

/** Проміжок між LIKHTAR HOME і розділами — трохи більший за проміжок між розділами. */
private val DockHomeGap = 4.dp
private val DockItemHeight = 40.dp
private val DockLabelStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

/**
 * Контекстні ряди доку.
 *
 * Спільний каркас на всі: заголовок над рядом і горизонтальний набір карток. Заголовка
 * немає лише в самої Головної — там картки й так називають себе повністю.
 */
/** Хто у фокусі на Головній, повідомляє сюди свій центр у вікні — під ним лягає світло. */
private val LocalLight = staticCompositionLocalOf<(Offset) -> Unit> { {} }

@Composable
private fun Modifier.lightSource(): Modifier {
    val report = LocalLight.current
    var cx by remember { mutableStateOf(Offset.Unspecified) }
    var focused by remember { mutableStateOf(false) }
    return this
        .onGloballyPositioned {
            cx = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f)
            if (focused && cx.isSpecified) report(cx) // ряд прокрутився під фокусом — світло їде за карткою
        }
        .onFocusChanged {
            focused = it.hasFocus
            if (it.hasFocus && cx.isSpecified) report(cx)
        }
}

@Composable
private fun ContextSection(label: String?, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let {
            Text(text = it, style = ShelfTitleStyle, color = Color.White)
        }
        // Прокрутка, а не просто ряд: картки мають сталу ширину, і шостий канал (налаштування
        // «6 каналів») не влазив — Row стискав останню картку до логотипа й «…».
        // Зайве ховається за краєм і під'їжджає разом із фокусом. Прокрутка обрізає вздовж
        // ряду, а картка під фокусом росте — тому запас по краях, зсунутий назад, щоб
        // картки стояли там само, а збільшена не різалась.
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .bleed(ScrollEdge)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ScrollEdge),
            content = content,
        )
    }
}

/**
 * Контекст самої Головної: Ліхтарик і налаштування. Новин (RSS) більше немає — читати їх
 * на ТБ незручно; стрічку, якщо буде, зробимо не з RSS.
 */
@Composable
private fun HomeContextRow(
    showTorch: Boolean,
    update: com.likhtar.home.core.data.AppRelease?,
    onUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTorch: () -> Unit,
    onAsk: (String) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    // «Ліхтарик підкаже» (прототип): готові запити одразу запускають Ліхтарика.
    // «Налаштування» лишаються завжди: вимкнувши Ліхтарик, людина мусить мати чим його повернути.
    var firstTaken = false
    val firstOnce: () -> Modifier = {
        if (firstTaken) crossZone else { firstTaken = true; crossZone.focusRequester(first) }
    }
    ContextSection(label = if (showTorch) stringResource(R.string.home_torch_suggests) else null) {
        // Нова версія — першою: її видно щоразу, коли фокус на LIKHTAR HOME, а це старт Головної.
        update?.let {
            HomePill(text = stringResource(R.string.home_update, it.version), icon = com.likhtar.home.core.designsystem.R.drawable.ic_solar_download, onClick = onUpdate, modifier = firstOnce())
        }
        if (showTorch) {
            // «Новинки українською» — лише українському інтерфейсу: поляк бачив «Nowości po ukraińsku»
            // поруч із власними підказками, як решту українського пласта.
            val ukrainian = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "uk"
            // Перша — сам Ліхтарик; далі три випадкові запити з набору, щоразу інші, коли рядок
            // з'являється.
            HomePill(text = stringResource(R.string.home_open_torch), icon = R.drawable.ic_dock_search, onClick = onOpenTorch, modifier = firstOnce())
            val pool = androidx.compose.ui.res.stringArrayResource(R.array.home_asks).toList() +
                listOfNotNull(stringResource(R.string.home_ask_ukrainian).takeIf { ukrainian })
            val asks = remember { pool.shuffled().take(ASKS_SHOWN) }
            asks.forEach { text ->
                HomePill(text = text, icon = R.drawable.ic_magic, onClick = { onAsk(text) }, modifier = firstOnce())
            }
        }
        HomePill(text = stringResource(R.string.home_settings), icon = R.drawable.ic_settings_solar, onClick = onOpenSettings, modifier = firstOnce())
        // Підтримати автора — поруч із налаштуваннями: тихо, без нагадувань.
        var support by remember { mutableStateOf(false) }
        HomePill(text = stringResource(R.string.home_support), icon = com.likhtar.home.core.designsystem.R.drawable.ic_solar_heart, onClick = { support = true }, modifier = firstOnce())
        if (support) com.likhtar.home.core.designsystem.SupportDialog(onDismiss = { support = false })
    }
}

/** Пігулка полиці: іконка бурштином і підпис; фокус — тепла біла пігулка. */
@Composable
private fun HomePill(
    text: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Другий рядок дрібно: стан пристрою; [lit] — бурштином (увімкнено). */
    small: String? = null,
    lit: Boolean = false,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().height(48.dp),
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = LikhtarColors.Surface.copy(alpha = 0.82f),
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusRingColor = Color.Transparent,
        focusedScale = 1.05f,
        instantFill = true,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 17.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (focused) LikhtarColors.AmberMuted else LikhtarColors.Glow),
                modifier = Modifier.size(17.dp),
            )
            Column {
                Text(text, style = DockLabelStyle.copy(fontSize = 13.sp), color = if (focused) LikhtarColors.OnWarmWhite else LikhtarColors.Ink, maxLines = 1)
                small?.let {
                    Text(
                        it,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                        color = when {
                            focused -> LikhtarColors.AmberMuted
                            lit -> LikhtarColors.Glow
                            else -> LikhtarColors.TextDim
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Фокус картки як у прототипі: біле кільце й теплий ореол навколо. Кільце — у тому ж шарі,
 * що росте разом із карткою, ореол — позаду, у фазі малювання; композиції не коштує.
 */
@Composable
private fun Modifier.focusHalo(shape: androidx.compose.ui.graphics.Shape): Modifier {
    // Теплий ореол під карткою у фокусі (прототип: тінь 0 26 60 −16 бурштином). Кільце — у
    // самій картці ([FocusRing]), щоб росло разом із нею.
    var focused by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "halo")
    return this
        .onFocusChanged { focused = it.hasFocus }
        .drawBehind {
            if (glow <= 0f) return@drawBehind
            drawOval(
                brush = Brush.radialGradient(
                    listOf(LikhtarColors.Amber.copy(alpha = 0.5f * glow), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.85f),
                    radius = size.width * 0.62f,
                ),
                topLeft = Offset(-size.width * 0.12f, size.height * 0.2f),
                size = Size(size.width * 1.24f, size.height * 1.15f),
            )
        }
}

/** Біле кільце 2.5 dp по краю картки у фокусі — як у прототипі. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.FocusRing(focused: Boolean, corner: Dp = CardCorner) {
    if (focused) {
        Box(Modifier.matchParentSize().border(2.5.dp, LikhtarColors.Paper, RoundedCornerShape(corner)))
    }
}

private val CardCorner = 9.dp
private val ShelfTitleStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
private val LightEase = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.7f, 0.1f, 1f)

/** ТБ Ефір: канали, які вмикали з лаунчера. */
@Composable
private fun ChannelsContextRow(
    channels: List<RecentChannel>,
    pinned: Set<String>,
    onOpen: (RecentChannel) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_recent_channels)) {
        if (channels.isEmpty()) {
            ContextHint(stringResource(R.string.home_recent_channels_empty))
        } else {
            channels.forEachIndexed { index, channel ->
                ChannelCard(
                    channel = channel,
                    pinned = channel.url in pinned,
                    onClick = { onOpen(channel) },
                    modifier = crossZone.thenFirst(index, first),
                )
            }
        }
    }
}

/**
 * Канал — типографічна картка, як у ТБ Ефірі (прототип): кольорова підкладка з назви,
 * знак каналу (якщо є) і назва. Колір — стабільний із назви, кожен канал впізнається.
 */
@Composable
private fun ChannelCard(channel: RecentChannel, pinned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (c1, c2) = remember(channel.name) {
        val palette = listOf(
            0xFFFF6AA8 to 0xFF7B5CFF, 0xFFFF6A3D to 0xFFFFB347, 0xFF1FA2FF to 0xFF12D8FA,
            0xFFF857A6 to 0xFFFF5858, 0xFF11998E to 0xFF38EF7D, 0xFF8E2DE2 to 0xFF4A00E0,
        )
        val (a, b) = palette[Math.floorMod(channel.name.hashCode(), palette.size)]
        Color(a) to Color(b)
    }
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().focusHalo(RoundedCornerShape(CardCorner)).size(width = ContinueCardWidth, height = ContinueCardHeight),
        shape = RoundedCornerShape(CardCorner),
        background = Color.Transparent,
        focusedBackground = Color.Transparent,
        borderColor = Color.Transparent,
        focusedScale = 1.07f,
    ) { focused ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2)))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to LikhtarColors.Shade.copy(alpha = 0.55f))))
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (channel.image.isNotBlank()) {
                    AsyncImage(
                        model = channel.image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(30.dp).padding(bottom = 6.dp),
                    )
                }
                Text(
                    channel.name,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 18.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (pinned) {
                Text(
                    stringResource(R.string.home_channel_pinned),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp),
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, bottom = 8.dp),
                )
            }
            FocusRing(focused)
        }
    }
}

/** Пошук: запити, з яких щось відкривали. Перша пігулка завжди веде в чистий пошук. */
@Composable
private fun SearchContextRow(
    queries: List<String>,
    onSearch: (String) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_recent_searches)) {
        HomePill(
            text = stringResource(R.string.home_new_search),
            icon = R.drawable.ic_dock_search,
            onClick = { onSearch("") },
            modifier = crossZone.focusRequester(first),
        )
        queries.forEach { query ->
            HomePill(text = query, icon = R.drawable.ic_history, onClick = { onSearch(query) }, modifier = crossZone)
        }
    }
}

/** Смарт дім: компактний зріз того, що зараз удома. */
@Composable
private fun SmartHomeContextRow(
    devices: List<SmartDevice>,
    onOpen: () -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_smart_now)) {
        if (devices.isEmpty()) {
            ContextHint(stringResource(R.string.home_smart_empty))
        } else {
            devices.forEachIndexed { index, device ->
                val on = device.state.equals("on", ignoreCase = true)
                HomePill(
                    text = device.name,
                    icon = if (on) R.drawable.ic_bulb_on else R.drawable.ic_bulb,
                    small = deviceStateLabel(device),
                    lit = on,
                    onClick = onOpen,
                    modifier = crossZone.thenFirst(index, first),
                )
            }
        }
    }
}

/** Стан пристрою людською мовою: «увімкнено», а не «on». */
@Composable
private fun deviceStateLabel(device: SmartDevice): String {
    device.detail?.let { return it }
    val unit = device.unit.orEmpty()
    return when (device.state.lowercase()) {
        "on" -> stringResource(R.string.home_device_on)
        "off" -> stringResource(R.string.home_device_off)
        "unavailable", "unknown" -> stringResource(R.string.home_device_unavailable)
        else -> "${device.state} $unit".trim()
    }
}

/**
 * Порожній ряд говорить, чого чекати, і не бере фокус.
 *
 * Історії наповнюються самим користуванням: доки нічого не вмикали й не шукали, рядові
 * нема що показати, і вдавати картки було б обіцянкою, якої екран не виконує.
 */
@Composable
private fun ContextHint(text: String) {
    Text(
        text = text,
        style = LikhtarType.Body2,
        color = LikhtarColors.InkFaint,
        modifier = Modifier.height(ContextCardHeight).padding(top = 14.dp),
    )
}

/**
 * Горизонтальні поля Головної — ті самі, що в решти розділів. Своє значення тут було
 * 36dp проти [LikhtarDimens.ScreenPadding] = 32dp, і на кожному переході Головна↔розділ
 * увесь вміст зсувався на 4dp убік.
 *
 * Вертикальні поля лишаються власними: у розділів згори стоїть меню, а тут його немає.
 */
private val HomePadding = 48.dp
private val ContentTop = 36.dp
private val DockBottom = 36.dp


private const val CARD_HOURS = 3



private const val AMBIENT_FADE_MS = 900



/**
 * Тінь лише під самим текстом годинника й дати — щоб біле читалось на світлих шпалерах.
 * Великі темні плями під ним сприймаються як поломка телевізора.
 */
private val TextShade = androidx.compose.ui.graphics.Shadow(
    color = LikhtarColors.Shade.copy(alpha = 0.6f),
    offset = Offset(0f, 2f),
    blurRadius = 18f,
)

/**
 * Годинник Головної — на 10% дрібніший за еталонний `LikhtarType.Clock`.
 *
 * Міняємо на місці вжитку, а не в типографії: той самий стиль носить велику цифру на
 * окремому екрані погоди, і там кегль не чіпаємо.
 */
private val HomeClockStyle = LikhtarType.Clock.copy(
    fontSize = 84.sp,
    lineHeight = 76.sp,
    letterSpacing = (-2.5).sp,
    shadow = TextShade,
)


/** Значок плитки доку. */
private val DockIconSize = 17.dp

/** Розміри контекстного ряду: під чотири картки в рядку на 1080p. */
private val ContextCardWidth = 196.dp
private val ContextCardHeight = 52.dp

/**
 * Кінозал: недодивлене, і більше нічого.
 *
 * Плиток застосунків і «Відкрити всі» тут немає навмисно. Раніше цей ряд був спільною
 * нижньою полицею Головної й тягнув їх із собою; тепер він — контекст рівно одного
 * пункту доку, а застосунки мають у доці власну плитку.
 */
@Composable
private fun CinemaContextRow(
    cards: List<ResumeCard>,
    onOpen: (ResumeCard) -> Unit,
    onOpenHistory: () -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_continue_watching)) {
        if (cards.isEmpty()) {
            ContextHint(stringResource(R.string.home_continue_watching_empty))
        } else {
            // Чотири картки і вхід у повну історію п'ятим слотом. Кількість була
            // налаштуванням, але вибирати тут нічого: п'ять слотів — це вся ширина,
            // яку ряд має під дком, а решта недодивленого живе в «Історії».
            cards.take(CONTINUE_CARDS).forEachIndexed { index, card ->
                ContinueCard(
                    card = card,
                    onClick = { onOpen(card) },
                    modifier = crossZone.thenFirst(index, first),
                )
            }
            ContinueAllCard(onClick = onOpenHistory, modifier = crossZone)
        }
    }
}

/**
 * Ряд над «Реаліті»: випуски, на яких зупинились, — те саме «Продовжити», що в самому
 * розділі. Досі над плиткою не було нічого: гілки для Реаліті в доці просто не існувало.
 */
@Composable
private fun RealityContextRow(
    cards: List<ResumeCard>,
    onOpen: (ResumeCard) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_continue_reality)) {
        if (cards.isEmpty()) {
            ContextHint(stringResource(R.string.home_continue_reality_empty))
        } else {
            cards.take(CONTINUE_CARDS + 1).forEachIndexed { index, card ->
                ContinueCard(card = card, onClick = { onOpen(card) }, modifier = crossZone.thenFirst(index, first))
            }
        }
    }
}

/** Останній слот ряду: решта недодивленого живе на сторінці «Історія». */
@Composable
private fun ContinueAllCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        // Вузька: чотири картки по 190 і ця — рівно ширина ряду (896 dp). Повної ширини п'ять
        // карток не влазили ніколи — Row мовчки стискав цю до ~95 dp, а відколи ряд
        // прокручується, її різав край екрана.
        modifier = modifier.lightSource().size(width = ContinueAllWidth, height = ContinueCardHeight),
        shape = RoundedCornerShape(CardCorner),
        background = LikhtarColors.Surface.copy(alpha = 0.82f),
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusRingColor = Color.Transparent,
        focusedScale = 1.07f,
    ) { focused ->
        Text(
            text = stringResource(R.string.home_all_history),
            style = LikhtarType.Label,
            color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextSecondary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Картка недодивленого: кадр, назва, скільки лишилось і смужка прогресу. */
@Composable
private fun ContinueCard(card: ResumeCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().focusHalo(RoundedCornerShape(CardCorner)).size(width = ContinueCardWidth, height = ContinueCardHeight),
        shape = RoundedCornerShape(CardCorner),
        background = ContinueCardFill,
        focusedBackground = ContinueCardFill,
        borderColor = Color.Transparent,
        focusedScale = 1.07f,
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize()) {
            card.imageUrl?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Градієнт знизу: назва мусить читатися на будь-якому кадрі. Назва текстом чи
            // мініатюра YouTube — щільна смуга, як у Кінозалі: на кадрах українських шоу
            // вже надруковано «13-16 СЕРІЇ…», і наш підпис ліг поверх нього.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (card.logoUrl == null || hasBakedText(card.imageUrl)) {
                            BakedTextScrim
                        } else {
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.9f)))
                        },
                    ),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                ResumeTitle(card.title, card.logoUrl, LikhtarType.Label, logoHeight = 30.dp)
                Text(
                    text = card.subtitle,
                    style = LikhtarType.MonoTiny,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            card.progress?.let { progress ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(LikhtarColors.Cyan),
                    )
                }
            }
        }
        FocusRing(focused)
    }
}

/** Чотири картки плюс «Вся історія» — рівно та ширина, що є під ряд. */
private const val CONTINUE_CARDS = 4

private val ContinueCardWidth = 200.dp
private val ContinueCardHeight = 112.dp
private val ContinueAllWidth = 96.dp
private val ContinueCardFill = LikhtarColors.ContinueFill

/** Знак бренду в доці більший за решту значків: це логотип, а не піктограма розділу. */
private val BrandIconSize = 26.dp

/**
 * Застосунки: п'ять закріплених або останніх запущених.
 *
 * Раніше останній застосунок стояв окремою плиткою доку — між «Пошуком» і
 * «Застосунками», зі стрілкою для циклу. Він плутав два різні рівні: док — це розділи
 * лаунчера, а конкретний застосунок розділом не є. Тепер він там, де й решта контексту.
 */
@Composable
private fun AppsContextRow(
    apps: List<LauncherApp>,
    onLaunch: (LauncherApp) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_your_apps)) {
        if (apps.isEmpty()) {
            ContextHint(stringResource(R.string.home_your_apps_empty))
        } else {
            apps.forEachIndexed { index, app ->
                AppCard(
                    app = app,
                    onClick = { onLaunch(app) },
                    modifier = crossZone.thenFirst(index, first),
                )
            }
        }
    }
}

/** Плитка застосунку, як у прототипі: темна 16:9, TV-банер застосунку цілком або іконка з назвою. */
@Composable
private fun AppCard(app: LauncherApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val artwork = rememberAppArtwork(app.packageName, app.activityName)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().focusHalo(RoundedCornerShape(CardCorner)).size(width = AppCardWidth, height = AppCardWidth * 9 / 16),
        shape = RoundedCornerShape(CardCorner),
        background = LikhtarColors.TileFloor,
        focusedBackground = LikhtarColors.TileFloor,
        borderColor = Color.Transparent,
        focusedScale = 1.07f,
    ) { focused ->
        when {
            artwork?.banner == true -> Image(
                bitmap = artwork.image,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().background(artwork?.backdrop ?: LikhtarColors.TileFloor).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                artwork?.let { Image(bitmap = it.image, contentDescription = null, modifier = Modifier.size(34.dp)) }
                Text(
                    app.label,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        FocusRing(focused)
    }
}

private val AppCardWidth = 150.dp

/**
 * Кеглі погодного віджета зняті пропорціями з еталона, а не підібрані на око.
 *
 * У картці свої стилі, а не спільні токени: `LikhtarType.WeatherTemp` (33sp) лишається
 * бічній панелі, де поруч стоїть щільна таблиця годин. Тут температура — головна цифра
 * екрана поруч із годинником, і вона мусить із ним перегукуватись.
 */
private val WeatherTempStyle = TextStyle(
    fontFamily = LikhtarType.DisplayFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 36.sp,
    lineHeight = 38.sp,
    letterSpacing = (-1.0).sp,
    fontFeatureSettings = "tnum",
)

private val WeatherCityStyle = TextStyle(
    fontFamily = LikhtarType.BodyFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
)

private val HourTimeStyle = TextStyle(
    fontFamily = LikhtarType.MonoFamily,
    fontSize = 10.sp,
)

private val HourTempStyle = TextStyle(
    fontFamily = LikhtarType.DisplayFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    fontFeatureSettings = "tnum",
)


/** Реквестер вішаємо лише на першу картку ряду — саме на неї веде ↑ з доку. */
private fun Modifier.thenFirst(index: Int, first: FocusRequester): Modifier =
    if (index == 0) this.focusRequester(first) else this

/** Запас ряду над доком під збільшення сфокусованої картки (див. ContextSection). */
private val ScrollEdge = 16.dp

/**
 * Вийти за межі батька на [edge] з обох боків, не зсуваючи сусідів. Простий `offset`
 * забирав запас у правого краю — і останню картку («Вся історія») різало там.
 */
private fun Modifier.bleed(edge: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val e = edge.roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * e else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = wide))
    val width = (placeable.width - 2 * e).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.place(-e, 0) }
}

/** Скільки випадкових запитів Ліхтарика показує рядок під «LIKHTAR HOME». */
private const val ASKS_SHOWN = 3
