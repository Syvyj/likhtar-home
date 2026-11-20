package com.likhtar.home.nowplaying

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Служба-слухач: ключ до чужих медіасесій (`MediaSessionManager.getActiveSessions`) і, якщо панель
 * сповіщень увімкнена, — їхнє джерело. Вимкнена — одразу відв'язується й у пам'яті не висить
 * (free-my-tv `NowPlaying`).
 */
@AndroidEntryPoint
class NowPlayingListener : NotificationListenerService() {
    @Inject lateinit var feed: NotificationFeedStore

    override fun onListenerConnected() {
        feed.attach(this)
    }

    override fun onListenerDisconnected() = feed.detach(this)

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (feed.enabled) feed.post(sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = feed.remove(sbn.key)
}
