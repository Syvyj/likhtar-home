package com.likhtar.home.feature.smarthome

import androidx.compose.foundation.border
import com.likhtar.home.core.designsystem.SmartHomeColors
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.likhtar.home.core.designsystem.focusGlow
import com.likhtar.home.core.designsystem.FocusRing
import com.likhtar.home.core.designsystem.R as DsR
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarScreenScaffold
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.DeviceIcons
import com.likhtar.home.core.designsystem.MicrophoneIcon
import com.likhtar.home.core.designsystem.Notice
import com.likhtar.home.core.designsystem.PageHeader
import com.likhtar.home.core.designsystem.PhoneQrDialog
import com.likhtar.home.core.smarthome.HaLinkStatus
import com.likhtar.home.core.designsystem.focusDirections
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.smarthome.SmartDevice
import com.likhtar.home.core.smarthome.SmartDeviceKind
import com.likhtar.home.core.smarthome.SmartHomeStatus
import com.likhtar.home.core.smarthome.R as CoreR
import java.util.Locale

private const val GRID_COLUMNS = 4

/**
 * «Смарт дім»: кімнати з HA, у кожній — картки пристроїв, OK перемикає.
 *
 * Сенсори (повітря, температура) стоять у рядку назви кімнати, а не окремими картками:
 * дивитись на них — не дія, і одинока картка «Якість повітря» розривала сітку. Карткою
 * лишається лише те, що потребує уваги (фільтр), і принтер.
 */
@Composable
fun SmartHomeScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    viewModel: SmartHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val menuFocusRequester = rememberInitialFocusRequester(state.devices.isEmpty())
    val firstDeviceFocusRequester = rememberInitialFocusRequester(state.devices.isNotEmpty())
    val voiceFocusRequester = remember { FocusRequester() }
    // ↑ з першого ряду карток веде на кнопку голосу, якщо вона є, і лише потім у меню.
    val gridUpFocusRequester = if (state.voiceEnabled) voiceFocusRequester else menuFocusRequester

    Box(modifier = Modifier.fillMaxSize()) {
        LikhtarScreenScaffold(
            section = LikhtarSection.SMART_HOME,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = firstDeviceFocusRequester,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LikhtarDimens.ScreenPadding),
            ) {
                // .head прототипу: «Смарт дім» 64 px / 700 і стан 26 px; «Голосом» праворуч.
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(LikhtarSection.SMART_HOME.titleRes),
                            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.64).sp),
                            color = Color.White,
                        )
                        Text(
                            statusText(state.status, state.devices),
                            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp),
                            color = LikhtarColors.TextDim,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (state.voiceEnabled) {
                        VoiceButton(
                            onPhrase = viewModel::onVoicePhrase,
                            onUnavailable = viewModel::onVoiceUnavailable,
                            modifier = Modifier
                                .focusRequester(voiceFocusRequester)
                                .focusDirections { up = menuFocusRequester },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (val status = state.status) {
                    is SmartHomeStatus.NotConfigured -> SetupHint(
                        configFilePath = state.configFilePath,
                        onTest = viewModel::testConnection,
                        onLink = viewModel::startPhoneLink,
                        focusRequester = firstDeviceFocusRequester,
                        menuFocusRequester = menuFocusRequester,
                    )

                    is SmartHomeStatus.Error -> if (state.devices.isEmpty()) {
                        SetupHint(
                            configFilePath = state.configFilePath,
                            onTest = viewModel::testConnection,
                        onLink = viewModel::startPhoneLink,
                            focusRequester = firstDeviceFocusRequester,
                            menuFocusRequester = menuFocusRequester,
                            error = status.message,
                        )
                    } else {
                        // Короткий збій опитування: останні стани лишаються, у шапці — помилка.
                        DeviceGrid(state.devices, firstDeviceFocusRequester, gridUpFocusRequester, viewModel::toggle)
                    }

                    else -> DeviceGrid(state.devices, firstDeviceFocusRequester, gridUpFocusRequester, viewModel::toggle)
                }
            }
        }
        Notice(
            message = state.message,
            modifier = Modifier.align(Alignment.BottomCenter),
            onShown = viewModel::clearMessage,
        )
        val link by viewModel.link.collectAsStateWithLifecycle()
        val linkStatus by viewModel.linkStatus.collectAsStateWithLifecycle()
        link?.let { url ->
            PhoneQrDialog(
                title = stringResource(R.string.fsmarthome_link_title),
                url = url.ifEmpty { null },
                lines = listOf(
                    stringResource(R.string.fsmarthome_link_line_scan),
                    stringResource(R.string.fsmarthome_link_line_password),
                ),
                status = when (val s = linkStatus) {
                    HaLinkStatus.Waiting -> null
                    HaLinkStatus.Opened -> stringResource(R.string.fsmarthome_link_opened)
                    HaLinkStatus.Done -> stringResource(R.string.fsmarthome_link_done)
                    is HaLinkStatus.Failed -> stringResource(R.string.fsmarthome_link_failed, s.message)
                },
                statusOk = linkStatus == HaLinkStatus.Done,
                onDismiss = viewModel::closePhoneLink,
            )
        }
    }
}

