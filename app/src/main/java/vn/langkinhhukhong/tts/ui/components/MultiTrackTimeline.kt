package vn.langkinhhukhong.tts.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.langkinhhukhong.tts.model.CanvasImageOverlayItem
import vn.langkinhhukhong.tts.model.CanvasTextOverlayItem
import vn.langkinhhukhong.tts.model.SecondaryMp3Clip
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Thanh Timeline Đa Tầng kiểu CapCut dạng Part:
 * - Tầng 1: Thước đo thời gian (chạm/kéo để tua Playhead).
 * - Tầng 2: Track Video chính 9:16 (Nút Bật/Tắt tiếng gốc MP4 + Cắt ngắn đầu/cuối Video + Tốc độ Speed).
 * - Tầng 3: Track Kép [MP3 Giọng đọc AI + Phụ đề gắn liền] (Chia nhiều đoạn part, kéo thả khớp với Video tùy ý).
 * - Tầng 4: Track Lớp phủ Chữ (Text) & Icon/Logo PNG (Kéo thả thời gian, hiển thị thông số làm mờ).
 * - Tầng 5: Các Track MP3 phụ (Nhạc nền BGM, SFX...).
 */
@Composable
fun MultiTrackTimeline(
    videoTrack: ShortVideoTrack,
    voiceClips: List<VoiceSubtitleClip>,
    textOverlays: List<CanvasTextOverlayItem>,
    imageOverlays: List<CanvasImageOverlayItem>,
    secondaryMp3Clips: List<SecondaryMp3Clip>,
    totalDurationMs: Long,
    playheadMs: Long,
    selectedClipId: String?,
    pixelsPerSecond: Dp = 56.dp,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onToggleMuteVideo: () -> Unit,
    onTrimVideoStartAtPlayhead: () -> Unit,
    onTrimVideoEndAtPlayhead: () -> Unit,
    onResetVideoTrim: () -> Unit,
    onDragVoiceClip: (clipId: String, deltaMs: Long) -> Unit,
    onDragTextOverlay: (overlayId: String, deltaMs: Long) -> Unit,
    onDragImageOverlay: (overlayId: String, deltaMs: Long) -> Unit,
    onDragSecondaryMp3: (clipId: String, deltaMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pxPerSec = with(density) { pixelsPerSecond.toPx() }
    val pxPerMs = pxPerSec / 1000f

    val totalSeconds = (totalDurationMs / 1000f).coerceAtLeast(10f)
    val timelineWidthDp = pixelsPerSecond * (totalSeconds + 4f)
    val scrollState = rememberScrollState()

    val currentOnSeek by rememberUpdatedState(onSeek)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF11131A))
            .horizontalScroll(scrollState)
            .padding(vertical = 6.dp, horizontal = 14.dp)
    ) {
        Column(
            modifier = Modifier.width(timelineWidthDp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. THƯỚC ĐO THỜI GIAN (TIME RULER)
            TimeRulerBar(
                totalDurationMs = totalDurationMs,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                onSeek = currentOnSeek
            )

            // 2. TRACK VIDEO 9:16 CHÍNH (BẬT/TẮT TIẾNG MP4 + CẮT NGẮN VIDEO + TỐC ĐỘ)
            VideoTrackRow(
                videoTrack = videoTrack,
                pxPerMs = pxPerMs,
                onToggleMuteVideo = onToggleMuteVideo,
                onTrimStart = onTrimVideoStartAtPlayhead,
                onTrimEnd = onTrimVideoEndAtPlayhead,
                onResetTrim = onResetVideoTrim
            )

            // 3. TRACK KÉP: MP3 GIỌNG ĐỌC AI + PHỤ ĐỀ TỰ ĐỘNG KHỚP (CHIA NHIỀU PART KÉO THẢ TÙY Ý)
            CompoundVoiceSubtitleTrackRow(
                voiceClips = voiceClips,
                selectedClipId = selectedClipId,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                onSelectClip = onSelectClip,
                onDragVoiceClip = onDragVoiceClip
            )

            // 4. TRACK CHỮ (TEXT) & ICON / LOGO PNG (KÈM LÀM MỜ & KÉO THẢ)
            VisualOverlaysTrackRow(
                textOverlays = textOverlays,
                imageOverlays = imageOverlays,
                selectedClipId = selectedClipId,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                onSelectClip = onSelectClip,
                onDragTextOverlay = onDragTextOverlay,
                onDragImageOverlay = onDragImageOverlay
            )

            // 5. TRACK MP3 PHỤ (BGM / SFX)
            SecondaryMp3TrackSection(
                secondaryClips = secondaryMp3Clips,
                selectedClipId = selectedClipId,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                onSelectClip = onSelectClip,
                onDragSecondaryMp3 = onDragSecondaryMp3
            )
        }

        // VẠCH CON TRỎ PLAYHEAD ĐỎ/TRẮNG (Điểm cắt Split & Xem thử)
        val playheadOffsetPx = (playheadMs * pxPerMs).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(x = playheadOffsetPx, y = 0) }
                .width(2.dp)
                .height(265.dp)
                .background(Color(0xFFFF3B5C))
        )
    }
}

