package dev.letconst.susan.utils

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
            return@withContext response.body?.string() ?: ""
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
