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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.likhtar.home.core.designsystem.requestWithRetry
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.PictureTier
import com.likhtar.home.core.catalog.VoiceGroup
import com.likhtar.home.core.catalog.pictureTier
import com.likhtar.home.core.catalog.extraChips
import com.likhtar.home.core.catalog.isUkrainian
import com.likhtar.home.core.catalog.rightNote
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.MetaChip
import com.likhtar.home.core.designsystem.VoiceBadge
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
    /** Конкретний варіант розгорнутої озвучки. */
    onPlayOption: (VoiceGroup, String) -> Unit,
    onBack: () -> Unit,
    /** `null` — шукати нічого: тайтл сервер уже обходив, і джерел у нього немає. */
    onSearch: (() -> Unit)? = null,
    /** Назва серії для шапки — бурштином, як на паузі плеєра; порожня — без неї. */
    episodeName: String = "",
) {
    // Фокус просимо не один раз, а поки вузол не з'явиться, і ЩОРАЗУ, коли зміст модалки
    // інший. Один запит на зміну `loading` цього не тримав: у порожньому списку кнопка
    // «Пошукати зараз» компонується вже після нього, запит гинув у порожнечі, і модалка
    // відкривалась без фокуса взагалі — натискати було нічого.
    // Порожність, а не розмір: повільніші джерела дописуються в уже показаний список, і новий запит
    // фокуса на кожен дописаний рядок смикав би людину нагору посеред гортання.
    val focus = rememberInitialFocusRequester(
        Triple(picker.loading, voices.isEmpty(), onSearch != null),
    )
    BackHandler(onBack = onBack)
    // Розгорнули озвучку — фокус на її перший варіант; згорнули — назад на озвучку.
    val optionFocus = remember { FocusRequester() }
    var lastOpened by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val rows = remember(voices) { pickerRows(voices) }
    // Повільніші джерела дописуються вже після показу, і група «4K · Dolby Vision» може з'явитись над
    // рядком, на якому стоїть фокус. Поки людина не натиснула жодної клавіші — ставимо
    // фокус на верх; щойно натиснула — не смикаємо (див. коментар до `focus`).
    var touched by remember { mutableStateOf(false) }
    val topKey = rows.topVoiceKey()
    LaunchedEffect(topKey) {
        if (!touched && topKey != null && picker.expanded == null) {
            listState.scrollToItem(0)
            focus.requestWithRetry()
        }
    }
    LaunchedEffect(picker.expanded) {
        if (picker.expanded != null) {
            lastOpened = picker.expanded
            // Список повернувся після мертвого варіанта: розгорнута озвучка може бути нижче
            // видимого, і її варіантів ще немає в композиції. Прокручуємо лише тоді —
            // звичайне розгортання рядка на місці не смикає (див. каскад пришпилювання).
            val at = rows.indexOfFirst { it is PickerItem.Voice && it.group.voice == picker.expanded }
            if (at >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == at }) listState.scrollToItem(at)
            optionFocus.requestWithRetry()
        }
    }

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
                .clip(RoundedCornerShape(20.dp))
                .background(LikhtarColors.Surface)
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                // Поки шукаємо — вікно своєї звичної висоти, а ліхтар точно в його центрі (макет «Екрани
                // очікування», A): малюємо його поверх, у центрі того ж Box, що й вікно.
                .then(if (picker.loading) Modifier.height(PickerLoadingHeight) else Modifier)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Шапка без системного «S1E1 · ОЗВУЧКА»: велике «Озвучка», поруч серія словами,
            // назва серії бурштином; під нею — підказка й тонка лінія.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.cinema_picker_heading),
                    style = LikhtarType.SectionTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                    color = LikhtarColors.TextPrimary,
                )
                if (picker.episode > 0) {
                    val line = stringResource(R.string.cinema_picker_title_episode, picker.season, picker.episode)
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            append(line)
                            if (episodeName.isNotBlank()) {
                                append(" · ")
                                withStyle(androidx.compose.ui.text.SpanStyle(color = LikhtarColors.LanternAmber, fontWeight = FontWeight.Bold)) {
                                    append(episodeName)
                                }
                            }
                        },
                        style = LikhtarType.Body1.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = LikhtarColors.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            if (picker.episode > 0) {
                Text(
                    text = stringResource(R.string.cinema_picker_episode_hint),
                    style = LikhtarType.HintText.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                    color = LikhtarColors.TextPrimary,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LikhtarColors.GlassBorder))
            if (picker.loading) return@Column

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
                state = listState,
                modifier = Modifier.heightIn(max = 420.dp).onPreviewKeyEvent { touched = true; false },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Ключ — озвучка, не позиція: коли допишуться повільніші джерела й список пересортується,
                // рядок у фокусі лишиться тим самим рядком. Заголовки груп не фокусуються:
                // ↑↓ ходять лише по озвучках.
                items(rows, key = { row ->
                    when (row) {
                        is PickerItem.Header -> "tier:" + row.tier.name
                        is PickerItem.Voice -> row.key
                    }
                }) { row ->
                    if (row is PickerItem.Header) {
                        TierHeader(row.tier)
                        return@items
                    }
                    val group = (row as PickerItem.Voice).group
                    val open = picker.expanded == group.voice
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PickerRow(
                            group = group,
                            first = row.key == topKey,
                            focus = focus,
                            // Згорнули варіанти — фокус назад на саму озвучку, а не в нікуди.
                            returnFocus = lastOpened == group.voice && picker.expanded == null,
                            // Один раз: інакше прокрутили озвучку за екран і назад — фокус стрибав на неї.
                            onReturned = { lastOpened = null },
                            onClick = { onPlay(group) },
                        )
                        if (open) {
                            group.refs.forEachIndexed { i, ref ->
                                ActionPill(
                                    label = group.optionLabel(ref),
                                    modifier = Modifier
                                        .padding(start = 28.dp)
                                        .then(if (i == 0) Modifier.focusRequester(optionFocus) else Modifier),
                                    onClick = { onPlayOption(group, ref) },
                                )
                            }
                        }
                    }
                }
            }
            if (picker.searching) {
                Text(
                    text = stringResource(R.string.cinema_picker_more),
                    style = LikhtarType.HintText,
                    color = LikhtarColors.TextSecondary,
                )
            }
        }
        if (picker.loading) {
            // У центрі того ж Box, що й вікно: сам ліхтар — точно в центрі вікна, тексти під ним.
            LanternLoader(title = stringResource(R.string.cinema_picker_searching))
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

/** Рядок списку озвучок: заголовок групи картинки або сама озвучка зі стабільним ключем. */
internal sealed interface PickerItem {
    data class Header(val tier: PictureTier) : PickerItem
    data class Voice(val key: String, val group: VoiceGroup) : PickerItem
}

/**
 * Плаский список для LazyColumn. Ключ — озвучка й її порядковий номер серед однойменних
 * у вихідному списку, а не позиція: коли повільніші джерела допишуться й список пересортується, рядок
 * у фокусі лишиться тим самим. Номер рахується за позицією, тож два однакові пункти
 * однакового ключа не отримають (LazyColumn на повторі падає).
 */
internal fun pickerRows(voices: List<VoiceGroup>): List<PickerItem> {
    val keyed = voices.mapIndexed { i, g -> g to g.voice + "#" + voices.take(i).count { it.voice == g.voice } }
    return PictureTier.entries.flatMap { tier ->
        val inTier = keyed.filter { (g, _) -> g.pictureTier() == tier }
        if (inTier.isEmpty()) emptyList()
        else listOf(PickerItem.Header(tier)) + inTier.map { (g, k) -> PickerItem.Voice(k, g) }
    }
}

internal fun List<PickerItem>.topVoiceKey(): String? = firstNotNullOfOrNull { (it as? PickerItem.Voice)?.key }

/** Заголовок групи картинки: бурштиновий моно-підпис і тонка лінія до краю. Не фокусується. */
@Composable
private fun TierHeader(tier: PictureTier) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(
                when (tier) {
                    PictureTier.DV_4K -> R.string.cinema_tier_dv_4k
                    PictureTier.HDR_4K -> R.string.cinema_tier_hdr_4k
                    PictureTier.UHD -> R.string.cinema_tier_4k
                    PictureTier.FHD -> R.string.cinema_tier_1080
                    PictureTier.LOWER -> R.string.cinema_tier_lower
                },
            ),
            // Крупніше.
            style = LikhtarType.MonoSmall.copy(fontSize = 11.5.sp),
            color = LikhtarColors.LanternAmber,
        )
        Box(Modifier.weight(1f).height(1.dp).background(LikhtarColors.LanternAmber.copy(alpha = 0.35f)))
    }
}