@Composable
private fun TimeRulerBar(
    totalDurationMs: Long,
    pxPerMs: Float,
    timelineWidthDp: Dp,
    onSeek: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .width(timelineWidthDp)
            .height(24.dp)
            .background(Color(0xFF191C26), RoundedCornerShape(4.dp))
            .pointerInput(totalDurationMs, pxPerMs) {
                detectTapGestures { offset ->
                    val targetMs = (offset.x / pxPerMs).toLong().coerceIn(0L, totalDurationMs)
                    onSeek(targetMs)
                }
            }
            .pointerInput(totalDurationMs, pxPerMs) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val targetMs = (change.position.x / pxPerMs).toLong().coerceIn(0L, totalDurationMs)
                    onSeek(targetMs)
                }
            }
    ) {
        val totalSec = (totalDurationMs / 1000L).toInt()
        Canvas(modifier = Modifier.matchParentSize()) {
            for (sec in 0..totalSec) {
                val x = sec * 1000f * pxPerMs
                val isMajor = sec % 5 == 0
                drawLine(
                    color = if (isMajor) Color(0xFF9EA6B8) else Color(0xFF454B5E),
                    start = Offset(x, size.height - if (isMajor) 11f else 5f),
                    end = Offset(x, size.height),
                    strokeWidth = if (isMajor) 2f else 1f
                )
            }
        }
        val density = LocalDensity.current
        for (sec in 0..totalSec step 5) {
            val xDp = with(density) { (sec * 1000f * pxPerMs).toDp() }
            Text(
                text = String.format("%02d:%02d", sec / 60, sec % 60),
                color = Color(0xFFB5BDCE),
                fontSize = 9.sp,
                modifier = Modifier.offset(x = xDp + 2.dp, y = 2.dp)
            )
        }
    }
}

