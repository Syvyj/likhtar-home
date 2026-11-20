package com.likhtar.home.feature.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CalendarEvent
import com.likhtar.home.core.catalog.EventKind
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.catalog.weekDays
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * «Цього тижня»: сім колонок ПН…НД і восьма — «Повний календар ›» (макет, екран 2).
 *
 * Фокус ходить по подіях, не по днях: порожній день лише каже «нічого». Не Lazy — вісім колонок
 * на екрані завжди, а стрілки між ними шукає сама платформа.
 */
@Composable
fun WeekStrip(
    today: String,
    events: List<CalendarEvent>,
    onOpen: (CalendarEvent) -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val byDate = events.groupBy { it.date }
    // Однакова висота всіх колонок і плитки календаря — за найвищою колонкою.
    Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekDays(today).forEach { day ->
            val isToday = day == today
            Column(
                // Без притемнення минулих днів: чіткі картки, не «напівмертві».
                modifier = Modifier.weight(1f).heightIn(min = DayMinHeight).fillMaxHeight()
                    .clip(DayShape)
                    .background(if (isToday) LikhtarColors.Surface else LikhtarColors.Panel)
                    .then(if (isToday) Modifier.border(1.dp, LikhtarColors.Amber, DayShape) else Modifier)
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = dayLabel(day),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = if (isToday) LikhtarColors.Glow else LikhtarColors.Paper,
                    maxLines = 1,
                )
                // «Сьогодні» — окремим рядком: у вужчих колонках Кінозалу «ВТ 17 · СЬОГОДНІ» обрізалось.
                if (isToday) Text(
                    text = stringResource(R.string.tracker_today).uppercase(),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = LikhtarColors.Glow,
                    maxLines = 1,
                    modifier = Modifier.offset(y = (-4).dp),
                )
                val list = byDate[day].orEmpty()
                if (list.isEmpty()) {
                    Text(stringResource(R.string.tracker_nothing), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = LikhtarColors.Paper)
                }
                list.forEach { event -> WeekEvent(event) { onOpen(event) } }
            }
        }
        FocusableSurface(
            onClick = onCalendar,
            modifier = Modifier.width(CalendarTileWidth).fillMaxHeight(),
            shape = DayShape,
            background = LikhtarColors.Surface,
            focusedBackground = LikhtarColors.GlassActive,
            focusedScale = 1.04f,
        ) { focused ->
            Text(
                text = stringResource(R.string.tracker_calendar_link),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, textAlign = TextAlign.Center),
                color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.Paper,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun WeekEvent(event: CalendarEvent, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.GlassActive,
        focusedScale = 1.04f,
    ) { focused ->
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = TmdbImages.poster(event.item.posterPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp, 45.dp).clip(RoundedCornerShape(4.dp)).background(LikhtarColors.SurfaceAlt),
            )
            Column {
                Text(
                    event.item.title,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.Paper,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    eventLabel(event),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = when {
                        focused -> LikhtarColors.OnGlassActiveMuted
                        event.kind != EventKind.EPISODE -> LikhtarColors.Glow
                        else -> LikhtarColors.Paper
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun eventLabel(event: CalendarEvent): String = when (event.kind) {
    EventKind.EPISODE -> stringResource(R.string.tracker_episode, event.season, event.episode)
    EventKind.ONLINE -> stringResource(R.string.tracker_online)
    EventKind.CINEMA -> stringResource(R.string.tracker_cinema)
}

/** «ПН 16» мовою інтерфейсу. */
internal fun dayLabel(iso: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
    SimpleDateFormat("EE d", Locale.getDefault()).format(date).uppercase().replace(".", "")
}.getOrDefault(iso)

private val DayShape = RoundedCornerShape(9.dp)
private val DayMinHeight = 147.dp
private val CalendarTileWidth = 78.dp
