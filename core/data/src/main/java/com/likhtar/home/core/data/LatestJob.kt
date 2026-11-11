package com.likhtar.home.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Одна задача за раз: нова скасовує попередню. Для пошуку озвучок («Почати») і розбору програми
 * ТБ Ефіру — там, де запізніла відповідь старої задачі інакше перезаписувала стан новішої
 * (озвучки 3-ї серії у вікні 5-ї; канали старого плейлиста після нового).
 */
class LatestJob(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        job?.cancel()
        return scope.launch(block = block).also { job = it }
    }

    fun cancel() {
        job?.cancel()
    }
}
