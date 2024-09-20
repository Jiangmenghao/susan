package dev.letconst.susan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.letconst.susan.ui.theme.SusanTheme
import dev.letconst.susan.utils.fetchSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SusanTheme {
                SearchScreen(onBackPressed = { finish() })
            }
        }
    }
}

private const val PREFS_NAME = "SearchHistoryPrefs"
private const val HISTORY_KEY = "search_history"

private fun saveSearchHistory(context: Context, history: List<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = Gson().toJson(history)
    prefs.edit().putString(HISTORY_KEY, jsonString).apply()
}

private fun loadSearchHistory(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(HISTORY_KEY, null)
    return if (jsonString != null) {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson(jsonString, type)
    } else {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackPressed: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    val searchApi = stringResource(id = R.string.search_api)
    var searchResults by remember { mutableStateOf(listOf<SearchResultItem>()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var searchHistory by remember { mutableStateOf(loadSearchHistory(context)) }

    fun handleSearch(keyword: String) {
        isLoading = true

        searchHistory = (listOf(keyword) + searchHistory.filter { it != keyword }).take(10)
        saveSearchHistory(context, searchHistory)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = fetchSearchResult(apiUrl = searchApi, keyword = keyword)
                isLoading = false

                val list = result.getJSONArray("list")
                val array = mutableListOf<SearchResultItem>()

                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    array += SearchResultItem(
                        id = item.getInt("vod_id"),
                        name = item.getString("vod_name"),
                        typeName = item.getString("type_name"),
                        remarks = item.getString("vod_remarks")
                    )
                }

                searchResults = array
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${e.message}，请重试", Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoading = false
            }
        }
    }

    SearchBar(
        query = query,
        onQueryChange = { query = it },
        onSearch = {
            keyboardController?.hide()
            if (query.isNotEmpty()) {
                handleSearch(query)
            } else {
                Toast.makeText(context, "请输入影片名称", Toast.LENGTH_SHORT).show()
            }
        },
        active = active,
        onActiveChange = {
            active = it
            if (!it) {
                onBackPressed()
            }
        },
        placeholder = { Text("请输入影片名称，例如：庆余年") },
        leadingIcon = {
            IconButton(onClick = { onBackPressed() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        trailingIcon = {
            IconButton(onClick = {
                query = ""
                searchResults = listOf()
            }) {
                Icon(Icons.Default.Clear, contentDescription = "清空")
            }
        }
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                CircularProgressIndicator()
            }
        } else if (query.isEmpty() && searchResults.isEmpty() && searchHistory.isNotEmpty()) {
            Column {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "历史记录",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = {
                            searchHistory = emptyList()
                            saveSearchHistory(context, searchHistory)
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "清空历史记录"
                        )
                    }
                }

                LazyColumn {
                    items(searchHistory) { historyItem ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = historyItem
                                    handleSearch(historyItem)
                                }
                                .padding(16.dp)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.baseline_history_24), contentDescription = null)
                            Text(
                                text = historyItem,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        } else {
            SearchResult(
                resultList = searchResults
            )
        }
    }
}

@Composable
fun SearchResult(
    resultList: List<SearchResultItem>
) {
    val context = LocalContext.current
    fun viewDetail(id: Int) {
        val intent = Intent(context, DetailActivity::class.java).apply {
            putExtra("EXTRA_ID", id)
        }
        context.startActivity(intent)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        items(resultList) { item ->
            Card(
                elevation = CardDefaults.elevatedCardElevation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { viewDetail(id = item.id) })
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "${item.name}（${item.typeName}）", style = MaterialTheme.typography.titleMedium)
                        if (item.remarks.isNotEmpty()) {
                            Text(text = item.remarks, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    IconButton(onClick = { viewDetail(id = item.id) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "剧集列表")
                    }
                }
            }
        }
    }
}