package vn.langkinhhukhong.tts.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay
import vn.langkinhhukhong.tts.ads.AdMonetizationManager
import vn.langkinhhukhong.tts.model.CanvasImageOverlayItem
import vn.langkinhhukhong.tts.model.CanvasTextOverlayItem
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.SubtitleCanvasConfig
import vn.langkinhhukhong.tts.model.VoiceModelCatalogItem
import vn.langkinhhukhong.tts.model.VoiceSubtitleClip
import vn.langkinhhukhong.tts.ui.components.MultiTrackTimeline
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapCutTtsEditorScreen(viewModel: EditorViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var showTtsSheet by remember { mutableStateOf(false) }
    var showAddTextSheet by remember { mutableStateOf(false) }
    var showAddStickerSheet by remember { mutableStateOf(false) }

    // Bộ chọn file Video MP4 (9:16, <= 3 phút)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "Video_9_16.mp4"
            viewModel.importShortVideoMp4(uri, name)
        }
    }

    // Bộ chọn ảnh Sticker PNG/JPG
    val stickerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "Sticker.png"
            viewModel.addLogoPngOverlay(uri, name)
        }
    }

    // Vòng lặp Playhead & Đồng bộ phát các đoạn Voice AI trên Timeline
    TimelinePlaybackSynchronizer(
        uiState = uiState,
        onSeekPlayhead = { viewModel.seekPlayhead(it) },
        onStopPlaying = { viewModel.setPlaying(false) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. THANH TIÊU ĐỀ TRÊN CÙNG: LKHK.TTS + Chọn MP4 + Xuất File (MP3/MP4)
        TopEditorHeader(
            uiState = uiState,
            onPickVideoMp4 = { videoPickerLauncher.launch("video/mp4") },
            onToggleMuteVideo = { viewModel.toggleVideoOriginalAudioMute() },
            onClickExport = { viewModel.requestExportFormatSelection() }
        )

        // 2. BANNER QUẢNG CÁO TREO TỰ ĐỘNG (AdMob Banner + Sponsor Banner)
        AutoBannerAdStrip()

        // 3. KHU VIEW 9:16 (CHO PHÉP KÉO THẢ VỊ TRÍ BẤT KỲ CHO PHỤ ĐỀ, TIÊU ĐỀ & STICKER)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Vertical9By16PreviewStage(
                videoTrack = uiState.videoTrack,
                playheadMs = uiState.playheadMs,
                isPlaying = uiState.isPlaying,
                activeSubtitleClip = uiState.activeSubtitleAtPlayhead,
                subtitleConfig = uiState.subtitleConfig,
                textOverlays = uiState.textOverlays,
                imageOverlays = uiState.imageOverlays,
                selectedClipId = uiState.selectedClipId,
                onDragSubtitle = { x, y -> viewModel.updateSubtitlePosition(x, y) },
                onDragTextOverlay = { id, x, y -> viewModel.updateTextOverlayPosition(id, x, y) },
                onDragImageOverlay = { id, x, y -> viewModel.updateImageOverlayPosition(id, x, y) }
            )
        }

        // 4. THANH CHỈNH SIZE SUB / SIZE TIÊU ĐỀ / ĐỘ MỜ CỦA ĐỐI TƯỢNG ĐANG CHỌN
        ActiveOverlayQuickInspector(
            uiState = uiState,
            onUpdateSubtitleFontSize = { sizeSp ->
                viewModel.updateSubtitleFontSize(sizeSp)
            },
            onUpdateSubtitleBlurOpacity = { opacity, blur ->
                viewModel.updateSubtitleBlurAndOpacity(opacity, blur)
            },
            onUpdateTextFontSize = { id, sizeSp ->
                viewModel.updateTextOverlayFontSize(id, sizeSp)
            },
            onUpdateTextBlurOpacity = { id, opacity, blur ->
                viewModel.updateTextOverlayBlurAndOpacity(id, opacity, blur)
            },
            onUpdateImageBlurOpacityScale = { id, opacity, blur, scale ->
                viewModel.updateImageOverlayBlurOpacityScale(id, opacity, blur, scale)
            }
        )

        // 5. THANH PHÁT / TẠM DỪNG & THỜI GIAN (ĐÃ BỎ DÒNG CHỮ Rườm Rà BÊN PHẢI)
        PlaybackTransportBar(
            playheadMs = uiState.playheadMs,
            totalDurationMs = uiState.totalTimelineDurationMs,
            isPlaying = uiState.isPlaying,
            isBusy = uiState.isSynthesizingTts || uiState.isExportingVideo,
            statusMessage = uiState.statusMessage,
            onTogglePlay = { viewModel.setPlaying(!uiState.isPlaying) }
        )

        // 6. MULTI-TRACK TIMELINE (CHỌN ĐỐI TƯỢNG VÀ BẤM NÚT "CẮT" BÊN DƯỚI)
        MultiTrackTimeline(
            totalTimelineMs = uiState.totalTimelineDurationMs,
            playheadMs = uiState.playheadMs,
            videoTrack = uiState.videoTrack,
            voiceClips = uiState.voiceClips,
            secondaryMp3Clips = uiState.secondaryMp3Clips,
            textOverlays = uiState.textOverlays,
            imageOverlays = uiState.imageOverlays,
            selectedClipId = uiState.selectedClipId,
            onSeekPlayhead = { ms -> viewModel.seekPlayhead(ms) },
            onSelectClip = { id -> viewModel.selectClip(id) },
            onDragVideoSegment = { segId, deltaMs -> viewModel.moveVideoSegmentByDelta(segId, deltaMs) },
            onDragVoiceClip = { id, deltaMs -> viewModel.moveVoiceClipByDelta(id, deltaMs) },
            onDragSecondaryMp3Clip = { id, deltaMs -> viewModel.moveSecondaryMp3ClipByDelta(id, deltaMs) },
            onDragTextOverlayTime = { id, deltaMs -> viewModel.moveTextOverlayTimeline(id, deltaMs) },
            onDragImageOverlayTime = { id, deltaMs -> viewModel.moveImageOverlayTimeline(id, deltaMs) },
            onToggleMuteVideoAudio = { viewModel.toggleVideoOriginalAudioMute() }
        )

        // 7. THANH CÔNG CỤ DƯỚI CÙNG (5 NÚT GỌN GÀNG: Tạo Voice+Sub | Cắt | Tiêu đề | Sticker | Xóa)
        BottomCapCutToolbar(
            onOpenTtsSheet = { showTtsSheet = true },
            onCutSelectedAtPlayhead = { viewModel.splitSelectedOrActiveClipAtPlayhead() },
            onOpenAddTitleText = { showAddTextSheet = true },
            onOpenAddSticker = { showAddStickerSheet = true },
            onDeleteSelected = { viewModel.deleteSelectedClip() }
        )
    }

    // SHEET 1: NHẬP TEXT CHUYỂN THÀNH VOICE AI + SUB PHỤ ĐỀ (TỐI ĐA 1000 KÝ TỰ)
    if (showTtsSheet) {
        OfflineTtsCreatorSheet(
            uiState = uiState,
            onDismiss = { showTtsSheet = false },
            onSelectVoice = { viewModel.selectVoiceModel(it) },
            onChangeSpeed = { viewModel.updateTtsSpeed(it) },
            onSynthesize = { inputText ->
                showTtsSheet = false
                viewModel.renderOfflineTtsAndAttachSubtitle(inputText)
            }
        )
    }

    // SHEET 2: THÊM TIÊU ĐỀ (+ LÀM MỜ TIÊU ĐỀ)
    if (showAddTextSheet) {
        AddTextOverlaySheet(
            onDismiss = { showAddTextSheet = false },
            onConfirmAddText = { content, fontSize, opacity, blurDp ->
                viewModel.addCanvasTextOverlay(content, fontSize, opacity, blurDp)
                showAddTextSheet = false
            }
        )
    }

    // SHEET 3: THÊM STICKER (+ LÀM MỜ STICKER)
    if (showAddStickerSheet) {
        AddStickerOverlaySheet(
            onDismiss = { showAddStickerSheet = false },
            onPickPngFile = {
                showAddStickerSheet = false
                stickerPickerLauncher.launch("image/*")
            }
        )
    }

    // HỘP THOẠI LỰA CHỌN XUẤT FILE: MP3 HOẶC MP4 (BẤM XUẤT -> CHUYỂN QUẢNG CÁO XONG MỚI XUẤT)
    if (uiState.showExportFormatSheet) {
        ExportFormatSelectionDialog(
            hasVoiceClips = uiState.voiceClips.isNotEmpty(),
            hasVideoLoaded = uiState.videoTrack.videoUri.isNotBlank(),
            onDismiss = { viewModel.dismissExportFormatSelection() },
            onSelectFormat = { format ->
                viewModel.startExportWithAd(format, activity)
            }
        )
    }

    // DIALOG QUẢNG CÁO TỰ ĐỘNG TRƯỚC KHI XUẤT FILE (KHI ADMOB ĐANG NẠP HOẶC OFFLINE)
    if (uiState.showFallbackExportAdDialog) {
        AutoExportAdOverlayDialog(
            exportFormat = uiState.pendingExportFormat ?: ExportFormat.MP4,
            onAdFinished = { viewModel.completeFallbackExportAd() }
        )
    }
}

