package vn.langkinhhukhong.tts.model

import java.util.UUID

/**
 * Giới hạn chuẩn của ứng dụng: Video Short dọc 9:16, tối đa 3 phút (180.000 ms).
 */
object EditorConstraints {
    const val MAX_VIDEO_DURATION_MS = 180_000L // 3 phút
    const val TARGET_ASPECT_WIDTH = 9
    const val TARGET_ASPECT_HEIGHT = 16
    val SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
}

/**
 * Cấu hình Quảng cáo kiếm tiền (AdMob + Quảng cáo trực tiếp của chủ app từ Server Online).
 */
data class MonetizationConfig(
    val admobAppId: String = "ca-app-pub-3940256099942544~3347511713",
    val admobRewardedUnitId: String = "ca-app-pub-3940256099942544/5224354917",
    val admobInterstitialUnitId: String = "ca-app-pub-3940256099942544/1033173712",
    val admobBannerUnitId: String = "ca-app-pub-3940256099942544/6300978111",
    val remoteVoiceCatalogUrl: String = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/server_catalog/voice_catalog.json",
    val customSponsorTitle: String = "Nhà tài trợ Lăng Kính Hư Không • Khóa học & Công cụ AI",
    val customSponsorCtaText: String = "Xem ngay ưu đãi từ Lăng Kính Hư Không",
    val customSponsorTargetUrl: String = "https://example.com/langkinhhukhong-ads",
    val rewardUsesPerAdWatch: Int = 5
)

/**
 * Phân loại nguồn lưu trữ của Giọng đọc AI:
 * - BUILT_IN_ASSET: Giọng cài sẵn trong APK (assets/models/).
 * - ONLINE_CLOUD_LIBRARY: Giọng đặt trên Server Online của bạn (Yêu cầu xem Quảng cáo để tải & cộng lượt dùng).
 * - CUSTOM_IMPORTED: File model giọng đọc (.onnx / .zip) người dùng nạp thêm.
 */
enum class VoiceStorageSource {
    BUILT_IN_ASSET,
    ONLINE_CLOUD_LIBRARY,
    CUSTOM_IMPORTED
}

/**
 * Trạng thái tải về của một Giọng đọc trong Thư viện Online.
 */
enum class VoiceInstallState {
    INSTALLED,      // Đã tải về bộ nhớ đệm của máy
    NOT_DOWNLOADED, // Nằm trên Server Online, cần Xem Quảng Cáo để tải về
    DOWNLOADING     // Đang tải từ Server Online về máy
}

/**
 * Cấu hình một Giọng đọc AI (Kết hợp Thư viện Online + Cơ chế Xem Quảng Cáo Mở Khóa/Cộng Lượt).
 *
 * Cơ chế bảo vệ doanh thu quảng cáo cho chủ App:
 * - Các giọng trong Thư viện Online có [requiresAdToUnlock] = true.
 * - Khi người dùng bấm Tải giọng đọc -> Bắt buộc xem 1 Video Quảng Cáo Có Thưởng (Rewarded Ad)
 *   thì mới tải file .onnx về máy và nhận ngay [adRewardUsesPerView] lượt tạo TTS (mặc định 5 lượt).
 * - Khi dùng hết 5 lượt -> Dù file .onnx đã nằm trong máy, người dùng vẫn phải xem tiếp 1 Quảng Cáo
 *   để nạp thêm +5 lượt dùng mới! Nhờ đó bạn vừa KHÔNG tốn phí GPU Server (máy người dùng tự render),
 *   vừa thu được tiền quảng cáo liên tục mỗi ngày.
 */
