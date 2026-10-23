package com.likhtar.home.core.catalog

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Чи тягне пристрій Dolby Vision роздач: профіль 8 (dvhe.08 — HEVC з HDR10-основою, ним
 * ідуть майже всі 4K WEB-DL) у 4K24. Перевіряємо саме профіль і рівень, а не «є декодер
 * DV»: на деяких приставках декодери DV є, але dvhe.08.06 вони не беруть (лог Just+:
 * «NoSupport dvhe.08.06»), і 4K DV «Людини-павука» показував темний екран.
 *
 * Не тягне — DV-роздачі йдуть у кінець (bestFirstKey) і сервер теж ставить їх останніми
 * (`nodv=1` у /api/play). Непідтримуваний профіль пропускаємо.
 */
object DolbyVision {
    val supported: Boolean by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION in it.supportedTypes }
                .any { info ->
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION).profileLevels.any {
                        it.profile == CodecProfileLevel.DolbyVisionProfileDvheSt &&
                            it.level >= CodecProfileLevel.DolbyVisionLevelUhd24
                    }
                }
        }.getOrDefault(false).also { android.util.Log.i("LikhtarDV", "Dolby Vision профіль 8 у 4K: $it") }
    }
}