/**
 * Рядок озвучки: мітка UA, чия озвучка, значки понад заголовок групи, праворуч — скільки
 * варіантів. Що всередині файла — лише під рядком у фокусі: два десятки
 * рядків доріжок під кожною озвучкою були простирадлом.
 */
@Composable
private fun PickerRow(
    group: VoiceGroup,
    first: Boolean,
    focus: FocusRequester,
    returnFocus: Boolean,
    onClick: () -> Unit,
    onReturned: () -> Unit = {},
) {
    val own = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(returnFocus) { if (returnFocus && own.requestWithRetry()) onReturned() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LikhtarButton(
            label = group.short,
            onClick = onClick,
            modifier = (if (first) Modifier.focusRequester(focus) else Modifier)
                .focusRequester(own)
                .onFocusChanged { focused = it.hasFocus }
                .fillMaxWidth(),
            // Частини рядка розділяють бурштинові вертикальні риски.
            leading = if (group.isUkrainian()) { _, _ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VoiceBadge()
                    AmberBar(Modifier.padding(start = 12.dp))
                }
            } else null,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    group.extraChips().forEach { MetaChip(it) }
                    group.rightNote().split(" · ").filter { it.isNotBlank() }.forEach { part ->
                        AmberBar()
                        Text(
                            text = part,
                            style = LikhtarType.MonoSmall.copy(fontSize = 11.5.sp),
                            // Чіткий білий, а на білій кнопці у фокусі — чіткий чорний.
                            color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary,
                            maxLines = 1,
                        )
                    }
                }
            },
        )
        val inside = group.inside
        if (focused && inside.isNotBlank()) {
            Text(
                text = inside,
                style = LikhtarType.HintText.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                color = LikhtarColors.TextPrimary,
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
                maxLines = 2,
            )
        }
    }
}

/** Бурштинова вертикальна риска між частинами рядка озвучки. */
@Composable
private fun AmberBar(modifier: Modifier = Modifier) {
    Box(modifier.width(1.5.dp).height(14.dp).background(LikhtarColors.LanternAmber))
}

/** Висота вікна, поки шукаємо джерела: шапка вгорі, ліхтар із підписами — у центрі. */
private val PickerLoadingHeight = 460.dp
