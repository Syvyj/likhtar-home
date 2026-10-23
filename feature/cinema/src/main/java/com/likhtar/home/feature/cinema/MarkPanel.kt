package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.likhtar.home.core.catalog.TitleMark
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType

/** Кнопка «Позначити»: знак і підпис стану позначки. */
@Composable
fun markState(mark: TitleMark?): Pair<MarkIcon, String> = when {
    mark == null -> MarkIcon.WATCHED to stringResource(R.string.cinema_mark)
    mark.fromFranchise -> MarkIcon.HIDE to stringResource(R.string.cinema_mark_hidden_with_franchise)
    mark.mark == "watched" -> when (mark.rating) {
        "like" -> MarkIcon.LIKE
        "dislike" -> MarkIcon.DISLIKE
        else -> MarkIcon.WATCHED
    } to when {
        mark.scope == "season" && mark.season > 0 -> stringResource(R.string.cinema_mark_watched_season, mark.season)
        mark.scope == "upto" && mark.season > 0 -> stringResource(R.string.cinema_mark_watched_upto, mark.season, mark.episode)
        else -> stringResource(R.string.cinema_mark_watched)
    }
    mark.mark == "skip" && mark.scope == "later" -> MarkIcon.SKIP to stringResource(R.string.cinema_mark_postponed_month)
    mark.mark == "skip" && mark.scope == "season" -> MarkIcon.SKIP to stringResource(R.string.cinema_mark_season_skipped_n, mark.season)
    mark.mark == "skip" -> MarkIcon.SKIP to stringResource(R.string.cinema_mark_episode_skipped)
    mark.reason == "seen" -> MarkIcon.WATCHED to stringResource(R.string.cinema_mark_seen_elsewhere)
    else -> MarkIcon.HIDE to stringResource(R.string.cinema_mark_not_recommended)
}

/** Що людина обрала в панелі — ті самі поля, що в `/marks`. */
data class MarkChoice(
    val mark: String,
    val scope: String = "",
    val reason: String = "",
    val rating: String = "",
    val season: Int = 0,
    val episode: Int = 0,
)

private enum class MarkStep { ROOT, WATCHED, RATING, SKIP, HIDE }

/**
 * «Позначити»: бічна панель праворуч, щоб тайтл лишався видно. Два кроки: що саме
 * (переглянуто / пропустити / не рекомендувати), потім уточнення. BACK — на крок назад.
 *
 * [season] — показаний сезон; [resumeSeason]/[resumeEpisode] — де людина зупинилась
 * (0 — не дивилась): з цього будуються «до серії» і «цю серію».
 */