data class OfflineVoiceProfile(
    val id: String,
    val displayName: String,
    val regionTag: String,
    val styleTag: String,
    val description: String,
    val sizeMb: Int,
    val storageSource: VoiceStorageSource,
    val modelPathOrAssetDir: String,
    val modelFileName: String,
    val onnxJsonFileName: String = "$modelFileName.json",
    val tokensFileName: String = "tokens.txt",
    val speakerId: Int = 0,
    val numSpeakers: Int = 1,
    val noiseScale: Float = 0.667f,
    val lengthScale: Float = 1.0f,
    val noiseW: Float = 0.8f,
    val downloadUrl: String = "",
    val onnxJsonDownloadUrl: String = "",
    val defaultPitchHz: Float = 165f,
    val colorHex: Long = 0xFF1E5128,
    val installState: VoiceInstallState = VoiceInstallState.INSTALLED,
    val downloadProgress: Float = 1.0f,
    val requiresAdToUnlock: Boolean = false,
    val remainingQuotaUses: Int = 999,
    val adRewardUsesPerView: Int = 5
) {
    /** Kiểm tra giọng đọc đã tải về VÀ còn lượt sử dụng (đã xem quảng cáo) hay chưa */
    val isReadyToRender: Boolean
        get() = installState == VoiceInstallState.INSTALLED && (!requiresAdToUnlock || remainingQuotaUses > 0)

    /** Đã tải về máy nhưng hết lượt dùng -> Cần xem 1 Quảng cáo để cộng thêm lượt */
    val needsAdTopUp: Boolean
        get() = installState == VoiceInstallState.INSTALLED && requiresAdToUnlock && remainingQuotaUses <= 0

    companion object {
        val BUILT_IN_MALE_MINH_KHANG = OfflineVoiceProfile(
            id = "vi_nam_minhkhang",
            displayName = "Nam - Minh Khang (Trầm ấm)",
            regionTag = "Miền Bắc",
            styleTag = "Mặc định",
            description = "Giọng chính #1 cài sẵn (minhkhang.onnx) • Miễn phí không giới hạn",
            sizeMb = 61,
            storageSource = VoiceStorageSource.BUILT_IN_ASSET,
            modelPathOrAssetDir = "models/minhkhang",
            modelFileName = "minhkhang.onnx",
            onnxJsonFileName = "minhkhang.onnx.json",
            speakerId = 0,
            numSpeakers = 1,
            defaultPitchHz = 130f,
            colorHex = 0xFF1E5128,
            installState = VoiceInstallState.INSTALLED,
            requiresAdToUnlock = false,
            remainingQuotaUses = 999
        )

        val BUILT_IN_FEMALE_NGOC_HUYEN = OfflineVoiceProfile(
            id = "vi_nu_ngochuyen",
            displayName = "Nữ - Ngọc Huyền (Tự nhiên)",
            regionTag = "Miền Bắc",
            styleTag = "Mặc định",
            description = "Giọng chính #2 cài sẵn (ngochuyennew(update).onnx) • Trong trẻo cho Short 9:16",
            sizeMb = 61,
            storageSource = VoiceStorageSource.BUILT_IN_ASSET,
            modelPathOrAssetDir = "models/ngochuyenupdate",
            modelFileName = "ngochuyennew(update).onnx",
            onnxJsonFileName = "ngochuyennew(update).onnx.json",
            speakerId = 0,
            numSpeakers = 1,
            defaultPitchHz = 220f,
            colorHex = 0xFF5C2A6A,
            installState = VoiceInstallState.INSTALLED,
            requiresAdToUnlock = false,
            remainingQuotaUses = 999
        )

        // Giữ alias tương thích ngược
        val BUILT_IN_MALE_MINH = BUILT_IN_MALE_MINH_KHANG
        val BUILT_IN_FEMALE_LINH = BUILT_IN_FEMALE_NGOC_HUYEN

        /**
         * Danh mục Thư viện Giọng đọc tích hợp 5 bộ model thật từ thư mục Voices/
         * (Minh Khang, Ngọc Huyền, Lạc Phi, Minh Quang, và 5 Speaker của VPBank).
         */
        val DEFAULT_ONLINE_LIBRARY_CATALOG = listOf(
            BUILT_IN_MALE_MINH_KHANG,
            BUILT_IN_FEMALE_NGOC_HUYEN,
            OfflineVoiceProfile(
                id = "vi_nam_lacphi",
                displayName = "Nam - Lạc Phi (Kể chuyện VIP)",
                regionTag = "Miền Bắc",
                styleTag = "Kể chuyện / Review",
                description = "Model Lạc Phi (lacphi.onnx) • Xem 1 Quảng cáo để mở khóa & nhận 5 lượt tạo",
                sizeMb = 61,
                storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                modelPathOrAssetDir = "models/lacphi",
                modelFileName = "lacphi.onnx",
                onnxJsonFileName = "lacphi.onnx.json",
                speakerId = 0,
                numSpeakers = 1,
                downloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/lacphi/lacphi.onnx",
                onnxJsonDownloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/lacphi/lacphi.onnx.json",
                defaultPitchHz = 138f,
                colorHex = 0xFF1B4332,
                installState = VoiceInstallState.NOT_DOWNLOADED,
                downloadProgress = 0f,
                requiresAdToUnlock = true,
                remainingQuotaUses = 0,
                adRewardUsesPerView = 5
            ),
            OfflineVoiceProfile(
                id = "vi_nam_minhquang",
                displayName = "Nam - Minh Quang (Thuyết minh VIP)",
                regionTag = "Miền Bắc",
                styleTag = "Thuyết minh / Kiến thức",
                description = "Model Minh Quang (minhquang.onnx) • Xem 1 Quảng cáo để mở khóa & nhận 5 lượt",
                sizeMb = 61,
                storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                modelPathOrAssetDir = "models/minhquang",
                modelFileName = "minhquang.onnx",
                onnxJsonFileName = "minhquang.onnx.json",
                speakerId = 0,
                numSpeakers = 1,
                downloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/minhquang/minhquang.onnx",
                onnxJsonDownloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/minhquang/minhquang.onnx.json",
                defaultPitchHz = 148f,
                colorHex = 0xFF0D47A1,
                installState = VoiceInstallState.NOT_DOWNLOADED,
                downloadProgress = 0f,
                requiresAdToUnlock = true,
                remainingQuotaUses = 0,
                adRewardUsesPerView = 5
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp0",
                displayName = "VPBank CSA - Giọng #0 (Chuẩn VIP)",
                regionTag = "Đa vùng miền",
                styleTag = "Speaker #0 • CSA v3",
                description = "Model VPBank Piper v3 (Speaker 0) • Xem 1 Quảng cáo để mở khóa & nhận 5 lượt",
                sizeMb = 74,
                storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 0,
                numSpeakers = 5,
                downloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonDownloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx.json",
                defaultPitchHz = 195f,
                colorHex = 0xFF1565C0,
                installState = VoiceInstallState.NOT_DOWNLOADED,
                downloadProgress = 0f,
                requiresAdToUnlock = true,
                remainingQuotaUses = 0,
                adRewardUsesPerView = 5
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp1",
                displayName = "VPBank CSA - Giọng #1 (Thân thiện VIP)",
                regionTag = "Đa vùng miền",
                styleTag = "Speaker #1 • CSA v3",
                description = "Model VPBank Piper v3 (Speaker 1) • Xem 1 Quảng cáo để mở khóa & nhận 5 lượt",
                sizeMb = 74,
                storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 1,
                numSpeakers = 5,
                downloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonDownloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx.json",
                defaultPitchHz = 210f,
                colorHex = 0xFF283593,
                installState = VoiceInstallState.NOT_DOWNLOADED,
                downloadProgress = 0f,
                requiresAdToUnlock = true,
                remainingQuotaUses = 0,
                adRewardUsesPerView = 5
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp2",
                displayName = "VPBank CSA - Giọng #2 (Truyền cảm VIP)",
                regionTag = "Đa vùng miền",
                styleTag = "Speaker #2 • CSA v3",
                description = "Model VPBank Piper v3 (Speaker 2) • Xem 1 Quảng cáo để mở khóa & nhận 5 lượt",
                sizeMb = 74,
                storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 2,
                numSpeakers = 5,
                downloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonDownloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx.json",
                defaultPitchHz = 175f,
                colorHex = 0xFF4527A0,
                installState = VoiceInstallState.NOT_DOWNLOADED,
                downloadProgress = 0f,
                requiresAdToUnlock = true,
                remainingQuotaUses = 0,
                adRewardUsesPerView = 5
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp3",
                displayName = "VPBank CSA - Giọng #3 (Chuyên nghiệp VIP)",
                regionTag = "Đa vùng miền",
                styleTag = "Speaker #3 • CSA v3",
                description = "Model VPBank Piper v3 (Speaker 3) • Xem 1 Quảng cáo để mở khóa & nhận 5 lượt",
                sizeMb = 74,
                storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 3,
                numSpeakers = 5,
                downloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonDownloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx.json",
                defaultPitchHz = 160f,
                colorHex = 0xFF6A1B4D,
                installState = VoiceInstallState.NOT_DOWNLOADED,
                downloadProgress = 0f,
                requiresAdToUnlock = true,
                remainingQuotaUses = 0,
                adRewardUsesPerView = 5
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp4",
                displayName = "VPBank CSA - Giọng #4 (Năng động VIP)",
                regionTag = "Đa vùng miền",
                styleTag = "Speaker #4 • CSA v3",
                description = "Model VPBank Piper v3 (Speaker 4) • Xem 1 Quảng cáo để mở khóa & nhận 5 lượt",
                sizeMb = 74,
                storageSource = VoiceStorageSource.ONLINE_CLOUD_LIBRARY,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 4,
                numSpeakers = 5,
                downloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonDownloadUrl = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/Voices/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx.json",
                defaultPitchHz = 228f,
                colorHex = 0xFF3E2723,
                installState = VoiceInstallState.NOT_DOWNLOADED,
                downloadProgress = 0f,
                requiresAdToUnlock = true,
                remainingQuotaUses = 0,
                adRewardUsesPerView = 5
            )
        )
    }
}

