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
import vn.langkinhhukhong.tts.model.VoiceInstallState
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import vn.langkinhhukhong.tts.tts.SherpaOfflineTtsEngine
import vn.langkinhhukhong.tts.tts.VoiceLibraryRepository
import java.io.File
import java.io.FileOutputStream

data class EditorUiState(
    val videoTrack: ShortVideoTrack = ShortVideoTrack(
        videoUri = "",
        displayName = "Demo_Short_9x16.mp4",
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
    val ttsInputText: String = "Chào mừng bạn đến với Lăng Kính Hư Không, ứng dụng tạo giọng đọc AI kèm phụ đề tự động.",
    val ttsSpeechRate: Float = 1.0f,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedClipId: String? = null,
    val isSynthesizingTts: Boolean = false,
    val isExportingVideo: Boolean = false,
    val statusMessage: String = "Sẵn sàng • Kéo thả trực tiếp Phụ đề, Text & Logo PNG trên khung View 9:16"
) {
    /** Các giọng đọc đã tải về máy */
    val installedVoices: List<OfflineVoiceProfile>
        get() = voiceCatalog.filter { it.installState == VoiceInstallState.INSTALLED }

    /** Tổng thời lượng Timeline dựa trên đoạn Video đã cắt ngắn & tốc độ Video (Tối đa 3 phút) */
    val totalTimelineDurationMs: Long
        get() = videoTrack.effectiveDurationMs.coerceIn(1_000L, EditorConstraints.MAX_VIDEO_DURATION_MS)

    /** Lấy đoạn VoiceClip đang phát ngay tại vị trí con trỏ Playhead hiện tại */
    val activeVoiceClipAtPlayhead: VoiceSubtitleClip?
        get() = voiceClips.firstOrNull { playheadMs in it.timelineStartMs..it.timelineEndMs }

    /** Danh sách các từ phụ đề đang hiển thị tại thời điểm Playhead */
    val currentSubtitleTokens: List<TimelineSubtitleToken>
        get() = activeVoiceClipAtPlayhead?.getMappedWords(playheadMs) ?: emptyList()

    /** Các lớp Text đang hiển thị tại thời điểm Playhead */
    val visibleTextOverlaysAtPlayhead: List<CanvasTextOverlayItem>
        get() = textOverlays.filter { it.isVisibleAt(playheadMs) }

    /** Các lớp Icon / Logo PNG đang hiển thị tại thời điểm Playhead */
    val visibleImageOverlaysAtPlayhead: List<CanvasImageOverlayItem>
        get() = imageOverlays.filter { it.isVisibleAt(playheadMs) }

    /** Đối tượng Text đang được chọn trên Canvas/Timeline (nếu có) */
    val selectedTextOverlay: CanvasTextOverlayItem?
        get() = textOverlays.firstOrNull { it.id == selectedClipId }

    /** Đối tượng Logo PNG đang được chọn trên Canvas/Timeline (nếu có) */
    val selectedImageOverlay: CanvasImageOverlayItem?
        get() = imageOverlays.firstOrNull { it.id == selectedClipId }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val adManager = AdMonetizationManager(application)
    private val ttsEngine = SherpaOfflineTtsEngine(application)
    private val voiceLibraryRepo = VoiceLibraryRepository(application, adManager)
    private val exportManager = VideoExportManager(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var activeTtsMediaPlayer: MediaPlayer? = null
    private var activeClipPlayingId: String? = null
    private var activeBgmMediaPlayer: MediaPlayer? = null
    private var activeBgmPlayingId: String? = null

    init {
        syncOnlineVoiceCatalog()
        seedInitialDemoClips()
    }

    /**
     * Đồng bộ danh mục Thư viện Giọng đọc từ Server Online.
     */
    fun syncOnlineVoiceCatalog() {
        viewModelScope.launch {
            val remoteUrl = _uiState.value.monetizationConfig.remoteVoiceCatalogUrl
            val catalog = voiceLibraryRepo.fetchOnlineAndLocalCatalog(remoteUrl)
            _uiState.update { state ->
                val currentSelected = catalog.firstOrNull { it.id == state.selectedVoiceProfile.id }
                    ?: catalog.first()
                state.copy(
                    voiceCatalog = catalog,
                    selectedVoiceProfile = currentSelected,
                    totalAdsWatched = adManager.getTotalAdsWatchedByUser(),
                    statusMessage = "Đã nạp ${catalog.size} giọng đọc (Minh Khang, Ngọc Huyền, Lạc Phi, Minh Quang, VPBank 5 giọng)"
                )
            }
        }
    }

    private fun seedInitialDemoClips() {
        viewModelScope.launch {
            val sample1 = ttsEngine.synthesizeWithSubtitles(
                text = "Giữa vũ trụ bao la, mỗi khoảnh khắc đều phản chiếu qua lăng kính hư không.",
                voiceProfile = OfflineVoiceProfile.BUILT_IN_MALE_MINH_KHANG,
                speechSpeed = 1.0f,
                insertAtTimelineMs = 400L
            ).getOrNull()

            val sample2Start = (sample1?.timelineEndMs ?: 4000L) + 600L
            val sample2 = ttsEngine.synthesizeWithSubtitles(
                text = "Bạn có thể cắt ngắn video, tắt tiếng gốc, chèn chữ, logo PNG làm mờ và kéo thả tự do.",
                voiceProfile = OfflineVoiceProfile.BUILT_IN_FEMALE_NGOC_HUYEN,
                speechSpeed = 1.0f,
                insertAtTimelineMs = sample2Start
            ).getOrNull()

            val demoTextOverlay = CanvasTextOverlayItem(
                text = "Lăng Kính Hư Không",
                normalizedX = 0.5f,
                normalizedY = 0.14f,
                fontSizeSp = 18f,
                colorHex = 0xFFFFE082,
                backgroundColorHex = 0x77000000,
                opacity = 0.92f,
                blurRadiusDp = 0f,
                timelineStartMs = 0L,
                timelineEndMs = 15_000L
            )

            val initialClips = listOfNotNull(sample1, sample2)
            _uiState.update { state ->
                state.copy(
                    voiceClips = initialClips,
                    textOverlays = listOf(demoTextOverlay),
                    selectedClipId = initialClips.firstOrNull()?.id
                )
            }
        }
    }

    // =========================================================================
    // 1. QUẢN LÝ QUẢNG CÁO & THƯ VIỆN GIỌNG ĐỌC
    // =========================================================================

    fun requestWatchAdForVoice(activity: Activity?, voice: OfflineVoiceProfile) {
        if (voice.installState == VoiceInstallState.DOWNLOADING) return

        adManager.showRewardedAdOrSponsorFallback(
            activity = activity,
            onRewardEarned = {
                onUserCompletedWatchingAd(voice)
            },
            onRequireFallbackAdDialog = {
                _uiState.update { it.copy(pendingAdVoiceTarget = voice) }
            }
        )
    }

    fun dismissSponsorAdDialog() {
        _uiState.update {
            it.copy(
                pendingAdVoiceTarget = null,
                statusMessage = "Bạn đã hủy xem quảng cáo • Chưa mở khóa giọng đọc."
            )
        }
    }

    fun onUserCompletedWatchingAd(voice: OfflineVoiceProfile) {
        _uiState.update { it.copy(pendingAdVoiceTarget = null) }

        if (voice.installState == VoiceInstallState.INSTALLED) {
            val newQuota = adManager.addRewardedUses(voice.id, voice.adRewardUsesPerView)
            _uiState.update { state ->
                val updatedList = state.voiceCatalog.map { item ->
                    if (item.id == voice.id) item.copy(remainingQuotaUses = newQuota) else item
                }
                val updatedSelected = updatedList.firstOrNull { it.id == voice.id } ?: state.selectedVoiceProfile
                state.copy(
                    voiceCatalog = updatedList,
                    selectedVoiceProfile = updatedSelected,
                    totalAdsWatched = adManager.getTotalAdsWatchedByUser(),
                    statusMessage = "🎉 Đã cộng +${voice.adRewardUsesPerView} lượt cho '${voice.displayName}' (Còn $newQuota lượt)."
                )
            }
            return
        }

        viewModelScope.launch {
            updateVoiceInCatalog(
                voice.id,
                installState = VoiceInstallState.DOWNLOADING,
                progress = 0.05f
            )
            _uiState.update {
                it.copy(statusMessage = "Đang kích hoạt model '${voice.displayName}'...")
            }

            val result = voiceLibraryRepo.downloadVoicePackAfterAdReward(voice) { progress ->
                updateVoiceInCatalog(
                    voice.id,
                    installState = VoiceInstallState.DOWNLOADING,
                    progress = progress
                )
            }

            result.onSuccess { installedProfile ->
                _uiState.update { state ->
                    val updatedList = state.voiceCatalog.map {
                        if (it.id == installedProfile.id) installedProfile else it
                    }
                    state.copy(
                        voiceCatalog = updatedList,
                        selectedVoiceProfile = installedProfile,
                        totalAdsWatched = adManager.getTotalAdsWatchedByUser(),
                        statusMessage = "✅ Đã mở khóa '${installedProfile.displayName}' + Tặng ${installedProfile.remainingQuotaUses} lượt tạo TTS!"
                    )
                }
            }.onFailure { err ->
                updateVoiceInCatalog(
                    voice.id,
                    installState = VoiceInstallState.NOT_DOWNLOADED,
                    progress = 0f
                )
                _uiState.update {
                    it.copy(statusMessage = "Lỗi tải giọng đọc: ${err.message}")
                }
            }
        }
    }

    fun previewVoiceSample(voice: OfflineVoiceProfile) {
        viewModelScope.launch {
            val previewProfile = voice.copy(
                installState = VoiceInstallState.INSTALLED,
                remainingQuotaUses = 1
            )
            _uiState.update {
                it.copy(statusMessage = "🔊 Đang phát thử mẫu giọng: ${voice.displayName}...")
            }
            val sampleRes = ttsEngine.synthesizeWithSubtitles(
                text = "Xin chào, đây là giọng đọc ${voice.displayName}",
                voiceProfile = previewProfile,
                speechSpeed = 1.0f,
                insertAtTimelineMs = 0L
            )
            sampleRes.onSuccess { clip ->
                stopAllAudioPlayers()
                runCatching {
                    val mp = MediaPlayer().apply {
                        setDataSource(clip.audioFilePath)
                        prepare()
                        start()
                    }
                    activeTtsMediaPlayer = mp
                }
            }
        }
    }

    fun deleteDownloadedVoice(voice: OfflineVoiceProfile) {
        viewModelScope.launch {
            voiceLibraryRepo.deleteDownloadedVoice(voice).onSuccess { resetProfile ->
                _uiState.update { state ->
                    val updatedCatalog = state.voiceCatalog.map { item ->
                        if (item.id == voice.id) resetProfile else item
                    }
                    val nextSelected = if (state.selectedVoiceProfile.id == voice.id) {
                        OfflineVoiceProfile.BUILT_IN_MALE_MINH_KHANG
                    } else {
                        state.selectedVoiceProfile
                    }
                    state.copy(
                        voiceCatalog = updatedCatalog,
                        selectedVoiceProfile = nextSelected,
                        statusMessage = "Đã xóa dữ liệu của '${voice.displayName}'."
                    )
                }
            }
        }
    }

    private fun updateVoiceInCatalog(
        voiceId: String,
        installState: VoiceInstallState,
        progress: Float
    ) {
        _uiState.update { state ->
            state.copy(
                voiceCatalog = state.voiceCatalog.map { item ->
                    if (item.id == voiceId) {
                        item.copy(installState = installState, downloadProgress = progress)
                    } else {
                        item
                    }
                }
            )
        }
    }

    // =========================================================================
    // 2. VIDEO 9:16 (<= 3 PHÚT): IMPORT, CẮT NGẮN VIDEO, TẮT/BẬT TIẾNG GỐC, SPEED
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
                val warningSuffix = when {
                    durationMs > EditorConstraints.MAX_VIDEO_DURATION_MS ->
                        " (Video dài > 3 phút đã được tự động cắt gọn ở 3:00)"
                    actualW > actualH ->
                        " (Đã căn vào khung dọc 9:16)"
                    else -> ""
                }

                _uiState.update { state ->
                    state.copy(
                        videoTrack = ShortVideoTrack(
                            videoUri = uri.toString(),
                            displayName = uri.lastPathSegment ?: "Video_9x16.mp4",
                            originalDurationMs = clampedDuration,
                            trimStartMs = 0L,
                            trimEndMs = clampedDuration,
                            width = actualW,
                            height = actualH,
                            speedMultiplier = 1.0f,
                            muteOriginalAudio = state.videoTrack.muteOriginalAudio
                        ),
                        playheadMs = 0L,
                        statusMessage = "Đã nạp video 9:16 (${clampedDuration / 1000}s)$warningSuffix"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "Không thể đọc video: ${err.message}")
                }
            }
        }
    }

    /**
     * Tắt / Bật âm thanh gốc của file Video MP4.
     */
    fun toggleMuteOriginalVideo() {
        _uiState.update { state ->
            val nextMute = !state.videoTrack.muteOriginalAudio
            state.copy(
                videoTrack = state.videoTrack.copy(muteOriginalAudio = nextMute),
                statusMessage = if (nextMute) {
                    "🔇 Đã TẮT tiếng gốc của Video MP4 (Chỉ phát giọng AI & nhạc phụ)"
                } else {
                    "🔊 Đã BẬT tiếng gốc của Video MP4"
                }
            )
        }
    }

    /**
     * Cắt bỏ phần đầu của Video MP4 từ 0 đến vị trí con trỏ Playhead hiện tại.
     */
    fun trimVideoStartAtPlayhead() {
        _uiState.update { state ->
            val video = state.videoTrack
            val cutSourceOffsetMs = (state.playheadMs * video.speedMultiplier).toLong()
            val newTrimStart = (video.trimStartMs + cutSourceOffsetMs)
                .coerceIn(0L, (video.trimEndMs - 1_000L).coerceAtLeast(0L))

            if (newTrimStart == video.trimStartMs) {
                return@update state.copy(
                    statusMessage = "Hãy di chuyển vạch Playhead vào trong đoạn Video để cắt bỏ phần đầu!"
                )
            }
            val updatedVideo = video.copy(trimStartMs = newTrimStart)
            state.copy(
                videoTrack = updatedVideo,
                playheadMs = 0L,
                statusMessage = "✂️ Đã cắt đầu Video • Đoạn giữ lại: ${formatMs(updatedVideo.trimStartMs)} → ${formatMs(updatedVideo.trimEndMs)} (${formatMs(updatedVideo.effectiveDurationMs)})"
            )
        }
    }

    /**
     * Cắt bỏ phần đuôi của Video MP4 từ vị trí con trỏ Playhead đến hết video.
     */
    fun trimVideoEndAtPlayhead() {
        _uiState.update { state ->
            val video = state.videoTrack
            val cutSourceOffsetMs = (state.playheadMs * video.speedMultiplier).toLong()
            val newTrimEnd = (video.trimStartMs + cutSourceOffsetMs)
                .coerceIn(video.trimStartMs + 1_000L, video.originalDurationMs)

            if (newTrimEnd == video.trimEndMs) {
                return@update state.copy(
                    statusMessage = "Hãy di chuyển vạch Playhead vào vị trí cần kết thúc Video để cắt đuôi!"
                )
            }
            val updatedVideo = video.copy(trimEndMs = newTrimEnd)
            state.copy(
                videoTrack = updatedVideo,
                playheadMs = state.playheadMs.coerceAtMost(updatedVideo.effectiveDurationMs),
                statusMessage = "✂️ Đã cắt ngắn đuôi Video • Tổng thời lượng mới: ${formatMs(updatedVideo.effectiveDurationMs)}"
            )
        }
    }

    /**
     * Chỉnh trực tiếp khoảng cắt ngắn Video (trimStartMs .. trimEndMs).
     */
    fun updateVideoTrimRange(newTrimStartMs: Long, newTrimEndMs: Long) {
        _uiState.update { state ->
            val video = state.videoTrack
            val safeStart = newTrimStartMs.coerceIn(0L, (video.originalDurationMs - 1_000L).coerceAtLeast(0L))
            val safeEnd = newTrimEndMs.coerceIn(safeStart + 1_000L, video.originalDurationMs)
            val updatedVideo = video.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
            state.copy(
                videoTrack = updatedVideo,
                playheadMs = state.playheadMs.coerceAtMost(updatedVideo.effectiveDurationMs),
                statusMessage = "Đoạn Video: ${formatMs(safeStart)} → ${formatMs(safeEnd)} (Còn ${formatMs(updatedVideo.effectiveDurationMs)})"
            )
        }
    }

    /**
     * Khôi phục thời lượng gốc của Video MP4.
     */
    fun resetVideoTrim() {
        _uiState.update { state ->
            val video = state.videoTrack
            val updated = video.copy(trimStartMs = 0L, trimEndMs = video.originalDurationMs)
            state.copy(
                videoTrack = updated,
                statusMessage = "Đã khôi phục toàn bộ thời lượng Video gốc (${formatMs(updated.effectiveDurationMs)})"
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
                statusMessage = "Tốc độ Video: ${safeSpeed}x → Thời lượng hiệu dụng: ${formatMs(newMaxDuration)}"
            )
        }
    }

    // =========================================================================
    // 3. TẠO GIỌNG ĐỌC AI + PHỤ ĐỀ KÈM THEO & KÉO/CẮT ĐOẠN MP3 VOICE
    // =========================================================================

    fun selectVoiceProfile(profile: OfflineVoiceProfile) {
        _uiState.update {
            it.copy(
                selectedVoiceProfile = profile,
                statusMessage = "Đã chọn giọng đọc: ${profile.displayName}"
            )
        }
    }

    fun updateTtsInputText(newText: String) {
        _uiState.update { it.copy(ttsInputText = newText) }
    }

    fun updateTtsSpeechRate(rate: Float) {
        _uiState.update { it.copy(ttsSpeechRate = rate.coerceIn(0.6f, 1.8f)) }
    }

    fun renderOfflineTtsAndAttachSubtitle(activity: Activity? = null) {
        val currentState = _uiState.value
        val voice = currentState.selectedVoiceProfile
        val text = currentState.ttsInputText.trim()

        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Vui lòng nhập nội dung văn bản để tạo giọng đọc!") }
            return
        }

        if (voice.needsAdTopUp || voice.installState != VoiceInstallState.INSTALLED) {
            requestWatchAdForVoice(activity, voice)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSynthesizingTts = true,
                    statusMessage = "Đang chuyển Text thành Giọng đọc (${voice.displayName})..."
                )
            }

            val result = ttsEngine.synthesizeWithSubtitles(
                text = text,
                voiceProfile = voice,
                speechSpeed = currentState.ttsSpeechRate,
                insertAtTimelineMs = currentState.playheadMs
            )

            result.onSuccess { newClip ->
                val remainingQuota = adManager.consumeOneUse(voice.id, voice.requiresAdToUnlock)
                _uiState.update { state ->
                    val updatedCatalog = state.voiceCatalog.map { item ->
                        if (item.id == voice.id) item.copy(remainingQuotaUses = remainingQuota) else item
                    }
                    val updatedSelected = updatedCatalog.firstOrNull { it.id == voice.id } ?: voice
                    val quotaNote = if (voice.requiresAdToUnlock) " (Còn $remainingQuota lượt)" else ""
                    state.copy(
                        isSynthesizingTts = false,
                        voiceCatalog = updatedCatalog,
                        selectedVoiceProfile = updatedSelected,
                        voiceClips = (state.voiceClips + newClip).sortedBy { it.timelineStartMs },
                        selectedClipId = newClip.id,
                        statusMessage = "Đã tạo Voice + Khớp ${newClip.words.size} từ phụ đề!$quotaNote"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSynthesizingTts = false,
                        statusMessage = "Lỗi tạo TTS: ${err.message}"
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
                statusMessage = "Đang kéo đoạn Voice • Phụ đề tự động đi theo khớp với Video"
            )
        }
    }

    /**
     * Kéo thả vị trí khung Phụ đề Voice AI đến bất kỳ vị trí nào trên Khu View 9:16.
     */
    fun moveSubtitleOverlayOnCanvas(deltaNormX: Float, deltaNormY: Float) {
        _uiState.update { state ->
            val current = state.subtitleStyle
            val nextX = (current.normalizedX + deltaNormX).coerceIn(0.10f, 0.90f)
            val nextY = (current.normalizedY + deltaNormY).coerceIn(0.08f, 0.92f)
            state.copy(
                subtitleStyle = current.copy(normalizedX = nextX, normalizedY = nextY),
                statusMessage = "Đã di chuyển vị trí Phụ đề trên khung View 9:16"
            )
        }
    }

    fun updateSubtitleOverlayStyle(
        opacity: Float? = null,
        blurRadiusDp: Float? = null,
        fontSizeSp: Float? = null
    ) {
        _uiState.update { state ->
            val cur = state.subtitleStyle
            state.copy(
                subtitleStyle = cur.copy(
                    opacity = opacity?.coerceIn(0.1f, 1.0f) ?: cur.opacity,
                    blurRadiusDp = blurRadiusDp?.coerceIn(0f, 20f) ?: cur.blurRadiusDp,
                    fontSizeSp = fontSizeSp?.coerceIn(11f, 28f) ?: cur.fontSizeSp
                )
            )
        }
    }

    // =========================================================================
    // 4. NHẬP TEXT OVERLAY & LOGO PNG OVERLAY (LÀM MỜ + KÉO THẢ VỊ TRÍ BẤT KỲ)
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
                normalizedY = (0.22f + (state.textOverlays.size % 4) * 0.12f).coerceIn(0.15f, 0.75f),
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
                statusMessage = "Đã thêm chữ '${newItem.text}' • Kéo thả trực tiếp trên khu View 9:16 hoặc chỉnh độ mờ!"
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
                            fontSizeSp = fontSizeSp?.coerceIn(10f, 44f) ?: item.fontSizeSp,
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

    /**
     * Kéo thả lớp Text đến vị trí bất kỳ (X, Y) trên Khu View 9:16.
     */
    fun moveTextOverlayOnCanvas(overlayId: String, deltaNormX: Float, deltaNormY: Float) {
        _uiState.update { state ->
            state.copy(
                selectedClipId = overlayId,
                textOverlays = state.textOverlays.map { item ->
                    if (item.id == overlayId) {
                        item.copy(
                            normalizedX = (item.normalizedX + deltaNormX).coerceIn(0.05f, 0.95f),
                            normalizedY = (item.normalizedY + deltaNormY).coerceIn(0.05f, 0.95f)
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

    /**
     * Nhập file Icon / Logo PNG từ bộ nhớ máy vào dự án.
     */
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
                    normalizedX = 0.80f,
                    normalizedY = 0.14f,
                    sizeDp = 76f,
                    opacity = 1.0f,
                    blurRadiusDp = 0f,
                    timelineStartMs = startMs,
                    timelineEndMs = endMs
                )
                state.copy(
                    imageOverlays = state.imageOverlays + newLogo,
                    selectedClipId = newLogo.id,
                    statusMessage = "Đã chèn Icon/Logo PNG ($displayName) • Kéo thả trên khu View 9:16 hoặc chỉnh làm mờ!"
                )
            }
        }
    }

    /**
     * Tạo nhanh 1 Icon / Logo PNG trong suốt mẫu (trường hợp người dùng muốn test ngay không cần tìm ảnh trong máy).
     */
    fun addSampleLogoPngOverlay(badgeLabel: String = "LKHK AI") {
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
                    normalizedX = 0.80f,
                    normalizedY = 0.14f,
                    sizeDp = 76f,
                    opacity = 0.95f,
                    blurRadiusDp = 0f,
                    timelineStartMs = startMs,
                    timelineEndMs = endMs
                )
                state.copy(
                    imageOverlays = state.imageOverlays + newLogo,
                    selectedClipId = newLogo.id,
                    statusMessage = "Đã thêm Logo PNG '$badgeLabel' • Kéo thả vị trí bất kỳ & chỉnh độ mờ!"
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

    /**
     * Kéo thả Icon / Logo PNG đến vị trí bất kỳ (X, Y) trên Khu View 9:16.
     */
    fun moveImageOverlayOnCanvas(overlayId: String, deltaNormX: Float, deltaNormY: Float) {
        _uiState.update { state ->
            state.copy(
                selectedClipId = overlayId,
                imageOverlays = state.imageOverlays.map { item ->
                    if (item.id == overlayId) {
                        item.copy(
                            normalizedX = (item.normalizedX + deltaNormX).coerceIn(0.05f, 0.95f),
                            normalizedY = (item.normalizedY + deltaNormY).coerceIn(0.05f, 0.95f)
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
    // 5. CẮT (SPLIT) ĐOẠN VOICE / MP3 / TEXT / LOGO PNG & XÓA TRÊN TIMELINE
    // =========================================================================

    fun splitSelectedOrActiveClipAtPlayhead() {
        val state = _uiState.value
        val cutMs = state.playheadMs

        // 1. Ưu tiên cắt đoạn Voice + Phụ đề đang chọn hoặc nằm tại Playhead
        val targetVoiceClip = state.voiceClips.firstOrNull {
            it.id == state.selectedClipId && cutMs in it.timelineStartMs..it.timelineEndMs
        } ?: state.voiceClips.firstOrNull {
            cutMs in it.timelineStartMs..it.timelineEndMs
        }

        if (targetVoiceClip != null) {
            val splitPair = targetVoiceClip.splitAtTimelineMs(cutMs)
            if (splitPair == null) {
                _uiState.update {
                    it.copy(statusMessage = "Điểm cắt quá sát mép đoạn Voice (cần cách mép ít nhất 0.25s).")
                }
                return
            }

            val (leftClip, rightClip) = splitPair
            _uiState.update { current ->
                val remaining = current.voiceClips.filterNot { it.id == targetVoiceClip.id }
                current.copy(
                    voiceClips = (remaining + leftClip + rightClip).sortedBy { it.timelineStartMs },
                    selectedClipId = rightClip.id,
                    statusMessage = "✂️ Đã cắt đôi Voice & Phụ đề: [${leftClip.words.size} từ] | [${rightClip.words.size} từ]. Kéo thả từng đoạn khớp với video!"
                )
            }
            return
        }

        // 2. Cắt lớp Text Overlay nếu đang chọn
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

        // 3. Cắt lớp Logo PNG nếu đang chọn
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

        // 4. Cắt file MP3 phụ
        val targetSecondary = state.secondaryMp3Clips.firstOrNull {
            it.id == state.selectedClipId && cutMs in it.timelineStartMs..it.timelineEndMs
        } ?: state.secondaryMp3Clips.firstOrNull {
            cutMs in it.timelineStartMs..it.timelineEndMs
        }
        if (targetSecondary != null) {
            val splitSecondary = targetSecondary.splitAtTimelineMs(cutMs)
            if (splitSecondary != null) {
                val (leftBgm, rightBgm) = splitSecondary
                _uiState.update { current ->
                    val remaining = current.secondaryMp3Clips.filterNot { it.id == targetSecondary.id }
                    current.copy(
                        secondaryMp3Clips = (remaining + leftBgm + rightBgm).sortedBy { it.timelineStartMs },
                        selectedClipId = rightBgm.id,
                        statusMessage = "✂️ Đã cắt đôi file MP3 phụ tại ${formatMs(cutMs)}"
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(statusMessage = "Hãy đặt vạch Playhead vào giữa đoạn Voice, Text, Logo PNG hoặc MP3 cần cắt!")
        }
    }

    fun importSecondaryMp3(uri: Uri) {
        viewModelScope.launch {
            val retriever = MediaMetadataRetriever()
            val durationMs = runCatching {
                retriever.setDataSource(getApplication(), uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }.getOrNull() ?: 8_000L
            runCatching { retriever.release() }

            val state = _uiState.value
            val clampedEnd = durationMs.coerceAtMost(state.totalTimelineDurationMs)
            val newSecondary = SecondaryMp3Clip(
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "MP3_Phu_${state.secondaryMp3Clips.size + 1}.mp3",
                audioUriOrPath = uri.toString(),
                timelineStartMs = state.playheadMs,
                trimStartMs = 0L,
                trimEndMs = clampedEnd,
                volume = 0.5f,
                trackLane = state.secondaryMp3Clips.size % 3
            )

            _uiState.update { current ->
                current.copy(
                    secondaryMp3Clips = current.secondaryMp3Clips + newSecondary,
                    selectedClipId = newSecondary.id,
                    statusMessage = "Đã thêm file MP3 phụ (${newSecondary.fileName})"
                )
            }
        }
    }

    fun moveSecondaryMp3ByDelta(clipId: String, deltaTimelineMs: Long) {
        _uiState.update { state ->
            val maxDuration = state.totalTimelineDurationMs
            val updated = state.secondaryMp3Clips.map { bgm ->
                if (bgm.id == clipId) {
                    bgm.moveTo(bgm.timelineStartMs + deltaTimelineMs, maxDuration)
                } else {
                    bgm
                }
            }
            state.copy(
                secondaryMp3Clips = updated,
                selectedClipId = clipId
            )
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
                secondaryMp3Clips = state.secondaryMp3Clips.filterNot { it.id == selectedId },
                textOverlays = state.textOverlays.filterNot { it.id == selectedId },
                imageOverlays = state.imageOverlays.filterNot { it.id == selectedId },
                selectedClipId = null,
                statusMessage = "Đã xóa phần tử đã chọn khỏi dự án."
            )
        }
    }

    // =========================================================================
    // 6. PHÁT XEM THỬ (PREVIEW TRANSPORT) & XUẤT VIDEO MP4 9:16
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
                    stopAllAudioPlayers()
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
        stopAllAudioPlayers()
    }

    private fun syncAudioPlaybackWithPlayhead(
        playheadMs: Long,
        isPlaying: Boolean,
        forceSeek: Boolean = false
    ) {
        if (!isPlaying) {
            stopAllAudioPlayers()
            return
        }

        // 1. Phát đoạn Voice AI đang nằm tại Playhead
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

        // 2. Phát đoạn MP3 phụ (BGM/SFX) nếu có tại Playhead
        val activeBgm = _uiState.value.secondaryMp3Clips.firstOrNull {
            playheadMs in it.timelineStartMs..it.timelineEndMs
        }
        if (activeBgm == null) {
            stopBgmAudioPlayer()
        } else if (activeBgmPlayingId != activeBgm.id || forceSeek || activeBgmMediaPlayer?.isPlaying != true) {
            stopBgmAudioPlayer()
            runCatching {
                val mp = MediaPlayer().apply {
                    setDataSource(getApplication(), Uri.parse(activeBgm.audioUriOrPath))
                    prepare()
                    val offsetInsideBgm = (activeBgm.trimStartMs + (playheadMs - activeBgm.timelineStartMs)).toInt()
                    seekTo(offsetInsideBgm)
                    setVolume(activeBgm.volume, activeBgm.volume)
                    start()
                }
                activeBgmMediaPlayer = mp
                activeBgmPlayingId = activeBgm.id
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

    private fun stopBgmAudioPlayer() {
        runCatching {
            activeBgmMediaPlayer?.stop()
            activeBgmMediaPlayer?.release()
        }
        activeBgmMediaPlayer = null
        activeBgmPlayingId = null
    }

    private fun stopAllAudioPlayers() {
        stopTtsAudioPlayer()
        stopBgmAudioPlayer()
    }

    fun exportFinalVideo(activity: Activity? = null) {
        adManager.showInterstitialBeforeExport(activity) {
            performExport()
        }
    }

    private fun performExport() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExportingVideo = true,
                    totalAdsWatched = adManager.getTotalAdsWatchedByUser(),
                    statusMessage = "Đang kết xuất Video 9:16 + Phụ đề + Text + Logo PNG..."
                )
            }

            val exportDir = File(getApplication<Application>().filesDir, "exports").apply { mkdirs() }
            val srtFile = File(exportDir, "LangKinhHuKhong_Subtitles.srt")
            exportManager.exportSyncedSrtFile(state.voiceClips, srtFile)

            if (state.videoTrack.videoUri.isBlank()) {
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        statusMessage = "Đã xuất phụ đề .SRT (${srtFile.name}). Hãy Nạp Video 9:16 để xuất MP4!"
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
                        statusMessage = "✅ Xuất thành công: ${mp4File.name} & ${srtFile.name}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        statusMessage = "Đã xuất ${srtFile.name} (Lưu ý Media3 MP4: ${err.message})"
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
