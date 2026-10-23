package com.likhtar.home.feature.cinema

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.R as DsR
import com.likhtar.home.core.designsystem.clockTime
import com.likhtar.home.core.designsystem.focusGlow
import com.likhtar.home.core.tv.ChannelLink
import com.likhtar.home.core.tv.ChannelSchedule

/*
 * ТБ Ефір у двох сценах (site/livetv/variants/flow.html; px прототипу ÷ 2 = dp):
 * сцена 1 — банер і ряди з карткою «канал попереду», у кінці кожного ряду «Усі канали»;
 * сцена 2 — список каналів ліворуч і прев'ю праворуч, групи — ще лівіше, розкриваються на ←.
 * Знак каналу — на світлій плашці: логотипи мовників різнокольорові й часто темні, і на графіті
 * половина губилась, а кольорові плашки з хеша назви вибивались із палітри.
 */

/** Знак каналу: логотип на світлій плашці, без логотипа — назва на ній же. */
@Composable
internal fun ChannelMark(link: ChannelLink, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    // Прозорий знак — на графіті (під нього й намальований), кольоровий логотип — на світлій плашці.
    val mark = link.mark.isNotBlank()
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(5.dp)).background(if (mark) LikhtarColors.Background else LikhtarColors.Paper),
        contentAlignment = Alignment.Center,
    ) {
        if (mark) {
            AsyncImage(model = link.mark, contentDescription = link.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
        } else if (link.logo.isNotBlank()) {
            AsyncImage(model = link.logo, contentDescription = link.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                channelLabel(link.name),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = (height.value * 0.3f).sp, lineHeight = (height.value * 0.34f).sp),
                color = LikhtarColors.Background,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * Картка сцени 1 (.a .card): 456 px, кадр 230 px, знак 104×64 px заходить на кадр знизу;
 * праворуч від знака — назва каналу 20 px, далі передача 26 px і рядок стану 20 px.
 */
@Composable
internal fun LiveChannelCard(
    card: LiveCard,
    now: Long,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val image = cardImage(card)
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(228.dp).onFocusChanged { if (it.hasFocus) onFocused() }.focusGlow(8.dp),
        shape = RoundedCornerShape(8.dp),
        background = LikhtarColors.Surface,
        focusedBackground = LikhtarColors.Surface,
        borderColor = Color.Transparent,
        focusLighten = 0f,
        focusedScale = 1.07f,
    ) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().height(115.dp).background(LikhtarColors.BackgroundDeep)) {
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    ChannelMark(card.link, 80.dp, 48.dp, Modifier.align(Alignment.Center))
                }
                ChannelMark(card.link, 52.dp, 32.dp, Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = 14.dp))
            }
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 9.dp)) {
                Text(
                    channelLabel(card.link.name),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    color = LikhtarColors.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 56.dp).height(14.dp),
                )
                Text(
                    programmeTitle(res, card),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(top = 5.dp).height(14.dp),
                ) {
                    val small = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    if (card.bare) {
                        Text(if (card.link.packageName.isBlank()) res.getString(R.string.cinema_live_bare_hint) else res.getString(R.string.cinema_live_youtube_hint), style = small, color = LikhtarColors.TextDim, maxLines = 1)
                    } else if (card.startMillis > now) {
                        Text(clockTime(card.startMillis), style = small, color = LikhtarColors.Glow)
                        Text(res.getString(R.string.cinema_live_starts_in, ((card.startMillis - now) / 60_000L).coerceAtLeast(1).toInt()), style = small, color = LikhtarColors.TextDim, maxLines = 1)
                    } else {
                        Text(clockTime(card.startMillis), style = small, color = LikhtarColors.TextDim)
                        ProgressLine(card, now, Modifier.weight(1f), focused)
                        Text(shortLeft(res, card.endMillis, now), style = small, color = LikhtarColors.Glow, maxLines = 1)
                    }
                }
            }
        }
        if (focused) Box(Modifier.matchParentSize().border(2.5.dp, LikhtarColors.Paper, RoundedCornerShape(8.dp)))
    }
}