/**
 * Thông tin từng từ (Word) kèm mốc thời gian gốc tính từ đầu file âm thanh TTS (0ms).
 */
data class SubtitleWord(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long
)

/**
 * Từ phụ đề đã được ánh xạ (map) ra thời gian thực trên thanh Timeline của Video.
 */
data class TimelineSubtitleToken(
    val word: String,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val isHighlighted: Boolean
)

/**
 * COMPOUND CLIP: Gắn chặt file MP3/WAV giọng đọc với Phụ đề (Subtitles).
 */
data class VoiceSubtitleClip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val fullText: String,
    val voiceProfile: OfflineVoiceProfile,
    val audioFilePath: String,
    val timelineStartMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val volume: Float = 1.0f,
    val words: List<SubtitleWord>
) {
    val durationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)

    val timelineEndMs: Long
        get() = timelineStartMs + durationMs

    val visibleSubText: String
        get() = words.joinToString(" ") { it.text }

    fun getMappedWords(currentPlayheadMs: Long): List<TimelineSubtitleToken> {
        return words.mapNotNull { word ->
            if (word.sourceEndMs <= trimStartMs || word.sourceStartMs >= trimEndMs) {
                null
            } else {
                val clampedSrcStart = word.sourceStartMs.coerceAtLeast(trimStartMs)
                val clampedSrcEnd = word.sourceEndMs.coerceAtMost(trimEndMs)
                val absStart = timelineStartMs + (clampedSrcStart - trimStartMs)
                val absEnd = timelineStartMs + (clampedSrcEnd - trimStartMs)
                TimelineSubtitleToken(
                    word = word.text,
                    timelineStartMs = absStart,
                    timelineEndMs = absEnd,
                    isHighlighted = currentPlayheadMs in absStart..absEnd
                )
            }
        }
    }

    fun moveTo(newTimelineStartMs: Long, maxTimelineMs: Long): VoiceSubtitleClip {
        val clampedStart = newTimelineStartMs.coerceIn(
            0L,
            (maxTimelineMs - durationMs).coerceAtLeast(0L)
        )
        return copy(timelineStartMs = clampedStart)
    }

    fun splitAtTimelineMs(cutTimelineMs: Long): Pair<VoiceSubtitleClip, VoiceSubtitleClip>? {
        val minSliceMs = 250L
        if (cutTimelineMs <= timelineStartMs + minSliceMs ||
            cutTimelineMs >= timelineEndMs - minSliceMs
        ) {
            return null
        }

        val relativeCutOffsetMs = cutTimelineMs - timelineStartMs
        val sourceSplitPointMs = trimStartMs + relativeCutOffsetMs

        val leftWords = mutableListOf<SubtitleWord>()
        val rightWords = mutableListOf<SubtitleWord>()

        for (word in words) {
            val wordMidpoint = (word.sourceStartMs + word.sourceEndMs) / 2
            if (wordMidpoint < sourceSplitPointMs) {
                leftWords.add(
                    word.copy(sourceEndMs = word.sourceEndMs.coerceAtMost(sourceSplitPointMs))
                )
            } else {
                rightWords.add(
                    word.copy(sourceStartMs = word.sourceStartMs.coerceAtLeast(sourceSplitPointMs))
                )
            }
        }

        val leftText = leftWords.joinToString(" ") { it.text }.ifBlank { "..." }
        val rightText = rightWords.joinToString(" ") { it.text }.ifBlank { "..." }

        val leftClip = copy(
            id = UUID.randomUUID().toString(),
            title = "${title} (1)",
            fullText = leftText,
            trimEndMs = sourceSplitPointMs,
            words = leftWords
        )

        val rightClip = copy(
            id = UUID.randomUUID().toString(),
            title = "${title} (2)",
            fullText = rightText,
            timelineStartMs = cutTimelineMs,
            trimStartMs = sourceSplitPointMs,
            words = rightWords
        )

        return leftClip to rightClip
    }
}

