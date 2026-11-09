package vn.langkinhhukhong.tts.ui

import android.app.Activity
import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.langkinhhukhong.tts.ads.AdMonetizationManager
import vn.langkinhhukhong.tts.media.VideoExportManager
import vn.langkinhhukhong.tts.model.CanvasImageOverlayItem
import vn.langkinhhukhong.tts.model.CanvasTextOverlayItem
import vn.langkinhhukhong.tts.model.SecondaryMp3Clip
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.SubtitleCanvasConfig
import vn.langkinhhukhong.tts.model.VideoSegmentClip
import vn.langkinhhukhong.tts.model.VoiceModelCatalogItem
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import vn.langkinhhukhong.tts.tts.SherpaOfflineTtsEngine
import vn.langkinhhukhong.tts.tts.VoicePackManager
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class ExportFormat {
    MP3,
    MP4
}

data class EditorUiState(
    val videoTrack: ShortVideoTrack = ShortVideoTrack(),
    val voiceClips: List<VoiceSubtitleClip> = emptyList(),
    val secondaryMp3Clips: List<SecondaryMp3Clip> = emptyList(),
    val textOverlays: List<CanvasTextOverlayItem> = emptyList(),
    val imageOverlays: List<CanvasImageOverlayItem> = emptyList(),
    val subtitleConfig: SubtitleCanvasConfig = SubtitleCanvasConfig(),
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val selectedVoiceId: String = "vits-piper-vi_VN-lac_phi-medium",
    val voiceCatalog: List<VoiceModelCatalogItem> = emptyList(),
    val downloadedVoiceIds: Set<String> = emptySet(),
    val downloadingVoiceId: String? = null,
    val downloadProgressPercent: Int = 0,
    val ttsSpeed: Float = 1.0f,
    val isSynthesizingTts: Boolean = false,
    val isExportingVideo: Boolean = false,
    val exportedMp4Path: String? = null,
    val maxCharQuota: Int = AdMonetizationManager.MAX_CHARACTER_QUOTA,
    val showExportFormatSheet: Boolean = false,
    val showFallbackExportAdDialog: Boolean = false,
    val pendingExportFormat: ExportFormat? = null,
    val statusMessage: String = ""
) {
    val totalTimelineDurationMs: Long
        get() {
            val videoEnd = videoTrack.timelineEndMs
            val maxVoiceEnd = voiceClips.maxOfOrNull { it.timelineEndMs } ?: 0L
            val maxMp3End = secondaryMp3Clips.maxOfOrNull { it.timelineEndMs } ?: 0L
            val maxTextEnd = textOverlays.maxOfOrNull { it.endTimeMs } ?: 0L
            val maxImgEnd = imageOverlays.maxOfOrNull { it.endTimeMs } ?: 0L
            return maxOf(videoEnd, maxVoiceEnd, maxMp3End, maxTextEnd, maxImgEnd, 15_000L)
        }

    val activeSubtitleAtPlayhead: VoiceSubtitleClip?
        get() = voiceClips.lastOrNull { clip ->
            playheadMs >= clip.timelineStartMs && playheadMs <= clip.timelineEndMs
        }

    val activeVideoSegmentAtPlayhead: VideoSegmentClip?
        get() {
            val speed = videoTrack.speedMultiplier
            return videoTrack.segments.lastOrNull { seg ->
                playheadMs >= seg.timelineStartMs && playheadMs <= seg.timelineEndAtSpeedMs(speed)
            }
        }

    val selectedVideoSegment: VideoSegmentClip?
        get() = videoTrack.segments.find { it.id == selectedClipId }

    val selectedVoiceClip: VoiceSubtitleClip?
        get() = voiceClips.find { it.id == selectedClipId }

    val selectedSecondaryMp3Clip: SecondaryMp3Clip?
        get() = secondaryMp3Clips.find { it.id == selectedClipId }

    val selectedTextOverlay: CanvasTextOverlayItem?
        get() = textOverlays.find { it.id == selectedClipId }

    val selectedImageOverlay: CanvasImageOverlayItem?
        get() = imageOverlays.find { it.id == selectedClipId }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val voicePackManager = VoicePackManager(application)
    private val ttsEngine = SherpaOfflineTtsEngine(application, voicePackManager)
    val adManager = AdMonetizationManager(application)
    private val videoExportManager = VideoExportManager(application)

    private val _uiState = MutableStateFlow(
        EditorUiState(
            voiceCatalog = voicePackManager.getCatalog(),
            downloadedVoiceIds = voicePackManager.getDownloadedVoiceIds(),
            maxCharQuota = AdMonetizationManager.MAX_CHARACTER_QUOTA
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // =========================================================================
    // 1. NẠP VIDEO MP4 (9:16, TỐI ĐA 3 PHÚT) & CHIA ĐOẠN VIDEO
    // =========================================================================

    fun importShortVideoMp4(uri: Uri, displayName: String) {
        val checkResult = videoExportManager.validateAndInspectShortVideo(uri, displayName)
        checkResult.onSuccess { info ->
            val initialSegment = VideoSegmentClip(
                id = "vid_seg_${UUID.randomUUID().toString().take(6)}",
                label = info.displayName,
                timelineStartMs = 0L,
                sourceStartMs = 0L,
                sourceEndMs = info.durationMs
            )
            _uiState.update { state ->
                state.copy(
                    videoTrack = ShortVideoTrack(
                        videoUri = info.uri.toString(),
                        displayName = info.displayName,
                        sourceDurationMs = info.durationMs,
                        trimStartMs = 0L,
                        trimEndMs = info.durationMs,
                        isOriginalAudioMuted = state.videoTrack.isOriginalAudioMuted,
                        segments = listOf(initialSegment)
                    ),
                    selectedClipId = initialSegment.id,
                    playheadMs = 0L,
                    statusMessage = "Đã nạp MP4: ${info.displayName}"
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(statusMessage = "⚠️ ${err.message}")
            }
        }
    }

    fun toggleVideoOriginalAudioMute() {
        _uiState.update { state ->
            val nextMuted = !state.videoTrack.isOriginalAudioMuted
            state.copy(
                videoTrack = state.videoTrack.copy(isOriginalAudioMuted = nextMuted),
                statusMessage = if (nextMuted) "Đã TẮT tiếng gốc MP4" else "Đã BẬT tiếng gốc MP4"
            )
        }
    }

    fun moveVideoSegmentByDelta(segmentId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val updatedSegs = state.videoTrack.segments.map { seg ->
                if (seg.id == segmentId) {
                    seg.copy(timelineStartMs = max(0L, seg.timelineStartMs + deltaTimelineMs))
                } else {
                    seg
                }
            }
            state.copy(
                videoTrack = state.videoTrack.copy(segments = updatedSegs)
            )
        }
    }

    // =========================================================================
    // 2. CHUYỂN TEXT THÀNH VOICE AI + SUB PHỤ ĐỀ (TỐI ĐA 1000 KÝ TỰ / LẦN)
    // =========================================================================

    fun selectVoiceModel(voiceId: String) {
        _uiState.update {
            it.copy(selectedVoiceId = voiceId)
        }
    }

    fun updateTtsSpeed(speed: Float) {
        _uiState.update {
            it.copy(ttsSpeed = speed.coerceIn(0.6f, 1.6f))
        }
    }

    fun renderOfflineTtsAndAttachSubtitle(rawInputText: String) {
        val text = rawInputText.trim()
        if (text.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "⚠️ Vui lòng nhập nội dung văn bản để tạo giọng đọc!")
            }
            return
        }

        if (text.length > AdMonetizationManager.MAX_CHARACTER_QUOTA) {
            _uiState.update {
                it.copy(
                    statusMessage = "⚠️ Vượt quá giới hạn ${AdMonetizationManager.MAX_CHARACTER_QUOTA} ký tự (${text.length}/${AdMonetizationManager.MAX_CHARACTER_QUOTA})!"
                )
            }
            return
        }

        val currentState = _uiState.value
        val voiceItem = currentState.voiceCatalog.find { it.id == currentState.selectedVoiceId }
            ?: currentState.voiceCatalog.first()

        viewModelScope.launch {
            if (!voicePackManager.isVoiceDownloaded(voiceItem.id)) {
                _uiState.update {
                    it.copy(
                        downloadingVoiceId = voiceItem.id,
                        downloadProgressPercent = 5,
                        statusMessage = "Đang tải giọng ${voiceItem.displayName}..."
                    )
                }
                val dlResult = voicePackManager.downloadVoicePackFromCloud(voiceItem) { pct ->
                    _uiState.update {
                        it.copy(downloadProgressPercent = pct)
                    }
                }
                if (dlResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            downloadingVoiceId = null,
                            statusMessage = "⚠️ Không thể tải giọng ${voiceItem.displayName}: ${dlResult.exceptionOrNull()?.message}"
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        downloadingVoiceId = null,
                        downloadedVoiceIds = voicePackManager.getDownloadedVoiceIds()
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isSynthesizingTts = true,
                    statusMessage = "Đang tạo giọng đọc ${voiceItem.displayName}..."
                )
            }

            val result = ttsEngine.synthesizeTextToAudioFile(
                text = text,
                voiceItem = voiceItem,
                speed = _uiState.value.ttsSpeed
            )

            result.onSuccess { synth ->
                val stateSnapshot = _uiState.value
                val insertStartMs = if (stateSnapshot.voiceClips.isEmpty()) {
                    stateSnapshot.playheadMs
                } else {
                    val maxExistingEnd = stateSnapshot.voiceClips.maxOf { it.timelineEndMs }
                    max(stateSnapshot.playheadMs, maxExistingEnd + 120L)
                }

                val newClip = VoiceSubtitleClip(
                    id = "voice_${UUID.randomUUID().toString().take(8)}",
                    voiceId = voiceItem.id,
                    voiceDisplayName = voiceItem.displayName,
                    subtitleText = text,
                    audioFilePath = synth.audioFile.absolutePath,
                    sourceDurationMs = synth.durationMs,
                    timelineStartMs = insertStartMs,
                    trimStartMs = 0L,
                    trimEndMs = synth.durationMs
                )

                _uiState.update { state ->
                    state.copy(
                        isSynthesizingTts = false,
                        voiceClips = state.voiceClips + newClip,
                        selectedClipId = newClip.id,
                        playheadMs = insertStartMs,
                        statusMessage = "Đã tạo Voice + Phụ đề (${synth.durationMs / 1000.0}s)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSynthesizingTts = false,
                        statusMessage = "⚠️ Lỗi tạo giọng đọc: ${err.message}"
                    )
                }
            }
        }
    }

    // =========================================================================
    // 3. NÚT "CẮT" DÙNG CHUNG (CHỌN ĐỐI TƯỢNG + VỊ TRÍ KIM ĐỎ PLAYHEAD -> BẤM CẮT)
    // =========================================================================

    fun splitSelectedOrActiveClipAtPlayhead() {
        val state = _uiState.value
        val playhead = state.playheadMs
        val selectedId = state.selectedClipId
        val videoSpeed = state.videoTrack.speedMultiplier

        // 1. Nếu đang chọn một đoạn Video MP4 (hoặc ưu tiên đối tượng đang được chọn)
        val selectedVidSeg = state.videoTrack.segments.find { it.id == selectedId }
        if (selectedVidSeg != null) {
            val splitPair = selectedVidSeg.splitAtTimelineMs(playhead, videoSpeed)
            if (splitPair != null) {
                val (leftSeg, rightSeg) = splitPair
                val updatedSegs = state.videoTrack.segments.flatMap {
                    if (it.id == selectedVidSeg.id) listOf(leftSeg, rightSeg) else listOf(it)
                }
                _uiState.update {
                    it.copy(
                        videoTrack = it.videoTrack.copy(segments = updatedSegs),
                        selectedClipId = rightSeg.id,
                        statusMessage = "✂️ Đã cắt chia đôi đoạn Video tại ${formatMs(playhead)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy di chuyển kim đỏ vào bên trong đoạn Video đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 2. Nếu đang chọn một đoạn Voice+Sub
        val selectedVoice = state.voiceClips.find { it.id == selectedId }
        if (selectedVoice != null) {
            val splitPair = selectedVoice.splitAtTimelineMs(playhead)
            if (splitPair != null) {
                val (leftClip, rightClip) = splitPair
                val updatedList = state.voiceClips.flatMap {
                    if (it.id == selectedVoice.id) listOf(leftClip, rightClip) else listOf(it)
                }
                _uiState.update {
                    it.copy(
                        voiceClips = updatedList,
                        selectedClipId = rightClip.id,
                        statusMessage = "✂️ Đã cắt chia đôi đoạn Voice & Phụ đề tại ${formatMs(playhead)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy di chuyển kim đỏ vào bên trong đoạn Voice đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 3. Nếu đang chọn một Tiêu đề (CanvasTextOverlayItem)
        val selectedText = state.textOverlays.find { it.id == selectedId }
        if (selectedText != null) {
            if (playhead >= selectedText.startTimeMs + 250L && playhead <= selectedText.endTimeMs - 250L) {
                val leftText = selectedText.copy(
                    id = "${selectedText.id}_L_${System.currentTimeMillis() % 1000}",
                    endTimeMs = playhead
                )
                val rightText = selectedText.copy(
                    id = "${selectedText.id}_R_${System.currentTimeMillis() % 1000}",
                    startTimeMs = playhead
                )
                val updated = state.textOverlays.flatMap {
                    if (it.id == selectedText.id) listOf(leftText, rightText) else listOf(it)
                }
                _uiState.update {
                    it.copy(
                        textOverlays = updated,
                        selectedClipId = rightText.id,
                        statusMessage = "✂️ Đã cắt chia đôi Tiêu đề tại ${formatMs(playhead)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy di chuyển kim đỏ vào bên trong đoạn Tiêu đề đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 4. Nếu đang chọn một Sticker (CanvasImageOverlayItem)
        val selectedSticker = state.imageOverlays.find { it.id == selectedId }
        if (selectedSticker != null) {
            if (playhead >= selectedSticker.startTimeMs + 250L && playhead <= selectedSticker.endTimeMs - 250L) {
                val leftSticker = selectedSticker.copy(
                    id = "${selectedSticker.id}_L_${System.currentTimeMillis() % 1000}",
                    endTimeMs = playhead
                )
                val rightSticker = selectedSticker.copy(
                    id = "${selectedSticker.id}_R_${System.currentTimeMillis() % 1000}",
                    startTimeMs = playhead
                )
                val updated = state.imageOverlays.flatMap {
                    if (it.id == selectedSticker.id) listOf(leftSticker, rightSticker) else listOf(it)
                }
                _uiState.update {
                    it.copy(
                        imageOverlays = updated,
                        selectedClipId = rightSticker.id,
                        statusMessage = "✂️ Đã cắt chia đôi Sticker tại ${formatMs(playhead)}"
                    )
                }
                return
            } else {
                _uiState.update {
                    it.copy(statusMessage = "⚠️ Hãy di chuyển kim đỏ vào bên trong đoạn Sticker đang chọn rồi bấm 'Cắt'!")
                }
                return
            }
        }

        // 5. Nếu chưa chọn đối tượng nào, tự động tìm đối tượng nằm ngay dưới kim đỏ Playhead (ưu tiên Voice -> Video)
        val autoVoice = state.voiceClips.find {
            playhead > it.timelineStartMs + 220L && playhead < it.timelineEndMs - 220L
        }
        if (autoVoice != null) {
            val splitPair = autoVoice.splitAtTimelineMs(playhead)
            if (splitPair != null) {
                val (leftClip, rightClip) = splitPair
                val updatedList = state.voiceClips.flatMap {
                    if (it.id == autoVoice.id) listOf(leftClip, rightClip) else listOf(it)
                }
                _uiState.update {
                    it.copy(
                        voiceClips = updatedList,
                        selectedClipId = rightClip.id,
                        statusMessage = "✂️ Đã cắt chia đôi đoạn Voice & Phụ đề tại ${formatMs(playhead)}"
                    )
                }
                return
            }
        }

        val autoVid = state.videoTrack.segments.find {
            playhead > it.timelineStartMs + 250L && playhead < it.timelineEndAtSpeedMs(videoSpeed) - 250L
        }
        if (autoVid != null) {
            val splitPair = autoVid.splitAtTimelineMs(playhead, videoSpeed)
            if (splitPair != null) {
                val (leftSeg, rightSeg) = splitPair
                val updatedSegs = state.videoTrack.segments.flatMap {
                    if (it.id == autoVid.id) listOf(leftSeg, rightSeg) else listOf(it)
                }
                _uiState.update {
                    it.copy(
                        videoTrack = it.videoTrack.copy(segments = updatedSegs),
                        selectedClipId = rightSeg.id,
                        statusMessage = "✂️ Đã cắt chia đôi đoạn Video tại ${formatMs(playhead)}"
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(
                statusMessage = "⚠️ Hãy chạm chọn đối tượng (Video / Voice / Tiêu đề / Sticker) và đặt kim đỏ vào vị trí cần cắt!"
            )
        }
    }

    fun moveVoiceClipByDelta(clipId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val updated = state.voiceClips.map { clip ->
                if (clip.id == clipId) {
                    val nextStart = max(0L, clip.timelineStartMs + deltaTimelineMs)
                    clip.copy(timelineStartMs = nextStart)
                } else {
                    clip
                }
            }
            state.copy(voiceClips = updated)
        }
    }

    fun moveSecondaryMp3ClipByDelta(clipId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val updated = state.secondaryMp3Clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(timelineStartMs = max(0L, clip.timelineStartMs + deltaTimelineMs))
                } else {
                    clip
                }
            }
            state.copy(secondaryMp3Clips = updated)
        }
    }

    fun deleteSelectedClip() {
        val state = _uiState.value
        val id = state.selectedClipId ?: run {
            _uiState.update {
                it.copy(statusMessage = "⚠️ Chạm chọn một đối tượng trên Timeline trước khi bấm Xóa!")
            }
            return
        }

        val isVidSeg = state.videoTrack.segments.any { it.id == id }
        val updatedVidSegs = state.videoTrack.segments.filterNot { it.id == id }

        _uiState.update {
            it.copy(
                videoTrack = if (isVidSeg) it.videoTrack.copy(segments = updatedVidSegs) else it.videoTrack,
                voiceClips = it.voiceClips.filterNot { c -> c.id == id },
                secondaryMp3Clips = it.secondaryMp3Clips.filterNot { c -> c.id == id },
                textOverlays = it.textOverlays.filterNot { c -> c.id == id },
                imageOverlays = it.imageOverlays.filterNot { c -> c.id == id },
                selectedClipId = null,
                statusMessage = "🗑️ Đã xóa đối tượng đã chọn khỏi Timeline"
            )
        }
    }

    // =========================================================================
    // 4. TIÊU ĐỀ & STICKER + LÀM MỜ + KÉO THẢ VỊ TRÍ BẤT KỲ
    // =========================================================================

    fun addCanvasTextOverlay(
        text: String,
        fontSizeSp: Float = 18f,
        opacity: Float = 1.0f,
        blurRadiusDp: Float = 0f
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        val state = _uiState.value
        val startMs = state.playheadMs
        val endMs = min(state.totalTimelineDurationMs, startMs + 6_000L)

        val item = CanvasTextOverlayItem(
            id = "txt_${UUID.randomUUID().toString().take(8)}",
            text = clean,
            xFraction = 0.5f,
            yFraction = 0.25f,
            fontSizeSp = fontSizeSp.coerceIn(10f, 38f),
            opacity = opacity.coerceIn(0.1f, 1.0f),
            blurRadiusDp = blurRadiusDp.coerceIn(0f, 25f),
            startTimeMs = startMs,
            endTimeMs = max(startMs + 2_000L, endMs)
        )

        _uiState.update {
            it.copy(
                textOverlays = it.textOverlays + item,
                selectedClipId = item.id,
                statusMessage = "Đã thêm Tiêu đề: \"$clean\""
            )
        }
    }

    fun addLogoPngOverlay(
        imageUri: Uri,
        label: String = "Sticker",
        opacity: Float = 1.0f,
        blurRadiusDp: Float = 0f
    ) {
        val state = _uiState.value
        val startMs = state.playheadMs
        val endMs = min(state.totalTimelineDurationMs, startMs + 8_000L)

        val item = CanvasImageOverlayItem(
            id = "img_${UUID.randomUUID().toString().take(8)}",
            imageUri = imageUri.toString(),
            label = label,
            xFraction = 0.80f,
            yFraction = 0.14f,
            scale = 1.0f,
            opacity = opacity.coerceIn(0.1f, 1.0f),
            blurRadiusDp = blurRadiusDp.coerceIn(0f, 25f),
            startTimeMs = startMs,
            endTimeMs = max(startMs + 2_000L, endMs)
        )

        _uiState.update {
            it.copy(
                imageOverlays = it.imageOverlays + item,
                selectedClipId = item.id,
                statusMessage = "Đã thêm Sticker"
            )
        }
    }

    fun updateSubtitlePosition(xFraction: Float, yFraction: Float) {
        _uiState.update { state ->
            state.copy(
                subtitleConfig = state.subtitleConfig.copy(
                    xFraction = xFraction.coerceIn(0.08f, 0.92f),
                    yFraction = yFraction.coerceIn(0.06f, 0.94f)
                )
            )
        }
    }

    fun updateSubtitleBlurAndOpacity(opacity: Float, blurDp: Float) {
        _uiState.update { state ->
            state.copy(
                subtitleConfig = state.subtitleConfig.copy(
                    opacity = opacity.coerceIn(0.15f, 1.0f),
                    blurRadiusDp = blurDp.coerceIn(0f, 20f)
                )
            )
        }
    }

    fun updateSubtitleFontSize(fontSizeSp: Float) {
        _uiState.update { state ->
            state.copy(
                subtitleConfig = state.subtitleConfig.copy(
                    fontSizeSp = fontSizeSp.coerceIn(9f, 28f)
                )
            )
        }
    }

    fun updateTextOverlayPosition(id: String, xFraction: Float, yFraction: Float) {
        _uiState.update { state ->
            state.copy(
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == id) {
                        item.copy(
                            xFraction = xFraction.coerceIn(0.05f, 0.95f),
                            yFraction = yFraction.coerceIn(0.05f, 0.95f)
                        )
                    } else item
                },
                selectedClipId = id
            )
        }
    }

    fun updateTextOverlayBlurAndOpacity(id: String, opacity: Float, blurRadiusDp: Float) {
        _uiState.update { state ->
            state.copy(
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == id) {
                        item.copy(
                            opacity = opacity.coerceIn(0.1f, 1.0f),
                            blurRadiusDp = blurRadiusDp.coerceIn(0f, 25f)
                        )
                    } else item
                }
            )
        }
    }

    fun updateTextOverlayFontSize(id: String, fontSizeSp: Float) {
        _uiState.update { state ->
            state.copy(
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == id) {
                        item.copy(fontSizeSp = fontSizeSp.coerceIn(10f, 38f))
                    } else item
                }
            )
        }
    }

    fun moveTextOverlayTimeline(id: String, deltaMs: Long) {
        _uiState.update { state ->
            state.copy(
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == id) {
                        val duration = item.endTimeMs - item.startTimeMs
                        val newStart = max(0L, item.startTimeMs + deltaMs)
                        item.copy(startTimeMs = newStart, endTimeMs = newStart + duration)
                    } else item
                }
            )
        }
    }

    fun updateImageOverlayPosition(id: String, xFraction: Float, yFraction: Float) {
        _uiState.update { state ->
            state.copy(
                imageOverlays = state.imageOverlays.map { item ->
                    if (item.id == id) {
                        item.copy(
                            xFraction = xFraction.coerceIn(0.05f, 0.95f),
                            yFraction = yFraction.coerceIn(0.05f, 0.95f)
                        )
                    } else item
                },
                selectedClipId = id
            )
        }
    }

    fun updateImageOverlayBlurOpacityScale(id: String, opacity: Float, blurRadiusDp: Float, scale: Float) {
        _uiState.update { state ->
            state.copy(
                imageOverlays = state.imageOverlays.map { item ->
                    if (item.id == id) {
                        item.copy(
                            opacity = opacity.coerceIn(0.1f, 1.0f),
                            blurRadiusDp = blurRadiusDp.coerceIn(0f, 25f),
                            scale = scale.coerceIn(0.3f, 2.5f)
                        )
                    } else item
                }
            )
        }
    }

    fun moveImageOverlayTimeline(id: String, deltaMs: Long) {
        _uiState.update { state ->
            state.copy(
                imageOverlays = state.imageOverlays.map { item ->
                    if (item.id == id) {
                        val duration = item.endTimeMs - item.startTimeMs
                        val newStart = max(0L, item.startTimeMs + deltaMs)
                        item.copy(startTimeMs = newStart, endTimeMs = newStart + duration)
                    } else item
                }
            )
        }
    }

    // =========================================================================
    // 5. ĐIỀU KHIỂN PLAYHEAD & XUẤT FILE (MP3 HOẶC MP4) KÈM QUẢNG CÁO TRƯỚC KHI XUẤT
    // =========================================================================

    fun seekPlayhead(ms: Long) {
        _uiState.update { state ->
            state.copy(playheadMs = ms.coerceIn(0L, state.totalTimelineDurationMs))
        }
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun selectClip(clipId: String?) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun requestExportFormatSelection() {
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
     * Khi người dùng chọn Xuất MP3 hoặc Xuất MP4:
     * 1. Kiểm tra điều kiện hợp lệ (MP3 cần có ít nhất 1 đoạn Voice, MP4 cần đã chọn Video MP4).
     * 2. Bật quảng cáo toàn màn hình (AdMob hoặc Fallback Ad 5s).
     * 3. Sau khi quảng cáo kết thúc mới tự động thực thi xuất file.
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
                    statusMessage = "⚠️ Chưa chọn Video MP4 (9:16)! Hãy bấm 'Chọn MP4 9:16' hoặc chọn Xuất MP3."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                showExportFormatSheet = false,
                pendingExportFormat = format,
                statusMessage = "📺 Đang hiển thị quảng cáo trước khi xuất file ${format.name}..."
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
                    exportedMp4Path = null,
                    statusMessage = if (format == ExportFormat.MP3) {
                        "⏳ Đang xuất file âm thanh MP3..."
                    } else {
                        "⏳ Đang xuất file Video MP4 9:16..."
                    }
                )
            }

            val result: Result<File> = if (format == ExportFormat.MP3) {
                videoExportManager.exportProjectToMp3(
                    voiceClips = state.voiceClips,
                    secondaryMp3Clips = state.secondaryMp3Clips
                )
            } else {
                videoExportManager.exportProjectToMp4(
                    videoTrack = state.videoTrack,
                    voiceClips = state.voiceClips,
                    secondaryMp3Clips = state.secondaryMp3Clips,
                    textOverlays = state.textOverlays,
                    imageOverlays = state.imageOverlays,
                    subtitleConfig = state.subtitleConfig
                )
            }

            result.onSuccess { outFile ->
                adManager.recordExportSuccess()
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        exportedMp4Path = outFile.absolutePath,
                        statusMessage = "✅ Đã xuất ${format.name}: ${outFile.name} (Đã lưu vào mục Downloads)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        statusMessage = "⚠️ Lỗi xuất ${format.name}: ${err.message}"
                    )
                }
            }
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000.0
        return "%.1fs".format(totalSec)
    }
}
