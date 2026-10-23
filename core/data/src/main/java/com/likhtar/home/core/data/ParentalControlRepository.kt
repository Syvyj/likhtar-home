package com.likhtar.home.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.parentalDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "likhtar_parental",
)

/**
 * PIN батьківського контролю.
 *
 * Сам PIN не зберігається: тільки SHA-256 від «сіль + PIN» і випадкова сіль. Це захищає
 * від підглядання у файлі налаштувань; від рішучої атаки на 4 цифри не захищає ніщо, і це
 * нормально — сценарій тут дитячий режим, а не банк.
 *
 * Важливе обмеження, яке треба показати в UX: блокування діє лише всередині LIKHTAR. Лаунчер
 * не може заборонити системі запустити чужий застосунок голосом чи з системних налаштувань
 * (ризик №3 у плані).
 */
@Singleton
class ParentalControlRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isPinSet: Flow<Boolean> = context.parentalDataStore.data.map { it[KEY_HASH] != null }

    suspend fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }.toHex()
        context.parentalDataStore.edit { prefs ->
            prefs[KEY_SALT] = salt
            prefs[KEY_HASH] = hash(salt, pin)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.parentalDataStore.data.first()
        val salt = prefs[KEY_SALT] ?: return false
        val expected = prefs[KEY_HASH] ?: return false
        return hash(salt, pin) == expected
    }

    /**
     * Скидає PIN. Викликати лише після успішної перевірки старого — сам метод нічого не питає,
     * бо це рішення екрана налаштувань, а не сховища.
     */
    suspend fun clearPin() {
        context.parentalDataStore.edit { prefs ->
            prefs.remove(KEY_SALT)
            prefs.remove(KEY_HASH)
        }
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt$pin".toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val SALT_BYTES = 16
        val KEY_SALT = stringPreferencesKey("pin_salt")
        val KEY_HASH = stringPreferencesKey("pin_hash")
    }
}
