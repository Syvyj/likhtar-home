package com.likhtar.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.likhtar.home.core.data.HomeLauncherRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Піднімає лаунчер після завантаження приставки.
 *
 * На Google TV системний `launcherx` оголошує HOME-фільтр із `priority=2`, тому резолвінг
 * HOME-інтенту при старті віддає його, а не нас — навіть коли `ROLE_HOME` наша і запис у
 * `preferred-activities` теж наш. Пріоритет
 * системного застосунку маніфестом не перебивається, тому лаунчер відкриває себе сам.
 *
 * Умова одна: система вважає домашнім екраном саме нас. Якщо користувач вибрав інший
 * лаунчер, ми не маємо права вилізати поверх нього. Питання «чи HOME наша» делеговано
 * репозиторію: він знає і про RoleManager (API 29+), і про фолбек на resolveActivity
 * для старіших збірок — RoleManager напряму тут падав би на API < 29 (minSdk 23).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var homeLauncherRepository: HomeLauncherRepository

    override fun onReceive(context: Context, intent: Intent) {
        // Оновлення телевізора вмикає вимкнене назад — домашній екран повертаємо собі (лише
        // якщо його брали через ADB телевізора; ключ уже дозволено, тож без питань на екрані).
        // ADB — мережа, тому не в головному потоці.
        val pending = goAsync()
        Thread {
            try {
                homeLauncherRepository.takeover.reapplyIfNeeded()
                if (homeLauncherRepository.takeover.isActive) {
                    // Домашнім зроблено через ADB телевізора — система запускає нас сама, ще до
                    // кінця завантаження, і вікно лишалось без фокуса вводу (InputDispatcher:
                    // FocusedWindows порожньо): живий лише годинник, стрілки нікуди (приставка).
                    // Свіжа активність уже після завантаження фокус отримує.
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                } else if (homeLauncherRepository.status().isDefault) {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
