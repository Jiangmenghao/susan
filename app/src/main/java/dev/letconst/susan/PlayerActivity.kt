package dev.letconst.susan

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.letconst.susan.ui.theme.SusanTheme
import dev.letconst.susan.utils.fetchApiResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

class PlayerActivity : ComponentActivity() {
    private var videoData by mutableStateOf<Video?>(null)
    private lateinit var player: ExoPlayer
    private lateinit var audioManager: AudioManager
    private var isMuted by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private lateinit var snackbarHostState: SnackbarHostState
    private var snackbarJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoDataString = intent.getStringExtra("API_RESPONSE")
        videoDataString?.let {
            val jsonData = JSONObject(it)
            val episodesData = jsonData.optJSONObject("episodes")
            val video = Video(
                url = jsonData.getString("url"),
                name = if (jsonData.has("name")) {
                    jsonData.getString("name")
                } else {
                    episodesData?.getString("title")
                },
                next = if (jsonData.has("next")) jsonData.getString("next") else null,
                ggdmapi = if (jsonData.has("ggdmapi")) jsonData.getString("ggdmapi") else null,
                episodes = episodesData?.let { episodesObj ->
                    Episodes(
                        coverImage = episodesObj.getString("coverImage"),
                        title = episodesObj.getString("title"),
                        subtitles = episodesObj.getJSONArray("subtitles").let { subtitlesArray ->
                            List(subtitlesArray.length()) { index -> subtitlesArray.getString(index) }
                        },
                        description = episodesObj.getString("description"),
                        episodes = episodesObj.getJSONArray("episodes").let { episodesArray ->
                            List(episodesArray.length()) { index ->
                                val episode = episodesArray.getJSONObject(index)
                                EpisodeItem(
                                    title = episode.getString("title"),
                                    url = episode.getString("url"),
                                    active = episode.getBoolean("active")
                                )
                            }
                        }
                    )
                }
            )
            videoData = video
        }

        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateScreenWakeState(isPlaying)
            }
        })

        videoData?.let { video ->
            val mediaItem = MediaItem.fromUri(video.url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        snackbarHostState = SnackbarHostState()

        enableEdgeToEdge()
        setContent {
            SusanTheme {
                videoData?.let {
                    AppLayout(
                        video = it,
                        player = player,
                        window = window,
                        onBackPressed = { finish() },
                        isMuted = isMuted,
                        onToggleMute = { toggleMute() },
                        isLoading = isLoading,
                        onNextVideo = { loadNextVideo() },
                        snackbarHostState = snackbarHostState
                    )
                } ?: Text("视频信息未完全初始化")
            }
        }
    }

    private fun showSnackbar(message: String) {
        snackbarJob?.cancel()
        
        snackbarJob = lifecycleScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    private fun updateScreenWakeState(isPlaying: Boolean) {
        if (isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun changeVolume(increase: Boolean) {
        vibrateDevice()

        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            0
        )

        if (isMuted && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) {
            isMuted = false
        }

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercentage = (currentVolume.toFloat() / maxVolume * 100).toInt()

        showSnackbar("音量已调至：$volumePercentage%")
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0
        )
        vibrateDevice()
        showSnackbar(if (isMuted) "已静音" else "已取消静音")
    }

    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun loadNextVideo() {
        vibrateDevice()
        videoData?.next?.let { nextUrl ->
            isLoading = true
            showSnackbar("正在加载下一集")
            lifecycleScope.launch {
                try {
                    val apiUrl = getString(R.string.api_url)
                    val apiResponse = fetchApiResponse(apiUrl, nextUrl)
                    val jsonData = JSONObject(apiResponse)
                    val episodesData = jsonData.optJSONObject("episodes")
                    val newVideo = Video(
                        url = jsonData.getString("url"),
                        name = if (jsonData.has("name")) jsonData.getString("name") else episodesData?.getString("title"),
                        next = if (jsonData.has("next")) jsonData.getString("next") else null,
                        ggdmapi = if (jsonData.has("ggdmapi")) jsonData.getString("ggdmapi") else null,
                        episodes = episodesData?.let { episodesObj ->
                            Episodes(
                                coverImage = episodesObj.getString("coverImage"),
                                title = episodesObj.getString("title"),
                                subtitles = episodesObj.getJSONArray("subtitles").let { 
                                    List(it.length()) { index -> it.getString(index) } 
                                },
                                description = episodesObj.getString("description"),
                                episodes = episodesObj.getJSONArray("episodes").let {
                                    List(it.length()) { index ->
                                        val episode = it.getJSONObject(index)
                                        EpisodeItem(
                                            title = episode.getString("title"),
                                            url = episode.getString("url"),
                                            active = episode.getBoolean("active")
                                        )
                                    }
                                }
                            )
                        }
                    )
                    videoData = newVideo
                    player.setMediaItem(MediaItem.fromUri(newVideo.url))
                    player.prepare()
                    player.playWhenReady = true
                    showSnackbar("下一集加载完成")
                } catch (e: Exception) {
                    showSnackbar("加载下一集失败：${e.message}")
                } finally {
                    isLoading = false
                }
            }
        } ?: showSnackbar("无下一集")
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

@Composable
fun AppLayout(
    video: Video,
    player: ExoPlayer,
    onBackPressed: () -> Unit,
    window: Window,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isLoading: Boolean,
    onNextVideo: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    val context = LocalContext.current
    val activity = context as? PlayerActivity

    if (isLandscape) {
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        Toast.makeText(context, "旋转至竖屏，可退出全屏模式", Toast.LENGTH_SHORT).show()
        LandscapeLayout(
            player = player,
            modifier = modifier
        )
    } else {
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        windowInsetsController.show((WindowInsetsCompat.Type.systemBars()))
        PortraitLayout(
            player = player,
            video = video,
            onBackPressed = onBackPressed,
            snackbarHostState = snackbarHostState,
            onVolumeChange = { increase ->
                activity?.changeVolume(increase)
            },
            onToggleMute = onToggleMute,
            isMuted = isMuted,
            isLoading = isLoading,
            onNextVideo = onNextVideo,
            modifier = modifier
        )
    }
}

@Composable
fun LandscapeLayout(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    VideoPlayer(player = player, modifier = modifier.fillMaxSize())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitLayout(
    player: ExoPlayer,
    video: Video,
    onBackPressed: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onVolumeChange: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    isMuted: Boolean,
    isLoading: Boolean,
    onNextVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = video.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            VideoPlayer(
                player = player,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
                Text(
                    text = "旋转屏幕进入全屏模式"
                )
            }
            RemoteController(
                onVolumeChange = onVolumeChange,
                onToggleMute = onToggleMute,
                isMuted = isMuted,
                isLoading = isLoading,
                onNextVideo = onNextVideo,
                onBackPressed = onBackPressed,
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setShowPreviousButton(false)
                setShowNextButton(false)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

@Composable
fun RemoteController(
    onVolumeChange: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    isMuted: Boolean,
    isLoading: Boolean,
    onNextVideo: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onBackPressed() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "首页",
                    modifier = Modifier.size(32.dp)
                )
            }
            FilledTonalIconButton(
                onClick = onNextVideo,
                enabled = !isLoading,
                modifier = Modifier.size(64.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "下一集",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isMuted) R.drawable.baseline_volume_off_24
                        else R.drawable.baseline_volume_up_24
                    ),
                    contentDescription = if (isMuted) "取消静音" else "静音",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalIconButton(
                onClick = {},
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "菜单",
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(percent = 50))
            ) {
                FilledTonalIconButton(
                    onClick = { onVolumeChange(true) },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "音量增加",
                        modifier = Modifier.size(32.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = { onVolumeChange(false) },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_remove_24),
                        contentDescription = "音量减少",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}