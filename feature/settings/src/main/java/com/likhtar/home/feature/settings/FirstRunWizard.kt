package com.likhtar.home.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.data.AppLanguage
import com.likhtar.home.core.data.ContentLocale
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.model.StreamingService
import java.util.Locale

/**
 * Майстер першого запуску: мова, країна, мої застосунки. Сервера тут немає
 * й не буде — у продукті він наш і невидимий.
 *
 * Мова й країна — за системою, людина лише підтверджує. BACK — крок назад, з першого —
 * пропустити: тоді лишаються системні, як і без майстра.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FirstRunWizard(
    onFinish: (language: String, country: String, onlyMyApps: Boolean) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val system = remember { ContentLocale.of("", "") }
    // Мову вже вибрали — екран перестворився під неї, і майстер іде далі з країни (стан
    // rememberSaveable цього не переживав: оверлей з'являється не з першого кадру).
    val picked = remember { AppLanguage.get(context) }
    var step by rememberSaveable { mutableIntStateOf(if (picked.isBlank()) 0 else 1) }
    var language by rememberSaveable { mutableStateOf(picked.ifBlank { system.language }) }
    var country by rememberSaveable { mutableStateOf(system.country) }
    BackHandler { if (step == 0) onSkip() else step -= 1 }
    // Панель модальна: головний екран під нею ставить свій фокус уже після нас,
    // тож фокус із панелі не виходить, а крок просить його, доки не візьме.
    var inside by remember { mutableStateOf(false) }
    val hold: suspend (FocusRequester) -> Unit = { focus ->
        repeat(40) {
            if (inside) return@repeat
            runCatching { focus.requestFocus() }
            delay(50)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { inside = it.hasFocus }
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.wizard_step, step + 1, 3), style = LikhtarType.Kicker, color = LikhtarColors.AccentBright)
        when (step) {
            0 -> LanguageStep(language, hold) {
                language = it
                step = 1
                // Решта майстра — уже цією мовою: екран перестворюється, крок і вибір
                // переживають це в rememberSaveable.
                if (AppLanguage.set(context, it)) context.findActivity()?.let(AppLanguage::restart)
            }
            1 -> CountryStep(country, language, hold) {
                country = it
                step = 2
            }
            else -> AppsStep(hold) { onlyMine -> onFinish(language, country, onlyMine) }
        }
    }
}

/** Назви мов — кожна своєю мовою: людина шукає очима «Polski», а не «польська». */
internal val LANGUAGE_NAMES = mapOf("uk" to "Українська", "pl" to "Polski", "en" to "English")

@Composable
private fun LanguageStep(current: String, hold: suspend (FocusRequester) -> Unit, onPick: (String) -> Unit) {
    StepTitle(stringResource(R.string.wizard_language_title), stringResource(R.string.wizard_language_hint))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { hold(focus) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ContentLocale.SUPPORTED.forEach { code ->
            LikhtarButton(
                label = LANGUAGE_NAMES.getValue(code),
                onClick = { onPick(code) },
                modifier = Modifier.width(170.dp).then(if (code == current) Modifier.focusRequester(focus) else Modifier),
            )
        }
    }
}

/** Куди найчастіше ставлять лаунчер — одразу під системною країною, решта за абеткою. */
private val NEAR = listOf("PL", "UA", "DE", "CZ", "GB", "US")

@Composable
private fun CountryStep(current: String, language: String, hold: suspend (FocusRequester) -> Unit, onPick: (String) -> Unit) {
    StepTitle(stringResource(R.string.wizard_country_title), stringResource(R.string.wizard_country_hint))
    // Назви країн — мовою, вибраною кроком раніше; список — стандартний ISO, без мережі.
    val countries = remember(language) { countryChoices(current, Locale(language)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { hold(focus) }
    LazyColumn(modifier = Modifier.width(420.dp).height(330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(countries, key = { _, c -> c.first }) { index, (code, title) ->
            LikhtarButton(
                label = title,
                onClick = { onPick(code) },
                modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
            )
        }
    }
}

/** Пари «код → назва»: поточна, потім [NEAR], далі решта ISO за абеткою; спільне з Налаштуваннями. */
internal fun countryChoices(current: String, inLanguage: Locale): List<Pair<String, String>> {
    val name = { code: String -> Locale("", code).getDisplayCountry(inLanguage) }
    val first = (listOf(current) + NEAR).distinct()
    return first.map { it to name(it) } +
        // За абеткою мови, а не кодами символів: інакше «Єгипет» ішов перед «Австралією».
        Locale.getISOCountries().filter { it !in first }.map { it to name(it) }
            .sortedWith(compareBy(java.text.Collator.getInstance(inLanguage)) { it.second })
}

@Composable
private fun AppsStep(hold: suspend (FocusRequester) -> Unit, onDone: (onlyMyApps: Boolean) -> Unit) {
    val pm = LocalContext.current.packageManager
    val mine = remember {
        StreamingService.withPage.filter { svc -> svc.packages.any { runCatching { pm.getPackageInfo(it, 0) }.isSuccess } }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { hold(focus) }
    if (mine.isEmpty()) {
        StepTitle(stringResource(R.string.wizard_apps_title), stringResource(R.string.wizard_apps_none))
        LikhtarButton(label = stringResource(R.string.wizard_done), onClick = { onDone(false) }, primary = true, modifier = Modifier.width(200.dp).focusRequester(focus))
        return
    }
    StepTitle(
        stringResource(R.string.wizard_apps_title),
        stringResource(R.string.wizard_apps_found, mine.joinToString { it.title }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LikhtarButton(label = stringResource(R.string.wizard_all), onClick = { onDone(false) }, modifier = Modifier.width(200.dp).focusRequester(focus))
        LikhtarButton(label = stringResource(R.string.wizard_only_mine), onClick = { onDone(true) }, modifier = Modifier.width(200.dp))
    }
}

@Composable
private fun StepTitle(title: String, hint: String) {
    Text(text = title, style = LikhtarType.HeroTitle, color = LikhtarColors.TextPrimary)
    Text(text = hint, style = LikhtarType.Body2, color = LikhtarColors.TextTertiary, modifier = Modifier.width(480.dp))
}

internal tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