/**
 * Голосова команда: системне розпізнавання (`ACTION_RECOGNIZE_SPEECH`) віддає текст, а далі
 * фраза йде в Assist Home Assistant. Власного мікрофона й дозволу RECORD_AUDIO не просимо —
 * запис робить системний розпізнавач.
 */
@Composable
private fun VoiceButton(
    onPhrase: (String) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recognizer = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val phrase = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        onPhrase(phrase)
    }

    val prompt = stringResource(R.string.fsmarthome_voice_prompt)
    com.likhtar.home.core.designsystem.ProtoButton(
        label = stringResource(R.string.fsmarthome_voice),
        icon = com.likhtar.home.core.designsystem.R.drawable.ic_solar_mic,
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            }
            // Розпізнавача може не бути (наприклад, у збірці без сервісів Google).
            try {
                recognizer.launch(intent)
            } catch (e: ActivityNotFoundException) {
                onUnavailable()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun statusText(status: SmartHomeStatus, devices: List<SmartDevice>): String = when (status) {
    SmartHomeStatus.NotConfigured -> stringResource(CoreR.string.smarthome_not_configured)
    SmartHomeStatus.Connecting -> stringResource(R.string.fsmarthome_connecting)
    is SmartHomeStatus.Connected -> {
        val on = devices.count { it.isControllable && it.isOn }
        listOfNotNull(
            "Home Assistant",
            pluralStringResource(CoreR.plurals.smarthome_devices, status.deviceCount, status.deviceCount),
            if (on > 0) stringResource(R.string.fsmarthome_on_count, on) else null,
        ).joinToString(" · ")
    }
    is SmartHomeStatus.Error -> status.message
}

@Composable
private fun SetupHint(
    configFilePath: String,
    onTest: () -> Unit,
    onLink: () -> Unit,
    focusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    error: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        error?.let {
            Text(text = it, style = LikhtarType.Body1, color = LikhtarColors.Live)
        }
        Note(
            stringResource(R.string.fsmarthome_setup_phone),
        )
        Note(
            stringResource(R.string.fsmarthome_setup_manual, configFilePath),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LikhtarButton(
                label = stringResource(R.string.fsmarthome_connect_phone),
                onClick = onLink,
                primary = true,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusDirections { up = menuFocusRequester },
            )
            LikhtarButton(
                label = stringResource(R.string.fsmarthome_test_connection),
                onClick = onTest,
                modifier = Modifier.focusDirections { up = menuFocusRequester },
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = LikhtarType.Body2,
        color = LikhtarColors.TextTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
            .background(LikhtarColors.SurfaceAlt)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun DeviceGrid(
    devices: List<SmartDevice>,
    firstDeviceFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onToggle: (SmartDevice) -> Unit,
) {
    // Сенсор без застереження — показник кімнати, а не картка.
    val (readings, cards) = devices.partition { it.kind == SmartDeviceKind.SENSOR && !it.warning }
    if (cards.isEmpty()) {
        Note(stringResource(R.string.fsmarthome_no_devices))
        return
    }
    // Кімнатами, як у HA; без кімнати — «Інше» в кінці (мапер уже так відсортував).
    // Прототип: кожна кімната — темне «приміщення» колонкою, у якому горить справжнє
    // світло: люстра — пляма зі стелі, підсвітка — тепло знизу, стрічка — смуга по низу.
    val rooms = cards.groupBy { it.room }.toList()
    val first = cards.first()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        items(rooms, key = { it.first ?: "" }) { (room, inRoom) ->
            Room(
                title = room ?: stringResource(if (rooms.size > 1) R.string.fsmarthome_room_other else R.string.fsmarthome_room_devices),
                readings = readings.filter { it.room == room },
                lamps = inRoom.filter { it.kind == SmartDeviceKind.LIGHT || (it.lamp() == Lamp.STRIP && it.isControllable) },
                width = if (rooms.size <= 3) ((864 - 36) / 3).dp else 276.dp,
            ) {
                inRoom.forEachIndexed { i, device ->
                    DeviceCard(
                        device = device,
                        onClick = { onToggle(device) },
                        modifier = Modifier
                            .then(if (device === first) Modifier.focusRequester(firstDeviceFocusRequester) else Modifier)
                            .then(if (i == 0) Modifier.focusDirections { up = upFocusRequester } else Modifier),
                    )
                }
            }
        }
    }
}

/** Як світить пристрій у кімнаті: люстра — зі стелі, стрічка — смугою, решта — знизу. */
private enum class Lamp { CEILING, FLOOR, STRIP }

private fun SmartDevice.lamp(): Lamp {
    // Спершу значок з HA — його людина ставить сама й однаково будь-якою мовою; назва —
    // запасний шлях для пристроїв без значка.
    val i = icon.orEmpty()
    when {
        "ceiling" in i || "chandelier" in i -> return Lamp.CEILING
        "led-strip" in i || "string-lights" in i -> return Lamp.STRIP
        // Лише однозначні значки: «mdi:desk-lamp» на LED-стрічці робив її торшером.
        "floor-lamp" in i -> return Lamp.FLOOR
    }
    val n = name.lowercase()
    return when {
        "люстр" in n || "ceiling" in n || "стел" in n -> Lamp.CEILING
        "стрічк" in n || "strip" in n || "led" in n -> Lamp.STRIP
        else -> Lamp.FLOOR
    }
}

/**
 * Кімната (.room прототипу): 552×720 px, --deep з тонкою рамкою, скруглення 28 px; назва 30 px
 * і показники моно 20 px; світло ввімкнених ламп — статичні градієнти, що наростають за .9s.
 */
@Composable
private fun Room(
    title: String,
    readings: List<SmartDevice>,
    lamps: List<SmartDevice>,
    width: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .width(width)
            .heightIn(min = 360.dp)
            .clip(shape)
            .background(LikhtarColors.BackgroundDeep)
            .border(0.75.dp, LikhtarColors.Faint, shape),
    ) {
        lamps.forEachIndexed { i, lampDevice ->
            val lit by androidx.compose.animation.core.animateFloatAsState(if (lampDevice.isOn) 1f else 0f, androidx.compose.animation.core.tween(900), label = "lamp")
            val kind = lampDevice.lamp()
            // «Підсвітка принтера» — правіше (--x: 75 % у прототипі), щоб дві плями не злились.
            val x = if (kind == Lamp.FLOOR && lamps.count { it.lamp() == Lamp.FLOOR } > 1 && i > 0) 0.75f else 0.5f
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = lit }
                    .drawBehind {
                        when (kind) {
                            Lamp.CEILING -> {
                                // radial 70 % × 62 % у (50 %, −6 %): .42 → .12 на 55 % → 0.
                                val c = androidx.compose.ui.geometry.Offset(size.width * 0.5f, -size.height * 0.06f)
                                val rx = size.width * 0.7f
                                val ry = size.height * 0.62f
                                scale(rx / ry, 1f, c) {
                                    drawCircle(Brush.radialGradient(0f to SmartHomeColors.Ceiling.copy(alpha = 0.42f), 0.55f to LikhtarColors.Amber.copy(alpha = 0.12f), 1f to Color.Transparent, center = c, radius = ry), ry, c)
                                }
                            }
                            Lamp.FLOOR -> {
                                // radial 80 % × 46 % у (x, 108 %): .36 → .10 на 55 % → 0.
                                val c = androidx.compose.ui.geometry.Offset(size.width * x, size.height * 1.08f)
                                val rx = size.width * 0.8f
                                val ry = size.height * 0.46f
                                scale(rx / ry, 1f, c) {
                                    drawCircle(Brush.radialGradient(0f to SmartHomeColors.FloorLamp.copy(alpha = 0.36f), 0.55f to LikhtarColors.Amber.copy(alpha = 0.1f), 1f to Color.Transparent, center = c, radius = ry), ry, c)
                                }
                            }
                            Lamp.STRIP -> {
                                // 0deg: --glow 5 px, далі .30 → .06 на 180 px → 0 на 260 px.
                                val h = size.height
                                val px = 1f / h
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f - 130.dp.toPx() * px to Color.Transparent,
                                        1f - 90.dp.toPx() * px to LikhtarColors.Amber.copy(alpha = 0.06f),
                                        1f - 2.5.dp.toPx() * px to SmartHomeColors.Strip.copy(alpha = 0.3f),
                                        1f - 2.5.dp.toPx() * px to LikhtarColors.Glow,
                                        1f to LikhtarColors.Glow,
                                    ),
                                )
                            }
                        }
                    },
            )
        }
        Column {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 15.dp, bottom = 15.dp),
            ) {
                Text(
                    title,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp),
                    color = Color.White,
                    maxLines = 1,
                )
                readings.forEach {
                    Text(
                        readingLabel(it),
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp),
                        color = LikhtarColors.TextDim,
                        maxLines = 1,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.padding(bottom = 15.dp)) {
                content()
            }
        }
    }
}

