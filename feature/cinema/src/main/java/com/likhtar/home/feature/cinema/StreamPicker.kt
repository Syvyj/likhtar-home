package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.VoiceGroup
import com.likhtar.home.core.catalog.WatchScan
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester

/**
 * Селектор перегляду: сезон → серія → потік в одному оверлеї.
 *
 * Не окремі маршрути навігації: для пульта важливіше, щоб BACK повертав на крок назад
 * усередині вибору, а не викидав зі сторінки фільму.
 */
@Composable
internal fun StreamPicker(
    picker: PickerState,
    voices: List<VoiceGroup>,
    emptyNote: String,
    onPlay: (VoiceGroup) -> Unit,
    onBack: () -> Unit,
    /** `null` — шукати нічого: тайтл сервер уже обходив, і джерел у нього немає. */
    onSearch: (() -> Unit)? = null,
) {
    // Фокус просимо не один раз, а поки вузол не з'явиться, і ЩОРАЗУ, коли зміст модалки
    // інший. Один запит на зміну `loading` цього не тримав: у порожньому списку кнопка
    // «Пошукати зараз» компонується вже після нього, запит гинув у порожнечі, і модалка
    // відкривалась без фокуса взагалі — натискати було нічого.
    val focus = rememberInitialFocusRequester(
        Triple(picker.loading, voices.size, onSearch != null),
    )
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LikhtarColors.BackgroundDeep.copy(alpha = 0.92f))
            .padding(LikhtarDimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(LikhtarDimens.CornerLarge))
                .background(LikhtarColors.Surface.copy(alpha = 0.96f))
                .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(LikhtarDimens.CornerLarge))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = (if (picker.episode > 0) stringResource(R.string.cinema_picker_title_episode, picker.season, picker.episode) else stringResource(R.string.cinema_picker_title)).uppercase(),
                style = LikhtarType.SectionTitle,
                color = LikhtarColors.TextPrimary,
            )
            // Селектор відповідає на одне питання — ЯКОЮ озвучкою, а не якою серією.
            // Без цього рядка люди шукали тут вибір сезону, якого тут немає й не буде:
            // серія обирається на другій сцені, і змішувати дві дії в одній модалці
            // означало б робити її розміром з екран.
            if (picker.episode > 0) {
                Text(
                    text = stringResource(R.string.cinema_picker_episode_hint),
                    style = LikhtarType.HintText,
                    color = LikhtarColors.TextSecondary,
                )
            }
            if (picker.loading) {
                LanternLoader(
                    title = stringResource(R.string.cinema_picker_searching),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                return@Column
            }

            // Відповідь сервера на пошук — у цій же модалці. Людина натиснула тут, тут і
            // дивиться; напис на сторінці під модалкою вона не побачить.
            if (picker.note.isNotBlank()) {
                Text(
                    text = picker.note,
                    style = LikhtarType.Body1,
                    color = LikhtarColors.TextSecondary,
                )
            }

            if (voices.isEmpty()) {
                Text(
                    text = emptyNote,
                    style = LikhtarType.Body1,
                    color = LikhtarColors.TextSecondary,
                )
                // Дія поруч із написом, а не лише пояснення: пошук за назвою не потребує
                // ані каталогу сервера, ані tmdb_id.
                onSearch?.let { search ->
                    ActionPill(
                        label = stringResource(R.string.cinema_search),
                        modifier = Modifier.focusRequester(focus).padding(top = 14.dp),
                        onClick = search,
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(voices, key = { index, group -> "$index:${group.label}" }) { index, group ->
                    PickerRow(
                        label = group.label,
                        inside = group.inside,
                        first = index == 0,
                        focus = focus,
                        onClick = { onPlay(group) },
                    )
                }
            }
        }
    }

}

/**
 * Чому список джерел порожній.
 *
 * Сервер розрізняє «не питали» і «питали й не знайшли» (`WatchScan.checked`), і на екрані
 * це різні речі: перше — привід зачекати, друге — привід не чекати. `status` тут не
 * годиться: це виробничий стан тайтла з TMDB («Ended»), а не відповідь про джерела.
 */
@Composable
internal fun WatchScan?.emptyNote(): String = when {
    this == null -> stringResource(R.string.cinema_scan_no_answer)
    // Не глухий кут: сервер шукає джерела й без свого каталогу, за назвою й роком.
    !known -> stringResource(R.string.cinema_scan_unknown)
    !checked -> stringResource(R.string.cinema_scan_unchecked)
    else -> stringResource(R.string.cinema_scan_none)
}

@Composable
private fun PickerRow(
    label: String,
    inside: String,
    first: Boolean,
    focus: FocusRequester,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ActionPill(
            label = label,
            modifier = if (first) Modifier.focusRequester(focus) else Modifier,
            onClick = onClick,
        )
        // Вміст файла — під пігулкою, а не в її написі: доріжок буває чотири, і в
        // кнопку вони не влазять, а без них «Дубляж» нічого не каже про те, чий саме.
        if (inside.isNotBlank()) {
            Text(
                text = inside,
                style = LikhtarType.HintText,
                color = LikhtarColors.TextSecondary,
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
            )
        }
    }
}
