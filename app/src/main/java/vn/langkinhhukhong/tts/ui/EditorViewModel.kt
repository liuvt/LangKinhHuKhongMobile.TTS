package vn.langkinhhukhong.tts.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
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
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import vn.langkinhhukhong.tts.tts.SherpaOfflineTtsEngine
import java.io.File
import java.io.FileOutputStream

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
    val pendingAdVoiceTarget: OfflineVoiceProfile? = null,
    val totalAdsWatched: Int = 0,
    val ttsInputText: String = "Xin chào các bạn, hôm nay chúng ta cùng khám phá câu chuyện thú vị này nhé.",
    val ttsSpeechRate: Float = 1.0f,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val isSynthesizingTts: Boolean = false,
    val isExportingVideo: Boolean = false,
    val statusMessage: String = "Sẵn sàng • Bấm 'Tạo Voice+Sub' để chuyển Text thành Giọng đọc kèm Phụ đề"
) {
    val installedVoices: List<OfflineVoiceProfile>
        get() = voiceCatalog

    /** Tổng thời lượng Timeline dựa trên Video (tối đa 3 phút) hoặc các đoạn Voice đã tạo */
    val totalTimelineDurationMs: Long
        get() {
            val maxVoiceEnd = voiceClips.maxOfOrNull { it.timelineEndMs } ?: 0L
            val baseDuration = if (videoTrack.videoUri.isNotBlank()) {
                videoTrack.effectiveDurationMs
            } else {
                maxOf(30_000L, maxVoiceEnd + 3_000L)
            }
            return baseDuration.coerceIn(3_000L, EditorConstraints.MAX_VIDEO_DURATION_MS)
        }

    val activeVoiceClipAtPlayhead: VoiceSubtitleClip?
        get() = voiceClips.lastOrNull { playheadMs in it.timelineStartMs..it.timelineEndMs }

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

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var activeTtsMediaPlayer: MediaPlayer? = null
    private var activeClipPlayingId: String? = null

    // =========================================================================
    // 1. VIDEO MP4 9:16 (<= 3 PHÚT): NẠP VIDEO, CẮT NGẮN VIDEO, TẮT/BẬT TIẾNG MP4
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
                val warningSuffix = if (durationMs > EditorConstraints.MAX_VIDEO_DURATION_MS) {
                    " (Giới hạn tối đa 3 phút)"
                } else {
                    ""
                }

                _uiState.update { state ->
                    state.copy(
                        videoTrack = ShortVideoTrack(
                            videoUri = uri.toString(),
                            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video_9x16.mp4",
                            originalDurationMs = clampedDuration,
                            trimStartMs = 0L,
                            trimEndMs = clampedDuration,
                            width = actualW,
                            height = actualH,
                            speedMultiplier = 1.0f,
                            muteOriginalAudio = state.videoTrack.muteOriginalAudio
                        ),
                        playheadMs = 0L,
                        statusMessage = "Đã nạp Video 9:16 (${clampedDuration / 1000}s)$warningSuffix"
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
                    "🔇 Đã tắt tiếng gốc của Video MP4"
                } else {
                    "🔊 Đã bật tiếng gốc của Video MP4"
                }
            )
        }
    }

    fun trimVideoStartAtPlayhead() {
        _uiState.update { state ->
            val video = state.videoTrack
            val cutSourceOffsetMs = (state.playheadMs * video.speedMultiplier).toLong()
            val newTrimStart = (video.trimStartMs + cutSourceOffsetMs)
                .coerceIn(0L, (video.trimEndMs - 1_000L).coerceAtLeast(0L))

            if (newTrimStart == video.trimStartMs) {
                return@update state.copy(
                    statusMessage = "Di chuyển vạch đỏ Playhead vào vị trí cần cắt bỏ phần đầu Video!"
                )
            }
            val updatedVideo = video.copy(trimStartMs = newTrimStart)
            state.copy(
                videoTrack = updatedVideo,
                playheadMs = 0L,
                statusMessage = "✂️ Đã cắt đầu Video • Thời lượng mới: ${formatMs(updatedVideo.effectiveDurationMs)}"
            )
        }
    }

    fun trimVideoEndAtPlayhead() {
        _uiState.update { state ->
            val video = state.videoTrack
            val cutSourceOffsetMs = (state.playheadMs * video.speedMultiplier).toLong()
            val newTrimEnd = (video.trimStartMs + cutSourceOffsetMs)
                .coerceIn(video.trimStartMs + 1_000L, video.originalDurationMs)

            if (newTrimEnd == video.trimEndMs) {
                return@update state.copy(
                    statusMessage = "Di chuyển vạch đỏ Playhead vào vị trí cần cắt ngắn đuôi Video!"
                )
            }
            val updatedVideo = video.copy(trimEndMs = newTrimEnd)
            state.copy(
                videoTrack = updatedVideo,
                playheadMs = state.playheadMs.coerceAtMost(updatedVideo.effectiveDurationMs),
                statusMessage = "✂️ Đã cắt ngắn đuôi Video • Thời lượng mới: ${formatMs(updatedVideo.effectiveDurationMs)}"
            )
        }
    }

    fun resetVideoTrim() {
        _uiState.update { state ->
            val video = state.videoTrack
            val updated = video.copy(trimStartMs = 0L, trimEndMs = video.originalDurationMs)
            state.copy(
                videoTrack = updated,
                statusMessage = "Đã khôi phục độ dài Video gốc (${formatMs(updated.effectiveDurationMs)})"
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
    // 2. CHUYỂN TEXT THÀNH VOICE + SUB PHỤ ĐỀ KÈM THEO & KÉO/CẮT ĐOẠN VOICE
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
        _uiState.update { it.copy(ttsInputText = newText) }
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
                text = "Xin chào, đây là giọng đọc ${voice.displayName}",
                voiceProfile = voice,
                speechSpeed = _uiState.value.ttsSpeechRate,
                insertAtTimelineMs = 0L
            )
            sampleRes.onSuccess { clip ->
                stopTtsAudioPlayer()
                runCatching {
                    val mp = MediaPlayer().apply {
                        setDataSource(clip.audioFilePath)
                        prepare()
                        start()
                    }
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
     * Chuyển Text thành file Voice + Tự động sinh Sub phụ đề kèm theo,
     * đặt lên Timeline và tự động phát ngay để người dùng nghe và xem phụ đề!
     */
    fun renderOfflineTtsAndAttachSubtitle(
        @Suppress("UNUSED_PARAMETER") activity: Activity? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val currentState = _uiState.value
        val voice = currentState.selectedVoiceProfile
        val text = currentState.ttsInputText.trim()

        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Vui lòng nhập văn bản cần chuyển thành Voice!") }
            return
        }

        // Nếu tại vị trí Playhead đã có đoạn Voice khác, tự động nối tiếp sau đoạn đó để không đè lên nhau
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
                    statusMessage = "⏳ Đang chuyển Text thành Voice (${voice.displayName})..."
                )
            }

            val result = ttsEngine.synthesizeWithSubtitles(
                text = text,
                voiceProfile = voice,
                speechSpeed = currentState.ttsSpeechRate,
                insertAtTimelineMs = insertAtMs
            )

            result.onSuccess { newClip ->
                _uiState.update { state ->
                    state.copy(
                        isSynthesizingTts = false,
                        voiceClips = (state.voiceClips + newClip).sortedBy { it.timelineStartMs },
                        selectedClipId = newClip.id,
                        playheadMs = newClip.timelineStartMs,
                        isPlaying = true,
                        statusMessage = "✅ Đã tạo Voice (${formatMs(newClip.durationMs)}) + ${newClip.words.size} từ Sub phụ đề!"
                    )
                }
                onComplete?.invoke()
                // Tự động phát ngay đoạn Voice vừa tạo kèm hiệu ứng chạy phụ đề
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
                selectedClipId = clipId,
                statusMessage = "Đang kéo đoạn Voice • Phụ đề tự động trượt theo khớp với Video"
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

    /**
     * Điều chỉnh Size chữ Phụ đề (fontSizeSp: 10..36sp) và Độ rõ/Làm mờ Phụ đề.
     */
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
    // 3. NHẬP CHỮ (TEXT) & ICON / LOGO PNG (LÀM MỜ + KÉO THẢ VỊ TRÍ BẤT KỲ)
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
                statusMessage = "Đã thêm chữ '${newItem.text}' • Kéo thả trực tiếp trên Khu View 9:16"
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
                    val dstFile = File(overlayDir, "logo_${System.currentTimeMillis()}.png")
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dstFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (dstFile.exists() && dstFile.length() > 0L) dstFile else null
                }.getOrNull()
            }

            val resolvedUri = copiedFile?.let { Uri.fromFile(it).toString() } ?: uri.toString()
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Logo.png"

            _uiState.update { state ->
                val startMs = state.playheadMs
                val endMs = state.totalTimelineDurationMs.coerceAtLeast(startMs + 3_000L)
                val newLogo = CanvasImageOverlayItem(
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
                    imageOverlays = state.imageOverlays + newLogo,
                    selectedClipId = newLogo.id,
                    statusMessage = "Đã chèn Logo PNG ($displayName) • Kéo thả trên Khu View 9:16 & chỉnh làm mờ"
                )
            }
        }
    }

    fun addSampleLogoPngOverlay(badgeLabel: String = "LOGO") {
        viewModelScope.launch {
            val logoFile = withContext(Dispatchers.IO) {
                val overlayDir = File(getApplication<Application>().filesDir, "overlays").apply { mkdirs() }
                val outFile = File(overlayDir, "badge_${System.currentTimeMillis()}.png")
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
                val newLogo = CanvasImageOverlayItem(
                    name = "Logo_$badgeLabel.png",
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
                    imageOverlays = state.imageOverlays + newLogo,
                    selectedClipId = newLogo.id,
                    statusMessage = "Đã tạo Logo PNG '$badgeLabel' • Kéo thả vị trí bất kỳ & chỉnh độ mờ"
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
    // 4. CẮT CHIA ĐOẠN (SPLIT) VOICE / TEXT / LOGO TẠI PLAYHEAD & XÓA
    // =========================================================================

    fun splitSelectedOrActiveClipAtPlayhead() {
        val state = _uiState.value
        val cutMs = state.playheadMs

        val targetVoiceClip = state.voiceClips.firstOrNull {
            it.id == state.selectedClipId && cutMs in it.timelineStartMs..it.timelineEndMs
        } ?: state.voiceClips.firstOrNull {
            cutMs in it.timelineStartMs..it.timelineEndMs
        }

        if (targetVoiceClip != null) {
            val splitPair = targetVoiceClip.splitAtTimelineMs(cutMs)
            if (splitPair == null) {
                _uiState.update {
                    it.copy(statusMessage = "Điểm cắt quá sát mép đoạn Voice (cần cách mép ít nhất 0.2s).")
                }
                return
            }

            val (leftClip, rightClip) = splitPair
            _uiState.update { current ->
                val remaining = current.voiceClips.filterNot { it.id == targetVoiceClip.id }
                current.copy(
                    voiceClips = (remaining + leftClip + rightClip).sortedBy { it.timelineStartMs },
                    selectedClipId = rightClip.id,
                    statusMessage = "✂️ Đã cắt đôi Voice & Phụ đề: [${leftClip.words.size} từ] | [${rightClip.words.size} từ]. Kéo thả từng đoạn tùy ý!"
                )
            }
            return
        }

        val targetText = state.textOverlays.firstOrNull {
            it.id == state.selectedClipId && cutMs in it.timelineStartMs..it.timelineEndMs
        }
        if (targetText != null) {
            val splitText = targetText.splitAtTimelineMs(cutMs)
            if (splitText != null) {
                val (leftT, rightT) = splitText
                _uiState.update { current ->
                    val remaining = current.textOverlays.filterNot { it.id == targetText.id }
                    current.copy(
                        textOverlays = (remaining + leftT + rightT).sortedBy { it.timelineStartMs },
                        selectedClipId = rightT.id,
                        statusMessage = "✂️ Đã tách đôi đoạn Chữ (Text) tại ${formatMs(cutMs)}"
                    )
                }
                return
            }
        }

        val targetImage = state.imageOverlays.firstOrNull {
            it.id == state.selectedClipId && cutMs in it.timelineStartMs..it.timelineEndMs
        }
        if (targetImage != null) {
            val splitImg = targetImage.splitAtTimelineMs(cutMs)
            if (splitImg != null) {
                val (leftI, rightI) = splitImg
                _uiState.update { current ->
                    val remaining = current.imageOverlays.filterNot { it.id == targetImage.id }
                    current.copy(
                        imageOverlays = (remaining + leftI + rightI).sortedBy { it.timelineStartMs },
                        selectedClipId = rightI.id,
                        statusMessage = "✂️ Đã tách đôi đoạn Logo PNG tại ${formatMs(cutMs)}"
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(statusMessage = "Hãy di chuyển vạch đỏ Playhead vào giữa đoạn Voice, Chữ hoặc Logo cần cắt!")
        }
    }

    fun selectClip(clipId: String?) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun deleteSelectedClip() {
        val selectedId = _uiState.value.selectedClipId ?: return
        _uiState.update { state ->
            state.copy(
                voiceClips = state.voiceClips.filterNot { it.id == selectedId },
                textOverlays = state.textOverlays.filterNot { it.id == selectedId },
                imageOverlays = state.imageOverlays.filterNot { it.id == selectedId },
                selectedClipId = null,
                statusMessage = "Đã xóa phần tử đang chọn."
            )
        }
    }

    // =========================================================================
    // 5. PHÁT XEM THỬ (PREVIEW) & XUẤT VIDEO MP4 9:16
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
            val tickMs = 40L
            while (isActive && _uiState.value.isPlaying) {
                delay(tickMs)
                val current = _uiState.value
                val nextMs = current.playheadMs + tickMs
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

    private fun syncAudioPlaybackWithPlayhead(
        playheadMs: Long,
        isPlaying: Boolean,
        forceSeek: Boolean = false
    ) {
        if (!isPlaying) {
            stopTtsAudioPlayer()
            return
        }

        val activeClip = _uiState.value.activeVoiceClipAtPlayhead
        if (activeClip == null) {
            stopTtsAudioPlayer()
        } else if (activeClipPlayingId != activeClip.id || forceSeek || activeTtsMediaPlayer?.isPlaying != true) {
            stopTtsAudioPlayer()
            val file = File(activeClip.audioFilePath)
            if (file.exists()) {
                runCatching {
                    val mp = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        val offsetInsideFile = (activeClip.trimStartMs + (playheadMs - activeClip.timelineStartMs)).toInt()
                        seekTo(offsetInsideFile)
                        setVolume(activeClip.volume, activeClip.volume)
                        start()
                    }
                    activeTtsMediaPlayer = mp
                    activeClipPlayingId = activeClip.id
                }
            }
        }
    }

    private fun stopTtsAudioPlayer() {
        runCatching {
            activeTtsMediaPlayer?.stop()
            activeTtsMediaPlayer?.release()
        }
        activeTtsMediaPlayer = null
        activeClipPlayingId = null
    }

    fun exportFinalVideo(@Suppress("UNUSED_PARAMETER") activity: Activity? = null) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExportingVideo = true,
                    statusMessage = "⏳ Đang kết xuất Video 9:16 + Voice + Phụ đề + Text + Logo PNG..."
                )
            }

            val exportDir = File(getApplication<Application>().getExternalFilesDir(null), "exports").apply { mkdirs() }
            val srtFile = File(exportDir, "LangKinhHuKhong_Subtitles.srt")
            exportManager.exportSyncedSrtFile(state.voiceClips, srtFile)

            if (state.videoTrack.videoUri.isBlank()) {
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        statusMessage = "Đã lưu file phụ đề (${srtFile.name}). Hãy bấm 'Nạp MP4 9:16' để xuất Video!"
                    )
                }
                return@launch
            }

            val result = exportManager.exportProjectToMp4(
                videoTrack = state.videoTrack,
                voiceClips = state.voiceClips,
                secondaryMp3Clips = state.secondaryMp3Clips,
                textOverlays = state.textOverlays,
                imageOverlays = state.imageOverlays,
                subtitleStyle = state.subtitleStyle
            )

            result.onSuccess { mp4File ->
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        statusMessage = "✅ Đã xuất Video: ${mp4File.absolutePath}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        statusMessage = "Đã xuất ${srtFile.name} (Lưu ý MP4: ${err.message})"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaybackTicker()
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
