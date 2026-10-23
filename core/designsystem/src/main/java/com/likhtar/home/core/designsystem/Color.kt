package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Кольорові токени LIKHTAR. Значення перенесені один-в-один з HTML-прототипу,
 * акцент приведено до бренд-бірюзового #00B8D4.
 */
object LikhtarColors {
    // Графіт, а не майже чорне #0A0D0F: на ньому темні постери й кадри зливались із тлом,
    // і весь лаунчер виглядав «надто темним».
    val Background = Color(0xFF27231E)
    val BackgroundDeep = Color(0xFF1D1B17)
    val Surface = Color(0xFF302C25)
    val SurfaceAlt = Color(0xFF343029)
    val SurfaceTranslucent = Color(0xCC2A2621)
    val Border = Color(0xFF3C3730)
    val BorderStrong = Color(0xFF49433A)

    /**
     * «Скло» лаунчера: темна напівпрозора підкладка й тонка світла рамка.
     *
     * Один токен на всі екрани навмисно. Розмиття за кадром тут немає — і не тому, що
     * не вміємо: перевіряли, воно дорожче (текстура замість константи на кожен піксель
     * поверхні), а з дивана різниці не видно. Головна свідомо носить те саме скло, що
     * ТВ Ефір, «Наше» й Кінозал.
     */
    val Glass = Color(0x8A2A2621)
    val GlassBorder = Color(0x1AFFFFFF)

    /**
     * Сфокусована / обрана поверхня — тепле біле, одне на весь лаунчер: досі кнопки
     * світились холодним білим склом, а Головна й Налаштування — теплим білим, і той самий
     * стан «у фокусі» виглядав по-різному. Чорнило — нейтральне, без бірюзи старої палітри.
     */
    val GlassActive = Color(0xFFFFFAF2)
    val OnGlassActive = Color(0xFF1C1A17)
    val OnGlassActiveMuted = Color(0xB81C1A17)

    /** Рамка поверхні, яка лишається обраною, коли фокус пішов деінде. */
    val GlassActiveBorder = Color(0x8AFFFFFF)

    /** Кант сфокусованої поверхні: світло, а не колір. */
    val FocusEdge = Color(0x59FFFFFF)

    /**
     * Суцільні панелі (Налаштування, картки ТВ Ефіру) — та сама тепла гама, що й тло.
     * Колись бірюзові; світлота збережена.
     */
    val PanelFloor = Color(0xFF1A1815)
    val Panel = Color(0xFF24211C)
    val PanelRaised = Color(0xFF2E2A24)
    val PanelBorder = Color(0xFF332F28)

    /**
     * Майже чорна підкладка карток на тлі — та сама тепла гама. На рівні тла картки
     * зливались із фоном; цим кольором — картки ТВ Ефіру й плитки брендів.
     */
    val CardBlack = Color(0xFF100E0C)

    /** Те саме тепле біле фокуса, що [GlassActive] (одна поверхня — два імені в коді). */
    val WarmWhite = GlassActive
    val OnWarmWhite = OnGlassActive
    val OnWarmWhiteMuted = OnGlassActiveMuted

    /**
     * Світло ліхтаря з логотипа — медовий бурштин у віконці. Це акцент лаунчера
     * всюди; бірюзи в лаунчері
     * більше немає. Deep — те саме світло на білій сфокусованій поверхні,
     * де світлий бурштин тоне.
     */
    val LanternAmber = Color(0xFFF5BE4F)
    val LanternAmberDeep = Color(0xFFB36C0C)

    /** Колишня бірюза акценту — тепер те саме світло ліхтаря. Назву лишено: 42 місця. */
    val Cyan = LanternAmber

    /** Незворотна дія: видалити джерело, скинути PIN. */
    val Danger = Color(0xFFFF6B61)

    /**
     * Палітра прототипів (`site/proto/shared.css`, `:root`) — еталон усіх екранів.
     * Тло, глибоке тло, поверхня, світіння й текст уже мають імена вище; тут решта.
     */
    val Amber = Color(0xFFF2A93B)
    val Glow = Color(0xFFFFD27A)
    /** `--white`: тепла біла фокуса карток і пігулок прототипів. */
    val Paper = Color(0xFFF4ECDF)
    /** `--muted`: текст .62. */
    val TextDim = Color(0x9EE8EDF0)
    /** `--faint`: тиха підкладка й волосяні лінії. */
    val Faint = Color(0x1AF4ECDF)
    /** Бурштин і приглушений текст на теплій білій ([Paper]): світлі там тонуть. */
    val AmberOnPaper = Color(0xFF8A6526)
    val InkOnPaper = Color(0xFF6B6257)
    /** Тло плиток сервісів (.svc). */
    val TileFloor = Color(0xFF171513)

