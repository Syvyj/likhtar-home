package com.likhtar.home.core.catalog

import com.likhtar.home.core.model.UkrainianPlurals

/**
 * Група картинки у вікні озвучки, найкраща першою. Шукають насамперед 4K з Dolby
 * Vision або HDR і вже серед них — українську, тож список ділиться саме так.
 */
enum class PictureTier { DV_4K, HDR_4K, UHD, FHD, LOWER }

private fun VoiceGroup.isDolbyVision(): Boolean =
    hdr.contains("DV", ignoreCase = true) || hdr.contains("Dolby", ignoreCase = true)

fun VoiceGroup.pictureTier(): PictureTier = when {
    quality >= 2160 && isDolbyVision() -> PictureTier.DV_4K
    quality >= 2160 && hdr.isNotBlank() -> PictureTier.HDR_4K
    quality >= 2160 -> PictureTier.UHD
    quality >= 1080 -> PictureTier.FHD
    else -> PictureTier.LOWER
}

/** Озвучки по групах; усередині групи — порядок, у якому вони прийшли. Порожніх груп немає. */
fun byPicture(voices: List<VoiceGroup>): List<Pair<PictureTier, List<VoiceGroup>>> {
    val by = voices.groupBy { it.pictureTier() }
    return PictureTier.entries.mapNotNull { tier -> by[tier]?.let { tier to it } }
}

/** Значки рядка — лише те, чого не каже заголовок групи: HDR10+ поруч із DV і звук. */
fun VoiceGroup.extraChips(): List<String> {
    val first = names[refs.firstOrNull()].orEmpty()
    val sound = when {
        audio.any { it.label.contains("Atmos", ignoreCase = true) } || first.contains("Atmos", ignoreCase = true) -> "Atmos"
        "7.1" in first -> "7.1"
        "5.1" in first -> "5.1"
        else -> null
    }
    val hdr10 = "HDR10+".takeIf { pictureTier() == PictureTier.DV_4K && hdr.contains("HDR10+", ignoreCase = true) }
    return listOfNotNull(hdr10, sound)
}

/** Праворуч у рядку: скільки сайтів (збірка може підписати свої джерела інакше). */
fun VoiceGroup.rightNote(): String {
    flavorRankNote(sources, rank.values.maxOrNull())?.let { return it }
    val sites = from.size.coerceAtLeast(1)
    return "онлайн · $sites ${UkrainianPlurals.pick(sites, "сайт", "сайти", "сайтів")}"
}

/**
 * Мітка UA в рядку: мова, яку назвав сервер, полем `lang` чи прапорцем у назві варіанта.
 */
fun VoiceGroup.isUkrainian(): Boolean = lang == "ukr" || names.values.any { "🇺🇦" in it }
