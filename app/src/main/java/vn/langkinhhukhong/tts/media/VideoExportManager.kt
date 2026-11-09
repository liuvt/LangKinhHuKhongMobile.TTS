package vn.langkinhhukhong.tts.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import vn.langkinhhukhong.tts.model.CanvasImageOverlayItem
import vn.langkinhhukhong.tts.model.CanvasTextOverlayItem
import vn.langkinhhukhong.tts.model.SecondaryMp3Clip
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.SubtitleOverlayStyle
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Bộ xuất Video MP4 9:16 Offline sử dụng AndroidX Media3 Transformer:
 * - Áp dụng cắt ngắn Video (`trimStartMs` -> `trimEndMs`), tắt/bật tiếng gốc (`muteOriginalAudio`) & tốc độ (`0.5x` -> `2.0x`).
 * - Ghép chính xác từng đoạn MP3/WAV Voice đã cắt/kéo theo đúng mốc thời gian `timelineStartMs` trên Timeline.
 * - Đốt (Burn-in) Phụ đề Karaoke + Text Overlays + Icon/Logo PNG (kèm hiệu ứng Làm mờ Opacity & Blur, vị trí kéo thả X/Y).
 * - Xuất kèm file phụ đề chuẩn `.SRT` đồng bộ từng mili-giây.
 */
class VideoExportManager(private val context: Context) {

