package vn.langkinhhukhong.tts.tts

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import vn.langkinhhukhong.tts.model.OfflineVoiceProfile
import vn.langkinhhukhong.tts.model.SubtitleWord
import vn.langkinhhukhong.tts.model.VoiceStorageSource
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Engine kết xuất giọng đọc AI Tiếng Việt trên Mobile chuẩn 1:1 theo kiến trúc bản PC (`LangKinhHuKhong.TTS`):
 *
 * 1. **TextProcessor + SpeechPacing ([VietnameseTtsTextProcessor]):**
 *    - Chuẩn hóa NFC, tự động thêm dấu kết câu `.` để VITS hạ giọng tự nhiên cuối câu.
 *    - Giữ nguyên dấu `,` và `:` trong câu để bảo toàn ngữ cảnh thanh điệu Tiếng Việt.
 *    - Chuyển `...` giữa câu trước chữ thường thành `,` để không nuốt từ nối (`thì`, `là`, `mà`, `nhưng`).
 * 2. **Persistent Sherpa-ONNX Piper VITS Session (`sherpa-onnx-1.12.40` + `espeak-ng-data`):**
 *    - Luôn tổng hợp từng câu ở tốc độ gốc `1.0x` của model ONNX (`minhkhang`, `ngochuyenupdate`, `lacphi`, `minhquang`, `VPBank` 5 speakers).
 *    - Giữ phiên model đang dùng trong RAM để tạo câu kế tiếp tức thì không phải nạp lại file ONNX.
 * 3. **WavPacing + SonicAudioProcessor (tương đương `WavPacing.cs` + `FFmpeg atempo` trên PC):**
 *    - Chèn khoảng lặng PCM 16-bit chính xác đến từng sample (`160ms` đầu file, `310ms` sau `.`, `350ms` sau `!`, `330ms` sau `?`, `550ms` ngắt đoạn).
 *    - Thay đổi tốc độ đọc (`0.7x`..`1.5x`) bằng `SonicAudioProcessor` giữ nguyên cao độ giọng (Pitch = 1.0), không bao giờ bị méo giọng!
 * 4. **TtsSubtitleTimeline:**
 *    - Tính mốc thời gian phụ đề trực tiếp từ số lượng PCM sample thực tế của từng câu + khoảng lặng giữa các câu, không bị trôi lệch phụ đề.
 */
class SherpaOfflineTtsEngine(private val context: Context) {

    private data class CachedSherpaSession(
        val modelKey: String,
        val tts: OfflineTts,
        val metadata: PiperModelMetadata
    )

    private data class MeasuredSpeechSegment(
        val rawText: String,
        val startSample1x: Long,
        val endSample1x: Long,
        val pauseAfterMs: Int
    )

    private data class PcmSynthesisResult(
        val durationMs: Long,
        val alignedWords: List<SubtitleWord>
    )

    private val sessionMutex = Mutex()
    private var cachedSession: CachedSherpaSession? = null

    private val outputDir: File by lazy {
        File(context.filesDir, "tts_renders").apply { mkdirs() }
    }

    private val voiceStorageRoot: File by lazy {
        File(context.filesDir, "voice_models").apply { mkdirs() }
    }

    @Volatile
    private var systemTts: TextToSpeech? = null

    @Volatile
    private var systemTtsReady: Boolean = false

    suspend fun synthesizeWithSubtitles(
        text: String,
        voiceProfile: OfflineVoiceProfile,
        speechSpeed: Float = 1.0f,
        insertAtTimelineMs: Long = 0L,
        onStatusProgress: ((String) -> Unit)? = null
    ): Result<VoiceSubtitleClip> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedText = text.trim()
            require(cleanedText.isNotEmpty()) { "Vui lòng nhập văn bản cần tạo giọng đọc." }

            val timestamp = System.currentTimeMillis()
            val wavFile = File(outputDir, "tts_${voiceProfile.id}_$timestamp.wav")
            val safeSpeed = speechSpeed.coerceIn(0.5f, 2.0f)

