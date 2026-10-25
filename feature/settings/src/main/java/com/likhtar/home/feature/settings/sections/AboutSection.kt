package com.likhtar.home.feature.settings.sections

import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.dpadScrollable
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.Note
import com.likhtar.home.feature.settings.R
import com.likhtar.home.feature.settings.ValueRow

/**
 * Сторонній компонент у APK: назва, ліцензія і файл її тексту в `assets/licenses/`.
 *
 * Список ведеться руками з `./gradlew :app:dependencies --configuration playReleaseRuntimeClasspath`
 * (21.10.2026). Дрібні модулі одного автора під однією ліцензією зведені в один рядок.
 */
internal data class Component(val name: String, val license: String, val file: String)

internal val COMPONENTS = listOf(
    Component("AndroidX, Jetpack Compose, Compose for TV", "Apache 2.0", "apache-2.0.txt"),
    Component("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache 2.0", "apache-2.0.txt"),
    Component("JetBrains Compose Multiplatform", "Apache 2.0", "apache-2.0.txt"),
    Component("Dagger / Hilt", "Apache 2.0", "apache-2.0.txt"),
    Component("Coil", "Apache 2.0", "apache-2.0.txt"),
    Component("OkHttp, Okio", "Apache 2.0", "apache-2.0.txt"),
    Component("Accompanist", "Apache 2.0", "apache-2.0.txt"),
    Component("Guava (ListenableFuture), JSpecify", "Apache 2.0", "apache-2.0.txt"),
    Component("javax.inject, jakarta.inject", "Apache 2.0", "apache-2.0.txt"),
    Component("ZXing", "Apache 2.0", "apache-2.0.txt"),
    Component("SMBJ, ASN-One", "Apache 2.0", "apache-2.0.txt"),
    Component("Bouncy Castle", "MIT", "bouncycastle.txt"),
    Component("SLF4J API", "MIT", "mit-slf4j.txt"),
    Component("MBassador", "MIT", "mit-mbassador.txt"),
    Component("Free My TV (ADB)", "MIT", "mit-free-my-tv.txt"),
    Component("JSR 305", "BSD 3-Clause", "bsd-jsr305.txt"),
    Component("Manrope", "SIL OFL 1.1", "ofl-manrope.txt"),
    Component("JetBrains Mono", "SIL OFL 1.1", "ofl-jetbrainsmono.txt"),
    Component("Oswald", "SIL OFL 1.1", "ofl-oswald.txt"),
)

/** Адреса вихідного коду. Порожня — рядок не показується; до першого поширення APK її треба заповнити (GPL, п. 6). */
internal const val SOURCE_URL = "https://github.com/Syvyj/likhtar-home"


// ─── ПРО ЗАСТОСУНОК ───────────────────────────────────────────────────
// GPL-3.0 вимагає показати ліцензію й відсутність гарантій; Apache, MIT, BSD і OFL — дати
// текст своєї ліцензії разом із програмою. Тексти лежать в APK, тут їх можна прочитати.
@Composable
internal fun AboutSection(appUpdate: com.likhtar.home.core.data.AppUpdate) {
    val context = LocalContext.current
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    GroupTitle(stringResource(R.string.settings_about_app_group))
    ValueRow(
        text = "Likhtar Home",
        value = version,
        subtitle = stringResource(R.string.settings_about_gpl),
        onClick = { shown = "gpl-3.0.txt" },
    )
    // Оновлення з GitHub — лише публічна збірка; особиста ставиться з комп'ютера.
    if (com.likhtar.home.core.model.Distribution.store) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val release by appUpdate.available.collectAsStateWithLifecycle()
        val progress by appUpdate.progress.collectAsStateWithLifecycle()
        var checking by remember { mutableStateOf(false) }
        var checked by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        ValueRow(
            text = stringResource(R.string.settings_about_update),
            value = when {
                release != null -> stringResource(R.string.settings_about_update_available, release!!.version)
                checking -> stringResource(R.string.settings_about_update_checking)
                checked -> stringResource(R.string.settings_about_update_latest)
                else -> stringResource(R.string.settings_about_update_check)
            },
            subtitle = stringResource(R.string.settings_about_update_subtitle),
            onClick = {
                if (release != null) dialog = true
                else if (!checking) scope.launch {
                    checking = true
                    dialog = appUpdate.check(force = true)
                    checking = false
                    checked = true
                }
            },
        )
        release?.takeIf { dialog }?.let { r ->
            com.likhtar.home.core.designsystem.UpdateDialog(
                version = r.version,
                notes = r.notes,
                progress = progress,
                error = error,
                onUpdate = { scope.launch { error = appUpdate.install(r) } },
                onDismiss = { dialog = false },
            )
        }
    }
    if (SOURCE_URL.isNotBlank()) {
        ValueRow(text = stringResource(R.string.settings_about_source), value = SOURCE_URL, onClick = {})
    }
    // Підтримати — три QR на сторінки оплати: з телевізора картку не введеш.
    var support by remember { mutableStateOf(false) }
    ValueRow(
        text = stringResource(R.string.settings_about_support),
        value = "",
        subtitle = stringResource(R.string.settings_about_support_subtitle),
        onClick = { support = true },
    )
    if (support) com.likhtar.home.core.designsystem.SupportDialog(onDismiss = { support = false })

    GroupTitle(stringResource(R.string.settings_about_components_group))
    COMPONENTS.forEach { c ->
        ValueRow(text = c.name, value = c.license, onClick = { shown = c.file })
    }

    GroupTitle(stringResource(R.string.settings_about_data_group))
    // Логотип TMDB поруч із текстом атрибуції — вимога умов TMDB API.
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_tmdb_logo),
        contentDescription = "TMDB",
        modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 4.dp),
    )
    Note(stringResource(R.string.settings_about_data))

    shown?.let { file -> LicenseDialog(file) { shown = null } }
}

@Composable
private fun LicenseDialog(file: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(file) {
        runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrDefault(file)
    }
    val focus = rememberInitialFocusRequester(file)
    LikhtarDialog(onDismiss = onDismiss, width = 900.dp) {
        // Стрілки гортають текст; Back закриває вікно.
        Text(
            text = text,
            style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp, lineHeight = 13.sp),
            color = LikhtarColors.TextPrimary,
            modifier = Modifier
                .heightIn(max = 480.dp)
                .focusRequester(focus)
                .dpadScrollable(rememberScrollState()),
        )
    }
}