@Composable
private fun TopEditorHeader(
    uiState: EditorUiState,
    onPickVideoMp4: () -> Unit,
    onToggleMuteVideo: () -> Unit,
    onClickExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141821))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "LKHK.TTS",
                color = Color(0xFF00E5FF),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "9:16 (${formatClock(uiState.videoTrack.timelineEndMs)}) • Tối đa ${uiState.maxCharQuota} ký tự",
                color = Color(0xFFA5D6A7),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleMuteVideo,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (uiState.videoTrack.isOriginalAudioMuted) Color(0xFF3E2723) else Color(0xFF1E2B3C),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = if (uiState.videoTrack.isOriginalAudioMuted) {
                        Icons.Default.VolumeOff
                    } else {
                        Icons.Default.VolumeUp
                    },
                    contentDescription = "Tắt/Bật tiếng MP4",
                    tint = if (uiState.videoTrack.isOriginalAudioMuted) Color(0xFFFF8A80) else Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedButton(
                onClick = onPickVideoMp4,
                modifier = Modifier.height(32.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Chọn MP4 9:16", fontSize = 11.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = onClickExport,
                enabled = !uiState.isExportingVideo,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                modifier = Modifier.height(32.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.isExportingVideo) "Đang xuất..." else "Xuất File",
                    fontSize = 11.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Thanh Banner Quảng Cáo Treo Tự Động:
 * - Tự động gắn Google AdMob Banner (`AdSize.BANNER`) và tự động làm mới.
 * - Khi AdMob đang tải hoặc thiết bị không có mạng, hiển thị thanh Banner tài trợ tích hợp sẵn.
 */
@Composable
private fun AutoBannerAdStrip() {
    var adLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color(0xFF101520))
            .border(0.5.dp, Color(0xFF263238)),
        contentAlignment = Alignment.Center
    ) {
        if (!adLoaded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD600), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "QC",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LKHK.TTS Studio • 9 Giọng AI Việt Nam (1000 ký tự/lần)",
                        color = Color(0xFFE0E0E0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Tài trợ",
                    color = Color(0xFF4FC3F7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdMonetizationManager.ADMOB_BANNER_UNIT_ID
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            adLoaded = true
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            adLoaded = false
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}

@Composable
private fun Vertical9By16PreviewStage(
    videoTrack: ShortVideoTrack,
    playheadMs: Long,
    isPlaying: Boolean,
    activeSubtitleClip: VoiceSubtitleClip?,
    subtitleConfig: SubtitleCanvasConfig,
    textOverlays: List<CanvasTextOverlayItem>,
    imageOverlays: List<CanvasImageOverlayItem>,
    selectedClipId: String?,
    onDragSubtitle: (Float, Float) -> Unit,
    onDragTextOverlay: (String, Float, Float) -> Unit,
    onDragImageOverlay: (String, Float, Float) -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(videoTrack.videoUri) {
        if (videoTrack.videoUri.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoTrack.videoUri))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } else {
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(videoTrack.isOriginalAudioMuted, videoTrack.speedMultiplier) {
        exoPlayer.volume = if (videoTrack.isOriginalAudioMuted) 0f else 1f
        exoPlayer.setPlaybackSpeed(videoTrack.speedMultiplier.coerceIn(0.5f, 2.0f))
    }

    val activeVideoSegment = remember(videoTrack.segments, playheadMs, videoTrack.speedMultiplier) {
        val speed = videoTrack.speedMultiplier
        videoTrack.segments.lastOrNull { seg ->
            playheadMs >= seg.timelineStartMs && playheadMs <= seg.timelineEndAtSpeedMs(speed)
        }
    }

    LaunchedEffect(isPlaying, activeVideoSegment?.id) {
        if (videoTrack.videoUri.isNotBlank() && activeVideoSegment != null) {
            if (isPlaying) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(playheadMs, isPlaying, activeVideoSegment) {
        if (videoTrack.videoUri.isNotBlank() && activeVideoSegment != null) {
            val speed = videoTrack.speedMultiplier
            val offsetInTimeline = (playheadMs - activeVideoSegment.timelineStartMs).coerceAtLeast(0L)
            val targetVideoPos = (activeVideoSegment.sourceStartMs + (offsetInTimeline * speed).toLong())
                .coerceIn(activeVideoSegment.sourceStartMs, activeVideoSegment.sourceEndMs)
            val drift = abs(exoPlayer.currentPosition - targetVideoPos)
            if (!isPlaying && drift > 80L) {
                exoPlayer.seekTo(targetVideoPos)
            } else if (isPlaying && drift > 450L) {
                exoPlayer.seekTo(targetVideoPos)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141824))
            .border(1.5.dp, Color(0xFF2D3548), RoundedCornerShape(12.dp))
    ) {
        val stageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val stageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // LỚP 1: VIDEO MP4 9:16
        if (videoTrack.videoUri.isNotBlank() && activeVideoSegment != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "KHU VIEW 9:16",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Bấm 'Chọn MP4 9:16' để nạp Video (≤ 3p)\n• Bấm 'Tạo Voice+Sub' để chuyển Text thành Giọng đọc\n• Kéo thả Phụ đề, Tiêu đề & Sticker tại đây",
                    color = Color(0xFF9AA4B8),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        // LỚP 2: CÁC STICKER PNG/JPG (KÉO THẢ VỊ TRÍ BẤT KỲ + LÀM MỜ)
        imageOverlays.filter { it.isVisibleAt(playheadMs) }.forEach { imgItem ->
            DraggableStickerNode(
                item = imgItem,
                isSelected = imgItem.id == selectedClipId,
                stageWidthPx = stageWidthPx,
                stageHeightPx = stageHeightPx,
                onUpdateFraction = { nx, ny -> onDragImageOverlay(imgItem.id, nx, ny) }
            )
        }

        // LỚP 3: CÁC TIÊU ĐỀ (KÉO THẢ VỊ TRÍ BẤT KỲ + LÀM MỜ)
        textOverlays.filter { it.isVisibleAt(playheadMs) }.forEach { txtItem ->
            DraggableCanvasTextNode(
                item = txtItem,
                isSelected = txtItem.id == selectedClipId,
                stageWidthPx = stageWidthPx,
                stageHeightPx = stageHeightPx,
                onUpdateFraction = { nx, ny -> onDragTextOverlay(txtItem.id, nx, ny) }
            )
        }

        // LỚP 4: PHỤ ĐỀ TỰ ĐỘNG ĐỒNG BỘ VỚI VOICE AI
        if (activeSubtitleClip != null) {
            DraggableSubtitleBox(
                subtitleText = activeSubtitleClip.subtitleText,
                config = subtitleConfig,
                stageWidthPx = stageWidthPx,
                stageHeightPx = stageHeightPx,
                onUpdateFraction = onDragSubtitle
            )
        }
    }
}

@Composable
private fun DraggableSubtitleBox(
    subtitleText: String,
    config: SubtitleCanvasConfig,
    stageWidthPx: Float,
    stageHeightPx: Float,
    onUpdateFraction: (Float, Float) -> Unit
) {
    val currentConfig by rememberUpdatedState(config)
    val currentOnUpdateFraction by rememberUpdatedState(onUpdateFraction)
    val xPx = (config.xFraction * stageWidthPx).roundToInt()
    val yPx = (config.yFraction * stageHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (xPx - (stageWidthPx * 0.42f).roundToInt()).coerceAtLeast(6),
                    y = (yPx - 28).coerceAtLeast(6)
                )
            }
            .width(with(LocalDensity.current) { (stageWidthPx * 0.84f).toDp() })
            .alpha(config.opacity)
            .let { mod ->
                if (config.blurRadiusDp > 0.5f) mod.blur(config.blurRadiusDp.dp) else mod
            }
            .background(Color(config.backgroundColorArgb), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFFFD600), RoundedCornerShape(8.dp))
            .pointerInput(stageWidthPx, stageHeightPx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val nextX = (currentConfig.xFraction + dragAmount.x / stageWidthPx).coerceIn(0.08f, 0.92f)
                    val nextY = (currentConfig.yFraction + dragAmount.y / stageHeightPx).coerceIn(0.06f, 0.94f)
                    currentOnUpdateFraction(nextX, nextY)
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = subtitleText,
            color = Color(config.textColorArgb),
            fontSize = config.fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = (config.fontSizeSp + 4f).sp
        )
    }
}

@Composable
private fun DraggableCanvasTextNode(
    item: CanvasTextOverlayItem,
    isSelected: Boolean,
    stageWidthPx: Float,
    stageHeightPx: Float,
    onUpdateFraction: (Float, Float) -> Unit
) {
    val currentItem by rememberUpdatedState(item)
    val currentOnUpdateFraction by rememberUpdatedState(onUpdateFraction)
    val xPx = (item.xFraction * stageWidthPx).roundToInt()
    val yPx = (item.yFraction * stageHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (xPx - 70).coerceAtLeast(4),
                    y = (yPx - 20).coerceAtLeast(4)
                )
            }
            .alpha(item.opacity)
            .let { mod ->
                if (item.blurRadiusDp > 0.5f) mod.blur(item.blurRadiusDp.dp) else mod
            }
            .background(Color(item.backgroundColorArgb), RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(item.id, stageWidthPx, stageHeightPx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val nx = (currentItem.xFraction + dragAmount.x / stageWidthPx).coerceIn(0.05f, 0.95f)
                    val ny = (currentItem.yFraction + dragAmount.y / stageHeightPx).coerceIn(0.05f, 0.95f)
                    currentOnUpdateFraction(nx, ny)
                }
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = item.text,
            color = Color(item.textColorArgb),
            fontSize = item.fontSizeSp.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun DraggableStickerNode(
    item: CanvasImageOverlayItem,
    isSelected: Boolean,
    stageWidthPx: Float,
    stageHeightPx: Float,
    onUpdateFraction: (Float, Float) -> Unit
) {
    val context = LocalContext.current
    val currentItem by rememberUpdatedState(item)
    val currentOnUpdateFraction by rememberUpdatedState(onUpdateFraction)
    val bitmap = remember(item.imageUri) {
        try {
            context.contentResolver.openInputStream(Uri.parse(item.imageUri))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    val xPx = (item.xFraction * stageWidthPx).roundToInt()
    val yPx = (item.yFraction * stageHeightPx).roundToInt()
    val sizeDp = (68f * item.scale).dp

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (xPx - 36).coerceAtLeast(4),
                    y = (yPx - 36).coerceAtLeast(4)
                )
            }
            .size(sizeDp)
            .alpha(item.opacity)
            .let { mod ->
                if (item.blurRadiusDp > 0.5f) mod.blur(item.blurRadiusDp.dp) else mod
            }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFFFFD600) else Color(0x55FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(item.id, stageWidthPx, stageHeightPx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val nx = (currentItem.xFraction + dragAmount.x / stageWidthPx).coerceIn(0.05f, 0.95f)
                    val ny = (currentItem.yFraction + dragAmount.y / stageHeightPx).coerceIn(0.05f, 0.95f)
                    currentOnUpdateFraction(nx, ny)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "STICKER",
                color = Color(0xFFFFE082),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Thanh chỉnh nhanh Size Chữ / Size Phụ đề / Độ mờ của đối tượng đang chọn.
 */
@Composable
private fun ActiveOverlayQuickInspector(
    uiState: EditorUiState,
    onUpdateSubtitleFontSize: (Float) -> Unit,
    onUpdateSubtitleBlurOpacity: (Float, Float) -> Unit,
    onUpdateTextFontSize: (String, Float) -> Unit,
    onUpdateTextBlurOpacity: (String, Float, Float) -> Unit,
    onUpdateImageBlurOpacityScale: (String, Float, Float, Float) -> Unit
) {
    val selectedText = uiState.selectedTextOverlay
    val selectedImg = uiState.selectedImageOverlay

    Surface(
        color = Color(0xFF161A24),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        when {
            selectedText != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔤 Size Tiêu đề: ${selectedText.fontSizeSp.roundToInt()}sp",
                        color = Color(0xFFCE93D8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    SmallStepChip("-") {
                        onUpdateTextFontSize(selectedText.id, selectedText.fontSizeSp - 1f)
                    }
                    Slider(
                        value = selectedText.fontSizeSp,
                        onValueChange = { onUpdateTextFontSize(selectedText.id, it) },
                        valueRange = 10f..38f,
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                    )
                    SmallStepChip("+") {
                        onUpdateTextFontSize(selectedText.id, selectedText.fontSizeSp + 1f)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mờ: ${selectedText.blurRadiusDp.roundToInt()}",
                        color = Color(0xFF9AA4B8),
                        fontSize = 10.sp
                    )
                    Slider(
                        value = selectedText.blurRadiusDp,
                        onValueChange = {
                            onUpdateTextBlurOpacity(selectedText.id, selectedText.opacity, it)
                        },
                        valueRange = 0f..20f,
                        modifier = Modifier
                            .width(68.dp)
                            .height(26.dp)
                    )
                }
            }

            selectedImg != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🖼️ Sticker",
                        color = Color(0xFFFFCC80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(68.dp)
                    )
                    Text("Rõ ${(selectedImg.opacity * 100).roundToInt()}%", color = Color.White, fontSize = 10.sp)
                    Slider(
                        value = selectedImg.opacity,
                        onValueChange = {
                            onUpdateImageBlurOpacityScale(selectedImg.id, it, selectedImg.blurRadiusDp, selectedImg.scale)
                        },
                        valueRange = 0.15f..1f,
                        modifier = Modifier.weight(1f).height(26.dp)
                    )
                    Text("Mờ ${selectedImg.blurRadiusDp.roundToInt()}px", color = Color.White, fontSize = 10.sp)
                    Slider(
                        value = selectedImg.blurRadiusDp,
                        onValueChange = {
                            onUpdateImageBlurOpacityScale(selectedImg.id, selectedImg.opacity, it, selectedImg.scale)
                        },
                        valueRange = 0f..20f,
                        modifier = Modifier.weight(1f).height(26.dp)
                    )
                }
            }

            else -> {
                val subCfg = uiState.subtitleConfig
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💬 Size Sub: ${subCfg.fontSizeSp.roundToInt()}sp",
                        color = Color(0xFFFFF59D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SmallStepChip("-") {
                        onUpdateSubtitleFontSize(subCfg.fontSizeSp - 1f)
                    }
                    Slider(
                        value = subCfg.fontSizeSp,
                        onValueChange = { onUpdateSubtitleFontSize(it) },
                        valueRange = 9f..28f,
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                    )
                    SmallStepChip("+") {
                        onUpdateSubtitleFontSize(subCfg.fontSizeSp + 1f)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rõ ${(subCfg.opacity * 100).roundToInt()}%", color = Color(0xFF9AA4B8), fontSize = 10.sp)
                    Slider(
                        value = subCfg.opacity,
                        onValueChange = { onUpdateSubtitleBlurOpacity(it, subCfg.blurRadiusDp) },
                        valueRange = 0.2f..1f,
                        modifier = Modifier
                            .width(72.dp)
                            .height(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallStepChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF283044))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlaybackTransportBar(
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isBusy: Boolean,
    statusMessage: String,
    onTogglePlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12151E))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(30.dp)
                .background(Color(0xFF00E5FF), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${formatClock(playheadMs)} / ${formatClock(totalDurationMs)}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(10.dp))

        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF00E5FF)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                color = Color(0xFF80DEEA),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Thanh công cụ dưới cùng 5 nút theo đúng yêu cầu:
 * 1. Tạo Voice+Sub
 * 2. Cắt (dùng chung cho Video, Voice+Sub, Tiêu đề, Sticker)
 * 3. Tiêu đề
 * 4. Sticker
 * 5. Xóa
 */
@Composable
private fun BottomCapCutToolbar(
    onOpenTtsSheet: () -> Unit,
    onCutSelectedAtPlayhead: () -> Unit,
    onOpenAddTitleText: () -> Unit,
    onOpenAddSticker: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141822))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarActionItem(
            icon = Icons.Default.Mic,
            label = "Tạo Voice+Sub",
            tint = Color(0xFF00E676),
            onClick = onOpenTtsSheet
        )
        ToolbarActionItem(
            icon = Icons.Default.ContentCut,
            label = "Cắt",
            tint = Color(0xFFFFD600),
            onClick = onCutSelectedAtPlayhead
        )
        ToolbarActionItem(
            icon = Icons.Default.TextFields,
            label = "Tiêu đề",
            tint = Color(0xFFCE93D8),
            onClick = onOpenAddTitleText
        )
        ToolbarActionItem(
            icon = Icons.Default.AddPhotoAlternate,
            label = "Sticker",
            tint = Color(0xFF4FC3F7),
            onClick = onOpenAddSticker
        )
        ToolbarActionItem(
            icon = Icons.Default.Delete,
            label = "Xóa",
            tint = Color(0xFFFF5252),
            onClick = onDeleteSelected
        )
    }
}

@Composable
private fun ToolbarActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================================
// ĐỒNG BỘ PLAYHEAD & PHÁT ÂM THANH CÁC ĐOẠN VOICE ĐÃ CẮT TRÊN TIMELINE
// ============================================================================

@Composable
private fun TimelinePlaybackSynchronizer(
    uiState: EditorUiState,
    onSeekPlayhead: (Long) -> Unit,
    onStopPlaying: () -> Unit
) {
    val voicePlayer = remember { MediaPlayer() }
    var activePlayingVoiceId by remember { mutableStateOf<String?>(null) }
    val currentState by rememberUpdatedState(uiState)

    DisposableEffect(Unit) {
        onDispose {
            try {
                voicePlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(uiState.isPlaying) {
        if (!uiState.isPlaying) {
            try {
                if (voicePlayer.isPlaying) voicePlayer.pause()
            } catch (_: Exception) {
            }
            activePlayingVoiceId = null
            return@LaunchedEffect
        }

        var lastTick = System.currentTimeMillis()
        while (currentState.isPlaying) {
            delay(40L)
            val now = System.currentTimeMillis()
            val delta = (now - lastTick).coerceAtLeast(16L)
            lastTick = now

            val nextPos = currentState.playheadMs + delta
            if (nextPos >= currentState.totalTimelineDurationMs) {
                onSeekPlayhead(0L)
                onStopPlaying()
                break
            } else {
                onSeekPlayhead(nextPos)
            }

            val clipUnderPlayhead = currentState.voiceClips.firstOrNull { clip ->
                nextPos >= clip.timelineStartMs && nextPos < clip.timelineEndMs
            }

            if (clipUnderPlayhead != null) {
                val offsetInClipMs = (clipUnderPlayhead.trimStartMs + (nextPos - clipUnderPlayhead.timelineStartMs)).toInt()
                if (activePlayingVoiceId != clipUnderPlayhead.id) {
                    try {
                        voicePlayer.reset()
                        voicePlayer.setDataSource(clipUnderPlayhead.audioFilePath)
                        voicePlayer.prepare()
                        if (offsetInClipMs > 80) {
                            voicePlayer.seekTo(offsetInClipMs)
                        }
                        voicePlayer.start()
                        activePlayingVoiceId = clipUnderPlayhead.id
                    } catch (_: Exception) {
                    }
                } else {
                    try {
                        if (!voicePlayer.isPlaying) {
                            if (offsetInClipMs > 80) {
                                voicePlayer.seekTo(offsetInClipMs)
                            }
                            voicePlayer.start()
                        }
                    } catch (_: Exception) {
                    }
                }
            } else if (activePlayingVoiceId != null) {
                try {
                    if (voicePlayer.isPlaying) voicePlayer.pause()
                } catch (_: Exception) {
                }
                activePlayingVoiceId = null
            }
        }
    }
}

// ============================================================================
// HỘP THOẠI LỰA CHỌN XUẤT FILE: MP3 HOẶC MP4
// ============================================================================

@Composable
private fun ExportFormatSelectionDialog(
    hasVoiceClips: Boolean,
    hasVideoLoaded: Boolean,
    onDismiss: () -> Unit,
    onSelectFormat: (ExportFormat) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF171C28),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LỰA CHỌN ĐỊNH DẠNG XUẤT FILE",
                    color = Color(0xFF00E5FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sau khi bấm chọn định dạng, hệ thống sẽ hiển thị quảng cáo ngắn rồi tự động xuất file về máy.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Lựa chọn 1: Xuất MP3
                Button(
                    onClick = { onSelectFormat(ExportFormat.MP3) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Xuất File MP3 (Chỉ Âm Thanh Voice AI)",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (hasVoiceClips) "Ghép toàn bộ đoạn Voice trên Timeline" else "Cần tạo ít nhất 1 đoạn Voice+Sub",
                            color = Color(0xFF1B5E20),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Lựa chọn 2: Xuất MP4
                Button(
                    onClick = { onSelectFormat(ExportFormat.MP4) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Xuất File MP4 (Video 9:16 + Voice + Sub)",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (hasVideoLoaded) "Độ phân giải chuẩn 1080x1920 + File .SRT" else "Cần chọn Video MP4 9:16 trước khi xuất",
                            color = Color(0xFF01579B),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Đóng", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// ============================================================================
// BOTTOM SHEETS: TẠO VOICE+SUB (1000 KÝ TỰ), THÊM TIÊU ĐỀ, THÊM STICKER
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineTtsCreatorSheet(
    uiState: EditorUiState,
    onDismiss: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onSynthesize: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = "Xin chào các bạn, hôm nay chúng ta cùng khám phá câu chuyện thú vị này nhé!",
                selection = TextRange(76)
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF171A24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎙️ Chuyển Text thành Voice AI & Phụ Đề",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${textValue.text.length}/${uiState.maxCharQuota} ký tự",
                    color = if (textValue.text.length >= uiState.maxCharQuota) Color(0xFFFF8A80) else Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Mở khóa toàn bộ 9 giọng đọc • Tối đa ${uiState.maxCharQuota} ký tự mỗi lần chuyển giọng đọc",
                color = Color(0xFF9AA4B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text("Chọn Giọng Đọc AI (9 Giọng Việt Nam):", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.voiceCatalog.forEach { voice: VoiceModelCatalogItem ->
                    val isSelected = voice.id == uiState.selectedVoiceId
                    val isReady = voice.isBundledInApk || uiState.downloadedVoiceIds.contains(voice.id)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectVoice(voice.id) },
                        label = {
                            val badge = if (isReady) "✅" else "☁️"
                            Text("$badge ${voice.displayName}", fontSize = 11.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E676),
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            if (uiState.downloadingVoiceId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.downloadProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00E5FF)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tốc độ đọc: ${"%.2f".format(uiState.ttsSpeed)}x",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(120.dp)
                )
                Slider(
                    value = uiState.ttsSpeed,
                    onValueChange = onChangeSpeed,
                    valueRange = 0.7f..1.4f,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    if (newValue.text.length <= uiState.maxCharQuota) {
                        textValue = newValue
                    } else {
                        val clipped = newValue.text.take(uiState.maxCharQuota)
                        textValue = newValue.copy(
                            text = clipped,
                            selection = TextRange(clipped.length)
                        )
                    }
                },
                label = {
                    Text(
                        "Nhập văn bản tiếng Việt (${textValue.text.length}/${uiState.maxCharQuota} ký tự)"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onSynthesize(textValue.text) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tạo Voice AI & Gắn Phụ Đề Vào Timeline",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTextOverlaySheet(
    onDismiss: () -> Unit,
    onConfirmAddText: (text: String, fontSizeSp: Float, opacity: Float, blurDp: Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = "Tiêu đề nổi bật",
                selection = TextRange(15)
            )
        )
    }
    var fontSizeSp by remember { mutableFloatStateOf(18f) }
    var opacity by remember { mutableFloatStateOf(1.0f) }
    var blurDp by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF171A24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "🔤 Thêm Tiêu đề Lên Khu View 9:16",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text("Nội dung Tiêu đề") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Cỡ chữ Tiêu đề: ${fontSizeSp.roundToInt()}sp", color = Color.White, fontSize = 12.sp)
            Slider(value = fontSizeSp, onValueChange = { fontSizeSp = it }, valueRange = 10f..38f)

            Text("Độ rõ (Opacity): ${(opacity * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp)
            Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.15f..1.0f)

            Text("Làm mờ Tiêu đề (Blur): ${blurDp.roundToInt()} dp", color = Color.White, fontSize = 12.sp)
            Slider(value = blurDp, onValueChange = { blurDp = it }, valueRange = 0f..20f)

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onConfirmAddText(textValue.text, fontSizeSp, opacity, blurDp) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE93D8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Thêm Tiêu đề Vào Khu View (Kéo Thả Tự Do)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStickerOverlaySheet(
    onDismiss: () -> Unit,
    onPickPngFile: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171A24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "🖼️ Thêm Sticker Lên Khu View 9:16",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sau khi chọn ảnh Sticker (PNG/JPG), bạn có thể kéo thả tới vị trí bất kỳ trên màn hình 9:16, bấm 'Cắt' để chia đoạn thời gian hoặc kéo thanh trượt để Làm mờ Sticker.",
                color = Color(0xFF9AA4B8),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onPickPngFile,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Chọn Ảnh Sticker Từ Điện Thoại", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

/**
 * Màn hình Quảng Cáo Tự Động khi bấm Xuất File (MP3 hoặc MP4):
 * Đếm ngược 5 giây và tự động tiến hành xuất file ngay sau khi xem xong quảng cáo.
 */
@Composable
private fun AutoExportAdOverlayDialog(
    exportFormat: ExportFormat,
    onAdFinished: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    Dialog(
        onDismissRequest = {
            if (secondsLeft <= 0) onAdFinished()
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF151924),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFFFFD600), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFD600), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "QUẢNG CÁO TRƯỚC KHI XUẤT FILE ${exportFormat.name}",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "LKHK.TTS Studio",
                    color = Color(0xFF00E5FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Cảm ơn bạn đã sử dụng LKHK.TTS!\nHệ thống sẽ tự động xuất file ${exportFormat.name} ngay sau khi quảng cáo kết thúc.",
                    color = Color(0xFFD0D7E2),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { (5 - secondsLeft) / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF00E676)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onAdFinished,
                    enabled = secondsLeft <= 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        disabledContainerColor = Color(0xFF2C3344)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (secondsLeft > 0) {
                            "Đang phát quảng cáo... (${secondsLeft}s)"
                        } else {
                            "Bắt Đầu Xuất File ${exportFormat.name} Ngay"
                        },
                        color = if (secondsLeft > 0) Color(0xFF9AA4B8) else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val tenth = (ms.coerceAtLeast(0L) % 1000L) / 100L
    return "%02d:%02d.%d".format(min, sec, tenth)
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}