            // 1. Ưu tiên tuyệt đối: Kết xuất chuẩn ONNX Piper Offline + WavPacing + Sonic Time-Stretch
            val sherpaResult = synthesizeWithNativeSherpaPiperPipeline(
                rawText = cleanedText,
                voiceProfile = voiceProfile,
                targetSpeed = safeSpeed,
                outputWavFile = wavFile,
                onStatusProgress = onStatusProgress
            )

            val finalWords: List<SubtitleWord>
            val finalDurationMs: Long

            if (sherpaResult != null) {
                finalDurationMs = sherpaResult.durationMs
                finalWords = sherpaResult.alignedWords
            } else {
                // Dự phòng an toàn qua Android System TTS nếu thiết bị thiếu tài nguyên bộ nhớ cho ONNX
                val sysDuration = trySynthesizeWithAndroidSystemTts(
                    text = cleanedText,
                    voiceProfile = voiceProfile,
                    speed = safeSpeed,
                    outputWavFile = wavFile
                ) ?: throw IllegalStateException(
                    "Không thể nạp model giọng đọc ${voiceProfile.displayName}. Vui lòng kiểm tra dung lượng trống trên thiết bị."
                )
                finalDurationMs = sysDuration
                finalWords = alignWordsProportionally(
                    text = cleanedText,
                    startMs = 0L,
                    endMs = sysDuration
                )
            }

