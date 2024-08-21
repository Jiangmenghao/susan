package com.example.susan

import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.susan.ui.theme.SusanTheme
import org.json.JSONObject

class PlayerActivity : ComponentActivity() {
    private var videoData by mutableStateOf<Video?>(null)
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoDataString = intent.getStringExtra("API_RESPONSE")
        videoDataString?.let {
            val jsonData = JSONObject(videoDataString)
            val video = Video(
                url = jsonData.getString("url"),
                name = if (jsonData.has("name")) jsonData.getString("name") else null,
                next = if (jsonData.has("next")) jsonData.getString("next") else null,
                ggdmapi = if (jsonData.has("ggdmapi")) jsonData.getString("ggdmapi") else null
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

        enableEdgeToEdge()
        setContent {
            SusanTheme {
                videoData?.let {
                    AppLayout(
                        video = it,
                        player = player,
                        onBackPressed = { finish() }
                    )
                } ?: Text("视频信息未完全初始化")
            }
        }
    }

    private fun updateScreenWakeState(isPlaying: Boolean) {
        if (isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        LandscapeLayout(
            player = player,
            modifier = modifier
        )
    } else {
        PortraitLayout(
            player = player,
            video = video,
            onBackPressed = onBackPressed,
            modifier = modifier
        )
    }
}

@Composable
fun LandscapeLayout(player: ExoPlayer, modifier: Modifier = Modifier) {
    VideoPlayer(player = player, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitLayout(
    player: ExoPlayer,
    video: Video,
    onBackPressed: () -> Unit,
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