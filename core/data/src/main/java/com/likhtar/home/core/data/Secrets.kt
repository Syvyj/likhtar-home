package com.likhtar.home.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Секрети людини (ключ AI, токен Home Assistant, ключ TMDB, плейлист із токеном провайдера,
 * паролі SMB) на диску — лише зашифровані ключем з Android Keystore. Ключ Keystore з пристрою не виймається, тож скопійовані дані застосунку без
 * самої приставки нічого не дають.
 *
 * Збережене без префікса — старий відкритий запис: [open] віддає його як є, а
 * [SettingsRepository] при старті переписує такі шифрованими.
 *
 * ponytail: якщо Keystore на якійсь приставці зламаний, [seal] лишає значення відкритим —
 * інакше людина втратила б плейлист і ключі; лог про це — один рядок без значення.
 */
object Secrets {
    private const val ALIAS = "likhtar_secrets"
    private const val PREFIX = "enc1:"
    private const val TAG = "LikhtarSecrets"

    fun isSealed(stored: String?): Boolean = stored?.startsWith(PREFIX) == true

    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            PREFIX + Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        }.getOrElse {
            Log.w(TAG, "Keystore недоступний, секрет лишається відкритим: ${it.javaClass.simpleName}")
            plain
        }
    }

    // Налаштування перечитуються на кожну зміну будь-якого поля, а Keystore на приставці не
    // безкоштовний: розшифроване тримаємо в пам'яті процесу за шифрованим рядком.
    private val opened = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun open(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        if (!isSealed(stored)) return stored
        return opened[stored] ?: runCatching {
            val bytes = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)).also { opened[stored] = it }
        }.getOrElse {
            Log.w(TAG, "Секрет не розшифрувався: ${it.javaClass.simpleName}")
            ""
        }
    }

    private const val IV_BYTES = 12

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }
}
