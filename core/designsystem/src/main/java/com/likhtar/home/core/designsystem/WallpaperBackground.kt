package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.model.BackdropSpec

/**
 * Профіль затемнення: у кожного компонування текст лежить в іншому місці кадру,
 * тому й лишати відкритим треба різні його частини.
 */
enum class WallpaperScrim {
    /** Головна: шпалери мають лишатися світлими й чистими, без загальної накладки. */
    NONE,

    /** Головна: годинник ліворуч, плитки внизу — середина кадру лишається чистою. */
    HOME,

    /** Кінозал: hero ліворуч, рядки карток унизу, меню зверху — середина кадру відкрита. */
    HERO,

    /**
     * Щільні екрани (налаштування, пошук, телепрограма): списки й сітки займають увесь
     * кадр, тому фото лишається тільки натяком — інакше текст на ньому не читається,
     * скільки локальних підкладок не додавай.
     */
    CONTENT,

    /**
     * Застосунки: фото навмисно лишається видимим, бо плитки — скляні, і крізь них має
     * просвічувати саме воно. Вінетки й світла колонка зліва — з макета «Застосунки —
     * редизайн».
     */
    APPS,
}

/**
 * Фон екрана: шпалери (зображення або процедурний фон) під обов'язковим scrim'ом.
 *
 * Scrim тут не декорація, а умова читабельності: світле фото інакше вбиває контраст годинника,
 * погоди й плиток. Тому він накладається завжди — але тримаємо його мінімальним, бо сенс
 * шпалер у тому, щоб їх було видно. [dimPercent] — ручка користувача поверх нього, і
 * навігація дає її лише Головній: у решти розділів свій scrim під власний макет.
 *
 * [imageUri] і [backdrop] взаємовиключні; якщо немає нічого, малюємо штатний темний градієнт.
 * [attribution] показуємо тут, а не на екранах: умови джерел зображень вимагають підпису
 * скрізь, де видно саме зображення.
 *
 * [scrim] = null означає «екран малює свій непрозорий фон сам» (каталоги): тоді не малюємо
 * нічого. Заливка, градієнт і scrim під чужим непрозорим фоном — три невидимі повноекранні
 * проходи GPU щокадру, а екран усе одно перекриває їх повністю.
 */
@Composable
fun WallpaperBackground(
    modifier: Modifier = Modifier,
    imageUri: String? = null,
    backdrop: BackdropSpec? = null,
    dimPercent: Int = 30,
    scrim: WallpaperScrim? = WallpaperScrim.HOME,
    attribution: String? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().let { if (scrim == null) it else it.background(LikhtarColors.Background) }) {
        if (scrim != null) {
            when {
                imageUri != null -> AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                backdrop != null -> ProceduralBackdrop(backdrop)

                else -> Box(modifier = Modifier.fillMaxSize().background(DefaultBackdrop))
            }

            // Вибіркові scrim'и замість одного суцільного: затемнюємо саме ті смуги, де лежить
            // текст, а решту кадру лишаємо видимою — інакше сенс шпалер зникає.
            if (scrim != WallpaperScrim.NONE || dimPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .cachedScrim(
                            dim = LikhtarColors.BackgroundDeep.copy(
                                alpha = (dimPercent.coerceIn(0, 60)) / 100f,
                            ),
                            // Профіль визначає вміст текстури, тому він і є ключем.
                            scrim,
                        ) { size ->
                            when (scrim) {
                                WallpaperScrim.NONE -> emptyList()
                                WallpaperScrim.HOME -> listOf(HomeVignette, cornerVignette(size))
                                WallpaperScrim.HERO -> listOf(HeroSideScrim, HeroVerticalScrim)
                                WallpaperScrim.CONTENT -> listOf(ContentScrim, ContentVerticalScrim)
                                WallpaperScrim.APPS -> listOf(AppsVignette, appsCorner(size), AppsLeftColumn)
                            }
                        },
                )
            }
        }

        content()

        // Підпис мусить бути, але не мусить читатися здалеку: це юридична вимога джерела,
        // а не частина інтерфейсу. Тому найдрібніший моно-стиль і напівпрозорий приглушений
        // колір — розбірливо зблизька, невидимо з дивана.
        attribution?.let {
            Text(
                text = it,
                style = LikhtarType.MonoTiny,
                color = LikhtarColors.TextMuted.copy(alpha = ATTRIBUTION_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LikhtarDimens.ScreenPadding, bottom = 6.dp),
            )
        }
    }
}

