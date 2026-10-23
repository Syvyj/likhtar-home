package com.likhtar.home.core.data

import android.util.Log
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Знайдене місто: назва для показу, координати для запиту погоди. */
data class GeoPlace(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
) {
    val title: String get() = if (country.isBlank()) name else "$name, $country"
}

/**
 * Пошук міста через геокодер Open-Meteo — той самий сервіс, що дає погоду, теж без ключа.
 *
 * Координати зберігаються разом із назвою міста, тому геокодер працює рівно один раз —
 * коли користувач обирає місто, а не при кожному оновленні погоди.
 */
@Singleton
class GeocodingRepository @Inject constructor() {

    suspend fun search(query: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        runCatching { fetch(trimmed) }
            .onFailure { Log.w(TAG, "Пошук міста не вдався", it) }
            .getOrDefault(emptyList())
    }

    private fun fetch(query: String): List<GeoPlace> {
        val url = "$ENDPOINT?name=${URLEncoder.encode(query, "UTF-8")}&count=$LIMIT&language=${java.util.Locale.getDefault().language}&format=json"
        val body = http(url, TIMEOUT_MS) { it.text() }

        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            GeoPlace(
                name = item.optString("name").ifBlank { return@mapNotNull null },
                country = item.optString("country"),
                latitude = item.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() }
                    ?: return@mapNotNull null,
                longitude = item.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() }
                    ?: return@mapNotNull null,
            )
        }
    }

    private companion object {
        const val TAG = "LikhtarWeather"
        const val ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"
        const val TIMEOUT_MS = 8_000
        const val LIMIT = 6
    }
}