@Composable
fun MarkPanel(
    title: String,
    isTv: Boolean,
    mark: TitleMark?,
    season: Int,
    resumeSeason: Int,
    resumeEpisode: Int,
    genres: List<String>,
    franchise: String?,
    onChoice: (MarkChoice) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    /** false — відкрито довгим OK на картці: перше OK лише «відпустили кнопку». */
    armed: Boolean = true,
) {
    var step by remember { mutableStateOf(MarkStep.ROOT) }
    // «Переглянуто» серіалу: спершу що саме, потім оцінка — обране тримаємо між кроками.
    var watched by remember { mutableStateOf(MarkChoice("watched", scope = "all")) }
    val first = remember { FocusRequester() }
    LaunchedEffect(step) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    LikhtarDialog(
        onDismiss = { if (step == MarkStep.ROOT) onDismiss() else step = MarkStep.ROOT },
        width = 460.dp,
        side = true,
        armed = armed,
    ) {
        Text(
            text = when (step) {
                MarkStep.ROOT -> stringResource(R.string.cinema_mark)
                MarkStep.WATCHED -> stringResource(R.string.cinema_mark_q_watched)
                MarkStep.RATING -> stringResource(R.string.cinema_mark_q_rating)
                MarkStep.SKIP -> stringResource(R.string.cinema_mark_q_skip)
                MarkStep.HIDE -> stringResource(R.string.cinema_mark_q_hide)
            },
            style = LikhtarType.SectionTitle,
            color = LikhtarColors.TextPrimary,
        )
        Text(
            text = title,
            style = LikhtarType.Body1,
            // Чітко й суцільно: сірі й напівпрозорі підписи з дивана не читаються.
            color = LikhtarColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(24.dp))
        val here = resumeSeason > 0 && resumeEpisode > 0
        val options = buildList {
            fun option(icon: MarkIcon?, label: String, hint: String, onClick: () -> Unit) =
                add(MarkOptionSpec(icon, label, hint, onClick))
            when (step) {
                MarkStep.ROOT -> {
                    if (mark != null) option(MarkIcon.CLEAR, stringResource(R.string.cinema_mark_clear), stringResource(R.string.cinema_mark_current, markState(mark).second.lowercase())) { onClear() }
                    option(MarkIcon.WATCHED, stringResource(R.string.cinema_mark_watched), stringResource(R.string.cinema_mark_watched_hint)) {
                        if (isTv) step = MarkStep.WATCHED else {
                            watched = MarkChoice("watched", scope = "all")
                            step = MarkStep.RATING
                        }
                    }
                    option(MarkIcon.SKIP, stringResource(R.string.cinema_mark_skip), stringResource(R.string.cinema_mark_skip_hint)) { step = MarkStep.SKIP }
                    option(MarkIcon.HIDE, stringResource(R.string.cinema_mark_hide), stringResource(R.string.cinema_mark_hide_hint)) { step = MarkStep.HIDE }
                }
                MarkStep.WATCHED -> {
                    option(null, stringResource(R.string.cinema_mark_whole_series), stringResource(R.string.cinema_mark_whole_series_hint)) {
                        watched = MarkChoice("watched", scope = "all")
                        step = MarkStep.RATING
                    }
                    if (season > 0) option(null, stringResource(R.string.cinema_season_n, season), stringResource(R.string.cinema_mark_season_all_hint, season)) {
                        watched = MarkChoice("watched", scope = "season", season = season)
                        step = MarkStep.RATING
                    }
                    if (here) option(null, stringResource(R.string.cinema_mark_upto, resumeSeason, resumeEpisode), stringResource(R.string.cinema_mark_upto_hint)) {
                        watched = MarkChoice("watched", scope = "upto", season = resumeSeason, episode = resumeEpisode)
                        step = MarkStep.RATING
                    }
                }
                MarkStep.RATING -> {
                    option(MarkIcon.LIKE, stringResource(R.string.cinema_mark_like), stringResource(R.string.cinema_mark_like_hint)) { onChoice(watched.copy(rating = "like")) }
                    option(MarkIcon.OK, stringResource(R.string.cinema_mark_ok), stringResource(R.string.cinema_mark_ok_hint)) { onChoice(watched.copy(rating = "ok")) }
                    option(MarkIcon.DISLIKE, stringResource(R.string.cinema_mark_dislike), stringResource(R.string.cinema_mark_dislike_hint)) { onChoice(watched.copy(rating = "dislike")) }
                }
                MarkStep.SKIP -> {
                    if (isTv && here) option(null, stringResource(R.string.cinema_mark_skip_episode, resumeSeason, resumeEpisode), stringResource(R.string.cinema_mark_skip_episode_hint)) {
                        onChoice(MarkChoice("skip", scope = "episode", season = resumeSeason, episode = resumeEpisode))
                    }
                    if (isTv && season > 0) option(null, stringResource(R.string.cinema_season_n, season), stringResource(R.string.cinema_mark_skip_season_hint)) {
                        onChoice(MarkChoice("skip", scope = "season", season = season))
                    }
                    option(null, stringResource(R.string.cinema_mark_postpone_month), stringResource(R.string.cinema_mark_postpone_month_hint)) {
                        onChoice(MarkChoice("skip", scope = "later"))
                    }
                }
                MarkStep.HIDE -> {
                    option(null, stringResource(R.string.cinema_mark_seen_elsewhere_option), stringResource(R.string.cinema_mark_seen_elsewhere_hint)) { onChoice(MarkChoice("hide", reason = "seen")) }
                    val kinds = genres.take(3).joinToString(", ").lowercase()
                    option(null, stringResource(R.string.cinema_mark_not_my_genre), if (kinds.isBlank()) stringResource(R.string.cinema_mark_not_my_genre_hint) else stringResource(R.string.cinema_mark_not_my_genre_hint_kinds, kinds)) {
                        onChoice(MarkChoice("hide", reason = "genre"))
                    }
                    if (franchise != null) option(null, stringResource(R.string.cinema_mark_not_franchise), stringResource(R.string.cinema_mark_not_franchise_hint, franchise)) {
                        onChoice(MarkChoice("hide", reason = "franchise"))
                    }
                    option(null, stringResource(R.string.cinema_mark_only_title), stringResource(R.string.cinema_mark_only_title_hint)) { onChoice(MarkChoice("hide", reason = "title")) }
                }
            }
        }
        // Перший пункт кожного кроку бере фокус. Список новий на кожен крок (key): інакше
        // фокус лишався на тому ж місці — на другому пункті нового кроку.
        key(step) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEachIndexed { i, o ->
                    MarkOption(o, if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}

internal class MarkOptionSpec(val icon: MarkIcon?, val label: String, val hint: String, val onClick: () -> Unit)

@Composable
internal fun MarkOption(o: MarkOptionSpec, modifier: Modifier) {
    FocusableSurface(
        onClick = o.onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LikhtarDimens.CornerMedium),
        background = LikhtarColors.Glass,
        focusedBackground = LikhtarColors.GlassActive,
        focusedScale = 1f,
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            if (o.icon != null) {
                MarkGlyph(o.icon, if (focused) LikhtarColors.LanternAmberDeep else LikhtarColors.LanternAmber, size = 26.dp)
                Spacer(Modifier.width(16.dp))
            }
            Column {
                Text(
                    text = o.label,
                    style = LikhtarType.Label,
                    color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary,
                )
                Text(
                    text = o.hint,
                    style = LikhtarType.HintText.copy(fontWeight = FontWeight.Medium),
                    color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