/**
 * Кадр контенту як фон екрана: постер-бекдроп під тим самим scrim'ом, що й шпалери.
 *
 * Кінозал-каталог показує кадр із фільму, на якому стоїть фокус, і саме він замінює шпалери,
 * поки користувач ходить по рядках. Scrim той самий (`HERO`), тому текст лишається читабельним
 * на будь-якому кадрі — від чорного нуару до засніженого пейзажу.
 */
/** Різні екрани потребують різної щільності затемнення того самого hero-кадру. */
enum class HeroBackdropTone {
    /** Каталог: щільніша ліва колонка, бо за нею одночасно стоять назва й опис. */
    Catalog,

    /** Деталі матеріалу: кадр має лишатись видимим, темні лише зони для тексту та низу. */
    Detail,

}

@Composable
fun HeroBackdrop(
    imageUri: String?,
    modifier: Modifier = Modifier,
    tone: HeroBackdropTone = HeroBackdropTone.Catalog,
    /** Два й більше кадрів — живий фон ([LiveStills]) замість одного [imageUri]. */
    stills: List<String> = emptyList(),
    /** −1…1: зсув живого фону проти фокуса (див. [LiveStills]). */
    parallax: () -> Float = { 0f },
) {
    Box(modifier = modifier.fillMaxSize().background(LikhtarColors.Background)) {
        if (stills.size > 1) {
            LiveStills(stills, Modifier.fillMaxSize(), parallax)
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Центр по горизонталі, зсув до верху по вертикалі — див. [FaceSafeCrop].
                // Кадр проєкту Телепорталу (2,6:1) — люди праворуч, темне поле ліворуч під
                // текст: тримаємо правий край, щоб обрізалась порожнеча, а не люди.
                alignment = if (imageUri?.contains("portal-img") == true) Alignment.CenterEnd else FaceSafeCrop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cachedScrim(dim = Color.Transparent, tone) {
                    when (tone) {
                        HeroBackdropTone.Catalog -> listOf(HeroSideScrim, HeroVerticalScrim)
                        HeroBackdropTone.Detail -> listOf(HeroDetailSideScrim, HeroDetailVerticalScrim)
                    }
                },
        )
    }
}

/**
 * Три градієнти scrim'а — в одну текстуру, яка перемальовується лише при зміні розміру.
 *
 * Заміряно на приставці (Google TV, 1080p): три градієнти на весь екран коштували
 * ~35 мс GPU на кадр — це 15 fps на порожній Головній, тоді як наш власний код рахував кадр
 * за 6 мс. Шейдер градієнта обчислюється для кожного пікселя щокадру, і три таких шари
 * заливали 6 мегапікселів. Готова текстура — це одна копія, і те саме зображення.
 */
@Composable
fun Modifier.cachedScrim(dim: Color, vararg keys: Any?, brushes: (Size) -> List<Brush>): Modifier {
    // Запам'ятовуємо ВЕСЬ модифікатор, а не лише пензлі.
    //
    // Причина заміряна: `drawWithCache` перепікає текстуру, коли змінюється сам блок, а
    // лямбда пензлів створюється заново на кожну рекомпозицію. На Кінозалі, де рядки
    // рекомпонуються від кожного руху фокуса, це давало перепікання щокадру — запис draw
    // 7.8 → 15.5 мс, тобто гірше за живий шейдер. Ключі описують вміст текстури: поки
    // вони ті самі, модифікатор той самий і текстура лежить у кеші.
    val cached = remember(*keys) { scrimModifier(brushes) }
    // Рівномірне затемнення НЕ печемо в текстуру, а малюємо окремою заливкою.
    //
    // Заливка одним кольором для GPU безкоштовна (це не шейдер градієнта), зате текстуру
    // не треба перепікати від кожного руху повзунка — і, головне, `dim` перестає бути
    // ключем кеша. Поки він ним був, [keys] на місцях виклику ніхто не передавав, і
    // текстура від чужого профілю лишалась на екрані: Головна малювалась щільним scrim'ом
    // Налаштувань, поки процес не перезапустять.
    val dimmed = if (dim.alpha > 0f) Modifier.drawBehind { drawRect(dim) } else Modifier
    return this.then(cached).then(dimmed)
}

