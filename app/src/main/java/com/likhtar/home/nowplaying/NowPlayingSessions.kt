package com.likhtar.home.nowplaying

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.likhtar.home.core.data.MediaSnapshot
import com.likhtar.home.core.data.NotificationListenerGrant
import com.likhtar.home.core.data.isPlaying
import com.likhtar.home.core.data.pickNowPlaying
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Що показати плашкою: назва, виконавець і застосунок. */
data class NowPlaying(val title: String, val artist: String, val app: String, val pkg: String, val playing: Boolean)

/**
 * «Що грає» — музика чи подкаст у фоні (free-my-tv, lounge). Сесії читаємо через ключ служби
 * [NowPlayingListener]; оновлюємо на зміні списку сесій і на зміні стану/метаданих кожної.
 */
@Singleton
class NowPlayingSessions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val grant: NotificationListenerGrant,
) {
    private val component = ComponentName(context, NowPlayingListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val manager get() = context.getSystemService(MediaSessionManager::class.java)
    private val current = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = current.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var shown: MediaController? = null
    private val playedAt = mutableMapOf<String, Long>()
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
    }

    /** Раз на старті лаунчера: доступ (фоном), далі — підписка на головному потоці. */
    fun start() {
        Thread {
            if (grant.ensure(component)) main.post { listen() }
        }.start()
    }

    private fun listen() = runCatching {
        manager.addOnActiveSessionsChangedListener({ list -> track(list.orEmpty()) }, component, main)
        track(manager.getActiveSessions(component))
    }.onFailure { Log.w(TAG, "сесії недоступні", it) }

    private fun track(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list.filter { it.packageName != context.packageName }
        controllers.forEach { it.registerCallback(callback, main) }
        refresh()
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val snaps = controllers.map { c ->
            val meta = c.metadata
            MediaSnapshot(
                pkg = c.packageName,
                state = c.playbackState?.state ?: PlaybackState.STATE_NONE,
                // YouTube та інші кладуть назву не в TITLE, а в опис (DISPLAY_TITLE / description.title).
                title = (meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: meta?.description?.title?.toString()).orEmpty(),
                artist = (meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: meta?.description?.subtitle?.toString() ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            )
        }
        snaps.filter { it.isPlaying() }.forEach { playedAt[it.pkg] = now }
        val pick = pickNowPlaying(snaps, playedAt, now)
        shown = pick?.let { p -> controllers.firstOrNull { it.packageName == p.pkg } }
        current.value = pick?.let { NowPlaying(it.title, it.artist, label(it.pkg), it.pkg, it.isPlaying()) }
    }

    /** OK на плашці: пауза / грати. */
    fun toggle() {
        val c = shown ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    /** Довге OK: відкрити застосунок, що грає. */
    fun open() {
        val pkg = shown?.packageName ?: return
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun label(pkg: String) = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private companion object {
        const val TAG = "LikhtarNowPlaying"
    }
}
