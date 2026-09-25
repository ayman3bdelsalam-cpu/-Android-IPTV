package com.ayman.smartiptv.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.ayman.smartiptv.data.model.ContentType
import com.ayman.smartiptv.data.model.ResumeEntry
import com.ayman.smartiptv.ui.theme.AccentBlue
import com.ayman.smartiptv.ui.theme.AccentRed
import com.ayman.smartiptv.viewmodel.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackTarget(
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
    val title: String,
    val isSeekable: Boolean,
    val type: ContentType,
    val storageKey: String,
    val itemId: Int,
    val icon: String?,
    val ext: String,
    val startPositionMs: Long
)

private enum class VideoScaleMode(val label: String, val resizeMode: Int) {
    FIT("FIT", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("FILL", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("ZOOM", AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    fun next(): VideoScaleMode = when (this) {
        FIT -> FILL
        FILL -> ZOOM
        ZOOM -> FIT
    }

    companion object {
        fun fromName(name: String?): VideoScaleMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ZOOM
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: AppViewModel,
    target: PlaybackTarget,
    onBack: () -> Unit,
    onPlayerActiveChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val sourceUrls = remember(target.url, target.fallbackUrls) {
        (listOf(target.url) + target.fallbackUrls).filter { it.isNotBlank() }.distinct()
    }

    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var fatalError by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var videoScaleMode by remember { mutableStateOf(VideoScaleMode.ZOOM) }

    var currentSourceIndex by remember { mutableIntStateOf(0) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var hasPlayedCurrentSource by remember { mutableStateOf(false) }
    var retryJob by remember { mutableStateOf<Job?>(null) }

    var networkAvailable by remember { mutableStateOf(isNetworkAvailable(context)) }
    var hadNetworkLoss by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onPlayerActiveChanged(true)
        onDispose { onPlayerActiveChanged(false) }
    }

    // Restore the user's last FIT/FILL/ZOOM choice.
    LaunchedEffect(Unit) {
        videoScaleMode = VideoScaleMode.fromName(viewModel.getSavedVideoScaleMode())
    }

    // Landscape + immersive playback, and prevent screen sleep while media is open.
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        val window = activity?.window

        if (activity != null && window != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            if (activity != null && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                activity.requestedOrientation = previousOrientation
                    ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val loadControl = remember(target.type) {
        if (target.type == ContentType.LIVE) {
            // Low-latency live profile: starts quickly and avoids buffering huge chunks
            // before zapping to another channel.
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2_500,  // min buffer
                    15_000, // max buffer
                    300,    // start playback
                    750     // resume after rebuffer
                )
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,
                    50_000,
                    1_000,
                    2_000
                )
                .build()
        }
    }

    val exoPlayer = remember(target.storageKey) {
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    fun startSource(index: Int, initial: Boolean = false, preservePosition: Boolean = false) {
        if (index !in sourceUrls.indices) return

        val position = when {
            initial && target.startPositionMs > 0 -> target.startPositionMs
            preservePosition && target.isSeekable -> exoPlayer.currentPosition.coerceAtLeast(0L)
            else -> 0L
        }

        currentSourceIndex = index
        retryAttempt = 0
        hasPlayedCurrentSource = false
        fatalError = false
        isBuffering = true

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(sourceUrls[index]))
        if (position > 0) exoPlayer.seekTo(position)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun retryCurrentSource() {
        if (currentSourceIndex !in sourceUrls.indices) return
        val position = if (target.isSeekable) exoPlayer.currentPosition.coerceAtLeast(0L) else 0L
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(sourceUrls[currentSourceIndex]))
        if (position > 0) exoPlayer.seekTo(position)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        isBuffering = true
    }

    fun persistResumeSnapshot() {
        if (target.type == ContentType.LIVE) return
        val position = exoPlayer.currentPosition
        if (position <= 10_000) return

        viewModel.saveResume(
            ResumeEntry(
                storageKey = target.storageKey,
                name = target.title,
                icon = target.icon,
                positionMs = position,
                durationMs = exoPlayer.duration.coerceAtLeast(0),
                timestamp = System.currentTimeMillis(),
                type = target.type,
                ext = target.ext,
                itemId = target.itemId
            )
        )
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        isBuffering = false
                        retryAttempt = 0
                        hasPlayedCurrentSource = true
                        fatalError = false
                        errorMessage = null
                    }
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_ENDED -> isBuffering = false
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                retryJob?.cancel()
                retryJob = scope.launch {
                    if (!networkAvailable) {
                        hadNetworkLoss = true
                        errorMessage = "انقطع الاتصال بالإنترنت — بانتظار عودة الشبكة"
                        isBuffering = true
                        return@launch
                    }

                    // If HLS never managed to start, move to TS immediately instead
                    // of wasting several seconds retrying an incompatible format.
                    if (
                        target.type == ContentType.LIVE &&
                        !hasPlayedCurrentSource &&
                        currentSourceIndex < sourceUrls.lastIndex
                    ) {
                        errorMessage = "HLS غير متاح — جاري تجربة TS..."
                        delay(500)
                        startSource(currentSourceIndex + 1)
                        return@launch
                    }

                    val retryDelays = longArrayOf(1_000L, 2_000L, 4_000L)
                    if (retryAttempt < retryDelays.size) {
                        val waitMs = retryDelays[retryAttempt]
                        retryAttempt++
                        errorMessage = "إعادة الاتصال (${retryAttempt}/3)..."
                        isBuffering = true
                        delay(waitMs)
                        if (networkAvailable) retryCurrentSource()
                        return@launch
                    }

                    if (currentSourceIndex < sourceUrls.lastIndex) {
                        errorMessage = "جاري تجربة مصدر بث بديل..."
                        delay(500)
                        startSource(currentSourceIndex + 1)
                    } else {
                        fatalError = true
                        isBuffering = false
                        errorMessage = "تعذر تشغيل البث بعد المحاولات التلقائية"
                    }
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            retryJob?.cancel()
            exoPlayer.removeListener(listener)
        }
    }

    // Initial source. Live gets HLS first and TS as automatic fallback.
    LaunchedEffect(exoPlayer, sourceUrls) {
        if (sourceUrls.isNotEmpty()) startSource(0, initial = true)
        else {
            fatalError = true
            isBuffering = false
            errorMessage = "رابط التشغيل غير صالح"
        }
    }

    // Detect Wi-Fi/mobile-network loss and recover playback automatically when it returns.
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { networkAvailable = true }
            }

            override fun onLost(network: Network) {
                mainHandler.post { networkAvailable = isNetworkAvailable(context) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val connected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                mainHandler.post { networkAvailable = connected }
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) { }

        onDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(networkAvailable) {
        if (!networkAvailable) {
            retryJob?.cancel()
            hadNetworkLoss = true
            isBuffering = true
            errorMessage = "انقطع الاتصال بالإنترنت — بانتظار عودة الشبكة"
        } else if (hadNetworkLoss) {
            hadNetworkLoss = false
            fatalError = false
            retryAttempt = 0
            errorMessage = "تمت استعادة الشبكة — جاري إعادة التشغيل..."
            delay(600)
            retryCurrentSource()
        }
    }

    // Pause outside the app, but do NOT pause when Android moves the player into PiP.
    DisposableEffect(lifecycleOwner, exoPlayer, activity) {
        var wasPlayingBeforePause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        wasPlayingBeforePause = exoPlayer.isPlaying
                        persistResumeSnapshot()
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforePause) {
                        exoPlayer.play()
                        wasPlayingBeforePause = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Lower I/O than the old 20-second loop: save only while content is actually playing.
    LaunchedEffect(exoPlayer, target.storageKey) {
        while (isActive) {
            delay(45_000)
            if (exoPlayer.isPlaying) persistResumeSnapshot()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            persistResumeSnapshot()
            exoPlayer.release()
        }
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = videoScaleMode.resizeMode
                    controllerShowTimeoutMs = 3_000
                    controllerAutoShow = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        }
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = videoScaleMode.resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentBlue)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        errorMessage ?: "جارٍ الاتصال بالبث...",
                        color = AccentBlue,
                        fontSize = 15.sp
                    )
                }
            }
        }

        if (fatalError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage ?: "تعذر تشغيل البث", color = AccentRed, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                fatalError = false
                                retryAttempt = 0
                                errorMessage = "إعادة المحاولة..."
                                retryCurrentSource()
                            }
                        ) { Text("إعادة المحاولة") }
                        Button(onClick = onBack) { Text("رجوع") }
                    }
                }
            }
        }

        if (controlsVisible) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(AccentRed, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "خروج",
                    tint = Color.White
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerToolButton(videoScaleMode.label) {
                    videoScaleMode = videoScaleMode.next()
                    viewModel.saveVideoScaleMode(videoScaleMode.name)
                }

                PlayerToolButton("صوت") {
                    try {
                        TrackSelectionDialogBuilder(
                            context,
                            "مسار الصوت",
                            exoPlayer,
                            C.TRACK_TYPE_AUDIO
                        ).build().show()
                    } catch (_: Exception) { }
                }

                PlayerToolButton("ترجمة") {
                    try {
                        TrackSelectionDialogBuilder(
                            context,
                            "الترجمة",
                            exoPlayer,
                            C.TRACK_TYPE_TEXT
                        )
                            .setShowDisableOption(true)
                            .build()
                            .show()
                    } catch (_: Exception) { }
                }
            }
        }

        // Tiny non-intrusive status label helps diagnose automatic format fallback.
        if (controlsVisible && target.type == ContentType.LIVE && sourceUrls.size > 1) {
            val sourceLabel = if (sourceUrls[currentSourceIndex].endsWith(".ts", true)) "TS" else "HLS"
            Text(
                "AUTO • $sourceLabel",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 74.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PlayerToolButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
        shape = RoundedCornerShape(9.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}