/**
 * File MP3 phụ (Nhạc nền BGM, hiệu ứng âm thanh SFX, tiếng động...)
 */
data class SecondaryMp3Clip(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val audioUriOrPath: String,
    val timelineStartMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long,
    val volume: Float = 0.6f,
    val trackLane: Int = 0
) {
    val durationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)

    val timelineEndMs: Long
        get() = timelineStartMs + durationMs

    fun moveTo(newTimelineStartMs: Long, maxTimelineMs: Long): SecondaryMp3Clip {
        val clamped = newTimelineStartMs.coerceIn(
            0L,
            (maxTimelineMs - durationMs).coerceAtLeast(0L)
        )
        return copy(timelineStartMs = clamped)
    }

    fun splitAtTimelineMs(cutTimelineMs: Long): Pair<SecondaryMp3Clip, SecondaryMp3Clip>? {
        val minSliceMs = 250L
        if (cutTimelineMs <= timelineStartMs + minSliceMs ||
            cutTimelineMs >= timelineEndMs - minSliceMs
        ) {
            return null
        }
        val relativeCut = cutTimelineMs - timelineStartMs
        val sourceSplit = trimStartMs + relativeCut

        val left = copy(
            id = UUID.randomUUID().toString(),
            fileName = "$fileName (1)",
            trimEndMs = sourceSplit
        )
        val right = copy(
            id = UUID.randomUUID().toString(),
            fileName = "$fileName (2)",
            timelineStartMs = cutTimelineMs,
            trimStartMs = sourceSplit
        )
        return left to right
    }
}

