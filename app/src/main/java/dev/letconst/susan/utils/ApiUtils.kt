package dev.letconst.susan.utils

import android.net.Uri
import dev.letconst.susan.EpisodeItem
import dev.letconst.susan.PlatformUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

suspend fun fetchApiResponse(apiUrl: String, videoLink: String): String {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        
        val json = JSONObject().apply {
            put("videoLink", videoLink)
        }.toString()

        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            
            val responseBody = response.body?.string() ?: "{}"
            val jsonResponse = JSONObject(responseBody).apply {
                put("current", videoLink)
            }
            return@withContext jsonResponse.toString()
        }
    }
}

data class Platform(val id: Int, val name: String, val urls: List<String>)

val platforms = listOf(
    Platform(1, "爱奇艺", listOf("iqiyi.com", "iqiyi.cn", "iq.com")),
    Platform(2, "优酷", listOf("youku.com")),
    Platform(3, "腾讯", listOf("qq.com")),
    Platform(4, "芒果", listOf("mgtv.com")),
    Platform(5, "乐视", listOf("le.com")),
    Platform(6, "搜狐", listOf("sohu.com")),
    Platform(7, "抖音", listOf("douyin.com")),
    Platform(8, "哔哩哔哩", listOf("bilibili.com", "b23.tv")),
    Platform(9, "咪咕", listOf("miguvideo.com")),
    Platform(10, "其他", emptyList())
)

fun getPlatformId(videoLink: String): Int {
    var id = 10
    platforms.forEach { platform ->
        if (platform.urls.any { videoLink.contains(it) }) {
            id = platform.id
        }
    }
    return id
}

fun formatUrl(url: String): String {
    val platformId = getPlatformId(videoLink = url)

    return when (platformId) {
        1 -> formatIqiyiUrl(url = url)
        3 -> formatWeTVUrl(url = url)
        8 -> formatBilibiliUrl(url = url)
        else -> url
    }
}


fun formatBilibiliUrl(url: String): String {
    val prefix = "https://b23.tv/ep"
    val newPrefix = "https://www.bilibili.com/bangumi/play/ep"
    return if (url.startsWith(prefix)) {
        newPrefix + url.substring(prefix.length)
    } else {
        url
    }
}

fun formatWeTVUrl(url: String): String {
    val regex = """cid=([^&]+).*vid=([^&]+)|vid=([^&]+).*cid=([^&]+)""".toRegex()
    val matchResult = regex.find(url)

    if (matchResult != null) {
        val cid = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[4] }
        val vid = matchResult.groupValues[2].ifEmpty { matchResult.groupValues[3] }
        return "https://v.qq.com/x/cover/$cid/$vid.html"
    }

    return url
}

fun formatIqiyiUrl(url: String): String {
    var parsedUri = Uri.parse(url)

    if (parsedUri.toString().startsWith("https://m.iqiyi.com")) {
        parsedUri = Uri.parse(url.replace("https://m.iqiyi.com", "https://www.iqiyi.com"))
    }

    return parsedUri.scheme + "://" + parsedUri.host + parsedUri.path
}

fun fetchSearchResult(apiUrl: String, keyword: String): JSONObject {
    val query = "?wd=$keyword"
    val requestUrl = "$apiUrl$query"
    val client = OkHttpClient()
    
    val request = Request.Builder()
        .url(requestUrl)
        .get()
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        
        val responseBody = response.body?.string() ?: "{}"
        return JSONObject(responseBody)
    }
}

fun fetchSearchDetail(apiUrl: String, id: Int): JSONObject? {
    val query = "?ids=$id"
    val requestUrl = "$apiUrl$query"
    val client = OkHttpClient()

    val request = Request.Builder()
        .url(requestUrl)
        .get()
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseBody = response.body?.string() ?: "{}"
        val list = JSONObject(responseBody).getJSONArray("list")
        if (list.length() > 0) {
            return list.getJSONObject(0)
        } else {
            return null
        }
    }
}

fun parsePlayUrlsFromDetail(vodPlayFrom: String, vodPlayUrl: String): List<PlatformUrls> {
    fun formatPlatformName(platform: String): String {
        return when (platform) {
            "qiyi" -> "爱奇艺"
            "youku" -> "优酷"
            "qq" -> "腾讯"
            "mgtv" -> "芒果"
            "bilibili" -> "哔哩哔哩"
            else -> platform
        }
    }

    val platforms = vodPlayFrom.split("$$$").map { formatPlatformName(it) }
    val urlGroups = vodPlayUrl.split("$$$")

    return platforms.mapIndexed { index, platform ->
        val urls = urlGroups[index].split("#").map { url ->
            val (title, link) = url.split("$")
            EpisodeItem(title, link, false)
        }
        PlatformUrls(platform, urls)
    }
}
