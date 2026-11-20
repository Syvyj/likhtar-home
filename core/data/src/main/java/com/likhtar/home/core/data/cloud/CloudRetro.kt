package com.likhtar.home.core.data.cloud

import com.likhtar.home.core.data.RetroGame

/*
 * Ретро — четверте джерело в тих самих списках: гра з образу живе
 * поруч із хмарними, і OK запускає кожну там, де вона є. З хмарною не зводиться навіть за тією ж
 * назвою — Contra з NES не Contra: Operation Galuga.
 */

private const val ROM_KEY = "rom:"

/** Образ як картка каталогу: ключ — за шляхом, жанр — «retro», тло — кадр із гри. */
fun RetroGame.toCloudGame(): CloudGame = CloudGame(
    key = ROM_KEY + path,
    title = title,
    sources = emptyMap(),
    stores = emptyList(),
    genres = listOf("retro"),
    free = false,
    coop = "",
    popularity = 0,
    pad = "full",
    desc = "",
    dev = "",
    pub = "",
    year = "",
    shots = listOfNotNull(snap),
    trailer = "",
    cover = cover,
    cover2 = null,
    hero = snap,
    rom = CloudRom(path, system.title),
)

/**
 * Образ зі списку, якого вже немає (витягли флешку): картка лишається тьмяною з назвою файлу, а не
 * зникає мовчки. Не образ — `null`.
 */
fun missingRom(key: String): CloudGame? {
    if (!key.startsWith(ROM_KEY)) return null
    val path = key.removePrefix(ROM_KEY)
    return CloudGame(
        key = key, title = path.substringAfterLast('/').substringBeforeLast('.'), sources = emptyMap(), stores = emptyList(),
        genres = listOf("retro"), free = false, coop = "", popularity = 0, pad = "", desc = "", dev = "", pub = "", year = "",
        shots = emptyList(), trailer = "", cover = null, cover2 = null, hero = null, rom = CloudRom(path, "", missing = true),
    )
}
