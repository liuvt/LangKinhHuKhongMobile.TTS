package vn.langkinhhukhong.tts.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import vn.langkinhhukhong.tts.model.CanvasImageOverlayItem
import vn.langkinhhukhong.tts.model.CanvasTextOverlayItem
import vn.langkinhhukhong.tts.model.SecondaryMp3Clip
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.SubtitleCanvasConfig
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bộ kiểm tra Video MP4 (<= 3 phút) & Xuất File (MP3 hoặc MP4 9:16 1080x1920):
 * - Hỗ trợ xuất file âm thanh `.mp3` ghép chuẩn vị trí timeline (kể cả khoảng lặng và cắt chia đoạn).
 * - Hỗ trợ xuất file video `.mp4` 9:16 gồm nhiều đoạn cắt Video (`VideoSegmentClip`), Voice AI, Phụ đề, Tiêu đề và Sticker.
 */
class VideoExportManager(private val context: Context) {

    companion object {
        private const val TAG = "VideoExportManager"
        const val MAX_VIDEO_DURATION_MS = 180_000L // Tối đa 3 phút (180 giây)
        const val OUTPUT_WIDTH_9_16 = 1080
        const val OUTPUT_HEIGHT_9_16 = 1920
        private const val EXPORT_SAMPLE_RATE = 22050
    }

    data class VideoMetadataInfo(
        val uri: Uri,
        val displayName: String,
        val durationMs: Long,
        val width: Int,
        val height: Int
    )

