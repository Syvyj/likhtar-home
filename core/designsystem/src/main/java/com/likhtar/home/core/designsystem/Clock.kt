package com.likhtar.home.core.designsystem

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «20:00» — години й хвилини в поточному поясі пристрою.
 *
 * Формат щоразу новий, і це головне: `SimpleDateFormat` запам'ятовує пояс у момент створення,
 * а лаунчер як домашній екран живе тижнями. Статичний формат ТВ Ефіру показував програму за
 * поясом, що був при запуску процесу, — у Польщі з київськими годинами («новини о 20:00»,
 * хоч у тебе 19:00). Один формат на весь лаунчер: годинник меню, Головна, Ефір.
 */
fun clockTime(millis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(millis))
