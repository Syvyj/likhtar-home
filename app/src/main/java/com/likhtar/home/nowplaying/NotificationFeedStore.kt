package com.likhtar.home.nowplaying

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.likhtar.home.core.data.NotificationFacts
import com.likhtar.home.core.data.NotificationListenerGrant
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.data.showInFeed
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Сповіщення в панелі Ліхтаря: [actions] — підписи кнопок без полів вводу. */
data class TvNotification(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val actions: List<String>,
)

/**
 * Панель сповіщень Android (lounge `NotificationFeed`). Лише в пам'яті: нічого не пишемо на диск
 * і не шлемо. Служба [NowPlayingListener] тримається прив'язаною, тільки коли панель увімкнена.
 */
@Singleton
class NotificationFeedStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val grant: NotificationListenerGrant,
) {
    private class Entry(val item: TvNotification, val content: PendingIntent?, val actions: List<PendingIntent>, val autoCancel: Boolean)

    private val component = ComponentName(context, NowPlayingListener::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = LinkedHashMap<String, Entry>()
    private val current = MutableStateFlow<List<TvNotification>>(emptyList())
    val state: StateFlow<List<TvNotification>> = current.asStateFlow()

    @Volatile var enabled = false
        private set
    /** Налаштування вже прочитано: до того служба, що підключилась першою, чекає, а не відв'язується. */
    @Volatile private var known = false
    @Volatile private var muted: Set<String> = emptySet()
    @Volatile private var service: NotificationListenerService? = null

    fun start() {
        scope.launch {
            settings.settings.map { it.notificationFeed to it.mutedNotificationApps }.distinctUntilChanged().collect { (on, mutedApps) ->
                muted = mutedApps
                val wasOn = enabled
                enabled = on
                val first = !known
                known = true
                when {
                    on && !wasOn -> service?.let(::fill) ?: run { if (grant.ensure(component)) NotificationListenerService.requestRebind(component) }
                    !on && first -> service?.requestUnbind()
                    !on && wasOn -> {
                        service?.requestUnbind()
                        replace(emptyList())
                    }
                    // Список схованих змінився (сховав чи повернув усі) — перечитати: повернуті теж мають з'явитись.
                    on -> service?.let(::fill)
                }
            }
        }
    }

    /**
     * Служба підключилась. Процес лаунчера щойно стартував — налаштування ще не прочитано: тоді лише
     * запам'ятати її, а [start] вирішить (заповнити чи відв'язати), коли прочитає.
     */
    fun attach(listener: NotificationListenerService) {
        service = listener
        when {
            !known -> Unit
            enabled -> fill(listener)
            else -> listener.requestUnbind()
        }
    }

    private fun fill(listener: NotificationListenerService) =
        replace(runCatching { listener.activeNotifications.orEmpty().toList() }.getOrDefault(emptyList()), runCatching { listener.currentRanking }.getOrNull())

    fun detach(listener: NotificationListenerService) {
        if (service === listener) service = null
    }

    fun post(sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?) {
        synchronized(entries) {
            entries.remove(sbn.key)
            toEntry(sbn, ranking)?.let { entries[sbn.key] = it }
            publish()
        }
    }

    fun remove(key: String) {
        synchronized(entries) { if (entries.remove(key) != null) publish() }
    }

    /** OK на сповіщенні — те саме, що тап у системній панелі. */
    fun open(key: String) {
        val entry = synchronized(entries) { entries[key] } ?: return
        send(entry.content)
        if (entry.autoCancel) dismiss(key)
    }

    fun act(key: String, index: Int) {
        val entry = synchronized(entries) { entries[key] } ?: return
        send(entry.actions.getOrNull(index))
    }

    fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
        remove(key)
    }

    fun mute(pkg: String) {
        scope.launch { settings.muteNotificationApp(pkg) }
    }

    private fun send(intent: PendingIntent?) {
        runCatching { intent?.send() }.onFailure { Log.w(TAG, "дія сповіщення не спрацювала", it) }
    }

    private fun replace(list: List<StatusBarNotification>, ranking: NotificationListenerService.RankingMap? = null) {
        synchronized(entries) {
            entries.clear()
            list.forEach { sbn -> toEntry(sbn, ranking)?.let { entries[sbn.key] = it } }
            publish()
        }
    }

    private fun publish() {
        current.value = entries.values.map { it.item }.sortedByDescending { it.postedAt }
    }

    private fun toEntry(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap?): Entry? {
        val n = sbn.notification ?: return null
        val extras = n.extras
        val importance = NotificationListenerService.Ranking().let { r ->
            if (rankingMap?.getRanking(sbn.key, r) == true) r.importance else com.likhtar.home.core.data.IMPORTANCE_DEFAULT
        }
        val facts = NotificationFacts(
            pkg = sbn.packageName,
            ongoing = sbn.isOngoing,
            clearable = sbn.isClearable,
            media = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            importance = importance,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
        )
        if (!showInFeed(facts, context.packageName, muted)) return null
        // Кнопки з полем вводу (відповісти в месенджері) пультом не заповнити — їх не показуємо.
        val actions = n.actions.orEmpty().filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null }.take(MAX_ACTIONS)
        val app = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        return Entry(
            item = TvNotification(sbn.key, sbn.packageName, app, facts.title.trim(), facts.text.trim(), sbn.postTime, actions.map { it.title.toString() }),
            content = n.contentIntent,
            actions = actions.map { it.actionIntent },
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    private companion object {
        const val TAG = "LikhtarNotifications"
        const val MAX_ACTIONS = 3
    }
}