            VoiceSubtitleClip(
                title = "${voiceProfile.displayName}: ${cleanedText.take(16)}",
                fullText = cleanedText,
                voiceProfile = voiceProfile,
                audioFilePath = wavFile.absolutePath,
                timelineStartMs = insertAtTimelineMs,
                trimStartMs = 0L,
                trimEndMs = finalDurationMs,
                volume = 1.0f,
                words = finalWords
            )
        }
    }

    /**
     * Pipeline chuẩn bản PC (`LangKinhHuKhong.TTS`):
     * NormalizeForTts -> SegmentText -> ONNX 1.0x Synthesis -> WavPacing Silence -> Sonic Pitch-Preserving Speed -> Frame-Accurate Subtitles.
     */
    private suspend fun synthesizeWithNativeSherpaPiperPipeline(
        rawText: String,
        voiceProfile: OfflineVoiceProfile,
        targetSpeed: Float,
        outputWavFile: File,
        onStatusProgress: ((String) -> Unit)?
    ): PcmSynthesisResult? = sessionMutex.withLock {
        try {
            val espeakDir = PiperModelConfigParser.ensureEspeakNgDataStaged(context) ?: return null
            val session = getOrCreateSherpaSession(voiceProfile, espeakDir, onStatusProgress) ?: return null

            val normalized = VietnameseTtsTextProcessor.normalizeForTts(rawText)
            val segments = VietnameseTtsTextProcessor.segmentText(normalized)
            if (segments.isEmpty()) return null

            val sampleRate = session.tts.sampleRate().takeIf { it > 0 } ?: session.metadata.sampleRate
            val safeSpeakerId = voiceProfile.speakerId.coerceIn(
                0,
                (session.metadata.numSpeakers - 1).coerceAtLeast(0)
            )

            // Bộ đệm ghép PCM 16-bit Little-Endian (tương đương WavPacing.cs)
            val pcm1xOut = ByteArrayOutputStream(sampleRate * 2 * 8)
            var totalSamples1x = 0L

            fun appendSilenceMs(ms: Int) {
                if (ms <= 0) return
                val silenceSamples = ((sampleRate.toDouble() * ms) / 1000.0).roundToLong().toInt()
                if (silenceSamples <= 0) return
                val zeroBytes = ByteArray(silenceSamples * 2)
                pcm1xOut.write(zeroBytes)
                totalSamples1x += silenceSamples
            }

            // Chèn khoảng lặng mở đầu 160ms (SpeechPacing.InitialPauseMs)
            appendSilenceMs(VietnameseTtsTextProcessor.INITIAL_PAUSE_MS)

            val measuredSegments = mutableListOf<MeasuredSpeechSegment>()

            for ((idx, seg) in segments.withIndex()) {
                if (segments.size > 1) {
                    onStatusProgress?.invoke("🎙️ Đang kết xuất câu ${idx + 1}/${segments.size} (${voiceProfile.displayName})...")
                }
                val piperInput = VietnameseTtsTextProcessor.prepareTextForPiper(seg.text)
                if (piperInput.isBlank()) {
                    appendSilenceMs(seg.pauseAfterMs)
                    continue
                }

                // Luôn tổng hợp ở tốc độ chuẩn 1.0f của ONNX để giữ chuẩn âm sắc & thanh điệu Tiếng Việt
                val generated = session.tts.generate(
                    text = piperInput,
                    sid = safeSpeakerId,
                    speed = 1.0f
                )
                val floatSamples = generated.samples
                if (floatSamples.isEmpty()) {
                    appendSilenceMs(seg.pauseAfterMs)
                    continue
                }

                val startSample = totalSamples1x
                val segPcmBytes = floatSamplesToPcm16Le(floatSamples)
                pcm1xOut.write(segPcmBytes)
                totalSamples1x += floatSamples.size
                val endSample = totalSamples1x

                measuredSegments.add(
                    MeasuredSpeechSegment(
                        rawText = seg.text,
                        startSample1x = startSample,
                        endSample1x = endSample,
                        pauseAfterMs = seg.pauseAfterMs
                    )
                )

                // Chèn khoảng lặng chuẩn sau câu (Period 310ms, Exclamation 350ms, Question 330ms, Line 350ms...)
                appendSilenceMs(seg.pauseAfterMs)
            }

            if (measuredSegments.isEmpty() || totalSamples1x <= 0L) {
                return null
            }

            val rawPcm1xBytes = pcm1xOut.toByteArray()

            // Chỉnh tốc độ đọc bằng SonicAudioProcessor (giữ nguyên Pitch giống FFmpeg atempo trên PC)
            val finalPcmBytes = if (abs(targetSpeed - 1.0f) >= 0.02f) {
                applyPitchPreservingSpeedPcm16(
                    pcm16LeBytes = rawPcm1xBytes,
                    sampleRate = sampleRate,
                    speed = targetSpeed
                )
            } else {
                rawPcm1xBytes
            }

            writeWavFile(
                file = outputWavFile,
                pcmData = finalPcmBytes,
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16
            )

            val finalTotalSamples = (finalPcmBytes.size / 2L).coerceAtLeast(1L)
            val finalDurationMs = ((finalTotalSamples * 1000.0) / sampleRate.toDouble())
                .roundToLong()
                .coerceAtLeast(300L)

            val rawDuration1xMs = ((totalSamples1x * 1000.0) / sampleRate.toDouble())
                .coerceAtLeast(1.0)
            val timeScale = finalDurationMs.toDouble() / rawDuration1xMs

            val alignedWords = buildFrameAccurateSubtitleWords(
                measuredSegments = measuredSegments,
                sampleRate = sampleRate,
                timeScale = timeScale,
                finalDurationMs = finalDurationMs
            )

            PcmSynthesisResult(
                durationMs = finalDurationMs,
                alignedWords = alignedWords
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Nạp và giữ phiên `OfflineTts` trong bộ nhớ (giống `PersistentPiperSession` trên PC).
     */
    private fun getOrCreateSherpaSession(
        voiceProfile: OfflineVoiceProfile,
        espeakDir: File,
        onStatusProgress: ((String) -> Unit)?
    ): CachedSherpaSession? {
        val sharedFolderKey = voiceProfile.modelPathOrAssetDir
            .substringAfterLast('/')
            .ifBlank { voiceProfile.id }

        val baseDir = if (voiceProfile.storageSource == VoiceStorageSource.CUSTOM_IMPORTED) {
            File(voiceProfile.modelPathOrAssetDir)
        } else {
            File(voiceStorageRoot, sharedFolderKey)
        }.apply { mkdirs() }

        var onnxFile = File(baseDir, "voice.onnx")
        if (!onnxFile.exists() || onnxFile.length() < 1024 * 1024L) {
            val directFile = File(baseDir, voiceProfile.modelFileName)
            if (directFile.exists() && directFile.length() > 1024 * 1024L) {
                onnxFile = directFile
            } else {
                onStatusProgress?.invoke("📦 Đang chuẩn bị model giọng ${voiceProfile.displayName} lần đầu...")
                val staged = PiperModelConfigParser.stageAssetModelToInternalStorage(
                    context = context,
                    assetDir = voiceProfile.modelPathOrAssetDir,
                    targetDir = baseDir,
                    modelFileName = voiceProfile.modelFileName,
                    onnxJsonFileName = voiceProfile.onnxJsonFileName,
                    tokensFileName = voiceProfile.tokensFileName
                )
                if (staged != null) {
                    onnxFile = staged
                } else {
                    // Dự phòng tải từ kho lưu trữ nếu bản build không nhúng sẵn file .onnx này
                    val downloaded = tryDownloadMissingVoiceModel(voiceProfile, baseDir, onStatusProgress)
                    if (downloaded != null) {
                        onnxFile = downloaded
                    }
                }
            }
        }

        if (!onnxFile.exists() || onnxFile.length() < 1024 * 1024L) {
            return null
        }

        val metadata = PiperModelConfigParser.ensureTokensAndReadMetadata(
            modelDir = baseDir,
            onnxJsonFileName = voiceProfile.onnxJsonFileName,
            tokensFileName = voiceProfile.tokensFileName
        )
        PiperModelConfigParser.ensureSherpaOnnxMetadata(onnxFile, metadata)

        val tokensFile = File(baseDir, voiceProfile.tokensFileName)
        if (!tokensFile.exists() || tokensFile.length() < 64L) {
            return null
        }

        val modelKey = "${onnxFile.absolutePath}|${onnxFile.length()}"
        cachedSession?.let { existing ->
            if (existing.modelKey == modelKey) {
                return existing
            }
            runCatching { existing.tts.free() }
            cachedSession = null
        }

        val cpuThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val vitsConfig = OfflineTtsVitsModelConfig(
            model = onnxFile.absolutePath,
            lexicon = "",
            tokens = tokensFile.absolutePath,
            dataDir = espeakDir.absolutePath,
            noiseScale = metadata.noiseScale,
            noiseScaleW = metadata.noiseW,
            lengthScale = metadata.lengthScale
        )
        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig,
            numThreads = cpuThreads,
            debug = false,
            provider = "cpu"
        )
        val ttsConfig = OfflineTtsConfig(
            model = modelConfig,
            maxNumSentences = 1
        )

        val ttsInstance = OfflineTts(assetManager = null, config = ttsConfig)
        val newSession = CachedSherpaSession(
            modelKey = modelKey,
            tts = ttsInstance,
            metadata = metadata
        )
        cachedSession = newSession
        return newSession
    }

    private fun tryDownloadMissingVoiceModel(
        profile: OfflineVoiceProfile,
        targetDir: File,
        onStatusProgress: ((String) -> Unit)?
    ): File? {
        return runCatching {
            val folderName = profile.modelPathOrAssetDir.substringAfterLast('/')
            val encodedFile = profile.modelFileName.replace(" ", "%20")
            val candidateUrl = profile.downloadUrl.takeIf { it.startsWith("http") }
                ?: "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/voices-assets/Voices/$folderName/$encodedFile"

            val tmpFile = File(targetDir, "voice.onnx.part")
            val dstFile = File(targetDir, "voice.onnx")
            val conn = (URL(candidateUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 25000
                requestMethod = "GET"
                connect()
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val totalLen = conn.contentLengthLong.coerceAtLeast(1L)
            conn.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded = 0L
                    var lastPercent = -1
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        val pct = ((downloaded * 100L) / totalLen).toInt().coerceIn(0, 100)
                        if (pct / 10 != lastPercent / 10) {
                            lastPercent = pct
                            onStatusProgress?.invoke("⬇️ Đang tải model ${profile.displayName}: $pct%...")
                        }
                    }
                }
            }
            conn.disconnect()
            if (tmpFile.exists() && tmpFile.length() > 1024 * 1024L) {
                if (dstFile.exists()) dstFile.delete()
                tmpFile.renameTo(dstFile)
                dstFile
            } else {
                tmpFile.delete()
                null
            }
        }.getOrNull()
    }

    private fun floatSamplesToPcm16Le(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        var byteIdx = 0
        for (sample in samples) {
            // Soft-clip nhẹ ở ngưỡng ±0.98 để âm thanh điện thoại trong, to rõ mà không bị rè (clipping)
            val clamped = sample.coerceIn(-0.98f, 0.98f)
            val pcmShort = (clamped * 32767f).toInt().coerceIn(-32768, 32767)
            bytes[byteIdx++] = (pcmShort and 0xFF).toByte()
            bytes[byteIdx++] = ((pcmShort ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Đổi tốc độ đọc giữ nguyên cao độ giọng (Pitch = 1.0) bằng `SonicAudioProcessor` của Media3,
     * tương đương hoàn toàn với bước `FFmpeg -af atempo=...` trong `AudioPipeline.cs` trên bản PC!
     */
    @OptIn(UnstableApi::class)
    private fun applyPitchPreservingSpeedPcm16(
        pcm16LeBytes: ByteArray,
        sampleRate: Int,
        speed: Float
    ): ByteArray {
        return runCatching {
            val sonic = SonicAudioProcessor()
            sonic.setSpeed(speed.coerceIn(0.5f, 2.0f))
            sonic.setPitch(1.0f)
            val inputFormat = AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT)
            sonic.configure(inputFormat)
            sonic.flush()

            val outStream = ByteArrayOutputStream((pcm16LeBytes.size / speed).toInt() + 4096)
            val chunkSize = 16 * 1024
            var offset = 0

            while (offset < pcm16LeBytes.size) {
                val len = minOf(chunkSize, pcm16LeBytes.size - offset)
                val inBuf = ByteBuffer.wrap(pcm16LeBytes, offset, len).order(ByteOrder.LITTLE_ENDIAN)
                sonic.queueInput(inBuf)
                offset += (len - inBuf.remaining())

                drainSonicOutput(sonic, outStream)
            }

            sonic.queueEndOfStream()
            var safetyLoops = 0
            while (!sonic.isEnded && safetyLoops < 2000) {
                drainSonicOutput(sonic, outStream)
                safetyLoops++
            }
            sonic.reset()

            val stretched = outStream.toByteArray()
            if (stretched.size >= 64) {
                // Đảm bảo chẵn khung 16-bit (2 bytes/sample)
                if (stretched.size % 2 == 0) stretched else stretched.copyOf(stretched.size - 1)
            } else {
                pcm16LeBytes
            }
        }.getOrDefault(pcm16LeBytes)
    }

    @OptIn(UnstableApi::class)
    private fun drainSonicOutput(sonic: SonicAudioProcessor, outStream: ByteArrayOutputStream) {
        while (true) {
            val outBuf = sonic.output
            if (!outBuf.hasRemaining()) break
            val temp = ByteArray(outBuf.remaining())
            outBuf.get(temp)
            outStream.write(temp)
        }
    }

    /**
     * Ánh xạ từ phụ đề dựa trên số lượng PCM sample thực tế của từng câu (giống `TtsSubtitleTimeline.cs` trên PC):
     * Không bao giờ để khoảng lặng giữa các câu làm trôi chữ phụ đề sang câu tiếp theo.
     */
    private fun buildFrameAccurateSubtitleWords(
        measuredSegments: List<MeasuredSpeechSegment>,
        sampleRate: Int,
        timeScale: Double,
        finalDurationMs: Long
    ): List<SubtitleWord> {
        val allWords = mutableListOf<SubtitleWord>()
        for (seg in measuredSegments) {
            val segStart1xMs = (seg.startSample1x * 1000.0) / sampleRate.toDouble()
            val segEnd1xMs = (seg.endSample1x * 1000.0) / sampleRate.toDouble()

            val scaledStartMs = (segStart1xMs * timeScale).roundToLong().coerceIn(0L, finalDurationMs)
            val scaledEndMs = (segEnd1xMs * timeScale).roundToLong()
                .coerceIn(scaledStartMs + 40L, finalDurationMs)

            allWords.addAll(
                alignWordsProportionally(
                    text = seg.rawText,
                    startMs = scaledStartMs,
                    endMs = scaledEndMs
                )
            )
        }
        return allWords
    }

    private fun alignWordsProportionally(
        text: String,
        startMs: Long,
        endMs: Long
    ): List<SubtitleWord> {
        val rawTokens = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return emptyList()

        val spanMs = (endMs - startMs).coerceAtLeast(40L)
        val weights = rawTokens.map { token ->
            val cleanLen = token.trim('.', ',', '!', '?', ';', ':', '"', '\'', '”', '’', '(', ')').length
            val baseWeight = 1.0f + (cleanLen.coerceIn(1, 7) * 0.09f)
            val clauseBonus = if (token.endsWith(",") || token.endsWith(";") || token.endsWith(":")) 0.32f else 0f
            baseWeight + clauseBonus
        }

        val totalWeight = weights.sum().coerceAtLeast(0.01f)
        val result = mutableListOf<SubtitleWord>()
        var cursorMs = startMs

        rawTokens.forEachIndexed { index, token ->
            val shareMs = ((weights[index] / totalWeight) * spanMs).toLong()
            val wStart = cursorMs
            val wEnd = if (index == rawTokens.lastIndex) {
                endMs
            } else {
                (wStart + shareMs).coerceAtMost(endMs)
            }
            result.add(
                SubtitleWord(
                    text = token,
                    sourceStartMs = wStart,
                    sourceEndMs = wEnd.coerceAtLeast(wStart + 30L)
                )
            )
            cursorMs = wEnd
        }

        return result
    }

    /**
     * Dự phòng qua Android Native `TextToSpeech.synthesizeToFile` nếu thiết bị quá yếu không nạp nổi ONNX.
     */
    private suspend fun trySynthesizeWithAndroidSystemTts(
        text: String,
        voiceProfile: OfflineVoiceProfile,
        speed: Float,
        outputWavFile: File
    ): Long? {
        val tts = ensureSystemTtsInitialized() ?: return null
        return withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine { cont ->
                try {
                    val viLocale = Locale("vi", "VN")
                    val langResult = tts.setLanguage(viLocale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        if (cont.isActive) cont.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    tts.setPitch(voiceProfile.ttsPitchMultiplier.coerceIn(0.75f, 1.3f))
                    tts.setSpeechRate(speed.coerceIn(0.6f, 1.8f))

                    val utteranceId = "utt_${UUID.randomUUID()}"
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}

                        override fun onDone(id: String?) {
                            if (id == utteranceId && cont.isActive) {
                                if (outputWavFile.exists() && outputWavFile.length() > 1024L) {
                                    val actualMs = extractActualAudioDurationMs(outputWavFile) ?: 2000L
                                    cont.resume(actualMs.coerceAtLeast(400L))
                                } else {
                                    cont.resume(null)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId && cont.isActive) cont.resume(null)
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId && cont.isActive) cont.resume(null)
                        }
                    })

                    val callResult = tts.synthesizeToFile(text, Bundle(), outputWavFile, utteranceId)
                    if (callResult != TextToSpeech.SUCCESS && cont.isActive) {
                        cont.resume(null)
                    }
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private suspend fun ensureSystemTtsInitialized(): TextToSpeech? {
        systemTts?.let { existing ->
            if (systemTtsReady) return existing
        }
        return withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                var instance: TextToSpeech? = null
                instance = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS && instance != null) {
                        val res = instance!!.setLanguage(Locale("vi", "VN"))
                        val ok = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                        systemTts = instance
                        systemTtsReady = ok
                        if (cont.isActive) cont.resume(if (ok) instance else null)
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
    }

    private fun extractActualAudioDurationMs(audioFile: File): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            if (dur != null && dur > 100L) dur else null
        }.getOrNull()
    }

    private fun writeWavFile(
        file: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign)
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())
            fos.write(pcmData)
        }
    }

    fun release() {
        runCatching { cachedSession?.tts?.free() }
        cachedSession = null
        runCatching { systemTts?.shutdown() }
        systemTts = null
    }
}
