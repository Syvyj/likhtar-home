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
import androidx.compose.ui.platform.LocalContext
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.HomeLauncherOnboarding
import com.likhtar.home.feature.settings.launchFirstAvailable
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ChoiceRow
import com.likhtar.home.feature.settings.ActionRow

// ─── ЗАПУСК ───────────────────────────────────────────────────────────
// «Кнопка HOME» і «розділ при старті» — одне питання «що я бачу, коли
// вмикаю телевізор», тому стоять поруч.
@Composable
internal fun StartupSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    LocaleRows(state, viewModel)

    // Без «Пізніше»: у налаштування приходять свідомо, а не при першому запуску.
    HomeLauncherOnboarding(repository = viewModel.homeLauncherRepository)
    RemoteKeysGroup(viewModel)

    GroupTitle(stringResource(R.string.settings_startup_group))
    START_SECTIONS.forEach { section ->
        ChoiceRow(
            text = stringResource(section.titleRes),
            selected = state.settings.startSection == section,
            onSelect = { viewModel.setStartSection(section) },
        )
    }

    GroupTitle(stringResource(R.string.settings_autostart_group))
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
private fun LocaleRows(state: SettingsUiState, viewModel: SettingsViewModel) {
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
    }
}

private const val LANGUAGE = "language"
private const val COUNTRY = "country"

private val START_SECTIONS = listOf(
    LikhtarSection.HOME,
    LikhtarSection.CINEMA,
    LikhtarSection.LIVE_TV,
)
