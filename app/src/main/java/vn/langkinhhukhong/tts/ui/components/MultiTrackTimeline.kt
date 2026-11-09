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
import androidx.compose.foundation.layout.Spacer
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
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Thanh Timeline dạng Part chuẩn CapCut:
 * - Đã loại bỏ nút Cắt đầu / Cắt đuôi trên thanh Video.
 * - Tất cả đối tượng (Video 9:16, Voice+Sub, Tiêu đề, Sticker) đều cho phép chạm chọn đối tượng,
 *   di chuyển vạch đỏ Playhead đến vị trí mong muốn và bấm nút "Cắt" dùng chung ở thanh dưới để chia đoạn.
 */
@Composable
fun MultiTrackTimeline(
    videoTrack: ShortVideoTrack,
    voiceClips: List<VoiceSubtitleClip>,
    textOverlays: List<CanvasTextOverlayItem>,
    imageOverlays: List<CanvasImageOverlayItem>,
    totalDurationMs: Long,
    playheadMs: Long,
    selectedClipId: String?,
    pixelsPerSecond: Dp = 54.dp,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onToggleMuteVideo: () -> Unit,
    onDragVideoSegment: (segmentId: String, deltaMs: Long) -> Unit = { _, _ -> },
    onDragVoiceClip: (clipId: String, deltaMs: Long) -> Unit,
    onDragTextOverlay: (overlayId: String, deltaMs: Long) -> Unit,
    onDragImageOverlay: (overlayId: String, deltaMs: Long) -> Unit,
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
            .background(Color(0xFF10131C))
            .horizontalScroll(scrollState)
            .padding(vertical = 5.dp, horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier.width(timelineWidthDp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. THƯỚC ĐO THỜI GIAN (20dp)
            TimeRulerBar(
                totalDurationMs = totalDurationMs,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                onSeek = currentOnSeek
            )

            // 2. TRACK VIDEO 9:16 (36dp) - Không còn Cắt đầu/Cắt đuôi, chạm chọn đoạn Video và bấm nút "Cắt" bên dưới
            VideoTrackRow(
                videoTrack = videoTrack,
                selectedClipId = selectedClipId,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                onSelectClip = onSelectClip,
                onToggleMuteVideo = onToggleMuteVideo,
                onDragVideoSegment = onDragVideoSegment
            )

            // 3. TRACK VOICE AI + SUB PHỤ ĐỀ (54dp)
            CompoundVoiceSubtitleTrackRow(
                voiceClips = voiceClips,
                selectedClipId = selectedClipId,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                onSelectClip = onSelectClip,
                onDragVoiceClip = onDragVoiceClip
            )

            // 4. TRACK TIÊU ĐỀ & STICKER (34dp)
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
        }

        // VẠCH ĐỎ PLAYHEAD
        val playheadOffsetPx = (playheadMs * pxPerMs).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(x = playheadOffsetPx, y = 0) }
                .width(2.dp)
                .height(158.dp)
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
            .height(20.dp)
            .background(Color(0xFF181C28), RoundedCornerShape(4.dp))
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
                    start = Offset(x, size.height - if (isMajor) 9f else 4f),
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
                modifier = Modifier.offset(x = xDp + 2.dp, y = 1.dp)
            )
        }
    }
}

