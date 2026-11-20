package com.likhtar.home.feature.settings

import androidx.compose.foundation.background
import com.likhtar.home.core.designsystem.tryFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarInputField
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.tv.TvCountry

/**
 * Порядок груп кнопок на картці: дві стрілки в кожному рядку.
 *
 * Стрілки — окремі органи керування, а не режим: ліво-право переводить між ними, вгору-вниз
 * ходить по рядках. Так видно, що саме натиснути, і не треба пам'ятати, у якому ти режимі.
 * Недоступна стрілка (вгору в першого, вниз в останнього) фокус не бере — інакше вона
 * ловила б натискання й нічого не робила.
 */
@Composable
internal fun TitleActionsDialog(
    order: List<String>,
    label: (String) -> String,
    onMove: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    LikhtarDialog(onDismiss = onDismiss, width = 460.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.settings_button_order), style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
            order.forEachIndexed { index, key ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
                        .background(LikhtarColors.Panel)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.  ${label(key)}",
                        style = LikhtarType.Label,
                        color = LikhtarColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MoveArrow("▲", enabled = index > 0) { onMove(key, true) }
                    MoveArrow("▼", enabled = index < order.lastIndex) { onMove(key, false) }
                }
            }
        }
    }
}

/** Стрілка руху. Вимкнена малюється блідою й фокус не бере. */
@Composable
private fun MoveArrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) {
        Text(
            text = glyph,
            style = LikhtarType.Label,
            color = LikhtarColors.TextMuted.copy(alpha = 0.35f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        return
    }
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(LikhtarDimens.CornerMedium),
        background = LikhtarColors.PanelFloor,
        focusedBackground = LikhtarColors.WarmWhite,
        borderColor = LikhtarColors.PanelBorder,
        focusRingColor = Color.Transparent,
        focusedScale = 1f,
    ) { focused ->
        Text(
            text = glyph,
            style = LikhtarType.Label,
            color = if (focused) LikhtarColors.OnWarmWhite else LikhtarColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * Бічна панель вибору — як список груп у «Ліхтар ТБ»: на всю висоту біля правого краю, назва з
 * бурштиновою рискою, список гортається. Розділи налаштувань ліворуч лишаються видно — людина
 * бачить, де вона, а сторінка не розтягується рядком на кожен варіант.
 */
@Composable
internal fun SettingsPanel(
    title: String,
    onDismiss: () -> Unit,
    armed: Boolean = false,
    header: @Composable () -> Unit = {},
    list: LazyListScope.() -> Unit,
) {
    LikhtarDialog(onDismiss = onDismiss, width = 460.dp, side = false, armed = armed) {
        Text(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = LikhtarType.Body,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            color = LikhtarColors.GlassActive,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        header()
        LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp), content = list)
    }
}

/**
 * Вибір країн: позначки плюс пошук.
 *
 * Список — той самий каталог iptv-org, що й у «Телебаченні»: там ISO-коди країн, тобто рівно
 * те, що приймає `country=` у `/api/watch`. Другого списку країн у проєкті не треба.
 * Обрані — вгору, решта за назвою; `LazyColumn` компонує лише видимі рядки, тому список повний.
 * [onAuto] — рядок «усі / автоматично» (порожній вибір), якщо для цього вибору він має сенс.
 */
@Composable
internal fun CountryPickerDialog(
    title: String,
    countries: List<TvCountry>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    autoLabel: String? = null,
    onAuto: () -> Unit = {},
) {
    var filter by remember { mutableStateOf(TextFieldValue("")) }
    val filterFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    // Фокус — на список, а не в поле пошуку: сфокусоване поле відкриває екранну клавіатуру,
    // і вона з'їдає пів вікна ще до того, як користувач вирішив шукати. Поле — на крок вгору.
    LaunchedEffect(Unit) { runCatching { firstRowFocus.requestFocus() } }
    val query = filter.text.trim().lowercase()
    // Порядок фіксуємо на відкритті: інакше позначена країна стрибала б угору з-під курсора.
    val pinned = remember { selected }
    val visible = countries
        .filter { query.isBlank() || it.name.lowercase().contains(query) || it.code.lowercase() == query }
        // Collator, а не порівняння кодів: українська «І» в Unicode стоїть перед «А», і Ісландія
        // з Італією випереджали Австралію.
        .sortedWith(compareByDescending<TvCountry> { it.code in pinned }.thenBy(java.text.Collator.getInstance()) { it.name })

    SettingsPanel(
        title = title,
        onDismiss = onDismiss,
        header = {
            // Поле саме з'їдає «вниз» (курсор у тексті), тому фокус зі нього в список
            // штовхаємо руками — інакше з пошуку немає виходу до країн.
            Box(
                modifier = Modifier.padding(bottom = 8.dp).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        firstRowFocus.tryFocus()
                    } else {
                        false
                    }
                },
            ) {
                LikhtarInputField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = stringResource(R.string.settings_country_search),
                    focusRequester = filterFocus,
                    onDone = { runCatching { firstRowFocus.requestFocus() } },
                )
            }
            if (countries.isEmpty()) Note(stringResource(R.string.settings_countries_loading))
        },
    ) {
        if (autoLabel != null) {
            item(key = "auto") {
                ChoiceRow(text = autoLabel, selected = selected.isEmpty(), onSelect = onAuto, focusRequester = firstRowFocus)
            }
        }
        itemsIndexed(visible, key = { _, it -> it.code }) { i, country ->
            ChoiceRow(
                text = "${country.flag} ${country.name}",
                selected = country.code in selected,
                onSelect = { onToggle(country.code) },
                focusRequester = firstRowFocus.takeIf { autoLabel == null && i == 0 },
            )
        }
    }
}

/**
 * Вибір одного варіанта зі списку. Фокус — на поточному: людина бачить, що вибрано зараз,
 * і одним OK лишає як було. Вибір закриває панель.
 */
@Composable
internal fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Відкрито не через OK (кнопкою пульта) — перше OK уже вибір, див. [LikhtarDialog]. */
    armed: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val current = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    SettingsPanel(title = title, onDismiss = onDismiss, armed = armed) {
        itemsIndexed(options, key = { _, it -> it.first }) { i, (code, name) ->
            ChoiceRow(
                text = name,
                selected = code == selected,
                onSelect = { onPick(code) },
                focusRequester = focus.takeIf { i == current },
            )
        }
    }
}

/** Кілька позначок зі списку; кожне OK перемикає, панель лишається відкритою до «Назад». */
@Composable
internal fun MultiChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SettingsPanel(title = title, onDismiss = onDismiss) {
        itemsIndexed(options, key = { _, it -> it.first }) { i, (key, name) ->
            ChoiceRow(
                text = name,
                selected = key in selected,
                onSelect = { onToggle(key) },
                focusRequester = focus.takeIf { i == 0 },
            )
        }
    }
}

/** «Сховати сервіси»: позначені не з'являються в Кінозалі — ні плиткою, ні рядами. */
@Composable
internal fun HiddenServicesDialog(hidden: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) =
    MultiChoiceDialog(
        title = stringResource(R.string.settings_watch_hidden_services),
        options = com.likhtar.home.core.model.StreamingService.withPage.mapNotNull { svc -> svc.key?.let { it to svc.title } },
        selected = hidden,
        onToggle = onToggle,
        onDismiss = onDismiss,
    )
