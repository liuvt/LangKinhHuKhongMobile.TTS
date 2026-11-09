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

data class MonetizationConfig(
    val admobAppId: String = "ca-app-pub-3940256099942544~3347511713",
    val admobRewardedUnitId: String = "ca-app-pub-3940256099942544/5224354917",
    val admobInterstitialUnitId: String = "ca-app-pub-3940256099942544/1033173712",
    val admobBannerUnitId: String = "ca-app-pub-3940256099942544/6300978111",
    val remoteVoiceCatalogUrl: String = "https://raw.githubusercontent.com/liuvt/LangKinhHuKhongMobile.TTS/main/server_catalog/voice_catalog.json",
    val customSponsorTitle: String = "Lăng Kính Hư Không",
    val customSponsorCtaText: String = "",
    val customSponsorTargetUrl: String = "",
    val rewardUsesPerAdWatch: Int = 999
)

enum class VoiceStorageSource {
    BUILT_IN_ASSET,
    ONLINE_CLOUD_LIBRARY,
    CUSTOM_IMPORTED
}

enum class VoiceInstallState {
    INSTALLED,
    NOT_DOWNLOADED,
    DOWNLOADING
}

/**
 * Cấu hình Giọng đọc AI (Tích hợp 5 bộ giọng từ thư mục Voices/:
 * Minh Khang, Ngọc Huyền, Lạc Phi, Minh Quang, VPBank).
 * Tất cả đều mở khóa sẵn để chuyển Text thành Voice ngay lập tức.
 */