@Composable
private fun VideoTrackRow(
    videoTrack: ShortVideoTrack,
    pxPerMs: Float,
    onToggleMuteVideo: () -> Unit,
    onTrimStart: () -> Unit,
    onTrimEnd: () -> Unit,
    onResetTrim: () -> Unit
) {
    val density = LocalDensity.current
    val widthDp = with(density) { (videoTrack.effectiveDurationMs * pxPerMs).toDp() }

    Box(
        modifier = Modifier
            .width(widthDp.coerceAtLeast(280.dp))
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF23395B))
            .border(1.dp, Color(0xFF4E7AC7), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Nút Bật/Tắt tiếng gốc của file Video MP4
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (videoTrack.muteOriginalAudio) Color(0xFFD32F2F) else Color(0xFF00C853))
                    .clickable { onToggleMuteVideo() }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (videoTrack.muteOriginalAudio) "🔇 Đã tắt tiếng MP4" else "🔊 Tiếng MP4: Bật",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Nút Cắt bỏ phần đầu Video tại vạch Playhead
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF152238))
                    .border(1.dp, Color(0xFF64B5F6), RoundedCornerShape(5.dp))
                    .clickable { onTrimStart() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "✂ Cắt đầu Video",
                    color = Color(0xFF80D8FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Nút Cắt bỏ phần cuối Video tại vạch Playhead
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF152238))
                    .border(1.dp, Color(0xFF64B5F6), RoundedCornerShape(5.dp))
                    .clickable { onTrimEnd() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Cắt đuôi Video ✂",
                    color = Color(0xFF80D8FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (videoTrack.isTrimmed) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFFFB300))
                        .clickable { onResetTrim() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "↺ Khôi phục gốc",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "🎬 ${videoTrack.displayName} (${videoTrack.effectiveDurationMs / 1000}s • ${videoTrack.speedMultiplier}x)",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompoundVoiceSubtitleTrackRow(
    voiceClips: List<VoiceSubtitleClip>,
    selectedClipId: String?,
    pxPerMs: Float,
    timelineWidthDp: Dp,
    onSelectClip: (String?) -> Unit,
    onDragVoiceClip: (clipId: String, deltaMs: Long) -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .width(timelineWidthDp)
            .height(68.dp)
            .background(Color(0xFF171A24), RoundedCornerShape(8.dp))
    ) {
        if (voiceClips.isEmpty()) {
            Text(
                text = "🎙️ Track Voice AI + Phụ đề (Chuyển Text thành Voice, cắt chia nhiều đoạn & kéo thả khớp với Video tùy ý)",
                color = Color(0xFF6C7486),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        voiceClips.forEach { clip ->
            val offsetX = (clip.timelineStartMs * pxPerMs).roundToInt()
            val clipWidthDp = with(density) { (clip.durationMs * pxPerMs).toDp().coerceAtLeast(48.dp) }
            val isSelected = clip.id == selectedClipId
            val currentOnDrag by rememberUpdatedState(onDragVoiceClip)

            val primaryColor = Color(clip.voiceProfile.colorHex)
            val borderColor = if (isSelected) Color(0xFFFFD600) else Color(0xFF4E9F3D)

            Column(
                modifier = Modifier
                    .offset { IntOffset(x = offsetX, y = 3) }
                    .width(clipWidthDp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectClip(clip.id) }
                    .pointerInput(clip.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { onSelectClip(clip.id) },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val deltaMs = (dragAmountPx / pxPerMs).toLong()
                                if (deltaMs != 0L) {
                                    currentOnDrag(clip.id, deltaMs)
                                }
                            }
                        )
                    }
            ) {
                // NỬA TRÊN: Dải Phụ đề (Subtitle Bar) bám chặt theo đoạn Voice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(25.dp)
                        .background(Color(0xCC9A4800))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "💬 Sub (${clip.words.size} từ): ${clip.visibleSubText}",
                        color = Color(0xFFFFF59D),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // NỬA DƯỚI: Dải MP3 Voice AI + Biểu đồ sóng âm
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val barCount = (size.width / 8f).toInt().coerceAtLeast(4)
                        for (i in 0 until barCount) {
                            val x = i * 8f + 4f
                            val waveRatio = (sin((i + clip.trimStartMs / 100f) * 0.8) * 0.4 + 0.5).toFloat()
                            val barHeight = size.height * waveRatio.coerceIn(0.2f, 0.85f)
                            val top = (size.height - barHeight) / 2f
                            drawLine(
                                color = Color(0x66A7FFEB),
                                start = Offset(x, top),
                                end = Offset(x, top + barHeight),
                                strokeWidth = 3f
                            )
                        }
                    }

                    Text(
                        text = "🎙️ ${clip.voiceProfile.displayName.substringBefore("(").trim()} • ${(clip.durationMs / 100f).roundToInt() / 10f}s",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualOverlaysTrackRow(
    textOverlays: List<CanvasTextOverlayItem>,
    imageOverlays: List<CanvasImageOverlayItem>,
    selectedClipId: String?,
    pxPerMs: Float,
    timelineWidthDp: Dp,
    onSelectClip: (String?) -> Unit,
    onDragTextOverlay: (overlayId: String, deltaMs: Long) -> Unit,
    onDragImageOverlay: (overlayId: String, deltaMs: Long) -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .width(timelineWidthDp)
            .height(44.dp)
            .background(Color(0xFF181625), RoundedCornerShape(8.dp))
    ) {
        if (textOverlays.isEmpty() && imageOverlays.isEmpty()) {
            Text(
                text = "🔤🖼️ Track Chữ (Text) & Icon/Logo PNG (Cho phép làm mờ & kéo thả vị trí bất kỳ trên khu View)",
                color = Color(0xFF7E7896),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        textOverlays.forEach { item ->
            val offsetX = (item.timelineStartMs * pxPerMs).roundToInt()
            val widthDp = with(density) { (item.durationMs * pxPerMs).toDp().coerceAtLeast(48.dp) }
            val isSelected = item.id == selectedClipId
            val currentOnDrag by rememberUpdatedState(onDragTextOverlay)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = offsetX, y = 4) }
                    .width(widthDp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF6A1B9A))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFFCE93D8),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectClip(item.id) }
                    .pointerInput(item.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { onSelectClip(item.id) },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val deltaMs = (dragAmountPx / pxPerMs).toLong()
                                if (deltaMs != 0L) {
                                    currentOnDrag(item.id, deltaMs)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val blurInfo = if (item.blurRadiusDp > 0.5f) " • Nhòe ${item.blurRadiusDp.toInt()}dp" else ""
                Text(
                    text = "🔤 \"${item.text}\" (Rõ ${(item.opacity * 100).toInt()}%$blurInfo)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        imageOverlays.forEach { img ->
            val offsetX = (img.timelineStartMs * pxPerMs).roundToInt()
            val widthDp = with(density) { (img.durationMs * pxPerMs).toDp().coerceAtLeast(52.dp) }
            val isSelected = img.id == selectedClipId
            val currentOnDrag by rememberUpdatedState(onDragImageOverlay)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = offsetX, y = 6) }
                    .width(widthDp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00695C))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFF80CBC4),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectClip(img.id) }
                    .pointerInput(img.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { onSelectClip(img.id) },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val deltaMs = (dragAmountPx / pxPerMs).toLong()
                                if (deltaMs != 0L) {
                                    currentOnDrag(img.id, deltaMs)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val blurInfo = if (img.blurRadiusDp > 0.5f) " • Nhòe ${img.blurRadiusDp.toInt()}dp" else ""
                Text(
                    text = "🖼️ ${img.name} (Rõ ${(img.opacity * 100).toInt()}%$blurInfo)",
                    color = Color(0xFFE0F2F1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SecondaryMp3TrackSection(
    secondaryClips: List<SecondaryMp3Clip>,
    selectedClipId: String?,
    pxPerMs: Float,
    timelineWidthDp: Dp,
    onSelectClip: (String?) -> Unit,
    onDragSecondaryMp3: (clipId: String, deltaMs: Long) -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .width(timelineWidthDp)
            .height(44.dp)
            .background(Color(0xFF151821), RoundedCornerShape(8.dp))
    ) {
        if (secondaryClips.isEmpty()) {
            Text(
                text = "🎵 Track MP3 Phụ (Nhạc nền / SFX - Hỗ trợ import & cắt nhiều đoạn MP3)",
                color = Color(0xFF6C7486),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        secondaryClips.forEach { bgm ->
            val offsetX = (bgm.timelineStartMs * pxPerMs).roundToInt()
            val clipWidthDp = with(density) { (bgm.durationMs * pxPerMs).toDp().coerceAtLeast(44.dp) }
            val isSelected = bgm.id == selectedClipId
            val currentOnDrag by rememberUpdatedState(onDragSecondaryMp3)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = offsetX, y = 4) }
                    .width(clipWidthDp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF004D61))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFF00B8D4),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectClip(bgm.id) }
                    .pointerInput(bgm.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { onSelectClip(bgm.id) },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val deltaMs = (dragAmountPx / pxPerMs).toLong()
                                if (deltaMs != 0L) {
                                    currentOnDrag(bgm.id, deltaMs)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "🎵 ${bgm.fileName} (${(bgm.durationMs / 100f).roundToInt() / 10f}s)",
                    color = Color(0xFFE0F7FA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
