package com.likhtar.home

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Заставка Ліхтаря: відео з висоти Apple і кадри нового з моїх сервісів, навперемін.
 *
 * Вмикає її HomeKeyService, коли пульт довго не чіпали й нічого не грає. Будь-яка кнопка закриває,
 * OK на кадрі — сторінка тайтла. Через задані хвилини — сон (як системна заставка, що вимикає екран).
 * Відео — з серверів Apple; у застосунку лише список посилань (assets/aerials.json), як в Aerial Views.
 */
@AndroidEntryPoint
class ScreensaverActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sources: ScreensaverSources

    private var slide by mutableStateOf<Slide?>(null)
    private var queue: List<Slide> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { Screensaver(slide, onDone = ::next) }
        lifecycleScope.launch {
            val s = settings.settings.first()
            queue = buildQueue(s.screensaverAerial, s.screensaverTitles)
            if (queue.isEmpty()) finish() else slide = queue[0]
        }
        lifecycleScope.launch {
            val minutes = settings.settings.first().screensaverSleepMinutes
            if (minutes <= 0) return@launch
            delay(minutes * 60_000L)
            HomeKeyService.sleepNow()
            finish()
        }
    }

    private fun next() {
        if (queue.isEmpty()) return
        index = (index + 1) % queue.size
        slide = queue[index]
    }

    /** Відео й кадри навперемін; чого бракує — тим, що є. */
    private suspend fun buildQueue(aerial: Boolean, titles: Boolean): List<Slide> {
        val videos = if (aerial) sources.aerials().shuffled().map { Slide.Aerial(it.url, it.name) } else emptyList()
        val frames = if (titles) sources.freshTitles().shuffled().map { Slide.Title(it.item, it.brand) } else emptyList()
        if (videos.isEmpty() || frames.isEmpty()) return videos + frames
        return buildList {
            for (i in 0 until maxOf(videos.size, frames.size)) {
                videos.getOrNull(i % videos.size)?.let(::add)
                frames.getOrNull(i % frames.size)?.let(::add)
            }
        }
    }

    // Як MainActivity: мова з налаштувань Ліхтаря й масштаб 960 dp — інакше дата йшла мовою системи.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.likhtar.home.core.data.AppLanguage.wrap(newBase))
    }

    /** Кнопки, натиснуті вже тут: UP від OK, яким заставку запустили з налаштувань, її не закриває. */
    private val pressed = mutableSetOf<Int>()

    // Як у MainActivity: перевизначаємо публічний Activity.dispatchKeyEvent, lint плутає з androidx.core.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) pressed += event.keyCode
        if (event.action != KeyEvent.ACTION_UP || !pressed.remove(event.keyCode)) return true
        val current = slide
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (ok && current is Slide.Title) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_TITLE_KIND, current.item.kind.name)
                    .putExtra(MainActivity.EXTRA_TITLE_ID, current.item.tmdbId),
            )
        }
        finish()
        return true
    }

    // Щось інше вийшло наперед або екран погас — заставка своє відпрацювала.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }

    sealed interface Slide {
        data class Aerial(val url: String, val name: String) : Slide
        data class Title(val item: CatalogItem, val brand: String) : Slide
    }

    companion object {
        @Volatile var showing = false
            private set
    }
}

private val Plate = LikhtarColors.ScrimDeep
private const val FRAME_MS = 20_000L

@Composable
private fun Screensaver(slide: ScreensaverActivity.Slide?, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = slide, animationSpec = tween(1200), label = "slide") { s ->
            when (s) {
                is ScreensaverActivity.Slide.Aerial -> AerialVideo(s, onDone)
                is ScreensaverActivity.Slide.Title -> TitleFrame(s, onDone)
                null -> Box(Modifier.fillMaxSize())
            }
        }
        Clock(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 40.dp))
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
private fun AerialVideo(slide: ScreensaverActivity.Slide.Aerial, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == androidx.media3.common.Player.STATE_ENDED) onDone()
                        }

                        // Відео не відкрилось (мережа, сервер Apple) — далі, а не чорний екран.
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) = onDone()
                    })
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(slide.url))
                    prepare()
                    playWhenReady = true
                }
                androidx.media3.ui.PlayerView(context).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            onRelease = { view ->
                view.player?.release()
                view.player = null
            },
        )
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 44.dp)
                .clip(RoundedCornerShape(12.dp)).background(Plate).padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(slide.name, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), color = Color.White)
            Text(stringResource(R.string.screensaver_aerial_source), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = LikhtarColors.Paper)
        }
    }
}

@Composable
private fun TitleFrame(slide: ScreensaverActivity.Slide.Title, onDone: () -> Unit) {
    LaunchedEffect(slide) {
        delay(FRAME_MS)
        onDone()
    }
    // Кадр повільно пливе — як герой Кінозалу.
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = TmdbImages.hero(slide.item.backdropPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val scale = 1.04f + 0.06f * drift
                scaleX = scale
                scaleY = scale
                translationX = -size.width * 0.016f * drift
                translationY = -size.height * 0.01f * drift
            },
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 44.dp).widthIn(max = 430.dp)
                .clip(RoundedCornerShape(12.dp)).background(Plate).padding(horizontal = 19.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    stringResource(R.string.screensaver_new_on, slide.brand),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp),
                    color = LikhtarColors.Amber,
                )
                if (slide.item.year.isNotBlank()) {
                    Box(Modifier.width(1.5.dp).height(13.dp).background(LikhtarColors.Amber))
                    Text(slide.item.year, style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = LikhtarColors.Paper)
                }
            }
            val logo = TmdbImages.logo(slide.item.logoPath)
            if (logo != null) {
                AsyncImage(model = logo, contentDescription = slide.item.title, modifier = Modifier.height(62.dp).widthIn(max = 330.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
            } else {
                Text(slide.item.title, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp), color = Color.White, maxLines = 2)
            }
            Text(stringResource(R.string.screensaver_open_hint), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = LikhtarColors.Paper)
        }
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    // Мова — з конфігурації композиції: змінили мову — годинник перемальовується нею ж.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(10_000)
        }
    }
    val shadow = androidx.compose.ui.graphics.Shadow(LikhtarColors.TextShadow, blurRadius = 28f)
    Column(modifier) {
        Text(
            SimpleDateFormat("HH:mm", locale).format(now),
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, letterSpacing = (-2).sp, shadow = shadow),
            color = Color.White,
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", locale).format(now).replaceFirstChar { it.uppercase() },
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, shadow = shadow),
            color = Color.White,
        )
    }
}
