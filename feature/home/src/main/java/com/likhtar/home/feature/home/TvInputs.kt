package com.likhtar.home.feature.home

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/** Вхід телевізора (HDMI тощо) для ряду «Пристрої» Головної. */
data class TvInputTile(val id: String, val name: String, val port: String, val kind: Kind) {
    enum class Kind { GAME, PLAYER, OTHER }
}

/**
 * Входи телевізора з Android: HDMI-порти й пристрої, що представились по CEC («PlayStation 5»).
 * На приставці входів немає — порожньо, і ряду теж немає.
 */
internal object TvInputs {
    fun list(context: Context): List<TvInputTile> = fake(context) ?: runCatching {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val all = tim.tvInputList.filter { it.isPassthroughInput && !it.isHidden(context) }
        // Порт, за яким стоїть CEC-пристрій, показуємо пристроєм, а не «HDMI 2».
        val parents = all.mapNotNull { it.parentId }.toSet()
        all.filter { it.id !in parents }
            .filter { tim.getInputState(it.id) != TvInputManager.INPUT_STATE_DISCONNECTED }
            .map { info ->
                val label = label(context, info)
                val port = info.parentId?.let { p -> all.firstOrNull { it.id == p } }?.let { label(context, it) }.orEmpty()
                TvInputTile(info.id, label, port.takeIf { it != label }.orEmpty(), kindOf(label))
            }
    }.onFailure { Log.w("LikhtarInputs", "входи ТБ: ${it.javaClass.simpleName}") }.getOrDefault(emptyList())

    /**
     * Лише debug-збірка: емулятор HDMI-входів не має, тож для перевірки ряду —
     * `settings put global likhtar_fake_inputs "PlayStation 5|HDMI 1,Apple TV|HDMI 2,HDMI 3"`.
     */
    private fun fake(context: Context): List<TvInputTile>? {
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val raw = android.provider.Settings.Global.getString(context.contentResolver, "likhtar_fake_inputs") ?: return null
        return raw.split(',').filter { it.isNotBlank() }.mapIndexed { i, s ->
            val name = s.substringBefore('|').trim()
            TvInputTile("fake$i", name, s.substringAfter('|', "").trim(), kindOf(name))
        }
    }

    private fun label(context: Context, info: TvInputInfo): String =
        info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() } ?: info.loadLabel(context).toString()

    /** Перемикання: вхід відкриває системний ТБ-застосунок. */
    fun open(context: Context, input: TvInputTile): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    private val games = Regex("playstation|\\bps[345]\\b|xbox|nintendo|switch|steam|valve", RegexOption.IGNORE_CASE)
    private val players = Regex("apple ?tv|chromecast|shield|fire ?tv|roku|google tv|android tv|blu-?ray|mi box|set.?top", RegexOption.IGNORE_CASE)

    /** ponytail: іконка за CEC-іменем; вендорських кодів не розбираємо, доки імен вистачає. */
    fun kindOf(name: String): TvInputTile.Kind = when {
        games.containsMatchIn(name) -> TvInputTile.Kind.GAME
        players.containsMatchIn(name) -> TvInputTile.Kind.PLAYER
        else -> TvInputTile.Kind.OTHER
    }
}
