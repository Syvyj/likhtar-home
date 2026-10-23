package com.likhtar.home.core.model

/**
 * Опис процедурного фону: базовий градієнт плюс кілька світлових плям.
 *
 * Такі шпалери нічого не важать в APK і не залежать від мережі, але виглядають як зображення,
 * а не як плоска заливка. Координати й радіус — частки розміру екрана, тому один опис однаково
 * працює на 1080p і 4K.
 */
data class BackdropSpec(
    val baseGradient: List<Long>,
    val blobs: List<BackdropBlob> = emptyList(),
)

data class BackdropBlob(
    val color: Long,
    /** Центр у частках ширини та висоти: 0f..1f. */
    val centerX: Float,
    val centerY: Float,
    /** Радіус у частках меншої сторони екрана. */
    val radius: Float,
    val alpha: Float,
)
