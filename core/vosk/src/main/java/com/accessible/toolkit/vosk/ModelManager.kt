package com.accessible.toolkit.vosk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val DEFAULT_MODEL_NAME = "model"
        private const val MODEL_DIR = "vosk_models"
        private const val ASSET_MODEL_ZIP = "vosk-model-small-cn-0.22.zip"
    }

    private val modelDir: File by lazy {
        File(context.filesDir, MODEL_DIR).also { it.mkdirs() }
    }

    /**
     * 获取默认模型路径（assets 中的中文小模型）
     */
    fun getDefaultModelPath(): String {
        return File(modelDir, DEFAULT_MODEL_NAME).absolutePath
    }

    /**
     * 检查默认模型是否已解压
     */
    fun isDefaultModelExtracted(): Boolean {
        val modelPath = File(modelDir, DEFAULT_MODEL_NAME)
        return modelPath.exists() && modelPath.isDirectory && hasModelFiles(modelPath)
    }

    /**
     * 从 assets 解压模型（带进度回调）
     */
    fun extractModelWithProgress(): Flow<ExtractProgress> = flow {
        val outputDir = File(modelDir, DEFAULT_MODEL_NAME)

        if (isDefaultModelExtracted()) {
            emit(ExtractProgress.Complete(outputDir.absolutePath))
            return@flow
        }

        try {
            emit(ExtractProgress.Starting)

            val assetManager = context.assets
            val assetFiles = assetManager.list("") ?: emptyArray()

            // 尝试查找 zip 文件或模型目录
            val zipEntry = assetFiles.find { it.contains("vosk-model") && it.endsWith(".zip") }
            val modelEntry = assetFiles.find { it.contains("vosk-model") && !it.contains(".zip") }

            when {
                zipEntry != null -> {
                    extractFromZip(zipEntry, outputDir) { progress ->
                        // Note: Flow progress would need more complex implementation
                    }
                }
                modelEntry != null -> {
                    // 直接复制目录
                    copyAssetDirectory(modelEntry, outputDir)
                }
                else -> {
                    emit(ExtractProgress.Error("未找到模型文件，请将 vosk-model-small-cn-0.22 放到 assets 目录"))
                    return@flow
                }
            }

            if (hasModelFiles(outputDir)) {
                emit(ExtractProgress.Complete(outputDir.absolutePath))
            } else {
                emit(ExtractProgress.Error("模型文件不完整"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract model", e)
            emit(ExtractProgress.Error("解压失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 从 zip 文件解压模型
     */
    private fun extractFromZip(
        zipFileName: String,
        outputDir: File,
        onProgress: (Int) -> Unit
    ) {
        context.assets.open(zipFileName).use { inputStream ->
            ZipInputStream(inputStream).use { zipInputStream ->
                outputDir.mkdirs()
                var entry = zipInputStream.nextEntry
                var totalBytes = 0L

                while (entry != null) {
                    if (entry.isDirectory) {
                        File(outputDir, entry.name).mkdirs()
                    } else {
                        val outputFile = File(outputDir, entry.name)
                        outputFile.parentFile?.mkdirs()

                        FileOutputStream(outputFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                                totalBytes += len
                            }
                        }
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        }
    }

    /**
     * 复制 assets 目录到内部存储
     */
    private fun copyAssetDirectory(assetPath: String, outputDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: emptyArray()

        outputDir.mkdirs()

        for (file in files) {
            val inputPath = if (assetPath.isEmpty()) file else "$assetPath/$file"
            val outputPath = File(outputDir, file)

            val subFiles = assetManager.list(inputPath)
            if (subFiles.isNullOrEmpty()) {
                // 是文件，复制
                assetManager.open(inputPath).use { input ->
                    FileOutputStream(outputPath).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // 是目录，递归复制
                copyAssetDirectory(inputPath, outputPath)
            }
        }
    }

    /**
     * 检查目录是否包含模型文件
     */
    private fun hasModelFiles(dir: File): Boolean {
        if (!dir.exists()) return false

        // Vosk 模型目录通常包含以下文件
        val requiredFiles = listOf(
            "am/final.mdl",
            "conf/mfcc.conf",
            "graph/words.txt"
        )

        // 至少要有一些 .mdl 或 .conf 文件
        val hasModelFiles = dir.walkTopDown().any {
            it.isFile && (it.name.endsWith(".mdl") || it.name.endsWith(".conf"))
        }

        return hasModelFiles
    }

    /**
     * 导入用户自定义模型
     */
    fun importCustomModel(sourcePath: String, modelName: String): Result<String> {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return Result.failure(IOException("源文件不存在: $sourcePath"))
            }

            val outputDir = File(modelDir, modelName)
            if (outputDir.exists()) {
                outputDir.deleteRecursively()
            }

            if (sourceFile.isDirectory) {
                sourceFile.copyRecursively(outputDir)
            } else {
                // 假设是 zip 文件
                extractFromZip(sourcePath, outputDir) {}
            }

            if (hasModelFiles(outputDir)) {
                Result.success(outputDir.absolutePath)
            } else {
                outputDir.deleteRecursively()
                Result.failure(IOException("导入的文件不是有效的 Vosk 模型"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import custom model", e)
            Result.failure(e)
        }
    }

    /**
     * 获取所有可用模型列表
     */
    fun getAvailableModels(): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()

        modelDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && hasModelFiles(dir)) {
                models.add(
                    ModelInfo(
                        name = dir.name,
                        path = dir.absolutePath,
                        size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    )
                )
            }
        }

        return models
    }

    /**
     * 删除模型
     */
    fun deleteModel(modelName: String): Boolean {
        val dir = File(modelDir, modelName)
        return dir.exists() && dir.deleteRecursively()
    }

    /**
     * 清除所有模型
     */
    fun clearAllModels() {
        modelDir.deleteRecursively()
        modelDir.mkdirs()
    }

    data class ModelInfo(
        val name: String,
        val path: String,
        val size: Long
    )

    sealed class ExtractProgress {
        object Starting : ExtractProgress()
        data class Progress(val percent: Int) : ExtractProgress()
        data class Complete(val path: String) : ExtractProgress()
        data class Error(val message: String) : ExtractProgress()
    }
}