package com.likhtar.home.feature.home

import androidx.compose.animation.core.animateFloatAsState
import com.likhtar.home.core.designsystem.WeatherColors
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.model.DayForecast
import com.likhtar.home.core.model.HourForecast
import com.likhtar.home.core.model.Sky
import com.likhtar.home.core.model.WeatherSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Сторінка погоди — «вікно в сьогоднішнє небо» (прототип, site/weather).
 *
 * Ліворуч — зараз: велика тонка цифра, стан неба, ліхтар-провісник із парасолькою, тихий
 * рядок метрик і крива доби. Праворуч — тиждень; ↑↓ по днях перебудовує криву під вибраний
 * день. Колір неба — від погоди й часу доби; усе статичне (градієнти), рухаються лише фокус
 * і парасолька, тож на слабкій приставці сторінка нічого не коштує.
 *
 * Back не перехоплюємо — його чекає навігація.
 */
@Composable
fun WeatherScreen(viewModel: WeatherDetailViewModel = hiltViewModel()) {
    val weatherState by viewModel.weather.collectAsStateWithLifecycle()
    val weather = weatherState
    if (weather == null) {
        Box(Modifier.fillMaxSize().background(LikhtarColors.Background)) {
            Text(
                text = stringResource(R.string.home_weather_loading),
                style = LikhtarType.SectionTitle,
                color = LikhtarColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    // Тиждень: сьогодні першим (є в нових знімках), далі прогноз.
    val days = remember(weather) { listOfNotNull(weather.today) + weather.forecast.take(WEEK_DAYS - 1) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val night = weather.hourly.firstOrNull()?.night ?: isNightHour(nowTime())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawSky(weather.sky, night) },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Where(weather.city)
                NowRow(weather, night, days.getOrNull(selected), selected)
                Spacer(Modifier.height(4.dp))
                Facts(weather)
                Spacer(Modifier.height(20.dp))
                val day = days.getOrNull(selected)
                Text(
                    text = if (selected == 0) {
                        stringResource(R.string.home_weather_hourly)
                    } else {
                        stringResource(R.string.home_weather_day_hours, day?.let { longDay(selected) }.orEmpty())
                    },
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                DayCurve(
                    hours = if (selected == 0) weather.hourly else day?.hours.orEmpty(),
                    nowMark = selected == 0,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp),
                )
            }
            Week(
                days = days,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.width(330.dp).fillMaxHeight(),
            )
        }
        Text(
            // Атрибуція обов'язкова за умовами Open-Meteo.
            text = stringResource(R.string.home_weather_attribution),
            style = LikhtarType.MonoTiny,
            color = LikhtarColors.TextMuted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 14.dp),
        )
    }
}

private const val WEEK_DAYS = 7
private val Muted = WeatherColors.Ink.copy(alpha = 0.62f)
private val Rain = WeatherColors.Rain