/** «Якість повітря у спальні» → «повітря 10 мкг/м³»: кімната вже в заголовку. */
@Composable
private fun readingLabel(device: SmartDevice): String {
    val name = device.name.lowercase()
    val label = when {
        "повітр" in name || "pm" in name || "air" in name -> stringResource(R.string.fsmarthome_reading_air)
        "волог" in name || "humid" in name -> stringResource(R.string.fsmarthome_reading_humidity)
        "температур" in name -> name.substringBefore(" у ").substringBefore(" в ")
        else -> device.name
    }
    val value = device.state.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
        ?: device.state
    val unit = when (device.unit) {
        "µg/m³", "μg/m³" -> stringResource(R.string.fsmarthome_unit_ugm3)
        else -> device.unit
    }
    return listOfNotNull(label, value + (unit?.let { if (it.startsWith("°") || it == "%") it else " $it" } ?: ""))
        .joinToString(" ")
}

/**
 * Пристрій (.dev прототипу): 130 px заввишки, значок 44 px, назва 27 px і стан 22 px. Увімкнене —
 * теплий відсвіт і бурштин; застереження — червоне; фокус — тепла біла плитка з кільцем.
 */
@Composable
private fun DeviceCard(
    device: SmartDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warn = device.warning
    val lit = device.isOn && !warn
    val shape = RoundedCornerShape(10.dp)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(65.dp)
            .then(
                if (lit) Modifier.drawBehind {
                    drawRoundRect(
                        Brush.radialGradient(listOf(SmartHomeColors.Halo.copy(alpha = 0.25f), Color.Transparent), center = center, radius = size.width * 0.6f),
                        topLeft = androidx.compose.ui.geometry.Offset(-20.dp.toPx(), -14.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width + 40.dp.toPx(), size.height + 28.dp.toPx()),
                    )
                } else Modifier,
            )
            .focusGlow(10.dp),
        shape = shape,
        focusedScale = 1.03f,
        background = if (lit) SmartHomeColors.LitTile.copy(alpha = 0.72f) else LikhtarColors.ShadeWarm.copy(alpha = 0.62f),
        focusedBackground = LikhtarColors.Paper,
        borderColor = if (lit) LikhtarColors.Glow.copy(alpha = 0.22f) else LikhtarColors.Paper.copy(alpha = 0.06f),
        instantFill = true,
    ) { focused ->
        val ink = LikhtarColors.Background
        val stateColor = when {
            focused -> ink
            warn -> SmartHomeColors.Fault
            lit -> LikhtarColors.Glow
            else -> LikhtarColors.TextDim
        }
        val iconTint = when {
            focused && lit -> LikhtarColors.AmberOnPaper
            focused -> ink
            warn -> SmartHomeColors.Fault
            lit -> LikhtarColors.Glow
            else -> LikhtarColors.TextPrimary.copy(alpha = 0.45f)
        }
        Box(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp),
            ) {
                DeviceIcon(device, iconTint)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.5.sp),
                        color = if (focused) ink else LikhtarColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = device.readableState ?: stringResource(
                            when {
                                device.kind == SmartDeviceKind.SCENE -> CoreR.string.smarthome_scene
                                device.isOn -> CoreR.string.smarthome_on
                                else -> CoreR.string.smarthome_off
                            },
                        ),
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp),
                        color = stateColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                device.extra?.let {
                    Text(text = it, style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp), color = stateColor, maxLines = 1)
                }
            }
            device.progress?.let { done ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(Modifier.fillMaxWidth(done.coerceIn(0f, 1f)).fillMaxSize().background(LikhtarColors.Amber))
                }
            }
            FocusRing(focused, 10.dp)
        }
    }
}

@Composable
private fun DeviceIcon(device: SmartDevice, tint: Color) {
    val icon = when {
        device.warning -> DsR.drawable.ic_solar_danger
        device.lamp() == Lamp.STRIP -> DsR.drawable.ic_solar_bulb_bolt
        device.kind == SmartDeviceKind.LIGHT -> DsR.drawable.ic_solar_bulb
        device.kind == SmartDeviceKind.PRINTER -> DsR.drawable.ic_solar_printer
        device.domain == "fan" || "очисн" in device.name.lowercase() || "purif" in device.name.lowercase() -> DsR.drawable.ic_solar_wind
        else -> DsR.drawable.ic_solar_power
    }
    androidx.tv.material3.Icon(androidx.compose.ui.res.painterResource(icon), null, tint = tint, modifier = Modifier.size(22.dp))
}
