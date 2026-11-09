package vn.langkinhhukhong.tts.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Multi-track Timeline kiểu CapCut rút gọn & trực quan:
 * - Đã loại bỏ nút "Cắt đầu" / "Cắt đuôi" trên thanh Video.
 * - Mọi đối tượng (Video MP4, Voice+Sub, Tiêu đề, Sticker) đều có thể chạm để chọn (viền vàng),
 *   kéo thả vị trí tùy ý, đưa Playhead vào vị trí bất kỳ và bấm nút "Cắt" dùng chung ở dưới để chia đoạn.
 */
@Composable
fun MultiTrackTimeline(
    totalTimelineMs: Long,
    playheadMs: Long,
    videoTrack: ShortVideoTrack,
    voiceClips: List<VoiceSubtitleClip>,
    secondaryMp3Clips: List<SecondaryMp3Clip>,
    textOverlays: List<CanvasTextOverlayItem>,
    imageOverlays: List<CanvasImageOverlayItem>,
    selectedClipId: String?,
    pixelsPerSecond: Dp = 68.dp,
    onSeekPlayhead: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onDragVideoSegment: (segmentId: String, deltaMs: Long) -> Unit,
    onDragVoiceClip: (clipId: String, deltaMs: Long) -> Unit,
    onDragSecondaryMp3Clip: (clipId: String, deltaMs: Long) -> Unit,
    onDragTextOverlayTime: (overlayId: String, deltaMs: Long) -> Unit,
    onDragImageOverlayTime: (overlayId: String, deltaMs: Long) -> Unit,
    onToggleMuteVideoAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val pxPerSec = with(density) { pixelsPerSecond.toPx() }
    val pxPerMs = pxPerSec / 1000f

    val safeTotalMs = max(totalTimelineMs, 15_000L)
    val timelineWidthDp = with(density) { ((safeTotalMs * pxPerMs) + 220f).toDp() }
    val currentOnSeekPlayhead by rememberUpdatedState(onSeekPlayhead)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF101218))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .width(timelineWidthDp)
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Thước thời gian (chạm hoặc kéo để di chuyển kim đỏ Playhead tới vị trí muốn Cắt)
                    TimeRulerHeader(
                        totalMs = safeTotalMs,
                        pxPerMs = pxPerMs,
                        onSeek = { ms -> currentOnSeekPlayhead(ms) }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. Track Video MP4 (9:16): Chọn vào đoạn Video và bấm nút "Cắt" ở thanh dưới để chia đoạn
                    VideoTrackRow(
                        videoTrack = videoTrack,
                        selectedClipId = selectedClipId,
                        pxPerMs = pxPerMs,
                        onSelectClip = onSelectClip,
                        onDragVideoSegment = onDragVideoSegment,
                        onToggleMute = onToggleMuteVideoAudio
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // 3. Track Voice AI + Phụ đề đồng bộ: Chọn vào đoạn Voice và bấm "Cắt" hoặc kéo thả vị trí
                    VoiceAndSubtitleTrackRow(
                        voiceClips = voiceClips,
                        selectedClipId = selectedClipId,
                        pxPerMs = pxPerMs,
                        onSelectClip = onSelectClip,
                        onDragClip = onDragVoiceClip
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // 4. Track Tiêu đề & Sticker: Chọn vào đối tượng và bấm "Cắt" hoặc kéo thả
                    VisualOverlaysTrackRow(
                        textOverlays = textOverlays,
                        imageOverlays = imageOverlays,
                        selectedClipId = selectedClipId,
                        pxPerMs = pxPerMs,
                        onSelectClip = onSelectClip,
                        onDragTextOverlay = onDragTextOverlayTime,
                        onDragImageOverlay = onDragImageOverlayTime
                    )
                }

                // Đường kim đỏ Playhead
                val playheadX = (playheadMs * pxPerMs).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(playheadX, 0) }
                        .width(2.dp)
                        .height(176.dp)
                        .background(Color(0xFFFF3B30))
                )
            }
        }
    }
}

