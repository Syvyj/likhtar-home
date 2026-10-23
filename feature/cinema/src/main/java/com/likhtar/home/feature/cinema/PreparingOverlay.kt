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
 * Що відбувається з роздачею, поки плеєр чекає: піри, сіди, швидкість і смуга
 * підвантаження початку. Без цього ліхтар «про конект» виглядав як зависання.
 */
@Composable
internal fun PreparingStat(stat: com.likhtar.home.core.catalog.TorrentStat, need: Double) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 10.dp),
    ) {
        // Три числа крупно — піри, сіди, швидкість; швидкість фарбуємо відносно того,
        // скільки треба цій якості: видно одразу, чи 4K зараз потягне.
        val mbps = stat.speedBytes / 1_048_576
        val speedColor = when {
            mbps <= 0.0 -> LikhtarColors.TextMuted
            mbps >= need -> LikhtarColors.TileGreen
            mbps >= need / 2 -> LikhtarColors.TileYellow
            else -> LikhtarColors.TileRed
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stat.peers.toString(), pluralStringResource(R.plurals.cinema_peers, stat.peers), LikhtarColors.TextPrimary)
            StatTile(stat.seeders.toString(), pluralStringResource(R.plurals.cinema_seeders, stat.seeders), LikhtarColors.TextPrimary)
            StatTile(
                if (mbps > 0) String.format(java.util.Locale.ROOT, "%.1f", mbps) else "—",
                stringResource(R.string.cinema_prep_mbps_need, String.format(java.util.Locale.ROOT, "%.1f", need)),
                speedColor,
            )
        }
        val share = stat.bufferShare
        Text(
            text = when {
                share != null && share >= 1f -> stringResource(R.string.cinema_prep_buffered)
                share != null -> stringResource(R.string.cinema_prep_buffering_percent, (share * 100).toInt())
                stat.peers == 0 -> stringResource(R.string.cinema_prep_looking_for_peers)
                mbps in 0.0001..<need -> stringResource(R.string.cinema_prep_slow)
                else -> stringResource(R.string.cinema_prep_measuring)
            },
            style = LikhtarType.Body2,
            color = Color.White,
        )
        Box(
            modifier = Modifier
                .width(420.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(LikhtarDimens.CornerTiny))
                .background(Color.White.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(share ?: 0f)
                    .background(LikhtarColors.Accent),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, caption: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
            .background(LikhtarColors.Glass)
            .padding(vertical = 10.dp),
    ) {
        Text(text = value, style = LikhtarType.SectionTitle, color = color, maxLines = 1)
        Text(text = caption, style = LikhtarType.MonoTiny, color = LikhtarColors.TextMuted, maxLines = 1)
    }
}

/** «🇺🇦 2160p · Dolby Vision · WEB-DL · HEVC · Дубляж · 92 ГБ · Торент · сезон» → плашки. */
@Composable
internal fun ReleaseChips(label: String) {
    val chips = releaseChips(label)
    if (chips.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        chips.forEachIndexed { i, chip ->
            Text(
                text = chip,
                style = LikhtarType.Label,
                color = if (i == 0) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
                    .background(if (i == 0) LikhtarColors.GlassActive else LikhtarColors.Glass)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
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