data class OfflineVoiceProfile(
    val id: String,
    val displayName: String,
    val regionTag: String,
    val styleTag: String,
    val description: String,
    val sizeMb: Int,
    val storageSource: VoiceStorageSource = VoiceStorageSource.BUILT_IN_ASSET,
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
    val ttsPitchMultiplier: Float = 1.0f,
    val colorHex: Long = 0xFF1E5128,
    val installState: VoiceInstallState = VoiceInstallState.INSTALLED,
    val downloadProgress: Float = 1.0f,
    val requiresAdToUnlock: Boolean = false,
    val remainingQuotaUses: Int = 999,
    val adRewardUsesPerView: Int = 999
) {
    val isReadyToRender: Boolean
        get() = true

    val needsAdTopUp: Boolean
        get() = false

    companion object {
        val BUILT_IN_MALE_MINH_KHANG = OfflineVoiceProfile(
            id = "vi_nam_minhkhang",
            displayName = "Nam - Minh Khang",
            regionTag = "Giọng Nam trầm",
            styleTag = "Mặc định",
            description = "Giọng Nam trầm ấm (minhkhang.onnx)",
            sizeMb = 61,
            storageSource = VoiceStorageSource.BUILT_IN_ASSET,
            modelPathOrAssetDir = "models/minhkhang",
            modelFileName = "minhkhang.onnx",
            onnxJsonFileName = "minhkhang.onnx.json",
            speakerId = 0,
            numSpeakers = 1,
            defaultPitchHz = 128f,
            ttsPitchMultiplier = 0.82f,
            colorHex = 0xFF1E5128
        )

        val BUILT_IN_FEMALE_NGOC_HUYEN = OfflineVoiceProfile(
            id = "vi_nu_ngochuyen",
            displayName = "Nữ - Ngọc Huyền",
            regionTag = "Giọng Nữ chuẩn",
            styleTag = "Mặc định",
            description = "Giọng Nữ tự nhiên trong trẻo (ngochuyennew(update).onnx)",
            sizeMb = 61,
            storageSource = VoiceStorageSource.BUILT_IN_ASSET,
            modelPathOrAssetDir = "models/ngochuyenupdate",
            modelFileName = "ngochuyennew(update).onnx",
            onnxJsonFileName = "ngochuyennew(update).onnx.json",
            speakerId = 0,
            numSpeakers = 1,
            defaultPitchHz = 220f,
            ttsPitchMultiplier = 1.12f,
            colorHex = 0xFF5C2A6A
        )

        val BUILT_IN_MALE_MINH = BUILT_IN_MALE_MINH_KHANG
        val BUILT_IN_FEMALE_LINH = BUILT_IN_FEMALE_NGOC_HUYEN

        val DEFAULT_ONLINE_LIBRARY_CATALOG = listOf(
            BUILT_IN_MALE_MINH_KHANG,
            BUILT_IN_FEMALE_NGOC_HUYEN,
            OfflineVoiceProfile(
                id = "vi_nam_lacphi",
                displayName = "Nam - Lạc Phi",
                regionTag = "Kể chuyện",
                styleTag = "Review",
                description = "Giọng Nam kể chuyện truyền cảm (lacphi.onnx)",
                sizeMb = 61,
                storageSource = VoiceStorageSource.BUILT_IN_ASSET,
                modelPathOrAssetDir = "models/lacphi",
                modelFileName = "lacphi.onnx",
                onnxJsonFileName = "lacphi.onnx.json",
                speakerId = 0,
                numSpeakers = 1,
                defaultPitchHz = 138f,
                ttsPitchMultiplier = 0.88f,
                colorHex = 0xFF1B4332
            ),
            OfflineVoiceProfile(
                id = "vi_nam_minhquang",
                displayName = "Nam - Minh Quang",
                regionTag = "Thuyết minh",
                styleTag = "Tin tức",
                description = "Giọng Nam thuyết minh rõ ràng (minhquang.onnx)",
                sizeMb = 61,
                storageSource = VoiceStorageSource.BUILT_IN_ASSET,
                modelPathOrAssetDir = "models/minhquang",
                modelFileName = "minhquang.onnx",
                onnxJsonFileName = "minhquang.onnx.json",
                speakerId = 0,
                numSpeakers = 1,
                defaultPitchHz = 148f,
                ttsPitchMultiplier = 0.94f,
                colorHex = 0xFF0D47A1
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp0",
                displayName = "VPBank - Giọng #0",
                regionTag = "CSA v3",
                styleTag = "Speaker #0",
                description = "Model VPBank Piper v3 (Speaker 0)",
                sizeMb = 74,
                storageSource = VoiceStorageSource.BUILT_IN_ASSET,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 0,
                numSpeakers = 5,
                defaultPitchHz = 195f,
                ttsPitchMultiplier = 1.0f,
                colorHex = 0xFF1565C0
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp1",
                displayName = "VPBank - Giọng #1",
                regionTag = "CSA v3",
                styleTag = "Speaker #1",
                description = "Model VPBank Piper v3 (Speaker 1)",
                sizeMb = 74,
                storageSource = VoiceStorageSource.BUILT_IN_ASSET,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 1,
                numSpeakers = 5,
                defaultPitchHz = 210f,
                ttsPitchMultiplier = 1.08f,
                colorHex = 0xFF283593
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp2",
                displayName = "VPBank - Giọng #2",
                regionTag = "CSA v3",
                styleTag = "Speaker #2",
                description = "Model VPBank Piper v3 (Speaker 2)",
                sizeMb = 74,
                storageSource = VoiceStorageSource.BUILT_IN_ASSET,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 2,
                numSpeakers = 5,
                defaultPitchHz = 175f,
                ttsPitchMultiplier = 0.90f,
                colorHex = 0xFF4527A0
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp3",
                displayName = "VPBank - Giọng #3",
                regionTag = "CSA v3",
                styleTag = "Speaker #3",
                description = "Model VPBank Piper v3 (Speaker 3)",
                sizeMb = 74,
                storageSource = VoiceStorageSource.BUILT_IN_ASSET,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 3,
                numSpeakers = 5,
                defaultPitchHz = 160f,
                ttsPitchMultiplier = 0.85f,
                colorHex = 0xFF6A1B4D
            ),
            OfflineVoiceProfile(
                id = "vi_vpbank_sp4",
                displayName = "VPBank - Giọng #4",
                regionTag = "CSA v3",
                styleTag = "Speaker #4",
                description = "Model VPBank Piper v3 (Speaker 4)",
                sizeMb = 74,
                storageSource = VoiceStorageSource.BUILT_IN_ASSET,
                modelPathOrAssetDir = "models/VPBank",
                modelFileName = "vi_VN-csa-voice-piper-v3-medium.onnx",
                onnxJsonFileName = "vi_VN-csa-voice-piper-v3-medium.onnx.json",
                speakerId = 4,
                numSpeakers = 5,
                defaultPitchHz = 228f,
                ttsPitchMultiplier = 1.18f,
                colorHex = 0xFF3E2723
            )
        )
    }
}

data class SubtitleWord(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long
)