/**
 * Cấu hình vị trí (Kéo thả 2D trên khu View 9:16) & độ mờ của khung Phụ đề Voice AI.
 */
data class SubtitleOverlayStyle(
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.82f,
    val fontSizeSp: Float = 15f,
    val opacity: Float = 1.0f,
    val blurRadiusDp: Float = 0f
)

/**
 * Lớp Chữ (Text Overlay) chèn lên Video 9:16:
 * - Hỗ trợ kéo thả vị trí bất kỳ (normalizedX, normalizedY từ 0.0 -> 1.0) trên khu View.
 * - Hỗ trợ làm mờ trong suốt (opacity: 0.1 -> 1.0) và làm mờ nhòe (blurRadiusDp: 0 -> 25dp).
 * - Hỗ trợ kéo thả thời gian xuất hiện và cắt (split) trên Timeline như một part độc lập.
 */
data class CanvasTextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.25f,
    val fontSizeSp: Float = 20f,
    val colorHex: Long = 0xFFFFFFFF,
    val backgroundColorHex: Long = 0x88000000,
    val opacity: Float = 1.0f,
    val blurRadiusDp: Float = 0f,
    val timelineStartMs: Long = 0L,
    val timelineEndMs: Long = 5_000L
) {
    val durationMs: Long
        get() = (timelineEndMs - timelineStartMs).coerceAtLeast(300L)

    fun isVisibleAt(playheadMs: Long): Boolean = playheadMs in timelineStartMs..timelineEndMs

    fun moveTimelineTo(newStartMs: Long, maxTimelineMs: Long): CanvasTextOverlayItem {
        val dur = durationMs
        val clampedStart = newStartMs.coerceIn(0L, (maxTimelineMs - dur).coerceAtLeast(0L))
        return copy(
            timelineStartMs = clampedStart,
            timelineEndMs = clampedStart + dur
        )
    }

    fun splitAtTimelineMs(cutTimelineMs: Long): Pair<CanvasTextOverlayItem, CanvasTextOverlayItem>? {
        val minSliceMs = 300L
        if (cutTimelineMs <= timelineStartMs + minSliceMs ||
            cutTimelineMs >= timelineEndMs - minSliceMs
        ) {
            return null
        }
        val left = copy(
            id = UUID.randomUUID().toString(),
            timelineEndMs = cutTimelineMs
        )
        val right = copy(
            id = UUID.randomUUID().toString(),
            timelineStartMs = cutTimelineMs
        )
        return left to right
    }
}

