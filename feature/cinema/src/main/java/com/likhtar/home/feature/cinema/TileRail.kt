package com.likhtar.home.feature.cinema

import androidx.compose.foundation.layout.Arrangement
import com.likhtar.home.core.designsystem.LikhtarColors
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.focusGlow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.SectionTitle

/** Плитка ряду брендів: сервіс, мовник, студія або «Всі стрімінги». */
internal data class BrandTile(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val brand: StreamingBrand? = StreamingBrand.fromTitle(title),
)

/**
 * Ряд плиток брендів — один для Кінозалу («Стрімінги та канали») і Реаліті («Сервіси,
 * мовники й студії»); до нього це були дві майже дослівні копії, і в Кінозалі ряд навіть
 * не пришпилювався.
 *
 * Плитка — точка повернення ([returnPoint]): з неї пішли на сторінку бренду — на неї й
 * повертається фокус після Back.
 */
@Composable
internal fun TileRail(
    title: String,
    tiles: List<BrandTile>,
    restore: RailRestore,
    rowIndex: Int,
    onRowFocus: () -> Unit,
    onOpen: (BrandTile) -> Unit,
    /** Текстові плитки Реаліті (.brand-t прототипу): назва й кількість проєктів. */
) {
    // Повернулись зі сторінки плитки — спершу ряд до неї: інакше дальня плитка («Всі
    // стрімінги» в кінці) ще не скомпонована, фокусу нікуди стати, і він падав на першу
    // картку розділу.
    val railState = rememberLazyListState()
    restore.ScrollRailTo(railState) { key -> tiles.indexOfFirst { "tile:${it.key}" == key } }
    Column {
        SectionTitle(text = title)
        LikhtarRail(state = railState) {
            items(tiles, key = { it.key }) { tile ->
                val tileClick = {
                    restore.leave("tile:${tile.key}")
                    onOpen(tile)
                }
                val tileModifier = Modifier
                    // Запас під збільшення фокуса, як у картках рядів.
                    .cardSlot()
                    .returnPoint(restore, "tile:${tile.key}", rowIndex, onRowFocus)
                CatalogTileCard(
                    title = tile.title,
                    subtitle = tile.subtitle,
                    brand = tile.brand,
                    onClick = tileClick,
                    modifier = tileModifier,
                )
            }
        }
    }
}