data class TimelineSubtitleToken(
    val word: String,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val isHighlighted: Boolean
)

/**
 * COMPOUND CLIP: Gắn chặt file âm thanh Voice với Phụ đề (Subtitles).
 * Khi cắt đôi (Split) hoặc kéo thả (Move), cả âm thanh lẫn phụ đề đều đi theo nhau.
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
        val minSliceMs = 200L
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
            title = "$title (1)",
            fullText = leftText,
            trimEndMs = sourceSplitPointMs,
            words = leftWords
        )

        val rightClip = copy(
            id = UUID.randomUUID().toString(),
            title = "$title (2)",
            fullText = rightText,
            timelineStartMs = cutTimelineMs,
            trimStartMs = sourceSplitPointMs,
            words = rightWords
        )

        return leftClip to rightClip
    }
}

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
        val minSliceMs = 200L
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

data class SubtitleOverlayStyle(
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.82f,
    val fontSizeSp: Float = 15f,
    val opacity: Float = 1.0f,
    val blurRadiusDp: Float = 0f
)

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
    val timelineEndMs: Long = 6_000L
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

data class CanvasImageOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val imageUri: String,
    val normalizedX: Float = 0.78f,
    val normalizedY: Float = 0.16f,
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

data class VideoSegmentClip(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Video 9:16",
    val timelineStartMs: Long = 0L,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = 30_000L
) {
    val sourceDurationMs: Long
        get() = (sourceEndMs - sourceStartMs).coerceAtLeast(300L)

    fun durationAtSpeedMs(speedMultiplier: Float): Long {
        return (sourceDurationMs / speedMultiplier.coerceAtLeast(0.25f)).toLong().coerceAtLeast(300L)
    }

    fun timelineEndAtSpeedMs(speedMultiplier: Float): Long {
        return timelineStartMs + durationAtSpeedMs(speedMultiplier)
    }

    fun splitAtTimelineMs(
        cutTimelineMs: Long,
        speedMultiplier: Float
    ): Pair<VideoSegmentClip, VideoSegmentClip>? {
        val minSliceMs = 300L
        val endMs = timelineEndAtSpeedMs(speedMultiplier)
        if (cutTimelineMs <= timelineStartMs + minSliceMs ||
            cutTimelineMs >= endMs - minSliceMs
        ) {
            return null
        }
        val relTimelineMs = cutTimelineMs - timelineStartMs
        val sourceSplitMs = (sourceStartMs + (relTimelineMs * speedMultiplier).toLong())
            .coerceIn(sourceStartMs + minSliceMs, sourceEndMs - minSliceMs)

        val left = copy(
            id = UUID.randomUUID().toString(),
            label = "$label (1)",
            sourceEndMs = sourceSplitMs
        )
        val right = copy(
            id = UUID.randomUUID().toString(),
            label = "$label (2)",
            timelineStartMs = cutTimelineMs,
            sourceStartMs = sourceSplitMs
        )
        return left to right
    }
}

data class ShortVideoTrack(
    val videoUri: String,
    val displayName: String,
    val originalDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = originalDurationMs,
    val width: Int = 1080,
    val height: Int = 1920,
    val speedMultiplier: Float = 1.0f,
    val muteOriginalAudio: Boolean = false,
    val segments: List<VideoSegmentClip> = listOf(
        VideoSegmentClip(
            label = displayName,
            timelineStartMs = 0L,
            sourceStartMs = trimStartMs,
            sourceEndMs = trimEndMs
        )
    )
) {
    val trimmedSourceDurationMs: Long
        get() = if (segments.isNotEmpty()) {
            segments.sumOf { it.sourceDurationMs }.coerceAtLeast(1_000L)
        } else {
            (trimEndMs.coerceAtMost(originalDurationMs) - trimStartMs.coerceAtLeast(0L))
                .coerceAtLeast(1_000L)
        }

    val effectiveDurationMs: Long
        get() = if (segments.isNotEmpty()) {
            segments.maxOf { it.timelineEndAtSpeedMs(speedMultiplier) }.coerceAtLeast(1_000L)
        } else {
            (trimmedSourceDurationMs / speedMultiplier).toLong().coerceAtLeast(1_000L)
        }

    val isTrimmed: Boolean
        get() = segments.size > 1 || trimStartMs > 0L || trimEndMs < originalDurationMs

    val isPortrait9By16: Boolean
        get() = height > width
}
