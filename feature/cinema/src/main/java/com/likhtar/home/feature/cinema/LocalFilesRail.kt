package com.likhtar.home.feature.cinema

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.media.LocalFile
import com.likhtar.home.core.designsystem.FocusRing
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.focusGlow
import com.likhtar.home.core.designsystem.R as DsR

/**
 * «Інше з медіатеки»: файли, яких немає в TMDB (домашнє відео, концерти). Постера немає —
 * плитка з назвою файлу й папкою, звідки він; OK — одразу в плеєр.
 */
@Composable
internal fun LocalFilesRail(
    title: String,
    files: List<LocalFile>,
    onPlay: (LocalFile) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(text = title)
        LikhtarRail {
            itemsIndexed(files, key = { _, f -> f.sourceId + f.path }) { _, file ->
                FileCard(
                    file = file,
                    onClick = { onPlay(file) },
                    modifier = Modifier.cardSlot().onFocusChanged { if (it.isFocused) onFocused() },
                )
            }
        }
    }
}

@Composable
private fun FileCard(file: LocalFile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val corner = 9.dp
    FocusableSurface(
        onClick = onClick,
        // Як кадр Кінозалу: та сама ширина й 16:9, щоб ряд стояв у сітці з рештою.
        modifier = modifier.width(CatalogBannerWidth).aspectRatio(16f / 9f).focusGlow(corner),
        shape = RoundedCornerShape(corner),
        background = LikhtarColors.Surface,
        focusedBackground = LikhtarColors.SurfaceHigh,
        borderColor = LikhtarColors.Faint,
        focusedScale = 1.07f,
    ) { focused ->
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(
                painter = painterResource(DsR.drawable.ic_solar_folder),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LikhtarColors.Amber),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = file.name.substringBeforeLast('.'),
                style = LikhtarType.Label,
                color = LikhtarColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = file.path.substringBeforeLast('/', "").ifBlank { file.sourceName },
                style = LikhtarType.Body2,
                color = LikhtarColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FocusRing(focused, corner)
    }
}