/**
 * Lớp Icon / Logo PNG (Image Overlay) chèn lên Video 9:16:
 * - Hỗ trợ nhập file ảnh PNG/JPG có nền trong suốt từ máy.
 * - Hỗ trợ kéo thả vị trí bất kỳ (normalizedX, normalizedY) trên khu View 9:16.
 * - Hỗ trợ làm mờ trong suốt (opacity: 0.1 -> 1.0), làm mờ nhòe (blurRadiusDp: 0 -> 25dp) và phóng to/thu nhỏ (sizeDp).
 * - Hỗ trợ kéo thả thời gian xuất hiện và cắt (split) trên Timeline.
 */
data class CanvasImageOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val imageUri: String,
    val normalizedX: Float = 0.80f,
    val normalizedY: Float = 0.14f,
    val sizeDp: Float = 72f,
    val opacity: Float = 1.0f,
    val blurRadiusDp: Float = 0f,
    val timelineStartMs: Long = 0L,
    val timelineEndMs: Long = 8_000L
) {
    val durationMs: Long
        get() = (timelineEndMs - timelineStartMs).coerceAtLeast(300L)

    fun isVisibleAt(playheadMs: Long): Boolean = playheadMs in timelineStartMs..timelineEndMs

    fun moveTimelineTo(newStartMs: Long, maxTimelineMs: Long): CanvasImageOverlayItem {
        val dur = durationMs
        val clampedStart = newStartMs.coerceIn(0L, (maxTimelineMs - dur).coerceAtLeast(0L))
        return copy(
            timelineStartMs = clampedStart,
            timelineEndMs = clampedStart + dur
        )
    }

    fun splitAtTimelineMs(cutTimelineMs: Long): Pair<CanvasImageOverlayItem, CanvasImageOverlayItem>? {
        val minSliceMs = 300L
        if (cutTimelineMs <= timelineStartMs + minSliceMs ||
            cutTimelineMs >= timelineEndMs - minSliceMs
        ) {
            return null
        }
        val left = copy(
            id = UUID.randomUUID().toString(),
            name = "$name (1)",
            timelineEndMs = cutTimelineMs
        )
        val right = copy(
            id = UUID.randomUUID().toString(),
            name = "$name (2)",
            timelineStartMs = cutTimelineMs
        )
        return left to right
    }
}

/**
 * Thông tin Video chính (9:16, <= 3 phút), hỗ trợ:
 * - Cắt ngắn đầu/cuối video (trimStartMs, trimEndMs).
 * - Tắt/bật âm thanh gốc của video MP4 (muteOriginalAudio).
 * - Tăng/giảm tốc độ (speedMultiplier).
 */
data class ShortVideoTrack(
    val videoUri: String,
    val displayName: String,
    val originalDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = originalDurationMs,
    val width: Int = 1080,
    val height: Int = 1920,
    val speedMultiplier: Float = 1.0f,
    val muteOriginalAudio: Boolean = false
) {
    val trimmedSourceDurationMs: Long
        get() = (trimEndMs.coerceAtMost(originalDurationMs) - trimStartMs.coerceAtLeast(0L))
            .coerceAtLeast(1_000L)

    val effectiveDurationMs: Long
        get() = (trimmedSourceDurationMs / speedMultiplier).toLong().coerceAtLeast(1_000L)

    val isTrimmed: Boolean
        get() = trimStartMs > 0L || trimEndMs < originalDurationMs

    val isPortrait9By16: Boolean
        get() = height > width
}