@Composable
private fun ProgressLine(card: LiveCard, now: Long, modifier: Modifier, focused: Boolean = true) {
    if (card.bare) return
    val span = (card.endMillis - card.startMillis).coerceAtLeast(1)
    val done = ((now - card.startMillis).toFloat() / span).coerceIn(0f, 1f)
    Box(modifier.height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(done).fillMaxHeight().background(if (focused) LikhtarColors.Amber else LikhtarColors.Amber.copy(alpha = 0.8f)))
    }
}

/** Група «усі канали» сцени 2 — не назва з сервера, тож своїм ключем. */
internal const val GuideAll = ""

/**
 * Сцена 2: групи (112 px згорнуті / 400 px розкриті), список 640 px, прев'ю — решта ширини.
 *
 * Пульт: ↑↓ — канали, прев'ю за фокусом; ← — розкрити групи, ↑↓ у них одразу фільтрують
 * список; → чи OK — назад у список; OK на каналі — дивитись; Back — згорнути групи, далі —
 * назад у сцену 1.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LiveGuide(
    channels: List<LiveCard>,
    groups: List<String>,
    schedules: Map<String, ChannelSchedule>,
    now: Long,
    group: String,
    onGroup: (String) -> Unit,
    onOpen: (LiveCard) -> Unit,
    onLong: (LiveCard) -> Unit,
    onClose: () -> Unit,
    /** Росте, коли закрилось меню каналу: фокус — назад у список. */
    refocus: Int = 0,
) {
    val res = LocalContext.current.resources
    var groupsOpen by remember { mutableStateOf(false) }
    val list = remember(channels, group) { if (group == GuideAll) channels else channels.filter { liveGroup(it) == group } }
    // Читають лише рядок-якір і прев'ю — не тіло сцени: інакше кожен рух фокуса перебудовував
    // усю сцену й усі видимі рядки(половина кадрів довша за 16,7 мс).
    val focusedIndex = remember(group) { mutableIntStateOf(0) }
    val listFocus = remember { FocusRequester() }
    val groupFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(group) { listState.scrollToItem(0) }

    var listHasFocus by remember { mutableStateOf(false) }
    var groupsHaveFocus by remember { mutableStateOf(false) }
    BackHandler { if (groupsOpen) groupsOpen = false else onClose() }
    // Фокус — туди, де людина: у групи, коли вони відкриті, інакше в список. Перевіряємо, чи
    // фокус справді там: requestFocus мовчить, коли вузол ще не фокусабельний (групи стають
    // фокусабельними лише після перекомпонування), і перша «вдала» спроба нічого не значила.
    LaunchedEffect(groupsOpen, list, refocus) {
        repeat(30) { attempt ->
            withFrameNanos { }
            if ((refocus == 0 || attempt >= 3) && (if (groupsOpen) groupsHaveFocus else listHasFocus)) return@LaunchedEffect
            runCatching { (if (groupsOpen) groupFocus else listFocus).requestFocus() }
        }
    }

    val move = tween<Dp>(250)
    val railWidth by animateDpAsState(if (groupsOpen) 200.dp else 56.dp, move, label = "groups")
    val shift by animateDpAsState(if (groupsOpen) 144.dp else 0.dp, move, label = "shift")
    val listAlpha by animateFloatAsState(if (groupsOpen) 0.55f else 1f, tween(250), label = "dim")

    Box(Modifier.fillMaxSize().background(LikhtarColors.Background)) {
        // Список і заголовок.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.offset(x = 89.dp + shift, y = 44.dp)) {
            Text(
                if (group == GuideAll) res.getString(R.string.cinema_live_all_channels) else liveGroupTitle(res, group),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = Color.White,
                modifier = Modifier.alignByBaseline(),
            )
            Text("${list.size}", style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.sp), color = LikhtarColors.TextDim, modifier = Modifier.alignByBaseline())
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 200.dp),
            modifier = Modifier
                .offset(x = 80.dp + shift, y = 71.dp)
                .width(320.dp)
                .fillMaxHeight()
                .graphicsLayer { alpha = listAlpha }
                .onFocusChanged { listHasFocus = it.hasFocus }
                .focusGroup()
                // Стрілками зі списку платформа фокус не виносить: ← відкриває групи сам рядок,
                // решта — нікуди. Лише стрілки: Cancel на будь-який вихід глушив і наш запит фокуса.
                .focusProperties { exit = { d -> if (d.isArrow()) FocusRequester.Cancel else FocusRequester.Default } },
        ) {
            itemsIndexed(list, key = { _, c -> c.link.url }) { i, c ->
                val anchor by remember(i, list) { derivedStateOf { i == focusedIndex.intValue.coerceAtMost(list.lastIndex) } }
                GuideRow(
                    card = c,
                    now = now,
                    onFocused = { focusedIndex.intValue = i },
                    onClick = { onOpen(c) },
                    onLongClick = { onLong(c) },
                    onLeft = { groupsOpen = true },
                    // ↑ з першого каналу — назад у сцену 1.
                    onUp = if (i == 0) onClose else null,
                    modifier = if (anchor) Modifier.focusRequester(listFocus) else Modifier,
                )
            }
        }

        // Прев'ю каналу у фокусі.
        GuidePreview(list, focusedIndex, schedules, now, shift)

        // Групи — поверх списку, як шухляда.
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(LikhtarColors.BackgroundDeep)
                .padding(top = 75.dp)
                .onFocusChanged { groupsHaveFocus = it.hasFocus }
                .onPreviewKeyEvent { e ->
                    // → із груп — назад у список (OK на групі робить те саме).
                    if (groupsOpen && e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { groupsOpen = false; true } else false
                }
                // Властивості — ДО focusGroup: після нього вони не діяли, і ↑ з «Усі» виводило фокус
                // у сховане під сценою 2 верхнє меню, а OK відкривало Головну.
                .focusProperties { exit = { d -> if (d.isArrow()) FocusRequester.Cancel else FocusRequester.Default } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            (listOf(GuideAll) + groups).forEach { g ->
                val count = if (g == GuideAll) channels.size else channels.count { liveGroup(it) == g }
                GroupItem(
                    title = if (g == GuideAll) res.getString(R.string.cinema_live_group_all) else liveGroupTitle(res, g),
                    icon = groupIcon(g),
                    count = count,
                    open = groupsOpen,
                    selected = g == group,
                    focusable = groupsOpen,
                    onFocused = { if (g != group) onGroup(g) },
                    onClick = { groupsOpen = false },
                    modifier = if (g == group) Modifier.focusRequester(groupFocus) else Modifier,
                )
            }
            if (!groupsOpen) {
                Spacer(Modifier.weight(1f))
                Text(
                    "← " + res.getString(R.string.cinema_live_groups),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 9.sp),
                    color = LikhtarColors.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                )
            }
        }
    }
}

