package com.likhtar.home.feature.cinema

import androidx.compose.foundation.background
import com.likhtar.home.core.designsystem.LikhtarColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.LikhtarCard
import com.likhtar.home.core.designsystem.FocusRing
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.focusGlow
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester

/** Шаблонні підписи з порожньої картки — не пояснення, показувати їх не треба. */
private val CardPlaceholders = setOf(
    "Наскільки складно дивитись.", "Скільки жорстокості.", "Скільки відвертих сцен.", "Чи підійде дітям.",
)

private fun String.cardNote(): String = trim().takeUnless { it.isBlank() || it in CardPlaceholders }.orEmpty()

/**
 * «Чи варто?» (.worth прототипу): ліворуч вердикт 28 px, «Кому підійде / не підійде» і
 * настрій мітками; праворуч шкали бурштином з поясненнями; знизу — «Цікаві факти» картками,
 * по яких ходить фокус. Фінал — за кнопкою: це спойлер, і випадково його не читають.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorthTab(card: LikhtarCard) {
    var ending by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // grid 880 px | решта, gap 72 px.
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.padding(end = 48.dp)) {
            Column(Modifier.width(440.dp)) {
                Text(
                    card.worth,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 14.sp, lineHeight = 20.3.sp),
                    color = Color.White,
                )
                WhoBlock(stringResource(R.string.cinema_worth_for_whom), card.forWhom)
                WhoBlock(stringResource(R.string.cinema_worth_not_for_whom), card.notForWhom)
                if (card.moods.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 11.dp),
                    ) {
                        card.moods.forEach { ProtoChip(it, height = 20.dp) }
                    }
                }
                if (card.endingExplained.isNotBlank()) {
                    ProtoButton(
                        label = stringResource(R.string.cinema_worth_ending_spoiler),
                        onClick = { ending = true },
                        icon = null,
                        modifier = Modifier.padding(top = 13.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                ScoreBlock(stringResource(R.string.cinema_score_complexity), card.complexity, card.complexityNote.cardNote())
                ScoreBlock(stringResource(R.string.cinema_score_violence), card.violence, card.violenceNote.cardNote())
                ScoreBlock(stringResource(R.string.cinema_score_explicitness), card.erotica, card.eroticaNote.cardNote())
                Column(Modifier.padding(bottom = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.cinema_score_kids), style = ScoreLabel, color = LikhtarColors.TextPrimary, modifier = Modifier.width(85.dp))
                        Text(
                            stringResource(if (card.childrenFriendly) R.string.cinema_kids_ok else R.string.cinema_kids_not),
                            style = ScoreLabel,
                            color = LikhtarColors.Glow,
                        )
                    }
                    card.childrenNote.cardNote().takeIf { it.isNotBlank() }?.let { Text(it, style = ScoreNote, color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
        }
        if (card.easterEggs.isNotEmpty()) {
            // .facts-t: margin 30 / 14 px; .fact: 560×176 px, поле 24×28, 21 px.
            SectionTitle(stringResource(R.string.cinema_worth_facts), modifier = Modifier.padding(top = 15.dp))
            LikhtarRail(gap = 14.dp) {
                items(card.easterEggs.take(8)) { fact ->
                    FocusableSurface(
                        onClick = {},
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .size(280.dp, 88.dp)
                            .focusGlow(9.dp),
                        shape = RoundedCornerShape(9.dp),
                        background = LikhtarColors.Surface.copy(alpha = 0.8f),
                        focusedBackground = LikhtarColors.SurfaceHigh,
                        borderColor = Color.Transparent,
                        focusedScale = 1.04f,
                        focusLighten = 0f,
                    ) { focused ->
                        Text(
                            fact,
                            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 10.5.sp, lineHeight = 15.2.sp),
                            color = LikhtarColors.TextPrimary,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 5,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                        FocusRing(focused, 9.dp)
                    }
                }
            }
        }
    }
    if (ending) {
        LikhtarDialog(onDismiss = { ending = false }, width = 820.dp) {
            Text(text = stringResource(R.string.cinema_worth_ending), style = LikhtarType.Label, color = LikhtarColors.Glow)
            Text(
                text = card.endingExplained,
                style = LikhtarType.Body1,
                color = Color.White,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            )
            LikhtarButton(label = stringResource(R.string.cinema_close), onClick = { ending = false }, modifier = Modifier.focusRequester(rememberInitialFocusRequester()))
        }
    }
}

/** .who: підпис 21 px кольором тексту над поясненням 22 px приглушеним; margin 20 px. */
@Composable
private fun WhoBlock(label: String, text: String) {
    if (text.isBlank()) return
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp), color = LikhtarColors.TextPrimary)
        Text(
            text,
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp, lineHeight = 16.sp),
            color = LikhtarColors.TextDim,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** .score: назва 170 px, п'ять смужок 30×9 px бурштином, пояснення 19 px. */
@Composable
private fun ScoreBlock(label: String, score: Int, note: String) {
    Column(Modifier.padding(bottom = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ScoreLabel, color = LikhtarColors.TextPrimary, modifier = Modifier.width(85.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(5) { i ->
                    Box(
                        Modifier
                            .size(width = 15.dp, height = 4.5.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (i < score) LikhtarColors.Amber else LikhtarColors.Paper.copy(alpha = 0.14f)),
                    )
                }
            }
        }
        if (note.isNotBlank()) Text(note, style = ScoreNote, color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 3.dp))
    }
}

private val ScoreLabel = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
private val ScoreNote = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp, lineHeight = 13.3.sp)
