package vn.langkinhhukhong.tts.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.langkinhhukhong.tts.ads.AdMonetizationManager
import vn.langkinhhukhong.tts.model.OfflineVoiceProfile
import vn.langkinhhukhong.tts.model.VoiceInstallState
import vn.langkinhhukhong.tts.model.VoiceStorageSource
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Quản lý Thư viện Giọng đọc Online (Online Voice Library) kết hợp Kiếm tiền từ Quảng Cáo:
 * - Tích hợp 5 bộ model thật từ `Voices/` (`Minh Khang`, `Ngọc Huyền`, `Lạc Phi`, `Minh Quang`, `VPBank` 5 speakers).
 * - Tự động đọc `.onnx.json` và sinh file `tokens.txt` chuẩn cho Sherpa-ONNX qua [PiperModelConfigParser].
 * - Bắt buộc người dùng Xem Quảng Cáo trước khi mở khóa/tải giọng đọc VIP hoặc khi hết lượt dùng.
 */
class VoiceLibraryRepository(
    private val context: Context,
    private val adManager: AdMonetizationManager
) {

    private val voiceStorageRoot: File by lazy {
        File(context.filesDir, "voice_models").apply { mkdirs() }
    }

    /**
     * Tải danh sách Giọng đọc từ Server Online (nếu có mạng và URL phản hồi),
     * kết hợp với danh mục mặc định và số lượt Quảng cáo đã tích lũy trên máy.
     */
    suspend fun fetchOnlineAndLocalCatalog(remoteCatalogUrl: String): List<OfflineVoiceProfile> =
        withContext(Dispatchers.IO) {
            val remoteProfiles = tryFetchRemoteCatalogJson(remoteCatalogUrl)
            val sourceList = if (remoteProfiles.isNotEmpty()) {
                OfflineVoiceProfile.DEFAULT_ONLINE_LIBRARY_CATALOG.take(2) + remoteProfiles
            } else {
                OfflineVoiceProfile.DEFAULT_ONLINE_LIBRARY_CATALOG
            }

            sourceList.map { profile ->
                val isBuiltIn = profile.storageSource == VoiceStorageSource.BUILT_IN_ASSET
                if (isBuiltIn) {
                    val modelDir = getLocalModelDir(profile)
                    PiperModelConfigParser.stageAssetModelToInternalStorage(
                        context = context,
                        assetDir = profile.modelPathOrAssetDir,
                        targetDir = modelDir,
                        modelFileName = profile.modelFileName,
                        onnxJsonFileName = profile.onnxJsonFileName,
                        tokensFileName = profile.tokensFileName
                    )
                    val meta = PiperModelConfigParser.ensureTokensAndReadMetadata(
                        modelDir = modelDir,
                        onnxJsonFileName = profile.onnxJsonFileName,
                        tokensFileName = profile.tokensFileName
                    )
                    profile.copy(
                        numSpeakers = meta.numSpeakers,
                        noiseScale = meta.noiseScale,
                        lengthScale = meta.lengthScale,
                        noiseW = meta.noiseW,
                        installState = VoiceInstallState.INSTALLED,
                        downloadProgress = 1f,
                        remainingQuotaUses = 999
                    )
                } else {
                    val modelFile = getLocalModelFile(profile)
                    val isUnlocked = adManager.isVoiceUnlocked(profile.id, isBuiltIn = false)
                    val isDownloaded = isUnlocked && modelFile.exists() && modelFile.length() > 1024L
                    val remainingUses = adManager.getRemainingUses(profile.id, isBuiltIn = false)
                    val meta = if (isDownloaded) {
                        PiperModelConfigParser.ensureTokensAndReadMetadata(
                            modelDir = getLocalModelDir(profile),
                            onnxJsonFileName = profile.onnxJsonFileName,
                            tokensFileName = profile.tokensFileName
                        )
                    } else {
                        null
                    }
                    profile.copy(
                        numSpeakers = meta?.numSpeakers ?: profile.numSpeakers,
                        noiseScale = meta?.noiseScale ?: profile.noiseScale,
                        lengthScale = meta?.lengthScale ?: profile.lengthScale,
                        noiseW = meta?.noiseW ?: profile.noiseW,
                        installState = if (isDownloaded) VoiceInstallState.INSTALLED else VoiceInstallState.NOT_DOWNLOADED,
                        downloadProgress = if (isDownloaded) 1f else 0f,
                        remainingQuotaUses = remainingUses
                    )
                }
            }
        }

    /**
     * Đọc file JSON cấu hình Thư viện Giọng đọc Online từ Server của bạn.
     */
    private fun tryFetchRemoteCatalogJson(remoteUrl: String): List<OfflineVoiceProfile> {
        if (!remoteUrl.startsWith("http")) return emptyList()
        return try {
            val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val root = JSONObject(jsonText)
            val voicesArray = root.optJSONArray("voices") ?: return emptyList()
            val list = mutableListOf<OfflineVoiceProfile>()

            for (i in 0 until voicesArray.length()) {
                val obj = voicesArray.getJSONObject(i)
                val id = obj.getString("id")
                val modelFileName = obj.optString("modelFileName", "$id.onnx")
                val onnxJsonFileName = obj.optString("onnxJsonFileName", "$modelFileName.json")
                val downloadUrl = obj.getString("downloadUrl")
                val onnxJsonDownloadUrl = obj.optString("onnxJsonDownloadUrl", "$downloadUrl.json")
                list.add(
                    OfflineVoiceProfile(
                        id = id,
                        displayName = obj.getString("displayName"),
                        regionTag = obj.optString("regionTag", "Việt Nam"),
                        styleTag = obj.optString("styleTag", "AI VIP"),
                        description = obj.optString("description", "Giọng đọc Online mở khóa bằng Quảng cáo"),
                        sizeMb = obj.optInt("sizeMb", 61),
                        storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                        modelPathOrAssetDir = obj.optString("modelPathOrAssetDir", "models/$id"),
                        modelFileName = modelFileName,
                        onnxJsonFileName = onnxJsonFileName,
                        speakerId = obj.optInt("speakerId", 0),
                        numSpeakers = obj.optInt("numSpeakers", 1),
                        downloadUrl = downloadUrl,
                        onnxJsonDownloadUrl = onnxJsonDownloadUrl,
                        defaultPitchHz = obj.optDouble("defaultPitchHz", 150.0).toFloat(),
                        requiresAdToUnlock = obj.optBoolean("requiresAdToUnlock", true),
                        adRewardUsesPerView = obj.optInt("adRewardUsesPerView", 5)
                    )
                )
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Các giọng cùng chung 1 file `.onnx` (ví dụ 5 speaker của `VPBank`) dùng chung thư mục lưu trữ
     * để không phải tải/chép trùng lặp 5 lần file 74MB.
     */
    fun getLocalModelDir(profile: OfflineVoiceProfile): File {
        return if (profile.storageSource == VoiceStorageSource.CUSTOM_IMPORTED) {
            File(profile.modelPathOrAssetDir)
        } else {
            val sharedFolderKey = profile.modelPathOrAssetDir
                .substringAfterLast('/')
                .ifBlank { profile.id }
            File(voiceStorageRoot, sharedFolderKey)
        }
    }

    fun getLocalModelFile(profile: OfflineVoiceProfile): File {
        return File(getLocalModelDir(profile), profile.modelFileName)
    }

    /**
     * Tải / Giải nén Giọng đọc về máy SAU KHI người dùng đã xem xong Quảng Cáo của bạn,
     * đồng thời tự động sinh `tokens.txt` từ `.onnx.json`.
     */
    suspend fun downloadVoicePackAfterAdReward(
        profile: OfflineVoiceProfile,
        onProgress: (Float) -> Unit
    ): Result<OfflineVoiceProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = getLocalModelDir(profile).apply { mkdirs() }
            val targetModelFile = File(targetDir, profile.modelFileName)
            val targetJsonFile = File(targetDir, profile.onnxJsonFileName)

            var modelReady = targetModelFile.exists() && targetModelFile.length() > 1024 * 1024L

            // 1. Ưu tiên giải nén từ gói model thực tế trong assets/models/<folder> nếu đã nhúng sẵn
            if (!modelReady) {
                modelReady = PiperModelConfigParser.stageAssetModelToInternalStorage(
                    context = context,
                    assetDir = profile.modelPathOrAssetDir,
                    targetDir = targetDir,
                    modelFileName = profile.modelFileName,
                    onnxJsonFileName = profile.onnxJsonFileName,
                    tokensFileName = profile.tokensFileName,
                    onProgress = onProgress
                )
            }

            // 2. Nếu chưa có trong assets, tải trực tiếp `.onnx` và `.onnx.json` từ Server Online
            if (!modelReady && profile.downloadUrl.startsWith("http")) {
                runCatching {
                    if (profile.onnxJsonDownloadUrl.startsWith("http") && !targetJsonFile.exists()) {
                        downloadFileFromUrl(profile.onnxJsonDownloadUrl, targetJsonFile, null)
                    }
                    downloadFileFromUrl(profile.downloadUrl, targetModelFile, onProgress)
                    modelReady = targetModelFile.exists() && targetModelFile.length() > 1024L
                }
            }

            // 3. Tự động đọc `.onnx.json` và sinh `tokens.txt` chuẩn (161 tokens) cho Sherpa-ONNX
            val metadata = PiperModelConfigParser.ensureTokensAndReadMetadata(
                modelDir = targetDir,
                onnxJsonFileName = profile.onnxJsonFileName,
                tokensFileName = profile.tokensFileName
            )

            // 4. Đánh dấu mở khóa & cộng ngay số lượt tạo TTS thưởng sau khi xem Quảng cáo
            adManager.markVoiceUnlocked(profile.id, true)
            val updatedQuota = adManager.addRewardedUses(
                voiceId = profile.id,
                bonusUses = profile.adRewardUsesPerView
            )

            onProgress(1.0f)
            profile.copy(
                numSpeakers = metadata.numSpeakers,
                noiseScale = metadata.noiseScale,
                lengthScale = metadata.lengthScale,
                noiseW = metadata.noiseW,
                installState = VoiceInstallState.INSTALLED,
                downloadProgress = 1.0f,
                remainingQuotaUses = updatedQuota
            )
        }
    }

    private fun downloadFileFromUrl(
        urlStr: String,
        targetFile: File,
        onProgress: ((Float) -> Unit)?
    ) {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 15000
            requestMethod = "GET"
            connect()
        }
        try {
            if (connection.responseCode in 200..299) {
                val contentLength = connection.contentLengthLong.coerceAtLeast(1L)
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress?.invoke((totalRead.toFloat() / contentLength).coerceIn(0.05f, 0.98f))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Xóa file model giọng đọc đã tải thêm để giải phóng bộ nhớ.
     */
    suspend fun deleteDownloadedVoice(profile: OfflineVoiceProfile): Result<OfflineVoiceProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(profile.storageSource != VoiceStorageSource.BUILT_IN_ASSET) {
                    "Không thể xóa 2 giọng đọc mặc định của hệ thống."
                }
                adManager.markVoiceUnlocked(profile.id, false)
                val dir = getLocalModelDir(profile)
                if (dir.exists() && profile.numSpeakers <= 1) {
                    dir.deleteRecursively()
                }
                profile.copy(
                    installState = VoiceInstallState.NOT_DOWNLOADED,
                    downloadProgress = 0f
                )
            }
        }
}
