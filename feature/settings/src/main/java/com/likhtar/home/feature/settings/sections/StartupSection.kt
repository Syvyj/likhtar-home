package com.likhtar.home.feature.settings.sections

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.data.AppLanguage
import com.likhtar.home.core.data.ContentLocale
import com.likhtar.home.core.data.locale
import com.likhtar.home.feature.settings.LANGUAGE_NAMES
import com.likhtar.home.feature.settings.R
import com.likhtar.home.feature.settings.SingleChoiceDialog
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.countryChoices
import com.likhtar.home.feature.settings.findActivity
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.HomeLauncherOnboarding
import com.likhtar.home.feature.settings.launchFirstAvailable
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.CountryPickerDialog
import com.likhtar.home.feature.settings.ActionRow

// ─── ЗАПУСК ───────────────────────────────────────────────────────────
// «Кнопка HOME» і «розділ при старті» — одне питання «що я бачу, коли
// вмикаю телевізор», тому стоять поруч.
@Composable
internal fun StartupSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    RemoteKeysGroup(viewModel)
}

/**
 * Що відбувається, коли вмикаєш телевізор: хто на кнопці HOME, з якого екрана починати,
 * чи відкриватися після ввімкнення. Це поведінка Головної — тож і стоїть у «Головній».
 */
@Composable
internal fun HomeLaunchGroup(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    GroupTitle(stringResource(R.string.settings_startup_group))
    HomeButtonRow(viewModel)
    var startOpen by remember { mutableStateOf(false) }
    ValueRow(
        text = stringResource(R.string.settings_startup_section),
        value = stringResource(state.settings.startSection.titleRes),
        onClick = { startOpen = true },
    )
    if (startOpen) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_startup_section),
            options = START_SECTIONS.map { it.name to stringResource(it.titleRes) },
            selected = state.settings.startSection.name,
            onPick = { name -> startOpen = false; START_SECTIONS.firstOrNull { it.name == name }?.let(viewModel::setStartSection) },
            onDismiss = { startOpen = false },
        )
    }

    val repository = viewModel.homeLauncherRepository
    val activityContext = LocalContext.current
    val allowed = repository.canAutostart()
    ActionRow(
        text = stringResource(R.string.settings_autostart),
        subtitle = stringResource(if (allowed) R.string.settings_autostart_granted else R.string.settings_autostart_needed),
        action = stringResource(if (allowed) R.string.settings_check else R.string.settings_autostart_grant),
        onClick = { launchFirstAvailable(activityContext, repository.autostartIntents()) },
    )
}

/**
 * Мова інтерфейсу й країна контенту — те саме, що питає майстер першого запуску. Мова
 * інтерфейсу тягне за собою мову контенту; країна мову не чіпає.
 */
@Composable
internal fun LocaleRows(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf<String?>(null) }
    val language = AppLanguage.get(context).ifBlank { state.settings.locale.language }
    val country = state.settings.locale.country
    GroupTitle(stringResource(R.string.settings_locale_group))
    ValueRow(
        text = stringResource(R.string.settings_language),
        value = LANGUAGE_NAMES[language].orEmpty(),
        onClick = { picker = LANGUAGE },
    )
    ValueRow(
        text = stringResource(R.string.settings_country),
        value = Locale("", country).getDisplayCountry(LocalConfiguration.current.locales[0]),
        subtitle = stringResource(R.string.settings_country_subtitle),
        onClick = { picker = COUNTRY },
    )
    // «Доступно в» — теж про країну: де шукати, на яких сервісах тайтл можна подивитись.
    ValueRow(
        text = stringResource(R.string.settings_watch_availability),
        value = viewModel.availabilityCountriesLabel(state.settings.availabilityCountries),
        onClick = { picker = AVAILABILITY },
        subtitle = stringResource(R.string.settings_watch_availability_subtitle),
    )
    when (picker) {
        LANGUAGE -> SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = ContentLocale.SUPPORTED.map { it to LANGUAGE_NAMES.getValue(it) },
            selected = language,
            onPick = { code ->
                picker = null
                if (AppLanguage.set(context, code)) {
                    viewModel.setContentLanguage(code) { context.findActivity()?.let(AppLanguage::restart) }
                }
            },
            onDismiss = { picker = null },
        )
        COUNTRY -> SingleChoiceDialog(
            title = stringResource(R.string.settings_country),
            options = remember(country) { countryChoices(country, Locale.getDefault()) },
            selected = country,
            onPick = { code ->
                picker = null
                viewModel.setContentCountry(code)
            },
            onDismiss = { picker = null },
        )
        AVAILABILITY -> CountryPickerDialog(
            title = stringResource(R.string.settings_availability_for),
            countries = state.tvCountries,
            selected = state.settings.availabilityCountries,
            onToggle = viewModel::toggleAvailabilityCountry,
            autoLabel = stringResource(R.string.settings_availability_auto),
            onAuto = viewModel::clearAvailabilityCountries,
            onDismiss = { picker = null },
        )
    }
}

private const val LANGUAGE = "language"
private const val COUNTRY = "country"
private const val AVAILABILITY = "availability"

private val START_SECTIONS = listOf(
    LikhtarSection.HOME,
    LikhtarSection.CINEMA,
    LikhtarSection.LIVE_TV,
)

/**
 * Кнопка HOME одним рядком: повний блок (як зробити Ліхтар домашнім, як повернути стоковий)
 * відкривається бічною панеллю. На пів сторінки він стояв завжди, хоча потрібен раз.
 */
@Composable
private fun HomeButtonRow(viewModel: SettingsViewModel) {
    val repository = viewModel.homeLauncherRepository
    var status by remember { mutableStateOf(repository.status()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        status = repository.status()
        onPauseOrDispose { }
    }
    var open by remember { mutableStateOf(false) }
    ValueRow(
        text = stringResource(R.string.settings_home_button),
        value = if (status.isDefault) "Likhtar" else status.currentLabel ?: stringResource(R.string.settings_not_set),
        // Android не дає застосунку нічого повернути, коли його видаляють, — це попередження
        // мусить бути видно без відкривання панелі.
        subtitle = stringResource(R.string.settings_tv_takeover_uninstall_warning).takeIf { status.isDefault && repository.takeover.isActive },
        onClick = { open = true },
    )
    if (open) {
        val close = {
            open = false
            status = repository.status()
        }
        com.likhtar.home.core.designsystem.LikhtarDialog(onDismiss = close, width = 560.dp, side = true) {
            androidx.compose.foundation.layout.Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) { HomeLauncherOnboarding(repository = repository, onDismiss = close) }
        }
    }
}
