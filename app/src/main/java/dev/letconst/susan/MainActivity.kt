package dev.letconst.susan

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import dev.letconst.susan.ui.theme.SusanTheme
import dev.letconst.susan.utils.fetchApiResponse
import dev.letconst.susan.utils.formatUrl
import dev.letconst.susan.viewmodels.UpdateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URL

class MainActivity : ComponentActivity() {
    private var backPressedTime: Long = 0
    private val backPressInterval = 2000
    private lateinit var updateViewModel: UpdateViewModel
    private var showDownloadProgress by mutableStateOf(false)
    private var downloadProgress by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[UpdateViewModel::class.java]

        updateViewModel.updateAvailable.observe(this) { isAvailable ->
            if (isAvailable && !updateViewModel.updateDialogShown) {
                showUpdateDialog()
                updateViewModel.setUpdateDialogShown(true)
            }
        }

        updateViewModel.downloadProgress.observe(this) { progress ->
            downloadProgress = progress.toFloat() / 100
        }

        updateViewModel.checkForUpdates()

        enableEdgeToEdge()
        setContent {
            SusanTheme {
                val context = LocalContext.current
                DisposableEffect(Unit) {
                    val callback = object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            if (backPressedTime + backPressInterval > System.currentTimeMillis()) {
                                finish()
                            } else {
                                Toast.makeText(context, "再按一次退出Susan", Toast.LENGTH_SHORT).show()
                                backPressedTime = System.currentTimeMillis()
                            }
                        }
                    }
                    onBackPressedDispatcher.addCallback(callback)
                    onDispose {
                        callback.remove()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    SusanAppLayout(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .safeContentPadding()
                            .background(color = MaterialTheme.colorScheme.surface)
                    )
                }

                if (showDownloadProgress) {
                    UpdateProgressSheet(
                        downloadProgress = downloadProgress,
                        onDismissRequest = { /* 不允许用户关闭 */ }
                    )
                }
            }
        }
    }

    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle("有版本更新 (${updateViewModel.updateVersion.value})")
            .setMessage(updateViewModel.updateDescription.value)
            .setPositiveButton("立即更新") { _, _ ->
                updateViewModel.downloadAndInstallApk()
                showDownloadProgress = true
            }
            .setNegativeButton("下次提醒", null)
            .show()
    }
}

@Composable
fun SusanAppLayout(
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        VideoLinkForm()
    }
}

@Composable
fun VideoLinkForm(
    modifier: Modifier = Modifier
) {
    var videoLink by remember { mutableStateOf("") }
    var isUrlValid by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val apiUrl = stringResource(id = R.string.api_url)
    var currentJob by remember { mutableStateOf<Job?>(null) }
    
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding()
    ) {
        OutlinedTextField(
            value = videoLink,
            onValueChange = { 
                videoLink = it
                isUrlValid = true
            },
            placeholder = {
                Text(
                    text = stringResource(id = R.string.video_link_input_placeholder),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 24.dp)
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_link_24),
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(32.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Uri
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (isValidUrl(videoLink)) {
                        currentJob = coroutineScope.launch {
                            handlePlayButtonClick(
                                videoLink,
                                apiUrl,
                                { isUrlValid = it },
                                { isLoading = it },
                                context
                            )
                        }
                    } else {
                        isUrlValid = false
                    }
                }
            ),
            isError = !isUrlValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        if (!isUrlValid) {
            Text(
                text = stringResource(id = R.string.video_link_error_hint),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.padding(vertical = 24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.animateContentSize(
                animationSpec = tween(durationMillis = 300)
            )
        ) {
            FilledIconButton(
                onClick = { 
                    focusManager.clearFocus()
                    currentJob = coroutineScope.launch {
                        handlePlayButtonClick(
                            videoLink,
                            apiUrl,
                            { isUrlValid = it },
                            { isLoading = it },
                            context
                        )
                    }
                },
                shape = CircleShape,
                enabled = !isLoading,
                modifier = Modifier.size(64.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            AnimatedVisibility(
                visible = videoLink.isNotEmpty(),
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                FilledTonalIconButton(
                    onClick = {
                        videoLink = ""
                        isUrlValid = true
                        isLoading = false
                        currentJob?.cancel()
                        currentJob = null
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateProgressSheet(
    downloadProgress: Float,
    onDismissRequest: () -> Unit
) {
    var isInstalling by remember { mutableStateOf(false) }

    LaunchedEffect(downloadProgress) {
        if (downloadProgress >= 1f) {
            isInstalling = true
        }
    }

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isInstalling) "安装中" else "正在下载更新")
            Spacer(modifier = Modifier.height(16.dp))
            if (!isInstalling) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(downloadProgress * 100).toInt()}%")
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

suspend fun handlePlayButtonClick(
    videoLink: String,
    apiUrl: String,
    updateUrlValidity: (Boolean) -> Unit,
    updateLoadingState: (Boolean) -> Unit,
    context: Context
) {
    val isValid = isValidUrl(videoLink)
    updateUrlValidity(isValid)
    
    if (isValid) {
        updateLoadingState(true)
        try {
            val response = fetchApiResponse(apiUrl, formatUrl(videoLink))
            
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra("API_RESPONSE", response)
            }
            
            if (context is Activity) {
                val options = ActivityOptions.makeCustomAnimation(
                    context,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            when (e) {
                is CancellationException -> Log.d("API_CANCELLED", "API请求已取消")
                else -> {
                    Log.e("API_ERROR", "解析失败: ${e.message}")
                    Toast.makeText(context, "解析失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            updateLoadingState(false)
        }
    } else {
        Log.d("URL_VALIDATION", "无效的URL")
        Toast.makeText(context, "请输入正确的URL", Toast.LENGTH_SHORT).show()
    }
}

fun isValidUrl(url: String): Boolean {
    return try {
        URL(url).toURI()
        true
    } catch (e: Exception) {
        false
    }
}