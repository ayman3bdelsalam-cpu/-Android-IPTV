package com.ayman.smartiptv

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ayman.smartiptv.data.model.ContentType
import com.ayman.smartiptv.data.model.StreamItem
import com.ayman.smartiptv.ui.screens.*
import com.ayman.smartiptv.ui.theme.AymanIPTVTheme
import com.ayman.smartiptv.ui.theme.BgDark
import com.ayman.smartiptv.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var playerActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AymanIPTVTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
                    AppNavigation(
                        viewModel = viewModel,
                        onPlayerActiveChanged = { active ->
                            playerActive = active
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    setPictureInPictureParams(
                                        PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .setAutoEnterEnabled(active)
                                            .build()
                                    )
                                } catch (_: Exception) { }
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Enter native Android Picture-in-Picture when Home/Recents is pressed while
     * media is playing. PlayerScreen keeps playback alive while the Activity is in PiP.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: AppViewModel,
    onPlayerActiveChanged: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    var checkedAutoLogin by remember { mutableStateOf(false) }
    var autoLoginResult by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        viewModel.tryAutoLogin { success ->
            autoLoginResult = success
            checkedAutoLogin = true
        }
    }

    if (!checkedAutoLogin) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().background(BgDark),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }

    val startDestination = if (autoLoginResult == true) "dashboard" else "login"

    var pendingPlayback by remember { mutableStateOf<PlaybackTarget?>(null) }
    var pendingSeries by remember { mutableStateOf<StreamItem?>(null) }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("login") {
            LoginScreen(viewModel) {
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }

        composable("dashboard") {
            DashboardScreen(
                onSelect = { type -> navController.navigate("content/${type.name}") },
                onLogout = {
                    viewModel.logout {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            "content/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("type") ?: "LIVE"
            val type = ContentType.valueOf(typeStr)

            ContentScreen(
                viewModel = viewModel,
                type = type,
                onBack = { navController.popBackStack() },
                onPlayItem = { item ->
                    val urls = viewModel.buildPlaybackUrls(type, item)
                    if (urls.isNotEmpty()) {
                        val storageKey = "${type.name}:${item.id}"
                        pendingPlayback = PlaybackTarget(
                            url = urls.first(),
                            fallbackUrls = urls.drop(1),
                            title = item.displayName,
                            isSeekable = type != ContentType.LIVE,
                            type = type,
                            storageKey = storageKey,
                            itemId = item.id,
                            icon = item.posterUrl,
                            ext = item.containerExtension ?: "mp4",
                            startPositionMs = if (type != ContentType.LIVE) {
                                viewModel.getResumePosition(storageKey)
                            } else 0L
                        )
                        navController.navigate("player")
                    }
                },
                onOpenSeries = { series ->
                    pendingSeries = series
                    navController.navigate("series_detail")
                }
            )
        }

        composable("series_detail") {
            val series = pendingSeries
            if (series != null) {
                SeriesDetailScreen(
                    viewModel = viewModel,
                    series = series,
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = { episode, _ ->
                        val url = viewModel.buildEpisodeUrl(episode)
                        val storageKey = "SERIES:${episode.id}"
                        pendingPlayback = PlaybackTarget(
                            url = url,
                            title = "${series.displayName} - حلقة ${episode.episodeNum ?: ""}",
                            isSeekable = true,
                            type = ContentType.SERIES,
                            storageKey = storageKey,
                            itemId = episode.id.toIntOrNull() ?: 0,
                            icon = series.posterUrl,
                            ext = episode.containerExtension ?: "mp4",
                            startPositionMs = viewModel.getResumePosition(storageKey)
                        )
                        navController.navigate("player")
                    }
                )
            }
        }

        composable("player") {
            val target = pendingPlayback
            if (target != null) {
                PlayerScreen(
                    viewModel = viewModel,
                    target = target,
                    onBack = { navController.popBackStack() },
                    onPlayerActiveChanged = onPlayerActiveChanged
                )
            }
        }
    }
}
