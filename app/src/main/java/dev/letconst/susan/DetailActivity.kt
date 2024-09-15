package dev.letconst.susan

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.letconst.susan.ui.theme.SusanTheme
import dev.letconst.susan.utils.fetchApiResponse
import dev.letconst.susan.utils.fetchSearchDetail
import dev.letconst.susan.utils.parsePlayUrlsFromDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : ComponentActivity() {
    private var videoId: Int = -1
    private var videoDetail = mutableStateOf<VideoDetail?>(null)
    private var isLoading = mutableStateOf(false)

    private fun getDetail(id: Int) {
        isLoading.value = true
        val apiUrl = getString(R.string.detail_api)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = fetchSearchDetail(apiUrl = apiUrl, id = id)
                response?.let {
                    val detail = VideoDetail(
                        id = it.getInt("vod_id"),
                        name = it.getString("vod_name"),
                        blurb = it.getString("vod_blurb"),
                        pic = it.getString("vod_pic").replace("http://", "https://"),
                        genres = it.getString("vod_class"),
                        area = it.getString("vod_year"),
                        remarks = it.getString("vod_remarks"),
                        score = it.getString("vod_score"),
                        episodes = parsePlayUrlsFromDetail(response.getString("vod_play_from"), response.getString("vod_play_url"))
                    )
                    videoDetail.value = detail
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailActivity, "${e.message}，请重试", Toast.LENGTH_LONG).show()
                }
                finish()
            } finally {
                isLoading.value = false
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = intent.getIntExtra("EXTRA_ID", -1)
        if (videoId > 0) {
            getDetail(id = videoId)
        }

        enableEdgeToEdge()
        setContent {
            SusanTheme {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(videoDetail.value?.name ?: "") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        DetailScreen(
                            videoDetail = videoDetail.value,
                            isLoading = isLoading,
                            modifier = Modifier
                                .padding(innerPadding)
                        )
                        if (isLoading.value) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            )
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    videoDetail: VideoDetail?,
    isLoading: MutableState<Boolean>,
    modifier: Modifier = Modifier
) {
    videoDetail?.let {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            CoverImageAndInfo(videoDetail = videoDetail)

            EpisodesPanel(videoDetail = videoDetail, isLoading = isLoading)
        }
    } ?: run {
        Text(text = "Video detail is not available")
    }
}

@Composable
fun CoverImageAndInfo(
    videoDetail: VideoDetail
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
    ) {
        AsyncImage(
            model = videoDetail.pic,
            contentDescription = "封面图片",
            modifier = Modifier
                .weight(2f)
                .height(224.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(3f)
        ) {
            videoDetail.genres?.let { Text(text = "类型：${videoDetail.genres}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp)) }
            videoDetail.area?.let { Text(text = "地区：${videoDetail.area}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp)) }
            videoDetail.year?.let { Text(text = "年份：${videoDetail.year}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp)) }
            videoDetail.remarks?.let { Text(text = "${videoDetail.remarks}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp)) }
            videoDetail.score?.let { Text(text = "评分：${videoDetail.score}", style = MaterialTheme.typography.titleSmall) }
        }
    }

    videoDetail.blurb?.let {
        Card {
            Text(
                text = videoDetail.blurb,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun EpisodesPanel(
    videoDetail: VideoDetail,
    isLoading: MutableState<Boolean>
) {
    val tabLabels = videoDetail.episodes?.map { it.platform } ?: emptyList()
    var tabState by remember { mutableIntStateOf(0) }
    val selectedEpisodes = videoDetail.episodes?.get(tabState)?.urls ?: emptyList()
    val parseApi = stringResource(id = R.string.api_url)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun handleSelect(episode: EpisodeItem) {
        if (isLoading.value) return

        isLoading.value = true
        coroutineScope.launch {
            try {
                val response = fetchApiResponse(parseApi, episode.url)

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
                Log.e("API_ERROR", "解析失败: ${e.message}")
                Toast.makeText(context, "解析失败，请稍后重试", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading.value = false // 结束加载
            }
        }
    }

    ScrollableTabRow(
        selectedTabIndex = tabState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        tabLabels.forEachIndexed { index, label ->
            Tab(
                selected = tabState == index,
                onClick = { tabState = index }
            ) {
                if (tabState == index) {
                    FilledTonalButton(onClick = { tabState = index }) {
                        Text( text = label)
                    }
                } else {
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(selectedEpisodes) { episode ->
                EpisodeItemView(episode = episode, onSelect = { handleSelect(it) })
            }
        }
    }
}

@Composable
fun EpisodeItemView(episode: EpisodeItem, onSelect: (EpisodeItem) -> Unit) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .padding(8.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable { onSelect(episode) }
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = episode.title,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}
