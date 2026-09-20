package com.adaptiq.tutor.engine

import android.content.Context
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
    private val _downloadState = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadState: StateFlow<Map<String, DownloadProgress>> = _downloadState.asStateFlow()

    // We will use 0x3's HF repos as placeholders for the MNN files
    // If a specific file is 404, we just skip it (some models don't have .weight files)
    private val requiredFiles = listOf(
        "config.json",
        "llm.mnn",
        "llm.mnn.weight",
        "tokenizer.txt"
    )

    suspend fun downloadModel(modelId: String, repoId: String) {
        withContext(Dispatchers.IO) {
            val modelDir = File(context.getExternalFilesDir("models"), modelId)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            _downloadState.value = _downloadState.value.toMutableMap().apply {
                this[modelId] = DownloadProgress(modelId, 0f, true)
            }

            try {
                // First get the total size for all files to calculate accurate progress
                var totalBytesToDownload = 0L
                var totalBytesDownloaded = 0L

                val validFiles = mutableListOf<Pair<String, Long>>()

                for (fileName in requiredFiles) {
                    val url = URL("https://huggingface.co/$repoId/resolve/main/$fileName")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 5000
                    
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val size = conn.contentLength.toLong()
                        if (size > 0) {
                            validFiles.add(Pair(fileName, size))
                            totalBytesToDownload += size
                        }
                    }
                    conn.disconnect()
                }

                if (validFiles.isEmpty()) {
                    throw Exception("No model files found in repository")
                }

                // Download each file
                for ((fileName, _) in validFiles) {
                    val url = URL("https://huggingface.co/$repoId/resolve/main/$fileName")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 30000
                    
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        continue // Skip if suddenly unavailable
                    }
                    
                    val file = File(modelDir, fileName)
                    val inputStream = conn.inputStream
                    val outputStream = FileOutputStream(file)
                    
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesDownloaded += bytesRead
                        
                        val progress = if (totalBytesToDownload > 0) {
                            totalBytesDownloaded.toFloat() / totalBytesToDownload.toFloat()
                        } else {
                            0f
                        }
                        
                        _downloadState.value = _downloadState.value.toMutableMap().apply {
                            this[modelId] = DownloadProgress(modelId, progress, true)
                        }
                    }
                    
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    conn.disconnect()
                }

                _downloadState.value = _downloadState.value.toMutableMap().apply {
                    this[modelId] = DownloadProgress(modelId, 1f, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadState.value = _downloadState.value.toMutableMap().apply {
                    this[modelId] = DownloadProgress(modelId, 0f, false, e.message ?: "Download failed")
                }
                
                // Cleanup partial downloads
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
        return modelDir.exists() && config.exists()
    }
}
