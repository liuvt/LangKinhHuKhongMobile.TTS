package vn.langkinhhukhong.tts.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.langkinhhukhong.tts.ads.AdMonetizationManager
import vn.langkinhhukhong.tts.media.VideoExportManager
import vn.langkinhhukhong.tts.model.CanvasImageOverlayItem
import vn.langkinhhukhong.tts.model.CanvasTextOverlayItem
import vn.langkinhhukhong.tts.model.EditorConstraints
import vn.langkinhhukhong.tts.model.MonetizationConfig
import vn.langkinhhukhong.tts.model.OfflineVoiceProfile
import vn.langkinhhukhong.tts.model.SecondaryMp3Clip
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.SubtitleOverlayStyle
import vn.langkinhhukhong.tts.model.TimelineSubtitleToken
import vn.langkinhhukhong.tts.model.VideoSegmentClip
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import vn.langkinhhukhong.tts.tts.SherpaOfflineTtsEngine
import java.io.File
import java.io.FileOutputStream

enum class ExportFormat {
    MP3,
    MP4
}

data class EditorUiState(
    val videoTrack: ShortVideoTrack = ShortVideoTrack(
        videoUri = "",
        displayName = "Chưa chọn Video 9:16",
        originalDurationMs = 30_000L,
        trimStartMs = 0L,
        trimEndMs = 30_000L,
        width = 1080,
        height = 1920,
        speedMultiplier = 1.0f,
        muteOriginalAudio = false
    ),
    val monetizationConfig: MonetizationConfig = MonetizationConfig(),
    val voiceCatalog: List<OfflineVoiceProfile> = OfflineVoiceProfile.DEFAULT_ONLINE_LIBRARY_CATALOG,
    val voiceClips: List<VoiceSubtitleClip> = emptyList(),
    val secondaryMp3Clips: List<SecondaryMp3Clip> = emptyList(),
    val textOverlays: List<CanvasTextOverlayItem> = emptyList(),
    val imageOverlays: List<CanvasImageOverlayItem> = emptyList(),
    val subtitleStyle: SubtitleOverlayStyle = SubtitleOverlayStyle(),
    val selectedVoiceProfile: OfflineVoiceProfile = OfflineVoiceProfile.BUILT_IN_MALE_MINH_KHANG,
    val remainingCharQuota: Int = AdMonetizationManager.MAX_CHARACTER_QUOTA,
    val maxCharQuota: Int = AdMonetizationManager.MAX_CHARACTER_QUOTA,
    val showFallbackQuotaAdDialog: Boolean = false,
    val showExportFormatSheet: Boolean = false,
    val showFallbackExportAdDialog: Boolean = false,
    val pendingExportFormat: ExportFormat? = null,
    val totalAdsWatched: Int = 0,
    val ttsInputText: String = "Xin chào các bạn, hôm nay chúng ta cùng khám phá câu chuyện thú vị này nhé.",
    val ttsSpeechRate: Float = 1.0f,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val isSynthesizingTts: Boolean = false,
    val isExportingVideo: Boolean = false,
    val statusMessage: String = ""
) {
    val installedVoices: List<OfflineVoiceProfile>
        get() = voiceCatalog

    /** Tổng thời lượng Timeline dựa trên Video (tối đa 3 phút) hoặc các đoạn Voice đã tạo */
    val totalTimelineDurationMs: Long
        get() {
            val maxVoiceEnd = voiceClips.maxOfOrNull { it.timelineEndMs } ?: 0L
            val baseDuration = if (videoTrack.videoUri.isNotBlank()) {
                maxOf(videoTrack.effectiveDurationMs, maxVoiceEnd)
            } else {
                maxOf(30_000L, maxVoiceEnd + 3_000L)
            }
            return baseDuration.coerceIn(3_000L, EditorConstraints.MAX_VIDEO_DURATION_MS)
        }

    val activeVoiceClipAtPlayhead: VoiceSubtitleClip?
        get() = voiceClips.lastOrNull { playheadMs in it.timelineStartMs..it.timelineEndMs }

    val activeVideoSegmentAtPlayhead: VideoSegmentClip?
        get() {
            val speed = videoTrack.speedMultiplier
            return videoTrack.segments.lastOrNull { seg ->
                playheadMs in seg.timelineStartMs..seg.timelineEndAtSpeedMs(speed)
            }
        }

    /**
     * Hiển thị cụm phụ đề ngắn gọn (tối đa 7 từ quanh từ đang đọc) chuẩn CapCut để dễ nhìn và chỉnh cỡ chữ.
     */
    val currentSubtitleTokens: List<TimelineSubtitleToken>
        get() {
            val allTokens = activeVoiceClipAtPlayhead?.getMappedWords(playheadMs) ?: return emptyList()
            if (allTokens.size <= 7) return allTokens
            val chunks = allTokens.chunked(7)
            return chunks.firstOrNull { chunk ->
                chunk.any { it.isHighlighted } ||
                    playheadMs in chunk.first().timelineStartMs..chunk.last().timelineEndMs
            } ?: chunks.first()
        }

    val visibleTextOverlaysAtPlayhead: List<CanvasTextOverlayItem>
        get() = textOverlays.filter { it.isVisibleAt(playheadMs) }

    val visibleImageOverlaysAtPlayhead: List<CanvasImageOverlayItem>
        get() = imageOverlays.filter { it.isVisibleAt(playheadMs) }

    val selectedVideoSegment: VideoSegmentClip?
        get() = videoTrack.segments.firstOrNull { it.id == selectedClipId }

    val selectedVoiceClip: VoiceSubtitleClip?
        get() = voiceClips.firstOrNull { it.id == selectedClipId }

    val selectedTextOverlay: CanvasTextOverlayItem?
        get() = textOverlays.firstOrNull { it.id == selectedClipId }

    val selectedImageOverlay: CanvasImageOverlayItem?
        get() = imageOverlays.firstOrNull { it.id == selectedClipId }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val ttsEngine = SherpaOfflineTtsEngine(application)
    private val exportManager = VideoExportManager(application)
    private val adManager = AdMonetizationManager(application)

    private val _uiState = MutableStateFlow(
        EditorUiState(
            remainingCharQuota = AdMonetizationManager.MAX_CHARACTER_QUOTA,
            maxCharQuota = AdMonetizationManager.MAX_CHARACTER_QUOTA,
            totalAdsWatched = adManager.getTotalAdsWatchedByUser()
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var activeTtsMediaPlayer: MediaPlayer? = null
    private var activeClipPlayingId: String? = null

    // =========================================================================
    // 1. VIDEO MP4 9:16 (<= 3 PHÚT): NẠP VIDEO, KÉO/CẮT ĐOẠN VIDEO, TẮT/BẬT TIẾNG MP4
    // =========================================================================

    fun importShortVideo(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(getApplication(), uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 30_000L
                val width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 1080
                val height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 1920
                val rotation = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0
                retriever.release()

                val actualW = if (rotation == 90 || rotation == 270) height else width
                val actualH = if (rotation == 90 || rotation == 270) width else height

                val clampedDuration = durationMs.coerceAtMost(EditorConstraints.MAX_VIDEO_DURATION_MS)
                val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video_9x16.mp4"
                val firstSegment = VideoSegmentClip(
                    label = displayName,
                    timelineStartMs = 0L,
                    sourceStartMs = 0L,
                    sourceEndMs = clampedDuration
                )

                _uiState.update { state ->
                    state.copy(
                        videoTrack = ShortVideoTrack(
                            videoUri = uri.toString(),
                            displayName = displayName,
                            originalDurationMs = clampedDuration,
                            trimStartMs = 0L,
                            trimEndMs = clampedDuration,
                            width = actualW,
                            height = actualH,
                            speedMultiplier = 1.0f,
                            muteOriginalAudio = state.videoTrack.muteOriginalAudio,
                            segments = listOf(firstSegment)
                        ),
                        selectedClipId = firstSegment.id,
                        playheadMs = 0L,
                        statusMessage = "Đã nạp Video 9:16 (${clampedDuration / 1000}s)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "Không thể đọc video: ${err.message}")
                }
            }
        }
    }

    fun toggleMuteOriginalVideo() {
        _uiState.update { state ->
            val nextMute = !state.videoTrack.muteOriginalAudio
            state.copy(
                videoTrack = state.videoTrack.copy(muteOriginalAudio = nextMute),
                statusMessage = if (nextMute) {
                    "🔇 Đã tắt tiếng gốc MP4"
                } else {
                    "🔊 Đã bật tiếng gốc MP4"
                }
            )
        }
    }

    fun moveVideoSegmentByDelta(segmentId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val updatedSegs = state.videoTrack.segments.map { seg ->
                if (seg.id == segmentId) {
                    val nextStart = (seg.timelineStartMs + deltaTimelineMs)
                        .coerceIn(0L, EditorConstraints.MAX_VIDEO_DURATION_MS - 500L)
                    seg.copy(timelineStartMs = nextStart)
                } else {
                    seg
                }
            }
            state.copy(
                videoTrack = state.videoTrack.copy(segments = updatedSegs),
                selectedClipId = segmentId
            )
        }
    }

    fun setVideoSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        _uiState.update { state ->
            val updatedVideo = state.videoTrack.copy(speedMultiplier = safeSpeed)
            val newMaxDuration = updatedVideo.effectiveDurationMs.coerceAtMost(EditorConstraints.MAX_VIDEO_DURATION_MS)
            state.copy(
                videoTrack = updatedVideo,
                playheadMs = state.playheadMs.coerceAtMost(newMaxDuration),
                statusMessage = "Tốc độ Video: ${safeSpeed}x (${formatMs(newMaxDuration)})"
            )
        }
    }

    // =========================================================================
    // 2. CHUYỂN TEXT THÀNH VOICE + SUB PHỤ ĐỀ (TỐI ĐA 1000 KÝ TỰ / LẦN)
    // =========================================================================

    fun selectVoiceProfile(profile: OfflineVoiceProfile) {
        _uiState.update {
            it.copy(
                selectedVoiceProfile = profile,
                statusMessage = "Đã chọn giọng: ${profile.displayName}"
            )
        }
    }

    fun updateTtsInputText(newText: String) {
        val capped = if (newText.length > AdMonetizationManager.MAX_CHARACTER_QUOTA) {
            newText.take(AdMonetizationManager.MAX_CHARACTER_QUOTA)
        } else {
            newText
        }
        _uiState.update {
            it.copy(
                ttsInputText = capped,
                remainingCharQuota = (AdMonetizationManager.MAX_CHARACTER_QUOTA - capped.length)
                    .coerceIn(0, AdMonetizationManager.MAX_CHARACTER_QUOTA)
            )
        }
    }

    fun updateTtsSpeechRate(rate: Float) {
        _uiState.update { it.copy(ttsSpeechRate = rate.coerceIn(0.6f, 1.8f)) }
    }

    fun previewVoiceSample(voice: OfflineVoiceProfile) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(statusMessage = "🔊 Đang tạo mẫu nghe thử giọng ${voice.displayName}...")
            }
            val sampleRes = ttsEngine.synthesizeWithSubtitles(
                text = "Xin chào, đây là giọng đọc ${voice.displayName}.",
                voiceProfile = voice,
                speechSpeed = _uiState.value.ttsSpeechRate,
                insertAtTimelineMs = 0L,
                onStatusProgress = { progressMsg ->
                    _uiState.update { s -> s.copy(statusMessage = progressMsg) }
                }
            )
            sampleRes.onSuccess { clip ->
                stopTtsAudioPlayer()
                runCatching {
                    val mp = createSpeechMediaPlayer(clip.audioFilePath, volume = 1.0f)
                    mp.start()
                    activeTtsMediaPlayer = mp
                    _uiState.update {
                        it.copy(statusMessage = "🔊 Đang phát mẫu giọng: ${voice.displayName}")
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "Lỗi phát thử: ${err.message}")
                }
            }
        }
    }

    /**
     * Chuyển Text thành file Voice + Tự động sinh Sub phụ đề kèm theo:
     * - Cho phép tối đa 1000 ký tự mỗi lần chuyển.
     * - Không chặn bằng quảng cáo khi tạo giọng đọc (quảng cáo sẽ hiển thị theo lượt xuất file MP3/MP4 + Banner treo tự động).
     */
    fun renderOfflineTtsAndAttachSubtitle(
        @Suppress("UNUSED_PARAMETER") activity: Activity? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val currentState = _uiState.value
        val voice = currentState.selectedVoiceProfile
        val text = currentState.ttsInputText.trim().take(AdMonetizationManager.MAX_CHARACTER_QUOTA)

        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Vui lòng nhập văn bản cần chuyển thành Voice!") }
            return
        }

        val insertAtMs = if (currentState.voiceClips.any { currentState.playheadMs in it.timelineStartMs until it.timelineEndMs }) {
            val maxEnd = currentState.voiceClips.maxOfOrNull { it.timelineEndMs } ?: 0L
            (maxEnd + 200L).coerceAtMost((currentState.totalTimelineDurationMs - 1_000L).coerceAtLeast(0L))
        } else {
            currentState.playheadMs
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSynthesizingTts = true,
                    statusMessage = "⏳ Đang chuyển ${text.length} ký tự thành Voice (${voice.displayName})..."
                )
            }

            val result = ttsEngine.synthesizeWithSubtitles(
                text = text,
                voiceProfile = voice,
                speechSpeed = currentState.ttsSpeechRate,
                insertAtTimelineMs = insertAtMs,
                onStatusProgress = { progressMsg ->
                    _uiState.update { s -> s.copy(statusMessage = progressMsg) }
                }
            )

            result.onSuccess { newClip ->
                _uiState.update { state ->
                    state.copy(
                        isSynthesizingTts = false,
                        voiceClips = (state.voiceClips + newClip).sortedBy { it.timelineStartMs },
                        selectedClipId = newClip.id,
                        playheadMs = newClip.timelineStartMs,
                        isPlaying = true,
                        statusMessage = "✅ Đã tạo Voice (${formatMs(newClip.durationMs)})"
                    )
                }
                onComplete?.invoke()

                syncAudioPlaybackWithPlayhead(newClip.timelineStartMs, isPlaying = true, forceSeek = true)
                startPlaybackTicker()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSynthesizingTts = false,
                        statusMessage = "❌ Lỗi tạo Voice: ${err.message}"
                    )
                }
            }
        }
    }

    fun moveVoiceClipByDelta(clipId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val maxDuration = state.totalTimelineDurationMs
            val updatedClips = state.voiceClips.map { clip ->
                if (clip.id == clipId) {
                    clip.moveTo(clip.timelineStartMs + deltaTimelineMs, maxDuration)
                } else {
                    clip
                }
            }
            state.copy(
                voiceClips = updatedClips,
                selectedClipId = clipId
            )
        }
    }

    fun moveSubtitleOverlayOnCanvas(deltaNormX: Float, deltaNormY: Float) {
        _uiState.update { state ->
            val current = state.subtitleStyle
            val nextX = (current.normalizedX + deltaNormX).coerceIn(0.10f, 0.90f)
            val nextY = (current.normalizedY + deltaNormY).coerceIn(0.08f, 0.92f)
            state.copy(
                subtitleStyle = current.copy(normalizedX = nextX, normalizedY = nextY)
            )
        }
    }

    fun updateSubtitleStyle(
        fontSizeSp: Float? = null,
        opacity: Float? = null,
        blurRadiusDp: Float? = null
    ) {
        _uiState.update { state ->
            val cur = state.subtitleStyle
            val nextSize = fontSizeSp?.coerceIn(10f, 36f) ?: cur.fontSizeSp
            val nextOpacity = opacity?.coerceIn(0.1f, 1.0f) ?: cur.opacity
            val nextBlur = blurRadiusDp?.coerceIn(0f, 20f) ?: cur.blurRadiusDp
            state.copy(
                subtitleStyle = cur.copy(
                    fontSizeSp = nextSize,
                    opacity = nextOpacity,
                    blurRadiusDp = nextBlur
                )
            )
        }
    }

    // =========================================================================
    // 3. TIÊU ĐỀ & STICKER (LÀM MỜ + KÉO THẢ VỊ TRÍ BẤT KỲ)
    // =========================================================================

    fun addTextOverlay(
        text: String,
        fontSizeSp: Float = 20f,
        colorHex: Long = 0xFFFFFFFF,
        opacity: Float = 1.0f,
        blurRadiusDp: Float = 0f
    ) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        _uiState.update { state ->
            val startMs = state.playheadMs
            val endMs = (startMs + 6_000L).coerceAtMost(state.totalTimelineDurationMs)
                .coerceAtLeast(startMs + 1_500L)
            val newItem = CanvasTextOverlayItem(
                text = cleaned,
                normalizedX = 0.5f,
                normalizedY = (0.25f + (state.textOverlays.size % 4) * 0.12f).coerceIn(0.15f, 0.75f),
                fontSizeSp = fontSizeSp,
                colorHex = colorHex,
                opacity = opacity.coerceIn(0.1f, 1.0f),
                blurRadiusDp = blurRadiusDp.coerceIn(0f, 25f),
                timelineStartMs = startMs,
                timelineEndMs = endMs
            )
            state.copy(
                textOverlays = state.textOverlays + newItem,
                selectedClipId = newItem.id,
                statusMessage = "Đã thêm Tiêu đề '${newItem.text}'"
            )
        }
    }

    fun updateSelectedTextOverlay(
        text: String? = null,
        fontSizeSp: Float? = null,
        colorHex: Long? = null,
        opacity: Float? = null,
        blurRadiusDp: Float? = null
    ) {
        val targetId = _uiState.value.selectedClipId ?: return
        _uiState.update { state ->
            state.copy(
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == targetId) {
                        item.copy(
                            text = text ?: item.text,
                            fontSizeSp = fontSizeSp?.coerceIn(10f, 56f) ?: item.fontSizeSp,
                            colorHex = colorHex ?: item.colorHex,
                            opacity = opacity?.coerceIn(0.05f, 1.0f) ?: item.opacity,
                            blurRadiusDp = blurRadiusDp?.coerceIn(0f, 25f) ?: item.blurRadiusDp
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun moveTextOverlayOnCanvas(overlayId: String, deltaNormX: Float, deltaNormY: Float) {
        _uiState.update { state ->
            state.copy(
                selectedClipId = overlayId,
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == overlayId) {
                        item.copy(
                            normalizedX = (item.normalizedX + deltaNormX).coerceIn(0.08f, 0.92f),
                            normalizedY = (item.normalizedY + deltaNormY).coerceIn(0.06f, 0.94f)
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun moveTextOverlayOnTimeline(overlayId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val maxDuration = state.totalTimelineDurationMs
            state.copy(
                selectedClipId = overlayId,
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == overlayId) {
                        item.moveTimelineTo(item.timelineStartMs + deltaTimelineMs, maxDuration)
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun importLogoPngOverlay(uri: Uri) {
        viewModelScope.launch {
            val copiedFile = withContext(Dispatchers.IO) {
                runCatching {
                    val overlayDir = File(getApplication<Application>().filesDir, "overlays").apply { mkdirs() }
                    val dstFile = File(overlayDir, "sticker_${System.currentTimeMillis()}.png")
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dstFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (dstFile.exists() && dstFile.length() > 0L) dstFile else null
                }.getOrNull()
            }

            val resolvedUri = copiedFile?.let { Uri.fromFile(it).toString() } ?: uri.toString()
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Sticker.png"

            _uiState.update { state ->
                val startMs = state.playheadMs
                val endMs = state.totalTimelineDurationMs.coerceAtLeast(startMs + 3_000L)
                val newSticker = CanvasImageOverlayItem(
                    name = displayName,
                    imageUri = resolvedUri,
                    normalizedX = 0.78f,
                    normalizedY = 0.16f,
                    sizeDp = 72f,
                    opacity = 1.0f,
                    blurRadiusDp = 0f,
                    timelineStartMs = startMs,
                    timelineEndMs = endMs
                )
                state.copy(
                    imageOverlays = state.imageOverlays + newSticker,
                    selectedClipId = newSticker.id,
                    statusMessage = "Đã thêm Sticker ($displayName)"
                )
            }
        }
    }

    fun addSampleLogoPngOverlay(badgeLabel: String = "STICKER") {
        viewModelScope.launch {
            val logoFile = withContext(Dispatchers.IO) {
                val overlayDir = File(getApplication<Application>().filesDir, "overlays").apply { mkdirs() }
                val outFile = File(overlayDir, "sticker_${System.currentTimeMillis()}.png")
                val bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(220, 0, 229, 255)
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                }
                val rect = RectF(16f, 44f, 224f, 196f)
                canvas.drawRoundRect(rect, 36f, 36f, bgPaint)
                canvas.drawRoundRect(rect, 36f, 36f, borderPaint)

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textSize = 38f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.drawText(badgeLabel.take(9), 120f, 134f, textPaint)
                FileOutputStream(outFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                bmp.recycle()
                outFile
            }

            _uiState.update { state ->
                val startMs = state.playheadMs
                val endMs = state.totalTimelineDurationMs.coerceAtLeast(startMs + 3_000L)
                val newSticker = CanvasImageOverlayItem(
                    name = "Sticker_$badgeLabel.png",
                    imageUri = Uri.fromFile(logoFile).toString(),
                    normalizedX = 0.78f,
                    normalizedY = 0.16f,
                    sizeDp = 72f,
                    opacity = 0.95f,
                    blurRadiusDp = 0f,
                    timelineStartMs = startMs,
                    timelineEndMs = endMs
                )
                state.copy(
                    imageOverlays = state.imageOverlays + newSticker,
                    selectedClipId = newSticker.id,
                    statusMessage = "Đã tạo Sticker '$badgeLabel'"
                )
            }
        }
    }

    fun updateSelectedImageOverlay(
        sizeDp: Float? = null,
        opacity: Float? = null,
        blurRadiusDp: Float? = null
    ) {
        val targetId = _uiState.value.selectedClipId ?: return
        _uiState.update { state ->
            state.copy(
                imageOverlays = state.imageOverlays.map { item ->
                    if (item.id == targetId) {
                        item.copy(
                            sizeDp = sizeDp?.coerceIn(28f, 220f) ?: item.sizeDp,
                            opacity = opacity?.coerceIn(0.05f, 1.0f) ?: item.opacity,
                            blurRadiusDp = blurRadiusDp?.coerceIn(0f, 25f) ?: item.blurRadiusDp
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun moveImageOverlayOnCanvas(overlayId: String, deltaNormX: Float, deltaNormY: Float) {
        _uiState.update { state ->
            state.copy(
                selectedClipId = overlayId,
                imageOverlays = state.imageOverlays.map { item ->
                    if (item.id == overlayId) {
                        item.copy(
                            normalizedX = (item.normalizedX + deltaNormX).coerceIn(0.06f, 0.94f),
                            normalizedY = (item.normalizedY + deltaNormY).coerceIn(0.06f, 0.94f)
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun moveImageOverlayOnTimeline(overlayId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val maxDuration = state.totalTimelineDurationMs
            state.copy(
                selectedClipId = overlayId,
                imageOverlays = state.imageOverlays.map { item ->
                    if (item.id == overlayId) {
                        item.moveTimelineTo(item.timelineStartMs + deltaTimelineMs, maxDuration)
                    } else {
                        item
                    }
                }
            )
        }
    }

    // =========================================================================
    // 4. NÚT "CẮT" DÙNG CHUNG (CHỌN ĐỐI TƯỢNG + VỊ TRÍ PLAYHEAD -> BẤM CẮT) & XÓA
    // =========================================================================

    fun splitSelectedOrActiveClipAtPlayhead() {
        val state = _uiState.value
        val cutMs = state.playheadMs
        val selectedId = state.selectedClipId
        val videoSpeed = state.videoTrack.speedMultiplier

        // 1. Ưu tiên đối tượng đang được chọn trên Timeline:
        // 1A. Nếu đang chọn một đoạn Video MP4
        val selectedVidSeg = state.videoTrack.segments.firstOrNull { it.id == selectedId }
        if (selectedVidSeg != null) {
            val splitVid = selectedVidSeg.splitAtTimelineMs(cutMs, videoSpeed)
            if (splitVid != null) {
                val (leftSeg, rightSeg) = splitVid
                _uiState.update { current ->
                    val updatedSegs = current.videoTrack.segments.flatMap {
                        if (it.id == selectedVidSeg.id) listOf(leftSeg, rightSeg) else listOf(it)
                    }
                    current.copy(
                        videoTrack = current.videoTrack.copy(segments = updatedSegs),
                        selectedClipId = rightSeg.id,
                        statusMessage = "✂️ Đã cắt đôi đoạn Video tại ${formatMs(cutMs)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy đặt vạch đỏ Playhead vào bên trong đoạn Video đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 1B. Nếu đang chọn một đoạn Voice+Sub
        val selectedVoice = state.voiceClips.firstOrNull { it.id == selectedId }
        if (selectedVoice != null) {
            val splitVoice = selectedVoice.splitAtTimelineMs(cutMs)
            if (splitVoice != null) {
                val (leftClip, rightClip) = splitVoice
                _uiState.update { current ->
                    val remaining = current.voiceClips.filterNot { it.id == selectedVoice.id }
                    current.copy(
                        voiceClips = (remaining + leftClip + rightClip).sortedBy { it.timelineStartMs },
                        selectedClipId = rightClip.id,
                        statusMessage = "✂️ Đã cắt đôi Voice & Phụ đề tại ${formatMs(cutMs)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy đặt vạch đỏ Playhead vào bên trong đoạn Voice đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 1C. Nếu đang chọn một Tiêu đề (Text)
        val selectedText = state.textOverlays.firstOrNull { it.id == selectedId }
        if (selectedText != null) {
            val splitText = selectedText.splitAtTimelineMs(cutMs)
            if (splitText != null) {
                val (leftT, rightT) = splitText
                _uiState.update { current ->
                    val remaining = current.textOverlays.filterNot { it.id == selectedText.id }
                    current.copy(
                        textOverlays = (remaining + leftT + rightT).sortedBy { it.timelineStartMs },
                        selectedClipId = rightT.id,
                        statusMessage = "✂️ Đã cắt đôi Tiêu đề tại ${formatMs(cutMs)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy đặt vạch đỏ Playhead vào bên trong đoạn Tiêu đề đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 1D. Nếu đang chọn một Sticker
        val selectedImage = state.imageOverlays.firstOrNull { it.id == selectedId }
        if (selectedImage != null) {
            val splitImg = selectedImage.splitAtTimelineMs(cutMs)
            if (splitImg != null) {
                val (leftI, rightI) = splitImg
                _uiState.update { current ->
                    val remaining = current.imageOverlays.filterNot { it.id == selectedImage.id }
                    current.copy(
                        imageOverlays = (remaining + leftI + rightI).sortedBy { it.timelineStartMs },
                        selectedClipId = rightI.id,
                        statusMessage = "✂️ Đã cắt đôi Sticker tại ${formatMs(cutMs)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy đặt vạch đỏ Playhead vào bên trong đoạn Sticker đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 2. Nếu chưa chọn đối tượng nào, tự động cắt đối tượng nằm dưới Playhead (ưu tiên Voice -> Video)
        val autoVoice = state.voiceClips.firstOrNull { cutMs in it.timelineStartMs..it.timelineEndMs }
        if (autoVoice != null) {
            val splitPair = autoVoice.splitAtTimelineMs(cutMs)
            if (splitPair != null) {
                val (leftClip, rightClip) = splitPair
                _uiState.update { current ->
                    val remaining = current.voiceClips.filterNot { it.id == autoVoice.id }
                    current.copy(
                        voiceClips = (remaining + leftClip + rightClip).sortedBy { it.timelineStartMs },
                        selectedClipId = rightClip.id,
                        statusMessage = "✂️ Đã cắt đôi Voice & Phụ đề tại ${formatMs(cutMs)}"
                    )
                }
                return
            }
        }

        val autoVid = state.videoTrack.segments.firstOrNull {
            cutMs in it.timelineStartMs..it.timelineEndAtSpeedMs(videoSpeed)
        }
        if (autoVid != null) {
            val splitVid = autoVid.splitAtTimelineMs(cutMs, videoSpeed)
            if (splitVid != null) {
                val (leftSeg, rightSeg) = splitVid
                _uiState.update { current ->
                    val updatedSegs = current.videoTrack.segments.flatMap {
                        if (it.id == autoVid.id) listOf(leftSeg, rightSeg) else listOf(it)
                    }
                    current.copy(
                        videoTrack = current.videoTrack.copy(segments = updatedSegs),
                        selectedClipId = rightSeg.id,
                        statusMessage = "✂️ Đã cắt đôi đoạn Video tại ${formatMs(cutMs)}"
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(statusMessage = "Hãy chạm chọn đối tượng (Video / Voice / Tiêu đề / Sticker) và đặt vạch đỏ vào vị trí cần cắt!")
        }
    }

    fun selectClip(clipId: String?) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun deleteSelectedClip() {
        val selectedId = _uiState.value.selectedClipId ?: run {
            _uiState.update {
                it.copy(statusMessage = "Hãy chạm chọn đối tượng cần xóa trên Timeline!")
            }
            return
        }
        _uiState.update { state ->
            val isVidSeg = state.videoTrack.segments.any { it.id == selectedId }
            val updatedVidSegs = state.videoTrack.segments.filterNot { it.id == selectedId }
            state.copy(
                videoTrack = if (isVidSeg) state.videoTrack.copy(segments = updatedVidSegs) else state.videoTrack,
                voiceClips = state.voiceClips.filterNot { it.id == selectedId },
                textOverlays = state.textOverlays.filterNot { it.id == selectedId },
                imageOverlays = state.imageOverlays.filterNot { it.id == selectedId },
                selectedClipId = null,
                statusMessage = "🗑️ Đã xóa đối tượng đã chọn"
            )
        }
    }

    // =========================================================================
    // 5. PHÁT XEM THỬ (PREVIEW) & XUẤT FILE (MP3 HOẶC MP4) KÈM QUẢNG CÁO TRƯỚC KHI XUẤT
    // =========================================================================

    fun seekTo(positionMs: Long) {
        val maxMs = _uiState.value.totalTimelineDurationMs
        val clamped = positionMs.coerceIn(0L, maxMs)
        _uiState.update { it.copy(playheadMs = clamped) }
        syncAudioPlaybackWithPlayhead(clamped, _uiState.value.isPlaying, forceSeek = true)
    }

    fun togglePlayPause() {
        val willPlay = !_uiState.value.isPlaying
        _uiState.update { state ->
            val resetPlayhead = if (willPlay && state.playheadMs >= state.totalTimelineDurationMs) 0L else state.playheadMs
            state.copy(isPlaying = willPlay, playheadMs = resetPlayhead)
        }

        if (willPlay) {
            syncAudioPlaybackWithPlayhead(_uiState.value.playheadMs, isPlaying = true, forceSeek = true)
            startPlaybackTicker()
        } else {
            stopPlaybackTicker()
        }
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val anchorPlayheadMs = _uiState.value.playheadMs
            val anchorWallClockMs = SystemClock.elapsedRealtime()
            val tickDelayMs = 25L

            while (isActive && _uiState.value.isPlaying) {
                delay(tickDelayMs)
                val current = _uiState.value
                val elapsedWallMs = (SystemClock.elapsedRealtime() - anchorWallClockMs).coerceAtLeast(0L)
                val nextMs = anchorPlayheadMs + elapsedWallMs

                if (nextMs >= current.totalTimelineDurationMs) {
                    _uiState.update { it.copy(playheadMs = current.totalTimelineDurationMs, isPlaying = false) }
                    stopTtsAudioPlayer()
                    break
                } else {
                    _uiState.update { it.copy(playheadMs = nextMs) }
                    syncAudioPlaybackWithPlayhead(nextMs, isPlaying = true, forceSeek = false)
                }
            }
        }
    }

    private fun stopPlaybackTicker() {
        playbackJob?.cancel()
        stopTtsAudioPlayer()
    }

    private fun createSpeechMediaPlayer(filePath: String, volume: Float): MediaPlayer {
        return MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(filePath)
            prepare()
            setVolume(volume, volume)
        }
    }

    private fun syncAudioPlaybackWithPlayhead(
        playheadMs: Long,
        isPlaying: Boolean,
        forceSeek: Boolean = false
    ) {
        if (!isPlaying) {
            stopTtsAudioPlayer()
            return
        }

        val activeClip = _uiState.value.voiceClips.lastOrNull {
            playheadMs >= it.timelineStartMs && playheadMs < it.timelineEndMs
        }
        if (activeClip == null) {
            stopTtsAudioPlayer()
            return
        }

        val relativeClipOffsetMs = (playheadMs - activeClip.timelineStartMs).coerceAtLeast(0L)
        val targetOffsetMs = (activeClip.trimStartMs + relativeClipOffsetMs).toInt()
        val existingMp = activeTtsMediaPlayer
        val needsRestart = activeClipPlayingId != activeClip.id || forceSeek || existingMp == null

        if (needsRestart) {
            stopTtsAudioPlayer()
            val file = File(activeClip.audioFilePath)
            if (file.exists() && file.length() > 64L) {
                runCatching {
                    val mp = createSpeechMediaPlayer(file.absolutePath, activeClip.volume)
                    if (activeClip.trimStartMs > 0L || relativeClipOffsetMs > 160L) {
                        mp.seekTo(targetOffsetMs)
                    }
                    mp.start()
                    activeTtsMediaPlayer = mp
                    activeClipPlayingId = activeClip.id
                }
            }
        } else if (existingMp != null) {
            runCatching {
                if (existingMp.isPlaying && existingMp.currentPosition >= activeClip.trimEndMs.toInt()) {
                    stopTtsAudioPlayer()
                }
            }
        }
    }

    private fun stopTtsAudioPlayer() {
        runCatching {
            activeTtsMediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        activeTtsMediaPlayer = null
        activeClipPlayingId = null
    }

    fun requestExportFormatSelection() {
        stopPlaybackTicker()
        _uiState.update {
            it.copy(
                isPlaying = false,
                showExportFormatSheet = true
            )
        }
    }

    fun dismissExportFormatSelection() {
        _uiState.update {
            it.copy(showExportFormatSheet = false)
        }
    }

    /**
     * Quảng cáo theo lượt xuất file:
     * Bấm chọn định dạng xuất file (MP3 hoặc MP4) -> chuyển quảng cáo -> xem xong mới tiến hành xuất file.
     */
    fun startExportWithAd(format: ExportFormat, activity: Activity?) {
        val state = _uiState.value
        if (format == ExportFormat.MP3 && state.voiceClips.isEmpty()) {
            _uiState.update {
                it.copy(
                    showExportFormatSheet = false,
                    statusMessage = "⚠️ Chưa có đoạn Voice AI nào trên Timeline để xuất MP3! Hãy bấm 'Tạo Voice+Sub' trước."
                )
            }
            return
        }
        if (format == ExportFormat.MP4 && state.videoTrack.videoUri.isBlank()) {
            _uiState.update {
                it.copy(
                    showExportFormatSheet = false,
                    statusMessage = "⚠️ Vui lòng bấm 'Chọn MP4 9:16' trước khi xuất MP4 (hoặc chọn Xuất MP3)!"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                showExportFormatSheet = false,
                pendingExportFormat = format,
                statusMessage = "📺 Đang mở quảng cáo trước khi xuất file ${format.name}..."
            )
        }

        adManager.triggerAdBeforeExport(
            activity = activity,
            onAdCompleted = {
                executeExportAfterAd(format)
            },
            onShowFallbackAdOverlay = {
                _uiState.update {
                    it.copy(
                        showFallbackExportAdDialog = true,
                        pendingExportFormat = format
                    )
                }
            }
        )
    }

    fun completeFallbackExportAd() {
        val format = _uiState.value.pendingExportFormat ?: ExportFormat.MP4
        _uiState.update {
            it.copy(
                showFallbackExportAdDialog = false,
                pendingExportFormat = null
            )
        }
        executeExportAfterAd(format)
    }

    private fun executeExportAfterAd(format: ExportFormat) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExportingVideo = true,
                    statusMessage = if (format == ExportFormat.MP3) {
                        "⏳ Đang xuất file âm thanh MP3..."
                    } else {
                        "⏳ Đang kết xuất Video MP4 9:16 + Voice + Phụ đề + Tiêu đề + Sticker..."
                    }
                )
            }

            if (format == ExportFormat.MP3) {
                val mp3Res = exportManager.exportProjectToMp3(state.voiceClips)
                mp3Res.onSuccess { mp3File ->
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            totalAdsWatched = adManager.getTotalAdsWatchedByUser(),
                            statusMessage = "✅ Đã xuất MP3: ${mp3File.name} (Đã lưu trong Downloads)"
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            statusMessage = "❌ Lỗi xuất MP3: ${err.message}"
                        )
                    }
                }
            } else {
                val mp4Res = exportManager.exportProjectToMp4(
                    videoTrack = state.videoTrack,
                    voiceClips = state.voiceClips,
                    secondaryMp3Clips = state.secondaryMp3Clips,
                    textOverlays = state.textOverlays,
                    imageOverlays = state.imageOverlays,
                    subtitleStyle = state.subtitleStyle
                )
                mp4Res.onSuccess { mp4File ->
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            totalAdsWatched = adManager.getTotalAdsWatchedByUser(),
                            statusMessage = "✅ Đã xuất MP4: ${mp4File.name} (Đã lưu trong Downloads)"
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            statusMessage = "❌ Lỗi xuất MP4: ${err.message}"
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaybackTicker()
        ttsEngine.release()
    }

    companion object {
        fun formatMs(ms: Long): String {
            val totalTenths = (ms.coerceAtLeast(0L)) / 100
            val tenths = totalTenths % 10
            val totalSeconds = totalTenths / 10
            val seconds = totalSeconds % 60
            val minutes = totalSeconds / 60
            return String.format("%02d:%02d.%d", minutes, seconds, tenths)
        }
    }
}
