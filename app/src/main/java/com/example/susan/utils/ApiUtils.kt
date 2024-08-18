package com.example.susan.utils

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