    @OptIn(UnstableApi::class)
    suspend fun exportProjectToMp4(
        videoTrack: ShortVideoTrack,
        voiceClips: List<VoiceSubtitleClip>,
        secondaryMp3Clips: List<SecondaryMp3Clip>,
        textOverlays: List<CanvasTextOverlayItem> = emptyList(),
        imageOverlays: List<CanvasImageOverlayItem> = emptyList(),
        subtitleStyle: SubtitleOverlayStyle = SubtitleOverlayStyle()
    ): Result<File> = suspendCancellableCoroutine { cont ->
        try {
            val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val outMp4File = File(exportDir, "LKHK_Short_${System.currentTimeMillis()}.mp4")
            val outSrtFile = File(exportDir, outMp4File.nameWithoutExtension + ".srt")

            // 1. Xuất file .SRT đồng bộ chính xác theo vị trí kéo/cắt hiện tại của các khối Voice
            exportSyncedSrtFile(voiceClips, outSrtFile)

            val textureOverlays = mutableListOf<TextureOverlay>()

            // 2A. Overlay Phụ đề Karaoke (Đặt theo đúng tọa độ X/Y, Size Text Phụ đề và độ mờ người dùng đã kéo trên khu View)
            val subtitleOverlay = object : TextOverlay() {
                override fun getText(presentationTimeUs: Long): SpannableString {
                    val currentMs = presentationTimeUs / 1000L
                    val activeClip = voiceClips.firstOrNull { clip ->
                        currentMs in clip.timelineStartMs..clip.timelineEndMs
                    } ?: return SpannableString("")

                    val allTokens = activeClip.getMappedWords(currentMs)
                    if (allTokens.isEmpty()) return SpannableString("")

                    val chunks = allTokens.chunked(7)
                    val mappedWords = chunks.firstOrNull { chunk ->
                        chunk.any { it.isHighlighted }
                    } ?: chunks.firstOrNull { chunk ->
                        currentMs in chunk.first().timelineStartMs..chunk.last().timelineEndMs
                    } ?: chunks.last()

                    val fullLine = mappedWords.joinToString(" ") { it.word }
                    val spannable = SpannableString(fullLine)

                    val sizeRatio = (subtitleStyle.fontSizeSp / 15f).coerceIn(0.65f, 2.5f)
                    if (spannable.isNotEmpty()) {
                        spannable.setSpan(
                            RelativeSizeSpan(sizeRatio),
                            0,
                            spannable.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    var charCursor = 0
                    for (token in mappedWords) {
                        val endCursor = (charCursor + token.word.length).coerceAtMost(fullLine.length)
                        if (token.isHighlighted && charCursor < endCursor) {
                            spannable.setSpan(
                                ForegroundColorSpan(android.graphics.Color.YELLOW),
                                charCursor,
                                endCursor,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        charCursor = endCursor + 1
                    }
                    return spannable
                }

                override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                    val anchorX = ((subtitleStyle.normalizedX - 0.5f) * 2f).coerceIn(-0.95f, 0.95f)
                    val anchorY = ((0.5f - subtitleStyle.normalizedY) * 2f).coerceIn(-0.95f, 0.95f)
                    return OverlaySettings.Builder()
                        .setBackgroundFrameAnchor(anchorX, anchorY)
                        .setAlphaScale(subtitleStyle.opacity.coerceIn(0.1f, 1.0f))
                        .build()
                }
            }
            textureOverlays.add(subtitleOverlay)

            // 2B. Overlays cho các lớp Chữ (Text Overlays) kèm vị trí X/Y, Size Text và Độ mờ (Opacity)
            for (textItem in textOverlays) {
                val customTextOverlay = object : TextOverlay() {
                    override fun getText(presentationTimeUs: Long): SpannableString {
                        val currentMs = presentationTimeUs / 1000L
                        if (!textItem.isVisibleAt(currentMs)) return SpannableString("")
                        val spannable = SpannableString(textItem.text)
                        val alphaInt = (textItem.opacity.coerceIn(0.05f, 1.0f) * 255).toInt()
                        val baseColor = textItem.colorHex.toInt()
                        val colorWithAlpha = (baseColor and 0x00FFFFFF) or (alphaInt shl 24)
                        val sizeRatio = (textItem.fontSizeSp / 16f).coerceIn(0.6f, 3.5f)
                        if (spannable.isNotEmpty()) {
                            spannable.setSpan(
                                RelativeSizeSpan(sizeRatio),
                                0,
                                spannable.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            spannable.setSpan(
                                ForegroundColorSpan(colorWithAlpha),
                                0,
                                spannable.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        return spannable
                    }

                    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                        val anchorX = ((textItem.normalizedX - 0.5f) * 2f).coerceIn(-0.95f, 0.95f)
                        val anchorY = ((0.5f - textItem.normalizedY) * 2f).coerceIn(-0.95f, 0.95f)
                        return OverlaySettings.Builder()
                            .setBackgroundFrameAnchor(anchorX, anchorY)
                            .setAlphaScale(textItem.opacity.coerceIn(0.05f, 1.0f))
                            .build()
                    }
                }
                textureOverlays.add(customTextOverlay)
            }

            // 2C. Overlays cho các lớp Icon / Logo PNG (Kèm làm mờ Opacity & Blur + Vị trí kéo thả X/Y)
            for (imgItem in imageOverlays) {
                val processedBitmap = loadAndProcessOverlayBitmap(imgItem)
                if (processedBitmap != null) {
                    val emptyBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                    val logoOverlay = object : BitmapOverlay() {
                        override fun getBitmap(presentationTimeUs: Long): Bitmap {
                            val currentMs = presentationTimeUs / 1000L
                            return if (imgItem.isVisibleAt(currentMs)) processedBitmap else emptyBitmap
                        }

                        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                            val anchorX = ((imgItem.normalizedX - 0.5f) * 2f).coerceIn(-0.95f, 0.95f)
                            val anchorY = ((0.5f - imgItem.normalizedY) * 2f).coerceIn(-0.95f, 0.95f)
                            return OverlaySettings.Builder()
                                .setBackgroundFrameAnchor(anchorX, anchorY)
                                .setAlphaScale(imgItem.opacity.coerceIn(0.05f, 1.0f))
                                .build()
                        }
                    }
                    textureOverlays.add(logoOverlay)
                }
            }

            val videoEffects = mutableListOf<Effect>(
                OverlayEffect(ImmutableList.copyOf(textureOverlays))
            )

            // 3. Cấu hình tăng/giảm tốc độ (Speed) qua SonicAudioProcessor
            val audioProcessors = mutableListOf<AudioProcessor>()
            if (videoTrack.speedMultiplier != 1.0f) {
                val sonic = SonicAudioProcessor().apply {
                    setSpeed(videoTrack.speedMultiplier)
                    setPitch(1.0f)
                }
                audioProcessors.add(sonic)
            }

            // 4. Áp dụng Cắt ngắn Video (trimStartMs .. trimEndMs) & Tắt/Bật tiếng gốc (muteOriginalAudio)
            val mainMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(videoTrack.videoUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(videoTrack.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(videoTrack.trimEndMs.coerceAtLeast(videoTrack.trimStartMs + 500L))
                        .build()
                )
                .build()

            val editedVideoItem = EditedMediaItem.Builder(mainMediaItem)
                .setRemoveAudio(videoTrack.muteOriginalAudio)
                .setEffects(Effects(audioProcessors, videoEffects))
                .build()

            val sequences = mutableListOf(
                EditedMediaItemSequence(listOf(editedVideoItem))
            )

            // 5. Ghép các đoạn Voice AI đã cắt/kéo đúng theo mốc thời gian `timelineStartMs` trên Timeline
            val voiceSequenceItems = buildTimelineSyncedVoiceItems(voiceClips)
            if (voiceSequenceItems.isNotEmpty()) {
                sequences.add(EditedMediaItemSequence(voiceSequenceItems))
            }

            // 6. Ghép các file MP3 phụ (BGM / SFX)
            val sortedSecondary = secondaryMp3Clips.sortedBy { it.timelineStartMs }
            if (sortedSecondary.isNotEmpty()) {
                val bgmItems = sortedSecondary.map { bgm ->
                    val item = MediaItem.Builder()
                        .setUri(Uri.parse(bgm.audioUriOrPath))
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(bgm.trimStartMs)
                                .setEndPositionMs(bgm.trimEndMs)
                                .build()
                        )
                        .build()
                    EditedMediaItem.Builder(item).build()
                }
                sequences.add(EditedMediaItemSequence(bgmItems))
            }

            val composition = Composition.Builder(sequences).build()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) {
                            cont.resume(Result.success(outMp4File))
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) {
                            cont.resume(Result.failure(exportException))
                        }
                    }
                })
                .build()

            transformer.start(composition, outMp4File.absolutePath)

            cont.invokeOnCancellation {
                transformer.cancel()
            }
        } catch (t: Throwable) {
            if (cont.isActive) {
                cont.resume(Result.failure(t))
            }
        }
    }

