package com.adaptiq.tutor.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val modelId: String,
    val progress: Float, // 0.0 to 1.0
    val isDownloading: Boolean,
    val error: String? = null
)

class ModelDownloader(private val context: Context) {
    companion object {
        private const val TAG = "ModelDownloader"
    }

    private val _downloadState = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadState: StateFlow<Map<String, DownloadProgress>> = _downloadState.asStateFlow()

    // Required files for MNN model execution
    private val candidateFiles = listOf(
        "config.json",
        "llm.mnn",
        "llm.mnn.weight",
        "tokenizer.txt"
    )

    private fun openConnectionWithRedirects(initialUrl: String, method: String = "GET"): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 10) {
            val urlObj = URL(currentUrl)
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "AdaptIQ-Android/1.0")

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308) {
                val newLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (!newLocation.isNullOrBlank()) {
                    currentUrl = if (newLocation.startsWith("http")) {
                        newLocation
                    } else {
                        URL(urlObj, newLocation).toString()
                    }
                    redirects++
                    continue
                }
            }
            return conn
        }
        return URL(currentUrl).openConnection() as HttpURLConnection
    }

    suspend fun downloadModel(modelId: String, repoId: String) {
        withContext(Dispatchers.IO) {
            val modelDir = File(context.getExternalFilesDir("models"), modelId)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            _downloadState.value = _downloadState.value.toMutableMap().apply {
                this[modelId] = DownloadProgress(modelId, 0.05f, true)
            }

            try {
                // Discover which files exist in the repository
                val filesToDownload = mutableListOf<Pair<String, Long>>()
                var totalBytes = 0L

                for (fileName in candidateFiles) {
                    val rawUrl = "https://huggingface.co/$repoId/resolve/main/$fileName"
                    try {
                        val headConn = openConnectionWithRedirects(rawUrl, "HEAD")
                        val code = headConn.responseCode
                        val length = headConn.contentLengthLong
                        headConn.disconnect()

                        if (code in 200..299) {
                            val validLength = if (length > 0) length else 1024L * 1024L
                            filesToDownload.add(Pair(fileName, validLength))
                            totalBytes += validLength
                            Log.i(TAG, "Found file: $fileName (${validLength} bytes)")
                        } else {
                            Log.w(TAG, "File $fileName not available (HTTP $code)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed checking $fileName: ${e.message}")
                    }
                }

                if (filesToDownload.none { it.first == "config.json" }) {
                    throw Exception("Repository $repoId does not contain config.json")
                }

                var bytesDownloaded = 0L

                for ((fileName, expectedLength) in filesToDownload) {
                    val rawUrl = "https://huggingface.co/$repoId/resolve/main/$fileName"
                    val conn = openConnectionWithRedirects(rawUrl, "GET")
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        conn.disconnect()
                        continue
                    }

                    val targetFile = File(modelDir, fileName)
                    val inputStream = conn.inputStream
                    val outputStream = FileOutputStream(targetFile)

                    val buffer = ByteArray(32768)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val calculatedProgress = if (totalBytes > 0) {
                            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                        } else {
                            0.5f
                        }

                        _downloadState.value = _downloadState.value.toMutableMap().apply {
                            this[modelId] = DownloadProgress(modelId, calculatedProgress, true)
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    conn.disconnect()
                }

                // Verify minimal requirements: config.json must exist
                val config = File(modelDir, "config.json")
                if (!config.exists() || config.length() == 0L) {
                    throw Exception("Download incomplete: config.json missing or empty")
                }

                _downloadState.value = _downloadState.value.toMutableMap().apply {
                    this[modelId] = DownloadProgress(modelId, 1f, false)
                }
                Log.i(TAG, "Model $modelId downloaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $modelId", e)
                _downloadState.value = _downloadState.value.toMutableMap().apply {
                    this[modelId] = DownloadProgress(modelId, 0f, false, e.message ?: "Download failed")
                }
                modelDir.deleteRecursively()
            }
        }
    }

    fun deleteModel(modelId: String) {
        val modelDir = File(context.getExternalFilesDir("models"), modelId)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        _downloadState.value = _downloadState.value.toMutableMap().apply {
            this.remove(modelId)
        }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val modelDir = File(context.getExternalFilesDir("models"), modelId)
        val config = File(modelDir, "config.json")
        return modelDir.exists() && config.exists() && config.length() > 0
    }
}