private fun scrimModifier(brushes: (Size) -> List<Brush>): Modifier =
    Modifier.drawWithCache {
        // Текстуру печемо ВЧЕТВЕРО меншою за екран і розтягуємо при малюванні.
        //
        // Причина з приставки: панель віддає 3840×2160, і текстура в повний розмір — це
        // 33 МБ та сплеск до 360 мс на першому кадрі (заміряно framestats). Градієнт
        // гладкий за визначенням, тому на око різниці немає, а пам'ять і хітч — на порядок
        // менші. Дрібніше робити не варто: на пологих переходах з'являються смуги.
        val width = (size.width / SCRIM_DOWNSCALE).toInt().coerceAtLeast(1)
        val height = (size.height / SCRIM_DOWNSCALE).toInt().coerceAtLeast(1)
        val layer = ImageBitmap(width, height)
        val layerSize = Size(width.toFloat(), height.toFloat())

        CanvasDrawScope().draw(
            density = this,
            layoutDirection = layoutDirection,
            canvas = Canvas(layer),
            size = layerSize,
        ) {
            // Пензлі беруться від розміру: кутова вінетка Головної задана у відсотках кадру,
            // а не в пікселях, тому статичним Brush її не описати.
            brushes(layerSize).forEach { drawRect(it) }
        }

        onDrawBehind {
            drawImage(
                image = layer,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(width, height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
    }


/** Ділник розміру текстури scrim'а. Чотири — межа, за якою на градієнтах видно смуги. */
private const val SCRIM_DOWNSCALE = 4f

/**
 * Малюємо базовий градієнт і поверх нього світлові плями радіальними градієнтами.
 * Кожна пляма гасне до прозорого, тому вони природно змішуються між собою.
 */
@Composable
private fun ProceduralBackdrop(spec: BackdropSpec) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val base = spec.baseGradient.map { Color(it) }
        if (base.size >= 2) {
            drawRect(brush = Brush.verticalGradient(base))
        } else {
            drawRect(color = base.firstOrNull() ?: LikhtarColors.Background)
        }

        val shorterSide = minOf(size.width, size.height)
        spec.blobs.forEach { blob ->
            val center = Offset(blob.centerX * size.width, blob.centerY * size.height)
            val radius = blob.radius * shorterSide
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(blob.color).copy(alpha = blob.alpha),
                        Color(blob.color).copy(alpha = 0f),
                    ),
                    center = center,
                    radius = radius,
                ),
            )
        }
    }
}

/**
 * Головна: вертикальна вінетка по всьому кадру.
 *
 * Затемнюємо верх (під годинником) і низ (під доком), а середину лишаємо майже чистою —
 * саме там видно фото. Це заміна суцільній підкладці: чорний прямокутник під годинником
 * замовник відхилив окремо.
 */
private val HomeVignette = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.25f),
    0.34f to Color.Black.copy(alpha = 0.06f),
    0.62f to Color.Black.copy(alpha = 0.09f),
    1.00f to Color.Black.copy(alpha = 0.54f),
)

/**
 * Головна: кутова вінетка під годинником — еліпс 120 % × 80 % кадру з центром у (12 %, 18 %).
 *
 * `Brush.radialGradient` кола не розтягує, тому беремо радіус за більшою піввіссю: різниця
 * читається як трохи ширше затемнення, а не як інша фігура.
 */
private fun cornerVignette(size: Size) = Brush.radialGradient(
    0.00f to Color.Black.copy(alpha = 0.3f),
    0.58f to Color.Black.copy(alpha = 0f),
    center = Offset(size.width * 0.12f, size.height * 0.18f),
    radius = maxOf(size.width * 1.2f, size.height * 0.8f) / 2f,
)

/**
 * Кінозал: широка ліва колонка під hero. Стопи взято з `.cinema-bg:after` прототипу —
 * зліва суцільний фон, до 74 % ширини він повністю розчиняється.
 */
private val HeroSideScrim = Brush.horizontalGradient(
    0.02f to LikhtarColors.Background,
    0.38f to LikhtarColors.Background.copy(alpha = 0.72f),
    0.74f to LikhtarColors.Background.copy(alpha = 0f),
)

/** Кінозал: підкладка під верхнім меню й під нижніми рядками карток. */
private val HeroVerticalScrim = Brush.verticalGradient(
    0.0f to LikhtarColors.Background.copy(alpha = 0.6f),
    0.22f to LikhtarColors.Background.copy(alpha = 0f),
    0.58f to LikhtarColors.Background.copy(alpha = 0.72f),
    0.88f to LikhtarColors.Background,
)

/**
 * Деталі фільму/серіалу: не фарбуємо весь кадр в чорний. Ліва полиця гарантує
 * контраст для дій та опису, але центр і праворуч залишаються власне кадром.
 */
