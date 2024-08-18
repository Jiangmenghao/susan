package com.example.susan

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.susan.ui.theme.SusanTheme
import org.json.JSONObject

class PlayerActivity : ComponentActivity() {
    private lateinit var apiResponse: JSONObject
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var insetsController: WindowInsetsControllerCompat

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowInsetsControllerCompat(window, window.decorView)

        val apiResponseString = intent.getStringExtra("API_RESPONSE")
        apiResponseString?.let {
            apiResponse = JSONObject(it)
        }

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            val mediaItem = MediaItem.fromUri(apiResponse.getString("url"))
            setMediaItem(mediaItem)
            playWhenReady = true
            prepare()
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updateScreenOnFlag(state == Player.STATE_READY && exoPlayer.playWhenReady)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateScreenOnFlag(isPlaying)
            }
        })

        setContent {
            SusanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (::apiResponse.isInitialized) {
                        PlayerScreen(apiResponse, exoPlayer, ::updateSystemUiVisibility)
                    } else {
                        Text("视频信息未完全初始化")
                    }
                }
            }
        }
    }

    private fun updateSystemUiVisibility(isLandscape: Boolean, controllerVisible: Boolean) {
        if (isLandscape) {
            if (controllerVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun updateScreenOnFlag(keepScreenOn: Boolean) {
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    apiResponse: JSONObject, 
    exoPlayer: ExoPlayer, 
    updateSystemUiVisibility: (Boolean, Boolean) -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controllerVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isLandscape, controllerVisible) {
        updateSystemUiVisibility(isLandscape, controllerVisible)
    }

    if (isLandscape) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoPlayer(exoPlayer, isLandscape) { visible ->
                controllerVisible = visible
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = apiResponse.optString("name", ""),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* 处理返回操作 */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = { Box(modifier = Modifier.width(68.dp)) }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                VideoPlayer(exoPlayer, isLandscape) { visible ->
                    controllerVisible = visible
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "旋转屏幕进入全屏模式"
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(
    exoPlayer: ExoPlayer, 
    isLandscape: Boolean,
    onControllerVisibilityChanged: (Boolean) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = if (isLandscape) {
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                } else {
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    onControllerVisibilityChanged(visibility == View.VISIBLE)
                })
            }
        },
        modifier = if (isLandscape) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        }.background(Color.Black)
    )
}

@Composable
fun DanmakuView(danmakuApiUrl: String) {
    // 实现弹幕 View
    // 这里需要根据弹幕 API 的具体格式来实现
    // 可以使用自定义 View 或第三方弹幕库
}