/** Прев'ю каналу у фокусі: кадр 280 dp, «зараз на X · ще N хв», назва, далі — дві наступні. */
@Composable
private fun GuidePreview(list: List<LiveCard>, focused: androidx.compose.runtime.IntState, schedules: Map<String, ChannelSchedule>, now: Long, shift: Dp) {
    val res = LocalContext.current.resources
    val c = list.getOrNull(focused.intValue) ?: list.firstOrNull() ?: return
    Column(Modifier.offset(x = 425.dp + shift, y = 75.dp).width(463.dp - shift)) {
        Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(11.dp)).background(LikhtarColors.BackgroundDeep)) {
            val image = cardImage(c)
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                ChannelMark(c.link, 120.dp, 72.dp, Modifier.align(Alignment.Center))
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Black.copy(alpha = 0f), 1f to com.likhtar.home.core.designsystem.ChannelCardScrim)))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                Text(
                    res.getString(R.string.cinema_live_now_on, channelLabel(c.link.name)) + if (c.bare) "" else " · " + shortLeft(res, c.endMillis, now),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                    color = LikhtarColors.Glow,
                    maxLines = 1,
                )
                Text(
                    programmeTitle(res, c),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 24.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        val next = schedules[c.link.url]?.upcoming.orEmpty().filter { it.startMillis >= c.endMillis - 60_000 }.take(2)
        if (next.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp).fillMaxWidth().graphicsLayer { alpha = 1f - (shift.value / 144f) }) {
                next.forEach { p ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(LikhtarColors.Paper.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 9.dp)) {
                        Text(res.getString(R.string.cinema_live_at, clockTime(p.startMillis)), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 9.sp), color = LikhtarColors.TextDim)
                        Text(cleanProgramme(c.link, p.title), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp), color = LikhtarColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** Рядок списку (.it): 92 px, знак 96×56 px, назва каналу 19 px, передача 24 px, смужка 4 px. */
@Composable
private fun GuideRow(card: LiveCard, now: Long, onFocused: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, onLeft: () -> Unit, onUp: (() -> Unit)?, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { e ->
                when {
                    e.type != KeyEventType.KeyDown -> false
                    e.key == Key.DirectionLeft -> { onLeft(); true }
                    e.key == Key.DirectionUp && onUp != null -> { onUp(); true }
                    else -> false
                }
            },
        shape = RoundedCornerShape(8.dp),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusedScale = 1.02f,
        instantFill = true,
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(horizontal = 9.dp).fillMaxSize()) {
            ChannelMark(card.link, 48.dp, 28.dp)
            Column(Modifier.weight(1f)) {
                Text(channelLabel(card.link.name), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp), color = if (focused) LikhtarColors.Background.copy(alpha = 0.7f) else LikhtarColors.TextDim, maxLines = 1)
                Text(programmeTitle(res, card), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = if (focused) LikhtarColors.Background else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressLine(card, now, Modifier.padding(top = 4.dp).fillMaxWidth())
            }
        }
    }
}