@Composable
private fun TimeRulerHeader(
    totalMs: Long,
    pxPerMs: Float,
    onSeek: (Long) -> Unit
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val totalSec = (totalMs / 1000L).toInt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0xFF171A23))
            .pointerInput(totalMs, pxPerMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val ms = (offset.x / pxPerMs).toLong().coerceIn(0L, totalMs)
                        currentOnSeek(ms)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val ms = (change.position.x / pxPerMs).toLong().coerceIn(0L, totalMs)
                        currentOnSeek(ms)
                    }
                )
            }
            .clickable { }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            for (sec in 0..totalSec) {
                val x = sec * 1000f * pxPerMs
                val isMajor = sec % 5 == 0
                drawLine(
                    color = if (isMajor) Color(0xFF9AA4B8) else Color(0xFF3E4452),
                    start = Offset(x, if (isMajor) 4f else 12f),
                    end = Offset(x, 24f),
                    strokeWidth = if (isMajor) 2f else 1f
                )
            }
        }
        for (sec in 0..totalSec step 5) {
            val offsetX = (sec * 1000f * pxPerMs).roundToInt()
            Text(
                text = "%02d:%02d".format(sec / 60, sec % 60),
                color = Color(0xFF9AA4B8),
                fontSize = 10.sp,
                modifier = Modifier
                    .offset { IntOffset(offsetX + 4, 2) }
            )
        }
    }
}

