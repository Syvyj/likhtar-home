package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlin.math.pow

// Кольори каналів — спільні для обох ТВ Ефірів: канал має один колір скрізь.

data class BadgeStyle(
    val label: String,
    val color: Color,
    val fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    val transparentOnFocus: Boolean = false,
)

fun channelBadge(title: String): BadgeStyle? = when {
    title.startsWith("1+1") -> BadgeStyle("1+1", Color(0xFF23313A), 16.sp, transparentOnFocus = true)
    title.startsWith("2+2") -> BadgeStyle("2+2", Color(0xFF526166), 14.sp)
    title.startsWith("ТЕТ") -> BadgeStyle("ТЕТ", Color(0xFFE05210))
    title.startsWith("Новий") -> BadgeStyle("Н", Color(0xFF4A7B0A), 15.sp)
    title.startsWith("СТБ") -> BadgeStyle("СТБ", Color(0xFFC91C32))
    title.startsWith("ICTV") -> BadgeStyle("ICTV²", Color(0xFFF04A0A), 10.sp)
    title.startsWith("Food") -> BadgeStyle("FOOD", Color(0xFF238E87), 9.sp)
    title.startsWith("Кус-кус") -> BadgeStyle("КУС", Color(0xFFC91C32), 10.sp)
    title.startsWith("Сіль") -> BadgeStyle("СІЛЬ", Color(0xFF247E80), 9.sp)
    title.startsWith("Travel") -> BadgeStyle("T&F", Color(0xFFE05210), 10.sp)
    title.startsWith("Kitchen") -> BadgeStyle("KTV", Color(0xFFE05210), 10.sp)
    else -> null
}


/**
 * Колір групи каналів.
 *
 * За хешем назви, а не за номером у списку: категорії приходять із даних, і жорсткого
 * переліку в коді бути не має (те саме правило, що з мовниками). Хеш кращий за індекс
 * тим, що додана або прибрана категорія не перефарбовує решту — колір тримається за
 * назвою, а не за позицією.
 *
 * Палітра підібрана під наш teal-ґрунт: усі кольори однакової світлості й насиченості,
 * тож жоден не б'є сусіда й не тягне на себе більше уваги, ніж інші.
 */
fun categoryColor(title: String): Color {
    // Відтінок із хеша по ВСЬОМУ колу, а не вибір із шести заготовок. Шести не вистачало
    // фізично: у «Українському ТБ» 11 каналів, тобто збіги були неминучі за принципом
    // Діріхле, та ще й три із шести заготовок були синюватими — через це K1 і K2 виходили
    // майже однакові, і тло під них перетікало на одиниці RGB (заміряно 13.09.2026:
    // K1 (22,65,80), K2 (13,68,78)).
    //
    // Смугу навколо бірюзи ґрунту пропускаємо: канал такого кольору зливався б із тлом,
    // а тло, перетікаючи в нього, не мінялося б узагалі.
    val hue = (HueSkipEnd + (title.hashCode().toUInt() % HueSpan.toUInt()).toInt()) % 360
    return Color.hsv(hue.toFloat(), CategorySaturation, CategoryValue)
}

/** Кінець забороненої смуги — звідси починається дозволене коло. */
private const val HueSkipEnd = 210

/** Скільки градусів кола дозволено: 360 мінус смуга бірюзи ґрунту (170°–210°). */
private const val HueSpan = 320

// Насиченість і світлість однакові для всіх: жоден колір не має тягнути на себе більше
// уваги, ніж сусід, — це правило лишилось із палітри шести заготовок.
private const val CategorySaturation = .62f
private const val CategoryValue = .92f

/**
 * Темний чи світлий текст на кольоровому тлі — за формулою, а не на око.
 *
 * Відколи колір береться з хеша по ВСЬОМУ колу відтінків, рано чи пізно випадає будь-яка
 * яскравість: на кадрі 13.09.2026 було біле по яскраво-жовтому («Viasat Serial»), біле по
 * помаранчевому («True Crime EU») і біле по світло-жовтому («ТВІЙ СЕРІАЛ»). Підбирати
 * винятки безглуздо — їх стільки ж, скільки каналів.
 *
 * Рахуємо відносну яскравість за WCAG: sRGB → лінеаризація → 0.2126R + 0.7152G + 0.0722B.
 * Темний беремо з палітри (`Ink`), а не чистий чорний: чорного в лаунчері немає ніде.
 */
fun onColor(background: Color): Color =
    if (relativeLuminance(background) > ContrastThreshold) LikhtarColors.OnWarmWhite else Color.White

fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return .2126f * channel(color.red) + .7152f * channel(color.green) + .0722f * channel(color.blue)
}

/** Поріг підібраний під наші кольори: нижче — світлий текст, вище — темний. */
private const val ContrastThreshold = .4f


/** Низ кадру картки каналу в сітці ТВ Ефіру: тепла майже чорна тінь під підписом (.85). */
val ChannelCardScrim = Color(0xD90A0908)