@Composable
private fun VideoTrackRow(
    videoTrack: ShortVideoTrack,
    selectedClipId: String?,
    pxPerMs: Float,
    timelineWidthDp: Dp,
    onSelectClip: (String?) -> Unit,
    onToggleMuteVideo: () -> Unit,
    onDragVideoSegment: (segmentId: String, deltaMs: Long) -> Unit
) {
    val density = LocalDensity.current
    val speed = videoTrack.speedMultiplier
    val segments = videoTrack.segments

    Box(
        modifier = Modifier
            .width(timelineWidthDp)
            .height(36.dp)
            .background(Color(0xFF151925), RoundedCornerShape(6.dp))
    ) {
        segments.forEachIndexed { index, seg ->
            val offsetX = (seg.timelineStartMs * pxPerMs).roundToInt()
            val segDurationMs = seg.durationAtSpeedMs(speed)
            val widthDp = with(density) { (segDurationMs * pxPerMs).toDp().coerceAtLeast(72.dp) }
            val isSelected = seg.id == selectedClipId
            val currentOnDrag by rememberUpdatedState(onDragVideoSegment)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = offsetX, y = 0) }
                    .width(widthDp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color(0xFF284268) else Color(0xFF213654))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFF4E7AC7),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectClip(seg.id) }
                    .pointerInput(seg.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { onSelectClip(seg.id) },
                            onHorizontalDrag = { change, dragAmountPx ->
                                change.consume()
                                val deltaMs = (dragAmountPx / pxPerMs).toLong()
                                if (deltaMs != 0L) {
                                    currentOnDrag(seg.id, deltaMs)
                                }
                            }
                        )
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (index == 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (videoTrack.muteOriginalAudio) Color(0xFFD32F2F) else Color(0xFF00C853))
                                .clickable { onToggleMuteVideo() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (videoTrack.muteOriginalAudio) "🔇 Tắt tiếng MP4" else "🔊 Tiếng MP4",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "🎬 ${seg.label} (${(segDurationMs / 100f).roundToInt() / 10f}s)",
                        color = if (isSelected) Color(0xFFFFF59D) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
            .height(54.dp)
            .background(Color(0xFF161A24), RoundedCornerShape(6.dp))
    ) {
        if (voiceClips.isEmpty()) {
            Text(
                text = "🎙️ Track Voice + Sub (Bấm 'Tạo Voice+Sub' bên dưới -> Chọn đối tượng & bấm 'Cắt' để chia đoạn)",
                color = Color(0xFF788296),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
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
                    .offset { IntOffset(x = offsetX, y = 2) }
                    .width(clipWidthDp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(primaryColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(6.dp)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(21.dp)
                        .background(Color(0xCC9A4800))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "💬 ${clip.visibleSubText}",
                        color = Color(0xFFFFF59D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 5.dp),
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
                                color = Color(0x55A7FFEB),
                                start = Offset(x, top),
                                end = Offset(x, top + barHeight),
                                strokeWidth = 2.5f
                            )
                        }
                    }

                    Text(
                        text = "🎙️ ${clip.voiceProfile.displayName} (${(clip.durationMs / 100f).roundToInt() / 10f}s)",
                        color = Color.White,
                        fontSize = 9.sp,
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
            .height(34.dp)
            .background(Color(0xFF181625), RoundedCornerShape(6.dp))
    ) {
        if (textOverlays.isEmpty() && imageOverlays.isEmpty()) {
            Text(
                text = "🔤🖼️ Track Tiêu đề & Sticker (Bấm 'Tiêu đề' hoặc 'Sticker' để thêm, chọn đối tượng & bấm 'Cắt')",
                color = Color(0xFF7E7896),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
            )
        }

        textOverlays.forEach { item ->
            val offsetX = (item.timelineStartMs * pxPerMs).roundToInt()
            val widthDp = with(density) { (item.durationMs * pxPerMs).toDp().coerceAtLeast(48.dp) }
            val isSelected = item.id == selectedClipId
            val currentOnDrag by rememberUpdatedState(onDragTextOverlay)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = offsetX, y = 2) }
                    .width(widthDp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF6A1B9A))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFFCE93D8),
                        shape = RoundedCornerShape(5.dp)
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
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "🔤 Tiêu đề: \"${item.text}\" (${(item.opacity * 100).toInt()}%)",
                    color = Color.White,
                    fontSize = 9.sp,
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
                    .offset { IntOffset(x = offsetX, y = 2) }
                    .width(widthDp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF00695C))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD600) else Color(0xFF80CBC4),
                        shape = RoundedCornerShape(5.dp)
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
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "🖼️ Sticker: ${img.name} (${(img.opacity * 100).toInt()}%)",
                    color = Color(0xFFE0F2F1),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