// .scrim сторінки тайтла (прототип): 90deg rgba(29,27,23) .94 → .78 на 34 % → .15 на 62 % → 0 на 80 %.
private val HeroDetailSideScrim = Brush.horizontalGradient(
    0.00f to LikhtarColors.BackgroundDeep.copy(alpha = 0.94f),
    0.34f to LikhtarColors.BackgroundDeep.copy(alpha = 0.78f),
    0.62f to LikhtarColors.BackgroundDeep.copy(alpha = 0.15f),
    0.80f to LikhtarColors.BackgroundDeep.copy(alpha = 0f),
)

/** Верх не потребує окремої чорної плашки, а низ лишається опорою для інформації. */
// 0deg: .96 знизу → .55 на 22 % → 0 на 45 %.
private val HeroDetailVerticalScrim = Brush.verticalGradient(
    0.55f to LikhtarColors.BackgroundDeep.copy(alpha = 0f),
    0.78f to LikhtarColors.BackgroundDeep.copy(alpha = 0.55f),
    1.00f to LikhtarColors.BackgroundDeep.copy(alpha = 0.96f),
)

/** У скільки разів піднімається насиченість кольору кадру. */
private const val EpisodesWashSaturation = 2.4f

/** Яскравість плями. Вище — і картки серій на ній перестають читатись. */
private const val EpisodesWashValue = 0.62f

/** Щільність плями в центрі. */
private const val EpisodesWashAlpha = 0.62f

/**
 * Той самий тон, але насичений і заданої яскравості.
 *
 * HSV, а не `lerp`: тон — єдине, що варто взяти з кадру, а насиченість і яскравість у
 * середнього кольору завжди занижені, бо він середній.
 */
private fun Color.vivid(saturation: Float, value: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * saturation).coerceAtMost(1f)
    hsv[2] = value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Низ сцени серій лишається темним: під ним стоять картки з дрібним текстом. */
private val EpisodesFloorScrim = Brush.verticalGradient(
    0.00f to LikhtarColors.Background.copy(alpha = 0f),
    0.42f to LikhtarColors.Background.copy(alpha = 0.3f),
    0.78f to LikhtarColors.Background.copy(alpha = 0.76f),
    1.00f to LikhtarColors.Background.copy(alpha = 0.94f),
)

/** Щільні екрани: майже суцільна заливка, крізь яку фото просвічує лише кольором. */
private val ContentScrim = Brush.horizontalGradient(
    0.0f to LikhtarColors.Background.copy(alpha = 0.95f),
    1.0f to LikhtarColors.Background.copy(alpha = 0.88f),
)

private val ContentVerticalScrim = Brush.verticalGradient(
    0.0f to LikhtarColors.Background.copy(alpha = 0.8f),
    0.35f to LikhtarColors.Background.copy(alpha = 0.45f),
    1.0f to LikhtarColors.Background.copy(alpha = 0.7f),
)

/**
 * Застосунки: вертикальна вінетка з макета — верх і низ темніші, середина світліша,
 * але фото видно всюди.
 */
private val AppsVignette = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.72f),
    0.4f to Color.Black.copy(alpha = 0.5f),
    1.0f to Color.Black.copy(alpha = 0.66f),
)

/** Кутове затемнення під заголовком: еліпс 110 % × 90 % з центром у (8 %, 12 %). */
private fun appsCorner(size: Size) = Brush.radialGradient(
    0.00f to Color.Black.copy(alpha = 0.7f),
    0.62f to Color.Black.copy(alpha = 0f),
    center = Offset(size.width * 0.08f, size.height * 0.12f),
    radius = maxOf(size.width * 1.1f, size.height * 0.9f) / 2f,
)

/**
 * Світла колонка зліва: у макеті це backdrop-blur із білою плівкою, що згасає до 720px.
 * Блюру немає (див. README, розділ «Скло»), лишається сама плівка — саме вона й дає
 * відчуття підсвіченого лівого краю.
 */
private val AppsLeftColumn = Brush.horizontalGradient(
    0.000f to Color.White.copy(alpha = 0.07f),
    0.235f to Color.White.copy(alpha = 0.04f),
    0.375f to Color.White.copy(alpha = 0f),
)

private val DefaultBackdrop = Brush.verticalGradient(
    0.0f to LikhtarColors.FloorTop,
    0.55f to LikhtarColors.RailFloor,
    1.0f to LikhtarColors.Background,
)

/** Підпис джерела зображення лишається обов'язковим, але не має конкурувати з вмістом. */
private const val ATTRIBUTION_ALPHA = 0.55f
