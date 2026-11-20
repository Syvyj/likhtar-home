package com.likhtar.home.feature.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CalendarEvent
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ShowState
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.LocalHighContrast
import com.likhtar.home.core.designsystem.ProtoCardCorner
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Повний календар (макет, екран 2a): дні списком, у кожному — ряд карток серій і фільмів.
 * Не місячна сітка: на телевізорі її не читають.
 */
@Composable
fun CalendarScreen(
    onOpen: (CatalogItem) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highContrast = LocalHighContrast.current
    // Старт — на першій картці від сьогодні; минулі дні вище.
    val startDay = state.days.firstOrNull { it.first >= state.today }?.first ?: state.days.firstOrNull()?.first
    val first = rememberInitialFocusRequester(startDay)
    // Сьогоднішній день може бути нижче екрана (кілька минулих днів із серіями) — тоді його картка
    // ще не скомпонована й фокус нікуди не ставав. Спершу докручуємо до нього.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(startDay) {
        val index = state.days.indexOfFirst { it.first == startDay }
        if (index >= 0) listState.scrollToItem(index + 1)
    }
    val pillBg = if (highContrast) LikhtarColors.SurfaceDeep else LikhtarColors.BlockFill.copy(alpha = 0.79f)
    val pillBorder = if (highContrast) LikhtarColors.PillBorderHighContrast else LikhtarColors.PillBorder
    Box(
        Modifier
            .fillMaxSize()
            .background(LikhtarColors.Background)
            .background(
                Brush.radialGradient(
                    colors = listOf(LikhtarColors.HeroGlowLive, Color.Transparent),
                    center = Offset(1520f, 180f),
                    radius = 780f,
                ),
            ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = LikhtarDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 28.dp, bottom = 36.dp),
        ) {
            item(key = "head") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.tracker_calendar),
                        style = LikhtarType.HeroTitle.copy(fontSize = 26.sp),
                        color = LikhtarColors.GlassActive,
                    )
                    // Сучасна скляна сегментована пігулка (як у Застосунках та шапці)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                            .background(pillBg)
                            .border(1.dp, pillBorder, RoundedCornerShape(LikhtarDimens.CornerPill))
                            .padding(horizontal = 5.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CalendarTabPill(
                            label = stringResource(R.string.tracker_tab_mine),
                            selected = state.tab == CalendarViewModel.Tab.MINE,
                            onClick = { viewModel.select(CalendarViewModel.Tab.MINE) },
                        )
                        CalendarTabPill(
                            label = stringResource(R.string.tracker_tab_all),
                            selected = state.tab == CalendarViewModel.Tab.ALL,
                            onClick = { viewModel.select(CalendarViewModel.Tab.ALL) },
                        )
                    }
                }
            }
            when {
                state.loading -> item(key = "loader") {
                    LanternLoader(title = stringResource(R.string.tracker_calendar_loading), modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
                }
                state.days.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.tracker_calendar_empty), style = LikhtarType.Body1, color = LikhtarColors.WarmMuted, modifier = Modifier.padding(top = 30.dp))
                }
                else -> items(state.days, key = { it.first }) { (day, events) ->
                    val isToday = day == state.today
                    val isPast = day < state.today
                    Row(
                        modifier = Modifier.alpha(if (isPast) 0.58f else 1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Ліва картка-дата з вертикальним акцентом таймлайну
                        Row(
                            modifier = Modifier.width(106.dp).padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 5.dp)) {
                                Box(
                                    Modifier
                                        .size(if (isToday) 9.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (isToday) LikhtarColors.Glow else LikhtarColors.GlassActive.copy(alpha = 0.33f)),
                                )
                                Box(
                                    Modifier
                                        .padding(top = 6.dp)
                                        .width(1.5.dp)
                                        .height(58.dp)
                                        .background(if (isToday) LikhtarColors.Glow.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    dayNumber(day),
                                    style = TextStyle(fontFamily = LikhtarType.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                                    color = if (isToday) LikhtarColors.Glow else LikhtarColors.GlassActive,
                                )
                                Text(
                                    if (isToday) stringResource(R.string.tracker_today_title) else weekday(day),
                                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                    color = if (isToday) LikhtarColors.Glow else LikhtarColors.WarmInkSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    monthName(day),
                                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                                    color = LikhtarColors.WarmMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                        LikhtarRail(gap = 12.dp) {
                            items(events, key = { "${it.item.id}:${it.season}:${it.episode}:${it.kind}" }) { event ->
                                DayCard(
                                    event = event,
                                    // Статус серіалу — лише про те, що попереду: під серією, що вже вийшла, «Чекаю серію» бреше.
                                    showState = day >= state.today,
                                    onClick = { onOpen(event.item) },
                                    modifier = if (day == startDay && event == events.first()) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = if (selected) LikhtarColors.GlassActive else Color.Transparent,
        focusedBackground = if (selected) LikhtarColors.GlassActive else LikhtarColors.BorderStrong,
        borderColor = Color.Transparent,
        focusedScale = 1.02f,
    ) { focused ->
        Text(
            text = label,
            style = LikhtarType.MenuItem.copy(fontSize = 13.sp),
            color = when {
                selected -> LikhtarColors.OnGlassActive
                focused -> LikhtarColors.GlassActive
                else -> LikhtarColors.WarmInkSoft
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DayCard(event: CalendarEvent, showState: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(248.dp).padding(vertical = 6.dp),
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.Surface,
        focusedBackground = LikhtarColors.BorderStrong,
        borderColor = LikhtarColors.GlassDivider,
        focusedScale = 1.04f,
    ) {
        Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = TmdbImages.poster(event.item.posterPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp, 78.dp).clip(RoundedCornerShape(7.dp)).background(LikhtarColors.Panel),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    event.item.title,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp),
                    color = LikhtarColors.GlassActive,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    eventLabel(event),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
                    color = LikhtarColors.Glow,
                )
                stateLabel(event)?.takeIf { showState }?.let {
                    Text(
                        it,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                        color = LikhtarColors.WarmMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun stateLabel(event: CalendarEvent): String? = when (event.state) {
    ShowState.NEW -> stringResource(R.string.tracker_state_new)
    ShowState.WATCHING -> stringResource(R.string.tracker_state_watching)
    ShowState.WAITING_EPISODE -> stringResource(R.string.tracker_state_waiting)
    else -> null
}

private fun dayNumber(iso: String): String = runCatching {
    SimpleDateFormat("d", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!)
}.getOrDefault(iso)

private fun monthName(iso: String): String = runCatching {
    SimpleDateFormat("MMMM", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!)
        .replaceFirstChar { it.uppercase() }
}.getOrDefault("")

private fun weekday(iso: String): String = runCatching {
    SimpleDateFormat("EEEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!)
        .replaceFirstChar { it.uppercase() }
}.getOrDefault(iso)
