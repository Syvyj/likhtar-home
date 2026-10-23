package com.likhtar.home.core.data

import com.likhtar.home.core.model.DayForecast
import com.likhtar.home.core.model.HourForecast
import com.likhtar.home.core.model.Sky
import com.likhtar.home.core.model.WeatherSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Погода для Головної.
 *
 * Контракт навмисно потоковий, а не `suspend`: на приставці мережа буває повільною саме в
 * момент старту, і домашній екран не має права показувати порожнє місце замість панелі.
 * Реалізація віддає кеш одразу, якщо він є, а свіжі дані — наступною емісією; без кешу
 * Головна чекає без погоди (див. HomeViewModel), вигаданих даних не показуємо.
 */
interface WeatherRepository {
    fun weather(): Flow<WeatherSnapshot>
}