/** Небо: ніч — глибокий синій у графіт, день — м'якший; хмари й дощ притушують. Знизу — темніше під текст. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSky(sky: Sky, night: Boolean) {
    val (top, bottom) = when {
        night && sky == Sky.RAIN -> WeatherColors.NightRainTop to WeatherColors.NightRainBottom
        night -> WeatherColors.NightTop to WeatherColors.NightBottom
        sky == Sky.RAIN -> WeatherColors.RainTop to WeatherColors.RainBottom
        sky == Sky.CLOUDY -> WeatherColors.CloudyTop to WeatherColors.CloudyBottom
        else -> WeatherColors.ClearTop to WeatherColors.ClearBottom
    }
    drawRect(Brush.verticalGradient(0f to top, 0.72f to bottom, 1f to bottom))
    // Світило за кадром угорі праворуч — тепле вдень, холодне вночі, ледь помітне в негоду.
    val orb = when {
        sky != Sky.CLEAR -> WeatherColors.OrbOvercast.copy(alpha = 0.1f)
        night -> WeatherColors.OrbNight.copy(alpha = 0.22f)
        else -> LikhtarColors.Glow.copy(alpha = 0.45f)
    }
    drawCircle(
        brush = Brush.radialGradient(listOf(orb, Color.Transparent), center = Offset(size.width * 0.78f, -size.height * 0.05f), radius = size.height * 0.85f),
        radius = size.height * 0.85f,
        center = Offset(size.width * 0.78f, -size.height * 0.05f),
    )
    drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to WeatherColors.Floor.copy(alpha = 0.55f)))
}

@Composable
private fun Where(city: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(city, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = Color.White)
        val now = remember { SimpleDateFormat("EEEE, d MMMM, HH:mm", Locale.getDefault()).format(Date()) }
        Text(now.replaceFirstChar { it.uppercase() }, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp), color = Muted)
    }
}

@Composable
private fun NowRow(weather: WeatherSnapshot, night: Boolean, day: DayForecast?, dayIndex: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = plainTemperature(weather.temperature).removeSuffix("°"),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Normal, fontSize = 150.sp, letterSpacing = (-0.06).em, lineHeight = 150.sp),
                color = Color.White,
            )
            Text("°", style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 70.sp, lineHeight = 70.sp), color = Color.White, modifier = Modifier.padding(top = 18.dp))
        }
        Spacer(Modifier.width(22.dp))
        Column {
            LikhtarIcons.Sky(sky = weather.sky, night = night, tint = LikhtarColors.Glow, size = 60.dp, weight = 1.4f)
            Text(
                weather.condition.substringBefore(" · "),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 23.sp),
                color = Color.White,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (weather.apparentTemperature.isNotBlank()) {
                Text(
                    stringResource(R.string.home_weather_feels_like, plainTemperature(weather.apparentTemperature)),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 14.sp),
                    color = Muted,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        UmbrellaLantern(day, dayIndex)
    }
}

/**
 * Ліхтар-провісник: чи брати парасольку. Найвологіша година дня (з 7:00; сьогодні — від
 * зараз), поріг 40 %. Дощ — парасолька розкрита над ліхтарем і блакитна, сухо — складена
 * поруч. Рух один: парасолька перелітає між станами, коли міняєш день.
 */
@Composable
private fun UmbrellaLantern(day: DayForecast?, dayIndex: Int) {
    val hours = day?.hours.orEmpty().filter { (it.time.take(2).toIntOrNull() ?: 0) >= 7 }
        .let { list -> if (dayIndex == 0) list.filter { it.time >= nowTime().take(2) + ":00" } else list }
    val wettest = hours.maxByOrNull { it.precipitationPercent }
    val chance = wettest?.precipitationPercent ?: (day?.precipitationPercent ?: 0)
    val wet = chance >= 40
    val maybe = !wet && chance >= 20
    val open by animateFloatAsState(if (wet) 1f else 0f, tween(400), label = "umbrella")

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(listOf(LikhtarColors.Spill.copy(alpha = 0.22f), Color.Transparent), center = Offset(55.dp.toPx(), size.height / 2), radius = 140.dp.toPx()),
            radius = 140.dp.toPx(),
            center = Offset(55.dp.toPx(), size.height / 2),
        )
    }) {
        Box(modifier = Modifier.size(width = 86.dp, height = 112.dp)) {
            Image(
                painter = painterResource(com.likhtar.home.core.designsystem.R.drawable.likhtar_lantern),
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 0.dp).size(62.dp, 80.dp),
            )
            // Складена — біля підніжжя, нахилена; розкрита — над ліхтарем.
            val s = 32.dp + 28.dp * open
            Image(
                painter = painterResource(R.drawable.ic_umbrella),
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (wet) Rain else WeatherColors.Ink.copy(alpha = 0.55f)),
                modifier = Modifier
                    .offset(x = 54.dp - 41.dp * open, y = 80.dp - 92.dp * open)
                    .size(s)
                    .rotate(18f * (1f - open)),
            )
        }
        Spacer(Modifier.width(14.dp))
        // .say max-width 300 px. Як у прототипі, колонка не стискається вужче за слова й може
        // зайти в проміжок праворуч: стиснута Row-ом, вона рвала «Парасол-ька».
        Column(modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).widthIn(max = 150.dp)) {
            Text(
                stringResource(
                    when {
                        wet -> R.string.home_weather_umbrella_take
                        maybe -> R.string.home_weather_umbrella_maybe
                        else -> R.string.home_weather_umbrella_no
                    },
                ),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 19.sp),
                color = if (wet) WeatherColors.RainBright else Color.White,
            )
            Text(
                when {
                    (wet || maybe) && wettest != null -> stringResource(R.string.home_weather_rain_at, wettest.time, chance)
                    chance > 0 -> stringResource(R.string.home_weather_rain_little, chance)
                    else -> stringResource(R.string.home_weather_dry)
                },
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.sp, lineHeight = 16.sp),
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Facts(weather: WeatherSnapshot) {
    val facts = buildList {
        add(stringResource(R.string.home_weather_wind) to stringResource(R.string.home_weather_mps, weather.windMetersPerSecond.toString()))
        if (weather.windGustMetersPerSecond > 0) {
            add(stringResource(R.string.home_weather_gusts) to stringResource(R.string.home_weather_mps, weather.windGustMetersPerSecond.toString()))
        }
        if (weather.humidityPercent > 0) add(stringResource(R.string.home_weather_humidity) to "${weather.humidityPercent}%")
        if (weather.pressureHpa > 0) add(stringResource(R.string.home_weather_pressure) to stringResource(R.string.home_weather_hpa, weather.pressureHpa.toString()))
        if (weather.sunrise.isNotBlank()) add(stringResource(R.string.home_weather_sunrise) to weather.sunrise.take(5))
        if (weather.sunset.isNotBlank()) add(stringResource(R.string.home_weather_sunset) to weather.sunset.take(5))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        facts.forEach { (label, value) ->
            Column {
                Text(label, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp), color = Muted)
                Text(value, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = Color.White)
            }
        }
    }
}