    /** Тепла пляма світла під карткою чи кадром (radial-gradient прототипів). */
    val Spill = Color(0xFFFFC45C)
    /** Бурштин другого рівня на теплій білій. */
    val AmberMuted = Color(0xFF6B5A3C)
    /** Чорнило бейджів і чипів поверх кадру. */
    val ChipInk = Color(0xFFF5F5F7)
    val ChipFloor = Color(0xFF121418)
    val BadgeFloor = Color(0xFF0F0D0B)
    /** Затемнення кадру: тепле майже чорне. */
    val Shade = Color(0xFF0A0908)
    val ShadeWarm = Color(0xFF12100E)
    /** Підлога каталогу під рядами, нижче тла. */
    val RailFloor = Color(0xFF1B1915)
    /** Вимкнене, помилка; на теплій білій — темніший червоний. */
    val DangerSoft = Color(0xFFFF6B6B)
    val DangerOnPaper = Color(0xFFC0392B)
    /** Застереження: замок, «потрібна дія». */
    val Warn = Color(0xFFFFD60A)

    /** Полум'я й слід ліхтаря на екрані завантаження. */
    val LanternFlame = Color(0xFFFFB540)
    val LanternTrail = Color(0xFFFFE3A6)
    /** Підлога під рядами: верх і низ градієнтів шапок. */
    val FloorTop = Color(0xFF1E1C18)
    val FloorLift = Color(0xFF23201B)
    val FloorBottom = Color(0xFF201D19)
    /** Тон сторінок франшиз і всесвітів. */
    val UniverseTone = Color(0xFF3D3830)
    val UniverseToneLow = Color(0xFF35312A)
    /** Сфокусована поверхня на темній панелі. */
    val SurfaceHigh = Color(0xFF3A352C)
    /** Тло, коли кадр не дав відтінку. */
    val TintFallback = Color(0xFF322E28)
    /** Неактивна крапка гортання. */
    val DotIdle = Color(0xFF5A5246)
    /** Тіні жанрових плиток і холодне затемнення онбордингу. */
    val ShadeDeep = Color(0xFF14120F)
    val ShadeCold = Color(0xFF060809)
    /** Тло картки «Продовжити» на Головній. */
    val ContinueFill = Color(0xFF111216)

    val Accent = LanternAmber
    val AccentBright = Color(0xFFFFD27A)
    val OnAccent = Color(0xFF2A1A05)

    val TextPrimary = Color(0xFFE8EDF0)
    val TextSecondary = Color(0xFFA8B3BA)
    // Другий і третій рівні не мають зникати на нерівному відеокадрі за текстом.
    val TextTertiary = Color(0xFF9AA6AC)
    val TextMuted = Color(0xFF718089)

    val Live = Color(0xFFEF4444)

    /**
     * Чорнило читальних сторінок (Головна, Погода, Новини): чистий білий і два
     * приглушені рівні. Жили приватними трійками в трьох файлах feature/home.
     */
    val Ink = Color(0xFFFFFFFF)
    val InkMuted = Color(0xFFCBD4DA)
    val InkFaint = Color(0xFFA9B4BB)

    /** Суцільна підкладка інформаційних блоків на тих самих сторінках. */
    val BlockFill = Color(0xFF1C1A16)

    /** Акценти плиток Головної та mock-іконок застосунків. */
    val TileCyan = Color(0xFF22D3EE)
    val TilePink = Color(0xFFF472B6)
    val TileGreen = Color(0xFF4ADE80)
    val TileGray = Color(0xFF9AA6AC)
    val TileYellow = Color(0xFFFACC15)
    val TileViolet = Color(0xFFA78BFA)
    val TileOrange = Color(0xFFFB923C)
    val TileRed = Color(0xFFF87171)
    val TileBlue = Color(0xFF60A5FA)
}