/** Група (.g): 64 px, значок 30 px; розкрита — назва 22 px і кількість моно 18 px. */
@Composable
private fun GroupItem(
    title: String,
    icon: Int,
    count: Int,
    open: Boolean,
    selected: Boolean,
    focusable: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(32.dp)
            .focusProperties { canFocus = focusable }
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(7.dp),
        background = if (selected) LikhtarColors.Paper.copy(alpha = 0.10f) else Color.Transparent,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusedScale = 1f,
        instantFill = true,
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp).fillMaxSize()) {
            Icon(
                painterResource(icon),
                null,
                tint = when {
                    focused -> LikhtarColors.Background
                    selected -> LikhtarColors.Glow
                    else -> LikhtarColors.TextDim
                },
                modifier = Modifier.size(15.dp),
            )
            if (open) {
                Text(title, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp), color = if (focused) LikhtarColors.Background else if (selected) Color.White else LikhtarColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("$count", style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp), color = if (focused) LikhtarColors.Background else LikhtarColors.TextDim)
            }
        }
    }
}

private fun androidx.compose.ui.focus.FocusDirection.isArrow(): Boolean =
    this == androidx.compose.ui.focus.FocusDirection.Up || this == androidx.compose.ui.focus.FocusDirection.Down ||
        this == androidx.compose.ui.focus.FocusDirection.Left || this == androidx.compose.ui.focus.FocusDirection.Right

/** Значок групи: те, що є в дизайн-системі; решта — тека. */
private fun groupIcon(group: String): Int = when {
    group == GuideAll -> DsR.drawable.ic_solar_tv
    group.contains("Україн") -> DsR.drawable.ic_solar_star
    group.contains("Кіно", ignoreCase = true) || group.contains("Film", ignoreCase = true) -> DsR.drawable.ic_solar_clapper
    group.contains("Серіал") || group.contains("Serial", ignoreCase = true) -> DsR.drawable.ic_solar_play_circle
    group.contains("Розваг") || group.contains("шоу") || group.contains("Rozryw", ignoreCase = true) -> DsR.drawable.ic_solar_magic
    group.contains("Природ") || group.contains("наук") -> DsR.drawable.ic_solar_bulb
    group.contains("Новин") || group.contains("Inform", ignoreCase = true) -> DsR.drawable.ic_solar_bell
    else -> DsR.drawable.ic_solar_folder
}

