package com.example.susan

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.susan.ui.theme.SusanTheme
import com.example.susan.utils.fetchApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SusanTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    SusanAppLayout(
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .safeContentPadding()
                            .background(color = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    }
}

@Composable
fun SusanAppLayout(
    snackbarHostState: SnackbarHostState,
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
        VideoLinkForm(snackbarHostState)
    }
}

@Composable
fun VideoLinkForm(
    snackbarHostState: SnackbarHostState,
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
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索图标",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                snackbarHostState,
                                context
                            )
                        }
                    } else {
                        isUrlValid = false
                    }
                }
            ),
            isError = !isUrlValid,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                cursorColor = Color.Black,
                errorBorderColor = Color.Red
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        if (!isUrlValid) {
            Text(
                text = stringResource(id = R.string.video_link_error_hint),
                color = Color.Red,
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
            Button(
                onClick = { 
                    focusManager.clearFocus()
                    currentJob = coroutineScope.launch {
                        handlePlayButtonClick(
                            videoLink,
                            apiUrl,
                            { isUrlValid = it },
                            { isLoading = it },
                            snackbarHostState,
                            context
                        )
                    }
                },
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                enabled = !isLoading,
                modifier = Modifier.size(64.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            AnimatedVisibility(
                visible = videoLink.isNotEmpty(),
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                OutlinedButton(
                    onClick = {
                        videoLink = ""
                        isUrlValid = true
                        isLoading = false
                        currentJob?.cancel()
                        currentJob = null
                    },
                    contentPadding = PaddingValues(0.dp),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear",
                        tint = Color.Black,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

suspend fun handlePlayButtonClick(
    videoLink: String,
    apiUrl: String,
    updateUrlValidity: (Boolean) -> Unit,
    updateLoadingState: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    context: Context
) {
    val isValid = isValidUrl(videoLink)
    updateUrlValidity(isValid)
    
    if (isValid) {
        updateLoadingState(true)
        try {
            val response = fetchApiResponse(apiUrl, videoLink)
            Log.d("API_RESPONSE", "响应内容: $response")
            
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
                    snackbarHostState.showSnackbar("解析失败，请稍后重试")
                }
            }
        } finally {
            updateLoadingState(false)
        }
    } else {
        Log.d("URL_VALIDATION", "无效的URL")
        snackbarHostState.showSnackbar("请输入正确的URL")
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