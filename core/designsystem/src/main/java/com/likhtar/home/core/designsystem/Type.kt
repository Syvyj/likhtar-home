package com.likhtar.home.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Типографіка LIKHTAR. Розміри — піксель прототипу / 2 (див. [LikhtarDimens]).
 *
 * Три сімейства, як у прототипі: Inter — текст, Inter Tight — великі заголовки й числа
 * (щільніший малюнок тримає довгі українські слова в одному рядку), JetBrains Mono —
 * час і службові дані. Шрифти статичні, а не варіативні: варіативні осі Android застосовує
 * лише з API 26, а лаунчер збирається з minSdk 23 (Fire OS 5).
 *
 * Файли в `res/font` — підмножини (латиниця + кирилиця + пунктуація), див. `tools/build_fonts.sh`.
 */
object LikhtarType {
    // Manrope — шрифт із макета. Заведений тут, а не в
    // окремій фічі: інакше ТВ Ефір малювався б одним шрифтом, а решта лаунчера — іншим,
    // і в одному APK жили б два різні застосунки.
    val Display = FontFamily(
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
    )
    val Body = FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
    )
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    )

    /**
     * Oswald — вузький афішний гротеск для української назви під чужомовним логотипом
     * . Кіношні знаки здебільшого такі самі — вузькі й великими
     * літерами, — і назва нашою мовою стає поруч із ними як частина того ж знака, а не
     * як підпис інтерфейсу. Лише насиченість 600: більше ніде він не вживається.
     */
    val Poster = FontFamily(Font(R.font.oswald_semibold, FontWeight.SemiBold))

    val DisplayFamily = Display
    val BodyFamily = Body
    val MonoFamily = Mono

    /**
     * Моноширинні цифри. Без них годинник смикається на кожній зміні хвилини, а колонки
     * температур у прогнозі не тримають вертикаль.
     */
    private const val TabularNums = "tnum"

    /** Годинник на Головній: 172px → 86sp. */
    val Clock = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 86.sp,
        lineHeight = 79.sp,
        letterSpacing = (-4.3).sp,
        fontFeatureSettings = TabularNums,
    )

    /** Температура у віджеті погоди: 66px → 33sp. */
    val WeatherTemp = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 33.sp,
        letterSpacing = (-1.3).sp,
        fontFeatureSettings = TabularNums,
    )

    /** Температура в комірці години: 20px → 10sp. */
    val HourTemp = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        fontFeatureSettings = TabularNums,
    )

    /** Перша літера застосунку, коли графіки немає: 40px → 20sp. */
    val AppLetter = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )

    /** Заголовок групи в сітці застосунків: mono 15px → 7.5sp, трекінг 0.18em. */
    val SectionMono = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 7.5.sp,
        letterSpacing = 1.35.sp,
    )

    /** Текст підказки в пігулці: 19px → 9.5sp. */
    val HintText = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp,
    )

    /** Підпис під плиткою застосунку: 20px → 10sp, вага 500. */
    val AppLabel = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = (-0.1).sp,
    )

    /** Назва розділу в доці Головної: 23px → 11.5sp. */
    val TileTitle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = (-0.2).sp,
    )

    /** Службовий підпис у комірці години (відсоток опадів). */
    val MonoTiny = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        fontFeatureSettings = TabularNums,
    )

    /** Температура: 76px → 38sp. */
    val Temperature = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = TabularNums,
    )

    /** Заголовок hero: 60px → 30sp. */
    val HeroTitle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.9).sp,
    )

    /** Заголовок сторінки: 44px → 22sp. */
    val PageTitle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.4).sp,
    )

    /** Логотип у верхньому меню: 24px → 12sp. */
    val Brand = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    )

    /** Назва на плитці / картці: читабельна з дивана, але не перетворює ряд на таблицю. */
    val CardTitle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        letterSpacing = (-0.3).sp,
    )

    /** Пункт верхнього меню: не дрібніший за службовий текст на TV. */
    val MenuItem = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
    )

    /** Заголовок секції: короткий, але має читатись без наближення до екрана. */
    val SectionTitle = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.6.sp,
    )

    /** Kicker над hero: службовий рівень, але не мікротекст. */
    val Kicker = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 1.1.sp,
    )

    /** Привітання й дата на Головній: 24px → 12sp. Поруч із годинником 75sp менше не читається. */
    val Lead = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.12).sp,
    )

    /** Основний текст для TV: 12sp — мінімум для синопсису з дистанції перегляду. */
    /**
     * Крупне речення-тизер на сцені матеріалу.
     *
     * Синопсис на 12sp читався як примітка під метаданими, і сцена не мала жодного тексту,
     * який тримає погляд поруч із логотипом. 17sp — те, на чому фраза стає другим за вагою
     * елементом після знака, не починаючи з ним змагатись.
     */
    val TitleLead = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    val Body1 = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

    /** Другорядний текст: не стає «сірим шумом» на 1080p телевізорі. */
    val Body2 = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )

    /** Мітка на кнопці / у рядку. */
    val Label = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
    )

    /** Числа в рядку тексту (прогноз, метадані) — Inter із табличними цифрами. */
    val LabelNumeric = Label.copy(fontFeatureSettings = TabularNums)

    /** Час і службові дані: найменший читабельний рівень TV-інтерфейсу. */
    val MonoSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 8.5.sp,
        letterSpacing = 0.sp,
    )
}

/**
 * Текст, який лежить просто на шпалерах, без власної підкладки.
 *
 * На «Застосунках» scrim навмисно лишає фото видимим (профіль `APPS`, у середній смузі
 * лише 50% затемнення) — і підписи плиток там світлі на світлому: на нічному фото вони
 * читаються, на денному зникають. Підкладка під кожним підписом убила б сам сенс
 * видимих шпалер, тому текст носить власну тінь: вона працює на будь-якому кадрі й
 * нічого не додає до розмітки.
 *
 * Застосовується як `LikhtarType.AppLabel.onWallpaper()`.
 */
fun TextStyle.onWallpaper(): TextStyle = copy(shadow = WallpaperInkShadow)

private val WallpaperInkShadow = Shadow(
    color = Color.Black.copy(alpha = 0.8f),
    offset = Offset(0f, 1f),
    blurRadius = 4f,
)
