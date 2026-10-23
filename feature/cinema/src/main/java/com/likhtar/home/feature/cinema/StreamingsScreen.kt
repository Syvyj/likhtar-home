package com.likhtar.home.feature.cinema

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.CatalogListingKind
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.PageHeader
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.cachedScrim
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester

/**
 * Усі сервіси — сіткою, а не рядом.
 *
 * Рядом вони й були, двома: «Стрімінги та канали» і другим із каналами. Другий виходив довжиною
 * в екран — мовників більше, ніж стрімінгів, — і пультом його не пройти. На головному
 * ряду лишилась остання картка-перехід, а весь перелік живе тут: сітка читається одним
 * поглядом, і вертикальний рух по ній коштує стільки ж, скільки горизонтальний.
 */
@Composable
fun StreamingsScreen(
    onOpenListing: (CatalogListingKind, String) -> Unit,
    viewModel: StreamingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstTile = rememberInitialFocusRequester(state.studios.isNotEmpty() || state.networks.isNotEmpty())

    // Те, що вже стоїть у стрімінгах, серед каналів не повторюємо: Netflix, HBO,
    // Paramount+ і Prime приходять із TMDB і як мовники — це той самий сервіс, тільки з
    // іншого боку, і дві плитки з однією назвою на одному екрані читаються як помилка.
    // Ключ — бренд, а не рядок: TMDB пише «Netflix», ми «NETFLIX», а Prime у нього
    // буває і «Amazon Prime Video».
    val known = state.studios.map { brandKey(it.title) }.toSet()
    // Відсів за регіоном. Без нього більшість списку — японські мовники (заміряно: 70 японських проти 15 українських):
    // вони приходять із TMDB разом з аніме й ховають українські канали за собою. Порогом за кількістю тайтлів їх не
    // відрізнити — Nippon TV має 4, а ТЕТ і 2+2 по шість.
    //
    // Порожня країна НЕ відсівається, і в неї два джерела: TMDB не сказав, або назва
    // неоднозначна (NBC — це і американська National Broadcasting, і японська Nagasaki
    // Broadcasting, а в TMDB у тайтлах лежать імена, не id). Обидва означають «не
    // знаємо», і це не підстава ховати канал.
    val channels = state.networks
        .filterNot { brandKey(it.name) in known }
        .filter { state.regions.isEmpty() || it.country.isBlank() || it.country in state.regions }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .cachedScrim(dim = Color.Transparent) { listOf(ListingBackground) },
    )

    val res = LocalContext.current.resources
    val streamingsTitle = stringResource(R.string.cinema_streamings)
    val channelsTitle = stringResource(R.string.cinema_tv_channels)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LikhtarDimens.ScreenPadding),
        // Крок сітки, а не рядів: між плитками по вертикалі має бути стільки ж, скільки
        // по горизонталі, інакше сітка розсипається на окремі рядки. Повітря під
        // заголовком блоку додається самим заголовком.
        // Ряди сітки — та сама видима відстань, що між плитками: слот уже дає 2 × 8 dp.
        verticalArrangement = Arrangement.spacedBy(LikhtarDimens.CardGap - LikhtarDimens.CardSlotY * 2),
        // Простір під останнім рядом: інакше нижні плитки впираються в край екрана, і
        // на телевізорі з оверскеном частина фокусної рамки лишається за кадром.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        item(key = "head") {
            PageHeader(
                title = stringResource(R.string.cinema_all_streamings),
                subtitle = (state.studios.size + channels.size).let {
                    pluralStringResource(R.plurals.cinema_services_count, it, it)
                },
            )
        }

        if (state.loading && state.studios.isEmpty() && state.networks.isEmpty()) {
            item(key = "loader") {
                LanternLoader(
                    title = stringResource(R.string.cinema_streamings_loading),
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                )
            }
            return@LazyColumn
        }

        // Стрімінги першими й окремим блоком: у них за плиткою стоїть наша курація
        // (готові ряди), а в мовника — просто перелік тайтлів цього каналу.
        if (state.studios.isNotEmpty()) {
            tileGrid(
                title = streamingsTitle,
                tiles = state.studios.map { studio ->
                    Tile(
                        title = studio.title,
                        subtitle = studio.rows.size.let {
                            res.getQuantityString(R.plurals.cinema_rows_count, it, it)
                        },
                        onClick = { onOpenListing(CatalogListingKind.STUDIO, studio.id) },
                    )
                },
                firstFocus = firstTile,
            )
        }

        if (channels.isNotEmpty()) {
            tileGrid(
                title = channelsTitle,
                tiles = channels.map { network ->
                    Tile(
                        title = network.name,
                        subtitle = network.titles.let {
                            res.getQuantityString(R.plurals.cinema_titles_count, it, it)
                        },
                        onClick = { onOpenListing(CatalogListingKind.NETWORK, network.name) },
                    )
                },
                firstFocus = firstTile.takeIf { state.studios.isEmpty() },
            )
        }

        state.message?.let { text ->
            item(key = "message") {
                Text(
                    text = text,
                    style = LikhtarType.MonoSmall,
                    color = LikhtarColors.TextMuted,
                )
            }
        }
    }
}

private data class Tile(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/**
 * Сітка плиток по [GRID_COLUMNS] у ряд.
 *
 * Рядами всередині LazyColumn, а не LazyVerticalGrid: вкладати скролюваний контейнер
 * у скролюваний не можна, а сама сітка тут коротка — усі сервіси видно за один-два
 * екрани, ліниве наповнення нічого не економить.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.tileGrid(
    title: String,
    tiles: List<Tile>,
    firstFocus: androidx.compose.ui.focus.FocusRequester?,
) {
    item(key = "title-$title") {
        SectionTitle(text = title, modifier = Modifier.padding(top = 18.dp))
    }
    tiles.chunked(GRID_COLUMNS).forEachIndexed { rowIndex, chunk ->
        item(key = "$title-$rowIndex") {
            Row(horizontalArrangement = Arrangement.spacedBy(LikhtarDimens.RailGap)) {
                chunk.forEachIndexed { columnIndex, tile ->
                    CatalogTileCard(
                        title = tile.title,
                        subtitle = tile.subtitle,
                        brand = StreamingBrand.fromTitle(tile.title),
                        onClick = tile.onClick,
                        modifier = Modifier.cardSlot().then(
                            if (rowIndex == 0 && columnIndex == 0 && firstFocus != null) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Чим упізнаємо той самий сервіс під різними назвами: бренд, а якщо його немає — сам
 * рядок. Потрібно, щоб мовник не подвоїв плитку стрімінга.
 */
private fun brandKey(title: String): String =
    StreamingBrand.fromTitle(title)?.name ?: title.lowercase().trim()

/** Плитка 152dp: пʼять у ряд — це 810dp, решта канви лишається полям. */
private const val GRID_COLUMNS = 5
