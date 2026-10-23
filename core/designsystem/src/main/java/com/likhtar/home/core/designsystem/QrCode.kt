package com.likhtar.home.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR-код: інструкція в налаштуваннях, «З телефону» в Ліхтарику.
 *
 * Матрицю рахує zxing, малюємо самі одним `Canvas`: генерувати `Bitmap` заради
 * 25×25 чорних квадратів — зайвий об'єкт на кожну композицію.
 */
@Composable
fun QrCode(content: String, size: Dp) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                QR_MODULES,
                QR_MODULES,
                mapOf(EncodeHintType.MARGIN to 0),
            )
        }.getOrNull()
    } ?: return

    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(LikhtarDimens.CornerSmall))
            .background(Color.White)
            .padding(5.dp),
    ) {
        val cell = this.size.width / matrix.width
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (!matrix.get(x, y)) continue
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x * cell, y * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

private const val QR_MODULES = 25