/** Назва групи: службові ряди — перекладом, жанри сервера — як прийшли. */
private fun liveGroupTitle(res: android.content.res.Resources, group: String): String =
    if (group == LiveRowOther) res.getString(R.string.cinema_live_row_other) else group

/**
 * Довге OK на каналі (обидві сцени): бічна панель у стилі «Позначити» Кінозалу. Відкрита
 * утриманням — перше OK лише «відпустили кнопку» (armed = false).
 */
@Composable
internal fun LiveChannelMenu(card: LiveCard, pinned: Boolean, onFavourite: () -> Unit, onHide: () -> Unit, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    com.likhtar.home.core.designsystem.LikhtarDialog(onDismiss = onDismiss, width = 460.dp, side = true, armed = false) {
        Text(channelLabel(card.link.name), style = LikhtarType.SectionTitle, color = LikhtarColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(programmeTitle(res, card), style = LikhtarType.Body1, color = LikhtarColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MarkOption(
                if (pinned) {
                    MarkOptionSpec(MarkIcon.CLEAR, res.getString(R.string.cinema_live_menu_unfav), res.getString(R.string.cinema_live_menu_unfav_hint)) { onFavourite(); onDismiss() }
                } else {
                    MarkOptionSpec(MarkIcon.BOOKMARK, res.getString(R.string.cinema_live_menu_fav), res.getString(R.string.cinema_live_menu_fav_hint)) { onFavourite(); onDismiss() }
                },
                Modifier.focusRequester(first),
            )
            MarkOption(MarkOptionSpec(MarkIcon.HIDE, res.getString(R.string.cinema_live_menu_hide), res.getString(R.string.cinema_live_menu_hide_hint)) { onHide(); onDismiss() }, Modifier)
        }
    }
}

/**
 * Сцена 2 визирає з-під сцени 1 — як друга сцена сторінки тайтла (вкладки й лінія, TitleHero):
 * групи пігулками .tabs (13 sp / 600, перша біла з рискою), нефокусовані; ↓ відкриває сцену.
 */
@Composable
internal fun GuidePeek(groups: List<String>, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.offset(x = (-11).dp)) {
            (listOf(GuideAll) + groups).take(8).forEachIndexed { i, g ->
                Box(Modifier.padding(start = 11.dp, end = 11.dp, top = 6.dp, bottom = 8.dp)) {
                    Text(
                        if (g == GuideAll) res.getString(R.string.cinema_live_all_channels) else liveGroupTitle(res, g),
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = if (i == 0) Color.White else LikhtarColors.TextDim,
                        maxLines = 1,
                        modifier = if (i == 0) {
                            Modifier.drawBehind {
                                val h = 1.5.dp.toPx()
                                drawRoundRect(LikhtarColors.Paper, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height + 6.dp.toPx() - h), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h))
                            }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LikhtarColors.Paper.copy(alpha = 0.2f)))
    }
}

/** Плеєр, який пропонуємо, коли на пристрої немає жодного: відкритий, без реклами, є в Google Play. */
internal val PlayerOnPlay = "Just Player" to "com.brouken.player"

/** Канал плейлиста, а відеоплеєра немає: пояснюємо й пропонуємо встановити [PlayerOnPlay]. */
@Composable
internal fun NeedPlayerDialog(onInstall: () -> Unit, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    com.likhtar.home.core.designsystem.LikhtarDialog(onDismiss = onDismiss, width = 460.dp, side = true, armed = true) {
        Text(res.getString(R.string.cinema_live_need_player), style = LikhtarType.SectionTitle, color = LikhtarColors.TextPrimary)
        Text(res.getString(R.string.cinema_live_need_player_hint), style = LikhtarType.Body1, color = LikhtarColors.TextPrimary)
        Spacer(Modifier.height(24.dp))
        MarkOption(
            MarkOptionSpec(null, res.getString(R.string.cinema_live_free_install, PlayerOnPlay.first), res.getString(R.string.cinema_live_need_player_why)) { onInstall(); onDismiss() },
            Modifier.focusRequester(first),
        )
    }
}
