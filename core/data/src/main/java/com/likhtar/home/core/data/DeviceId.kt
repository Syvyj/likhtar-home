package com.likhtar.home.core.data

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Стабільний ідентифікатор приставки — щоб сервер знав, з якого пристрою була спроба.
 *
 * `ANDROID_ID` живе до скидання до заводських налаштувань і не потребує жодного дозволу.
 * Це не ідентифікація людини: він однаковий для всіх профілів на приставці й саме тому
 * ходить у парі з `profile`. Порожній рядок можливий на дивних прошивках — сервер тоді
 * просто не знатиме пристрою, і це краще, ніж вигадувати випадковий id щозапуску.
 */
@Singleton
class DeviceId @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val value: String by lazy {
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
    }
}