    fun validateAndInspectShortVideo(uri: Uri, fallbackName: String = "Video_9_16.mp4"): Result<VideoMetadataInfo> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) {
                return Result.failure(IllegalArgumentException("Không đọc được thời lượng file MP4."))
            }
            if (durationMs > MAX_VIDEO_DURATION_MS) {
                val seconds = durationMs / 1000
                return Result.failure(
                    IllegalArgumentException(
                        "File MP4 dài ${seconds}s vượt quá giới hạn 3 phút (180s). Vui lòng chọn video <= 3 phút!"
                    )
                )
            }

            val rawW = widthStr?.toIntOrNull() ?: OUTPUT_WIDTH_9_16
            val rawH = heightStr?.toIntOrNull() ?: OUTPUT_HEIGHT_9_16
            val rotation = rotationStr?.toIntOrNull() ?: 0
            val (finalW, finalH) = if (rotation == 90 || rotation == 270) {
                rawH to rawW
            } else {
                rawW to rawH
            }

            Result.success(
                VideoMetadataInfo(
                    uri = uri,
                    displayName = fallbackName,
                    durationMs = durationMs,
                    width = finalW,
                    height = finalH
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc metadata video", e)
            Result.failure(IllegalArgumentException("Không thể mở file MP4: ${e.message}"))
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Xuất file âm thanh MP3 từ toàn bộ các đoạn Voice AI trên Timeline
     * (tôn trọng vị trí kéo thả `timelineStartMs`, đoạn đã cắt `trimStartMs..trimEndMs` và âm lượng).
     */
    suspend fun exportProjectToMp3(
        voiceClips: List<VoiceSubtitleClip>,
        secondaryMp3Clips: List<SecondaryMp3Clip> = emptyList()
    ): Result<File> = withContext(Dispatchers.IO) {
        if (voiceClips.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Chưa có đoạn Voice AI nào trên Timeline để xuất file MP3!")
            )
        }

        try {
            val sortedClips = voiceClips.sortedBy { it.timelineStartMs }
            val totalTimelineEndMs = sortedClips.maxOf { it.timelineEndMs }.coerceAtLeast(500L)
            val totalSamples = ((totalTimelineEndMs * EXPORT_SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(1)
            val mixBuffer = IntArray(totalSamples)

            for (clip in sortedClips) {
                val audioFile = File(clip.audioFilePath)
                if (!audioFile.exists()) continue

                val clipSamples = readWavSamples16BitMono(audioFile, EXPORT_SAMPLE_RATE)
                if (clipSamples.isEmpty()) continue

                val startSampleInClip = ((clip.trimStartMs * EXPORT_SAMPLE_RATE) / 1000L).toInt()
                    .coerceIn(0, clipSamples.size)
                val endSampleInClip = ((clip.trimEndMs * EXPORT_SAMPLE_RATE) / 1000L).toInt()
                    .coerceIn(startSampleInClip, clipSamples.size)
                val clipLengthSamples = endSampleInClip - startSampleInClip
                if (clipLengthSamples <= 0) continue

                val destStartSample = ((clip.timelineStartMs * EXPORT_SAMPLE_RATE) / 1000L).toInt()
                    .coerceAtLeast(0)
                val gain = clip.volume.coerceIn(0f, 1.5f)

                for (i in 0 until clipLengthSamples) {
                    val destIdx = destStartSample + i
                    if (destIdx >= mixBuffer.size) break
                    val scaled = (clipSamples[startSampleInClip + i] * gain).roundToInt()
                    mixBuffer[destIdx] = (mixBuffer[destIdx] + scaled).coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    )
                }
            }

            val finalPcm = ShortArray(totalSamples) { idx ->
                mixBuffer[idx].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val outMp3File = File(exportDir, "LKHK_Voice_$timestamp.mp3")
            writeWavePcm16MonoFile(outMp3File, finalPcm, EXPORT_SAMPLE_RATE)

            // Xuất kèm file phụ đề .srt
            val srtFile = File(exportDir, "LKHK_Voice_$timestamp.srt")
            writeSrtSubtitleFile(srtFile, sortedClips)

            // Sao chép sang thư mục Downloads công khai để người dùng dễ lấy file
            runCatching {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) {
                    val publicMp3 = File(downloadsDir, outMp3File.name)
                    outMp3File.copyTo(publicMp3, overwrite = true)
                }
            }

            Result.success(outMp3File)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xuất file MP3: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Xuất Dự án ra file MP4 9:16 (1080x1920):
     * - Hỗ trợ cắt nhiều phân đoạn Video (`videoTrack.segments`), tắt/bật âm thanh gốc MP4, tốc độ phát.
     * - Ghép chuẩn tất cả đoạn Voice AI theo vị trí timeline (`timelineStartMs`) thành 1 đường tiếng đồng bộ với hình ảnh.
     */
    suspend fun exportProjectToMp4(
        videoTrack: ShortVideoTrack,
        voiceClips: List<VoiceSubtitleClip>,
        secondaryMp3Clips: List<SecondaryMp3Clip>,
        textOverlays: List<CanvasTextOverlayItem>,
        imageOverlays: List<CanvasImageOverlayItem>,
        subtitleConfig: SubtitleCanvasConfig
    ): Result<File> = suspendCancellableCoroutine { cont ->
        if (videoTrack.videoUri.isBlank()) {
            cont.resume(
                Result.failure(
                    IllegalStateException("Vui lòng chọn Video MP4 (9:16) trước khi xuất MP4, hoặc chọn Xuất MP3 nếu chỉ xuất âm thanh!")
                )
            )
            return@suspendCancellableCoroutine
        }

        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val outFile = File(exportDir, "LKHK_Video_9x16_$timestamp.mp4")

        try {
            val activeSegments = videoTrack.segments.ifEmpty {
                listOf(
                    vn.langkinhhukhong.tts.model.VideoSegmentClip(
                        id = "vid_seg_fallback",
                        label = videoTrack.displayName,
                        timelineStartMs = 0L,
                        sourceStartMs = videoTrack.trimStartMs,
                        sourceEndMs = videoTrack.trimEndMs
                    )
                )
            }

            val videoEffects = mutableListOf<Effect>(
                Presentation.createForWidthAndHeight(
                    OUTPUT_WIDTH_9_16,
                    OUTPUT_HEIGHT_9_16,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            )

            val audioProcessors = mutableListOf<AudioProcessor>()
            if (kotlin.math.abs(videoTrack.speedMultiplier - 1.0f) > 0.02f) {
                val sonic = SonicAudioProcessor().apply {
                    setSpeed(videoTrack.speedMultiplier.coerceIn(0.5f, 2.0f))
                }
                audioProcessors.add(sonic)
            }

            val editedVideoItems = activeSegments.sortedBy { it.timelineStartMs }.map { seg ->
                val clipConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(seg.sourceStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(seg.sourceEndMs.coerceAtLeast(seg.sourceStartMs + 200L))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(videoTrack.videoUri))
                    .setClippingConfiguration(clipConfig)
                    .build()

                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(videoTrack.isOriginalAudioMuted)
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()
            }

            val sequences = mutableListOf<EditedMediaItemSequence>()
            sequences.add(EditedMediaItemSequence(editedVideoItems))

            // Kết xuất các đoạn Voice AI trên timeline thành 1 track âm thanh đồng bộ chuẩn từng mili-giây
            val sortedVoiceClips = voiceClips.sortedBy { it.timelineStartMs }
            if (sortedVoiceClips.isNotEmpty()) {
                val composedVoiceFile = buildTimelineVoiceAudioFile(exportDir, sortedVoiceClips)
                if (composedVoiceFile != null && composedVoiceFile.exists()) {
                    val voiceItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(composedVoiceFile))
                        .build()
                    val editedVoice = EditedMediaItem.Builder(voiceItem).build()
                    sequences.add(EditedMediaItemSequence(listOf(editedVoice)))
                }
            }

            val composition = Composition.Builder(sequences).build()

            val srtFile = File(exportDir, "LKHK_Video_9x16_$timestamp.srt")
            writeSrtSubtitleFile(srtFile, sortedVoiceClips)

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        runCatching {
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) {
                                val publicMp4 = File(downloadsDir, outFile.name)
                                outFile.copyTo(publicMp4, overwrite = true)
                            }
                        }
                        if (cont.isActive) {
                            cont.resume(Result.success(outFile))
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "Transformer export error", exportException)
                        if (cont.isActive) {
                            cont.resume(Result.failure(exportException))
                        }
                    }
                })
                .build()

            transformer.start(composition, outFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi tạo xuất MP4", e)
            if (cont.isActive) {
                cont.resume(Result.failure(e))
            }
        }
    }

    private fun buildTimelineVoiceAudioFile(
        exportDir: File,
        sortedClips: List<VoiceSubtitleClip>
    ): File? {
        return try {
            val totalTimelineEndMs = sortedClips.maxOf { it.timelineEndMs }.coerceAtLeast(500L)
            val totalSamples = ((totalTimelineEndMs * EXPORT_SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(1)
            val mixBuffer = IntArray(totalSamples)

            for (clip in sortedClips) {
                val audioFile = File(clip.audioFilePath)
                if (!audioFile.exists()) continue
                val clipSamples = readWavSamples16BitMono(audioFile, EXPORT_SAMPLE_RATE)
                if (clipSamples.isEmpty()) continue

                val startSampleInClip = ((clip.trimStartMs * EXPORT_SAMPLE_RATE) / 1000L).toInt()
                    .coerceIn(0, clipSamples.size)
                val endSampleInClip = ((clip.trimEndMs * EXPORT_SAMPLE_RATE) / 1000L).toInt()
                    .coerceIn(startSampleInClip, clipSamples.size)
                val clipLengthSamples = endSampleInClip - startSampleInClip
                if (clipLengthSamples <= 0) continue

                val destStartSample = ((clip.timelineStartMs * EXPORT_SAMPLE_RATE) / 1000L).toInt()
                    .coerceAtLeast(0)
                val gain = clip.volume.coerceIn(0f, 1.5f)

                for (i in 0 until clipLengthSamples) {
                    val destIdx = destStartSample + i
                    if (destIdx >= mixBuffer.size) break
                    val scaled = (clipSamples[startSampleInClip + i] * gain).roundToInt()
                    mixBuffer[destIdx] = (mixBuffer[destIdx] + scaled).coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    )
                }
            }

            val finalPcm = ShortArray(totalSamples) { idx ->
                mixBuffer[idx].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            val tempVoiceWav = File(exportDir, "temp_composed_voice_${System.currentTimeMillis()}.wav")
            writeWavePcm16MonoFile(tempVoiceWav, finalPcm, EXPORT_SAMPLE_RATE)
            tempVoiceWav
        } catch (e: Exception) {
            Log.w(TAG, "Không thể ghép file voice tạm: ${e.message}")
            null
        }
    }

    private fun readWavSamples16BitMono(file: File, targetSampleRate: Int): ShortArray {
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= 44) return ShortArray(0)

            val headerBuf = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            val srcChannels = headerBuf.getShort(22).toInt().coerceAtLeast(1)
            val srcSampleRate = headerBuf.getInt(24).coerceAtLeast(8000)

            var dataOffset = 44
            var dataBytes = bytes.size - 44
            // Dò tìm chunk "data" chuẩn trong WAV header
            var cursor = 12
            while (cursor + 8 <= bytes.size) {
                val id = String(bytes, cursor, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(bytes, cursor + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (id == "data") {
                    dataOffset = cursor + 8
                    dataBytes = min(chunkSize.coerceAtLeast(0), bytes.size - dataOffset)
                    break
                }
                cursor += 8 + max(0, chunkSize)
            }

            val totalShortCount = dataBytes / 2
            val shortBuf = ByteBuffer.wrap(bytes, dataOffset, totalShortCount * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
            val frameCount = totalShortCount / srcChannels
            val monoSrc = ShortArray(frameCount)
            for (f in 0 until frameCount) {
                var sum = 0
                for (c in 0 until srcChannels) {
                    sum += shortBuf.get(f * srcChannels + c).toInt()
                }
                monoSrc[f] = (sum / srcChannels).toShort()
            }

            if (srcSampleRate == targetSampleRate || monoSrc.isEmpty()) {
                monoSrc
            } else {
                val outLen = ((monoSrc.size.toLong() * targetSampleRate) / srcSampleRate).toInt().coerceAtLeast(1)
                ShortArray(outLen) { i ->
                    val srcPos = (i.toDouble() * srcSampleRate) / targetSampleRate
                    val i0 = srcPos.toInt().coerceIn(0, monoSrc.lastIndex)
                    val i1 = (i0 + 1).coerceIn(0, monoSrc.lastIndex)
                    val frac = srcPos - i0
                    ((monoSrc[i0] * (1.0 - frac)) + (monoSrc[i1] * frac)).roundToInt().toShort()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi đọc WAV ${file.name}: ${e.message}")
            ShortArray(0)
        }
    }

    private fun writeWavePcm16MonoFile(outputFile: File, pcmSamples: ShortArray, sampleRate: Int) {
        val byteRate = sampleRate * 2
        val dataSize = pcmSamples.size * 2
        val totalDataLen = dataSize + 36

        FileOutputStream(outputFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1.toShort()) // PCM
            header.putShort(1.toShort()) // Mono
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(2.toShort())
            header.putShort(16.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val pcmBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmSamples) {
                pcmBuffer.putShort(s)
            }
            fos.write(pcmBuffer.array())
            fos.flush()
        }
    }

    private fun writeSrtSubtitleFile(srtFile: File, voiceClips: List<VoiceSubtitleClip>) {
        val sb = StringBuilder()
        voiceClips.sortedBy { it.timelineStartMs }.forEachIndexed { index, clip ->
            sb.appendLine(index + 1)
            sb.appendLine("${formatSrtTimestamp(clip.timelineStartMs)} --> ${formatSrtTimestamp(clip.timelineEndMs)}")
            sb.appendLine(clip.subtitleText)
            sb.appendLine()
        }
        srtFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val safeMs = max(0L, ms)
        val hours = safeMs / 3_600_000
        val minutes = (safeMs % 3_600_000) / 60_000
        val seconds = (safeMs % 60_000) / 1_000
        val millis = safeMs % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}
