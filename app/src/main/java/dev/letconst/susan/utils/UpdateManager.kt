package dev.letconst.susan.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.letconst.susan.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()

    fun checkForUpdates(onUpdateCheckCompleted: (isUpdateAvailable: Boolean, updateUrl: String?, updateDescription: String?, updateVersion: String?) -> Unit) {
        val request = Request.Builder()
            .url(context.getString(R.string.latest_release_api))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                onUpdateCheckCompleted(false, null, null, null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onUpdateCheckCompleted(false, null, null, null)
                        return
                    }

                    val responseData = response.body?.string()
                    if (responseData != null) {
                        val jsonObject = JSONObject(responseData)
                        val latestVersion = jsonObject.getString("name")
                        val updateUrl = jsonObject.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
                        val updateDescription = jsonObject.getString("body")

                        if (latestVersion > getCurrentVersion()) {
                            onUpdateCheckCompleted(true, updateUrl, updateDescription, latestVersion)
                        } else {
                            onUpdateCheckCompleted(false, null, null, null)
                        }
                    }
                }
            }
        })
    }

    fun downloadAndInstallApk(updateUrl: String, onProgressUpdate: (Int) -> Unit) {
        val request = Request.Builder().url(updateUrl).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    return
                }

                response.body?.let { responseBody ->
                    val fileName = updateUrl.substringAfterLast("/")
                    val file = File(context.getExternalFilesDir(null), fileName)

                    try {
                        val totalBytes = responseBody.contentLength()
                        var downloadedBytes = 0L

                        file.outputStream().use { fileOutputStream ->
                            responseBody.byteStream().use { inputStream ->
                                val buffer = ByteArray(8192)
                                var bytes: Int
                                while (inputStream.read(buffer).also { bytes = it } != -1) {
                                    fileOutputStream.write(buffer, 0, bytes)
                                    downloadedBytes += bytes
                                    val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                    onProgressUpdate(progress)
                                }
                            }
                        }

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private fun getCurrentVersion(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
}