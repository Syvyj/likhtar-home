package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.cleanStreamLabel
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Що відбувається з джерелом, поки плеєр чекає (макет «Екрани очікування», B): одна шкала швидкості —
 * бурштином скільки качає, біла позначка — скільки треба цій якості; під нею словами, що зараз і
 * скільки з'єднань. Без цього ліхтар «про конект» виглядав як зависання.
 */
@Composable
internal fun PreparingStat(stat: com.likhtar.home.core.catalog.SourceStat, need: Double) {
    val mbps = stat.speedBytes / 1_048_576
    val fmt = { v: Double -> String.format(Locale.getDefault(), "%.1f", v) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.cinema_prep_rate, if (mbps > 0) fmt(mbps) else "—"),
                style = LikhtarType.MonoSmall.copy(fontSize = 12.sp),
                color = LikhtarColors.TextPrimary,
                maxLines = 1,
            )
            // Шкала до подвійної потреби: позначка «треба» — посередині, заповнення — бурштином.
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth((mbps / (need * 2)).toFloat().coerceIn(0f, 1f))
                        .background(LikhtarColors.LanternAmber),
                )
            }
            Text(
                text = stringResource(R.string.cinema_prep_need, fmt(need)),
                style = LikhtarType.MonoSmall.copy(fontSize = 12.sp),
                color = LikhtarColors.TextPrimary,
                maxLines = 1,
            )
        }
        val share = stat.bufferShare
        Text(
            text = when {
                share != null && share >= 1f -> stringResource(R.string.cinema_prep_buffered)
                share != null -> stringResource(R.string.cinema_prep_buffering_percent, (share * 100).toInt())
                stat.connections == 0 -> stringResource(R.string.cinema_prep_looking)
                mbps in 0.0001..<need -> stringResource(R.string.cinema_prep_slow)
                else -> stringResource(R.string.cinema_prep_measuring)
            } + "  ·  ${stat.connections} " + pluralStringResource(R.plurals.cinema_stat_links, stat.connections) +
                " · ${stat.sources} " + pluralStringResource(R.plurals.cinema_stat_sources, stat.sources),
            style = LikhtarType.Body2,
            color = LikhtarColors.TextPrimary,
        )
    }
}

/** «🇺🇦 2160p · Dolby Vision · WEB-DL · …» → значки в стилі плеєра («UA»), між ними бурштинові риски. */
@Composable
internal fun ReleaseChips(label: String) {
    val chips = releaseChips(label)
    if (chips.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        chips.forEachIndexed { i, chip ->
            if (i > 0) Box(Modifier.width(1.5.dp).height(15.dp).background(LikhtarColors.LanternAmber))
            Text(
                text = chip,
                style = LikhtarType.Label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(LikhtarColors.BadgeFloor.copy(alpha = 0.60f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

/** Плашки з підпису: без прапора, «сезон», «Серія N» і повторів озвучки — вона вже в заголовку. */
internal fun releaseChips(label: String): List<String> =
    cleanStreamLabel(label).split("·").map { it.trim() }
        .filter { it.isNotBlank() && it != "сезон" && !it.startsWith("Серія") }
        .filterNot { it in setOf("Дубляж", "Багатоголосий", "Двоголосий", "Авторський") }
        .take(6)

/** Скільки МБ/с треба якості з підпису — ті самі пороги, що й у сервера (needSpeed). */
internal fun needMbps(label: String): Double = when {
    "2160" in label || "4K" in label -> 4.0
    "1080" in label -> 1.5
    "720" in label -> 0.8
    else -> 1.0
}


/** Фраза для «Готую»: крок раз на ~3 с; коли вже пішло підвантаження — про нього. */
@Composable
internal fun rememberPreparingPhrase(buffering: Boolean): String {
    val preparingPhrases = stringArrayResource(R.array.cinema_preparing_phrases)
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_800)
            step = (step + 1).coerceAtMost(preparingPhrases.lastIndex)
        }
    }
    return if (buffering) stringResource(R.string.cinema_prep_buffering_first) else preparingPhrases[step]
}
