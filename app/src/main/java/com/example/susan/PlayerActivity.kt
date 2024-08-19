package com.example.susan

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
import com.example.susan.ui.theme.SusanTheme
import com.example.susan.utils.fetchApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PlayerActivity : ComponentActivity() {
    private var apiResponse by mutableStateOf<JSONObject?>(null)
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var insetsController: WindowInsetsControllerCompat
    private val _isLoading = MutableStateFlow(false)
    private val isLoading = _isLoading.asStateFlow()

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
            apiResponse?.let { response ->
                val mediaItem = MediaItem.fromUri(response.getString("url"))
                setMediaItem(mediaItem)
                playWhenReady = true
                prepare()
            }
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
                    apiResponse?.let { response ->
                        PlayerScreen(
                            apiResponse = response,
                            exoPlayer = exoPlayer,
                            updateSystemUiVisibility = ::updateSystemUiVisibility,
                            onNextEpisode = { nextUrl ->
                                handleNextEpisode(nextUrl)
                            },
                            isLoading = isLoading.collectAsState().value,
                            onBackPressed = { finish() }
                        )
                    } ?: Text("视频信息未完全初始化")
                }
            }
        }
    }

    private fun handleNextEpisode(nextUrl: String) {
        if (_isLoading.value) return

        lifecycleScope.launch {
            _isLoading.value = true
            try {
                val apiUrl = getString(R.string.api_url)
                val response = fetchApiResponse(apiUrl, nextUrl)
                Log.d("API_RESPONSE", "响应内容: $response")
                withContext(Dispatchers.Main) {
                    apiResponse = JSONObject(response)
                    apiResponse?.let { newApiResponse ->
                        exoPlayer.apply {
                            val mediaItem = MediaItem.fromUri(newApiResponse.getString("url"))
                            setMediaItem(mediaItem)
                            prepare()
                            play()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "获取下一集失败", e)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun updateSystemUiVisibility(isLandscape: Boolean, controllerVisible: Boolean) {
        if (isLandscape) {
            insetsController.hide(WindowInsetsCompat.Type.navigationBars())
            
            if (controllerVisible) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
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

    override fun onPause() {
        super.onPause()
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
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
    updateSystemUiVisibility: (Boolean, Boolean) -> Unit,
    onNextEpisode: (String) -> Unit,
    isLoading: Boolean,
    onBackPressed: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var controllerVisible by remember { mutableStateOf(true) }

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(isLandscape, controllerVisible) {
        updateSystemUiVisibility(isLandscape, controllerVisible)
    }

    val hasNext by remember(apiResponse) {
        mutableStateOf(apiResponse.has("next") && apiResponse.getString("next").isNotEmpty())
    }

    LaunchedEffect(hasNext) {
        Log.d("PlayerScreen", "hasNext changed: $hasNext")
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
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = { Box(modifier = Modifier.width(68.dp)) }
                )
            }
        ) { innerPadding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.animateContentSize(
                        animationSpec = tween(durationMillis = 300)
                    )
                ) {
                    Button(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.size(64.dp)
                    ) {
                        if (isPlaying) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_pause_24),
                                contentDescription = "暂停",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = hasNext,
                        enter = fadeIn() + slideInHorizontally(),
                        exit = fadeOut() + slideOutHorizontally()
                    ) {
                        Button(
                            onClick = {
                                if (!isLoading) {
                                    val nextUrl = apiResponse.getString("next")
                                    onNextEpisode(nextUrl)
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.size(64.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "下一集",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
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