/**
 * Крива доби: температура лінією з теплою заливкою, ймовірність дощу — стовпчиками внизу.
 * Підписи кожні 3 години. Малюється одним Canvas, без вузлів на кожну годину.
 */
@Composable
private fun DayCurve(hours: List<HourForecast>, nowMark: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val tempStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
    val hourStyle = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp, color = Muted)
    val pctStyle = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp, color = Rain)
    Canvas(modifier) {
        val points = hours.take(24)
        if (points.size < 2) return@Canvas
        val temps = points.map { degrees(it.temperature) }
        val lo = temps.min() - 1
        val hi = temps.max() + 1
        val top = 22.dp.toPx(); val bottom = size.height * 0.62f; val base = size.height - 16.dp.toPx()
        val x = { i: Int -> 10.dp.toPx() + i * (size.width - 20.dp.toPx()) / (points.size - 1) }
        val y = { t: Float -> bottom - (t - lo) / (hi - lo) * (bottom - top) }

        points.forEachIndexed { i, h ->
            val p = h.precipitationPercent
            if (p > 0) {
                val bh = p / 100f * (base - bottom) * 0.9f
                drawRoundRect(WeatherColors.RainBar.copy(alpha = 0.55f), Offset(x(i) - 4.dp.toPx(), base - bh), Size(8.dp.toPx(), bh), CornerRadius(2.dp.toPx()))
            }
        }
        val line = Path().apply { temps.forEachIndexed { i, t -> if (i == 0) moveTo(x(i), y(t)) else lineTo(x(i), y(t)) } }
        val area = Path().apply { addPath(line); lineTo(x(points.lastIndex), base); lineTo(x(0), base); close() }
        drawPath(area, Brush.verticalGradient(listOf(LikhtarColors.Glow.copy(alpha = 0.28f), Color.Transparent), startY = top, endY = base))
        drawPath(line, LikhtarColors.Glow, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (nowMark) {
            drawLine(LikhtarColors.Paper, Offset(x(0), 10.dp.toPx()), Offset(x(0), base), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }
        points.forEachIndexed { i, h ->
            if (i % 3 != 0) return@forEachIndexed
            val px = x(i); val py = y(temps[i])
            drawCircle(LikhtarColors.Paper, 2.5.dp.toPx(), Offset(px, py))
            val t = measurer.measure("${temps[i].toInt()}°", tempStyle)
            drawText(t, topLeft = Offset(px - t.size.width / 2, py - t.size.height - 4.dp.toPx()))
            val hh = measurer.measure(h.time, hourStyle)
            drawText(hh, topLeft = Offset(px - hh.size.width / 2, size.height - hh.size.height))
            if (h.precipitationPercent >= 20) {
                val pc = measurer.measure("${h.precipitationPercent}%", pctStyle)
                drawText(pc, topLeft = Offset(px - pc.size.width / 2, base - (base - bottom) * 0.9f - pc.size.height - 2.dp.toPx()))
            }
        }
    }
}

/** Тиждень: день, небо, мін., смужка діапазону на спільній шкалі тижня, макс., дощ. */
@Composable
private fun Week(days: List<DayForecast>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val first = rememberInitialFocusRequester()
    val lows = days.map { degrees(it.minTemperature.ifBlank { it.temperature }) }
    val highs = days.map { degrees(it.temperature) }
    val wLo = lows.minOrNull() ?: 0f
    val wHi = (highs.maxOrNull() ?: 1f).coerceAtLeast(wLo + 1f)
    Column(modifier) {
        Text(
            stringResource(R.string.home_weather_week),
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
            color = Muted,
            modifier = Modifier.padding(start = 14.dp, bottom = 10.dp, top = 3.dp),
        )
        days.forEachIndexed { i, day ->
            val on = i == selected
            val grow by animateFloatAsState(if (on) 1.03f else 1f, tween(160), label = "day")
            val ink = if (on) LikhtarColors.OnWarmWhite else Color.White
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp)
                    .padding(vertical = 1.5.dp)
                    .graphicsLayer { scaleX = grow; scaleY = grow }
                    .clip(RoundedCornerShape(50))
                    .background(if (on) LikhtarColors.Paper else Color.Transparent)
                    .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                    .onFocusChanged { if (it.isFocused) onSelect(i) }
                    .focusable()
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    if (i == 0) stringResource(R.string.home_weather_today) else day.day,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(72.dp),
                )
                LikhtarIcons.Sky(sky = day.sky, night = false, tint = if (on) LikhtarColors.AmberOnPaper else LikhtarColors.Glow, size = 20.dp)
                Text(
                    "${lows[i].toInt()}°",
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 14.sp),
                    color = if (on) LikhtarColors.InkOnPaper else Muted,
                    modifier = Modifier.width(36.dp).padding(start = 6.dp),
                )
                RangeTrack(lows[i], highs[i], wLo, wHi, on, Modifier.weight(1f))
                Text(
                    "${highs[i].toInt()}°",
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = ink,
                    modifier = Modifier.width(34.dp).padding(start = 8.dp),
                )
                Text(
                    if (day.precipitationPercent >= 10) "${day.precipitationPercent}%" else "",
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp),
                    color = if (on) WeatherColors.RainOnPaper else Rain,
                    modifier = Modifier.width(32.dp),
                )
            }
        }
    }
}

@Composable
private fun RangeTrack(lo: Float, hi: Float, wLo: Float, wHi: Float, on: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier.height(5.dp).clip(RoundedCornerShape(50)).background(if (on) LikhtarColors.Background.copy(alpha = 0.14f) else WeatherColors.Ink.copy(alpha = 0.14f))) {
        val w = maxWidth
        val start: Dp = w * ((lo - wLo) / (wHi - wLo))
        val len: Dp = (w * ((hi - lo) / (wHi - wLo))).coerceAtLeast(6.dp)
        Box(
            Modifier
                .offset(x = start)
                .width(len)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Rain, LikhtarColors.Glow, LikhtarColors.Amber))),
        )
    }
}

/** «+12°» / «−3°» → число. */
private fun degrees(raw: String): Float =
    raw.replace('−', '-').filter { it.isDigit() || it == '-' || it == '.' }.toFloatOrNull() ?: 0f

private fun nowTime(): String = com.likhtar.home.core.designsystem.clockTime()

private fun longDay(offset: Int): String {
    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
    return SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(c.time).replaceFirstChar { it.uppercase() }
}