    /**
     * Tạo chuỗi `EditedMediaItem` cho các đoạn Voice (`VoiceSubtitleClip` định dạng `.wav` hoặc `.mp3`),
     * tự động chèn các đoạn âm thanh khoảng lặng (Silence WAV) vào những khoảng trống giữa các đoạn
     * để sau khi kéo thả/cắt chia đoạn trên Timeline, tiếng đọc khớp 100% với vị trí thực tế trên Video.
     */
    @OptIn(UnstableApi::class)
    private fun buildTimelineSyncedVoiceItems(
        voiceClips: List<VoiceSubtitleClip>
    ): List<EditedMediaItem> {
        if (voiceClips.isEmpty()) return emptyList()
        val sorted = voiceClips.sortedBy { it.timelineStartMs }
        val items = mutableListOf<EditedMediaItem>()
        val tempDir = File(context.cacheDir, "mixed_audio").apply { mkdirs() }
        var cursorMs = 0L

        for ((index, clip) in sorted.withIndex()) {
            val audioFile = File(clip.audioFilePath)
            if (!audioFile.exists() || audioFile.length() == 0L) continue

            val gapMs = clip.timelineStartMs - cursorMs
            if (gapMs >= 80L) {
                val silenceFile = createSilenceWavFile(
                    outFile = File(tempDir, "silence_gap_${index}_${gapMs}ms.wav"),
                    durationMs = gapMs
                )
                if (silenceFile != null) {
                    val silenceItem = MediaItem.fromUri(Uri.fromFile(silenceFile))
                    items.add(EditedMediaItem.Builder(silenceItem).build())
                }
                cursorMs += gapMs
            }

            val clippedVoiceItem = MediaItem.Builder()
                .setUri(Uri.fromFile(audioFile))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 100L))
                        .build()
                )
                .build()
            items.add(EditedMediaItem.Builder(clippedVoiceItem).build())
            cursorMs = clip.timelineEndMs
        }

        return items
    }

    private fun createSilenceWavFile(outFile: File, durationMs: Long): File? {
        return runCatching {
            val sampleRate = 22050
            val totalSamples = ((durationMs.coerceIn(80L, 180_000L) / 1000.0) * sampleRate).toInt().coerceAtLeast(100)
            val silentPcm = ByteArray(totalSamples * 2)
            writeWavHeaderAndData(outFile, silentPcm, sampleRate)
            outFile
        }.getOrNull()
    }

    private fun loadAndProcessOverlayBitmap(item: CanvasImageOverlayItem): Bitmap? {
        return runCatching {
            val uri = Uri.parse(item.imageUri)
            val rawBitmap = if (uri.scheme == "file" && uri.path != null) {
                BitmapFactory.decodeFile(uri.path)
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } ?: return null

            val targetPx = (item.sizeDp * 2.5f).toInt().coerceIn(48, 512)
            val scaled = Bitmap.createScaledBitmap(rawBitmap, targetPx, targetPx, true)
            val pad = (item.blurRadiusDp * 2f).toInt().coerceAtLeast(4)
            val outBmp = Bitmap.createBitmap(targetPx + pad * 2, targetPx + pad * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (item.opacity.coerceIn(0.05f, 1.0f) * 255).toInt()
                if (item.blurRadiusDp > 0.5f) {
                    maskFilter = BlurMaskFilter(item.blurRadiusDp * 1.5f, BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.drawBitmap(scaled, pad.toFloat(), pad.toFloat(), paint)
            outBmp
        }.getOrNull()
    }

    private fun writeWavHeaderAndData(file: File, pcmData: ByteArray, sampleRate: Int) {
        val channels = 1
        val bitsPerSample = 16
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

    /**
     * Xuất file phụ đề .SRT chuẩn xác theo vị trí mới của các khối MP3 sau khi người dùng kéo/thả/cắt.
     */
    fun exportSyncedSrtFile(voiceClips: List<VoiceSubtitleClip>, targetSrtFile: File): File {
        val sortedClips = voiceClips.sortedBy { it.timelineStartMs }
        val sb = StringBuilder()
        var srtIndex = 1

        for (clip in sortedClips) {
            val mappedTokens = clip.getMappedWords(0L)
            if (mappedTokens.isEmpty()) continue

            mappedTokens.chunked(6).forEach { chunk ->
                val startMs = chunk.first().timelineStartMs
                val endMs = chunk.last().timelineEndMs
                val lineText = chunk.joinToString(" ") { it.word }

                sb.appendLine(srtIndex++)
                sb.appendLine("${formatSrtTimestamp(startMs)} --> ${formatSrtTimestamp(endMs)}")
                sb.appendLine(lineText)
                sb.appendLine()
            }
        }

        targetSrtFile.writeText(sb.toString(), Charsets.UTF_8)
        return targetSrtFile
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        val millis = ms % 1000
        return String.format(
            Locale.US,
            "%02d:%02d:%02d,%03d",
            hours,
            minutes,
            seconds,
            millis
        )
    }
}
