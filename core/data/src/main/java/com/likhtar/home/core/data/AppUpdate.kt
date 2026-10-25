package com.likhtar.home.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.likhtar.home.core.model.Distribution
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Новіший випуск на GitHub: номер, що змінилось, і пряме посилання на APK. */
data class AppRelease(val version: String, val notes: String, val apkUrl: String)

/**
 * Оновлення лаунчера з GitHub без магазину: раз на добу питаємо `releases/latest` (один запит
 * без жодних даних про людину), і якщо там новіша версія — лаунчер пропонує оновитись. «Оновити»
 * завантажує APK у кеш і відкриває системне вікно встановлення: тихо Android ставити не дає,
 * і це правильно — останнє слово за людиною.
 *
 * Лише публічна збірка ([Distribution.store]): особиста ставиться з комп'ютера й має інший номер.
 */
@Singleton
class AppUpdate @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs get() = context.getSharedPreferences("likhtar_update", Context.MODE_PRIVATE)

    private val _available = MutableStateFlow<AppRelease?>(null)
    /** Новіша версія, якщо вона є; `null` — стоїть остання або ще не перевіряли. */
    val available: StateFlow<AppRelease?> = _available.asStateFlow()

    private val _progress = MutableStateFlow<Float?>(null)
    /** Завантаження APK, 0–1; `null` — не завантажуємо. */
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    val installed: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    init {
        // Знайдене минулого разу — одразу, без мережі: кнопка не зникає до наступної перевірки.
        prefs.getString(KEY_RELEASE, null)?.let(::decode)?.takeIf { newer(it.version, installed) }?.let { _available.value = it }
    }

    /** Перевірити, якщо минула доба (або [force]). `true` — є новіша. */
    suspend fun check(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!Distribution.store) return@withContext false
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_CHECKED, 0) < CHECK_MS) return@withContext _available.value != null
        val release = runCatching {
            http(LATEST, TIMEOUT_MS, headers = mapOf("Accept" to "application/vnd.github+json")) { c ->
                if (c.responseCode != 200) null else parse(c.text())
            }
        }.onFailure { Log.w(TAG, "перевірка оновлень", it) }.getOrNull()
        prefs.edit().putLong(KEY_CHECKED, now).apply()
        val fresh = release?.takeIf { newer(it.version, installed) }
        prefs.edit().putString(KEY_RELEASE, fresh?.let(::encode)).apply()
        _available.value = fresh
        fresh != null
    }

    /**
     * Завантажити APK і відкрити встановлення. Текст помилки або `null`, якщо вікно встановлення
     * відкрилось (чи система спершу попросила дозволу ставити застосунки).
     */
    suspend fun install(release: AppRelease): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val ask = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return@withContext runCatching { context.startActivity(ask) }.exceptionOrNull()?.message
        }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "likhtar-home.apk")
        try {
            _progress.value = 0f
            http(release.apkUrl, TIMEOUT_MS, DOWNLOAD_TIMEOUT_MS) { c ->
                if (c.responseCode != 200) error("GitHub відповів ${c.responseCode}")
                val total = c.contentLengthLong.coerceAtLeast(1)
                c.inputStream.use { input ->
                    apk.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            _progress.value = (done.toFloat() / total).coerceAtMost(1f)
                        }
                    }
                }
            }
            // Той самий застосунок і справді новіший — інакше система показала б незрозумілу помилку.
            val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: error("файл пошкоджено")
            if (info.packageName != context.packageName) error("це інший застосунок")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            null
        } catch (e: Exception) {
            Log.w(TAG, "оновлення", e)
            e.message ?: e.javaClass.simpleName
        } finally {
            _progress.value = null
        }
    }

    private fun encode(r: AppRelease) = JSONObject().put("v", r.version).put("n", r.notes).put("u", r.apkUrl).toString()

    private fun decode(raw: String) = runCatching {
        JSONObject(raw).let { AppRelease(it.getString("v"), it.optString("n"), it.getString("u")) }
    }.getOrNull()

    companion object {
        private const val TAG = "LikhtarUpdate"
        private const val LATEST = "https://api.github.com/repos/Syvyj/likhtar-home/releases/latest"
        private const val APK_NAME = "likhtar-home.apk"
        private const val CHECK_MS = 24 * 60 * 60_000L
        private const val TIMEOUT_MS = 15_000
        private const val DOWNLOAD_TIMEOUT_MS = 120_000
        private const val KEY_CHECKED = "checked_at"
        private const val KEY_RELEASE = "release"

        /** Відповідь GitHub → випуск; без APK у вкладеннях — `null`. */
        internal fun parse(json: String): AppRelease? {
            val o = JSONObject(json)
            val assets = o.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).mapNotNull(assets::optJSONObject)
                .firstOrNull { it.optString("name") == APK_NAME }?.optString("browser_download_url") ?: return null
            return AppRelease(o.optString("tag_name").removePrefix("v"), plainNotes(o.optString("body")), apk)
        }

        /** Markdown приміток — простим текстом для екрана ТБ: без зірочок, посилань і контрольної суми. */
        internal fun plainNotes(body: String): String = body.lines()
            .filterNot { it.startsWith("SHA-256") || it.startsWith("Install over") }
            .joinToString("\n")
            .replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1")
            .replace(Regex("""[*`]"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        /** «0.1.10» новіша за «0.1.9»; суфікси на кшталт «-alpha01» не враховуємо. */
        internal fun newer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