@Composable
private fun VideoTrackRow(
    videoTrack: ShortVideoTrack,
    selectedClipId: String?,
    pxPerMs: Float,
    onSelectClip: (String?) -> Unit,
    onDragVideoSegment: (String, Long) -> Unit,
    onToggleMute: () -> Unit
) {
    val segments = videoTrack.segments
    val speed = videoTrack.speedMultiplier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF141822))
    ) {
        if (segments.isEmpty()) {
            val fallbackWidthPx = max(160f, videoTrack.trimmedDurationMs * pxPerMs).roundToInt()
            Box(
                modifier = Modifier
                    .width(with(LocalDensity.current) { fallbackWidthPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF23324A))
                    .border(1.dp, Color(0xFF3D5A80), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "🎬 ${videoTrack.displayName} (Bấm 'Chọn MP4 9:16' để nạp video)",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            segments.forEachIndexed { index, seg ->
                val isSelected = seg.id == selectedClipId
                val startPx = (seg.timelineStartMs * pxPerMs).roundToInt()
                val segDurationMs = seg.durationAtSpeedMs(speed)
                val widthPx = max(72f, segDurationMs * pxPerMs).roundToInt()
                val currentOnDrag by rememberUpdatedState(onDragVideoSegment)
                var dragResidualMs by remember(seg.id) { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(startPx, 0) }
                        .width(with(LocalDensity.current) { widthPx.toDp() })
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF2C456B) else Color(0xFF23324A))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFFFFD600) else Color(0xFF3D5A80),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectClip(seg.id) }
                        .pointerInput(seg.id, pxPerMs) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragResidualMs = 0f
                                    onSelectClip(seg.id)
                                },
                                onHorizontalDrag = { change, dragAmountPx ->
                                    change.consume()
                                    val totalDeltaMs = (dragAmountPx / pxPerMs) + dragResidualMs
                                    val stepMs = totalDeltaMs.toLong()
                                    dragResidualMs = totalDeltaMs - stepMs
                                    if (stepMs != 0L) {
                                        currentOnDrag(seg.id, stepMs)
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index == 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (videoTrack.isOriginalAudioMuted) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                                    )
                                    .clickable { onToggleMute() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (videoTrack.isOriginalAudioMuted) "🔇 Tắt tiếng MP4" else "🔊 Bật tiếng MP4",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = "🎬 ${seg.label} (${formatSec(segDurationMs)})",
                            color = if (isSelected) Color(0xFFFFF59D) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceAndSubtitleTrackRow(
    voiceClips: List<VoiceSubtitleClip>,
    selectedClipId: String?,
    pxPerMs: Float,
    onSelectClip: (String?) -> Unit,
    onDragClip: (String, Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xFF151922))
    ) {
        if (voiceClips.isEmpty()) {
            Text(
                text = "🎙️ Track Voice AI + Phụ đề (Bấm 'Tạo Voice+Sub' bên dưới để thêm, chọn đối tượng và bấm 'Cắt' để chia đoạn)",
                color = Color(0xFF697386),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        voiceClips.forEach { clip ->
            val isSelected = clip.id == selectedClipId
            val startPx = (clip.timelineStartMs * pxPerMs).roundToInt()
            val widthPx = max(48f, clip.trimmedDurationMs * pxPerMs).roundToInt()
            val currentOnDragClip by rememberUpdatedState(onDragClip)
            var dragResidualMs by remember(clip.id) { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx, 0) }
                    .width(with(LocalDensity.current) { widthPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) Color(0xFF2B4C3F) else Color(0xFF1E3A2F))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFF4CAF50),
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable { onSelectClip(clip.id) }
                    .pointerInput(clip.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragResidualMs = 0f
                                onSelectClip(clip.id)
                            },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val totalDeltaMs = (dragAmountPx / pxPerMs) + dragResidualMs
                                val stepMs = totalDeltaMs.toLong()
                                dragResidualMs = totalDeltaMs - stepMs
                                if (stepMs != 0L) {
                                    currentOnDragClip(clip.id, stepMs)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "💬 ${clip.subtitleText}",
                        color = Color(0xFFFFF59D),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3E2723), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🎙️ ${clip.voiceDisplayName} (${formatSec(clip.trimmedDurationMs)})",
                            color = Color(0xFFA5D6A7),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
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
    onSelectClip: (String?) -> Unit,
    onDragTextOverlay: (String, Long) -> Unit,
    onDragImageOverlay: (String, Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF141720))
    ) {
        if (textOverlays.isEmpty() && imageOverlays.isEmpty()) {
            Text(
                text = "🔤🖼️ Track Tiêu đề & Sticker (Bấm 'Tiêu đề' hoặc 'Sticker' để thêm, chọn đối tượng và bấm 'Cắt' để chia đoạn)",
                color = Color(0xFF697386),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        textOverlays.forEach { item ->
            val isSelected = item.id == selectedClipId
            val startPx = (item.startTimeMs * pxPerMs).roundToInt()
            val durationMs = max(500L, item.endTimeMs - item.startTimeMs)
            val widthPx = max(44f, durationMs * pxPerMs).roundToInt()
            val currentOnDragText by rememberUpdatedState(onDragTextOverlay)
            var dragResidualMs by remember(item.id) { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx, 2) }
                    .width(with(LocalDensity.current) { widthPx.toDp() })
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF4A2545))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFFCE93D8),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectClip(item.id) }
                    .pointerInput(item.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragResidualMs = 0f
                                onSelectClip(item.id)
                            },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val totalDeltaMs = (dragAmountPx / pxPerMs) + dragResidualMs
                                val stepMs = totalDeltaMs.toLong()
                                dragResidualMs = totalDeltaMs - stepMs
                                if (stepMs != 0L) {
                                    currentOnDragText(item.id, stepMs)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "🔤 Tiêu đề: ${item.text} (${formatSec(durationMs)})",
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        imageOverlays.forEach { item ->
            val isSelected = item.id == selectedClipId
            val startPx = (item.startTimeMs * pxPerMs).roundToInt()
            val durationMs = max(500L, item.endTimeMs - item.startTimeMs)
            val widthPx = max(44f, durationMs * pxPerMs).roundToInt()
            val currentOnDragImage by rememberUpdatedState(onDragImageOverlay)
            var dragResidualMs by remember(item.id) { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx, 2) }
                    .width(with(LocalDensity.current) { widthPx.toDp() })
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF3E2723))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFFFFB74D),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectClip(item.id) }
                    .pointerInput(item.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragResidualMs = 0f
                                onSelectClip(item.id)
                            },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val totalDeltaMs = (dragAmountPx / pxPerMs) + dragResidualMs
                                val stepMs = totalDeltaMs.toLong()
                                dragResidualMs = totalDeltaMs - stepMs
                                if (stepMs != 0L) {
                                    currentOnDragImage(item.id, stepMs)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "🖼️ Sticker: ${item.label} (${formatSec(durationMs)})",
                    color = Color(0xFFFFE082),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatSec(ms: Long): String {
    val sec = ms / 1000.0
    return "%.1fs".format(sec)
}
