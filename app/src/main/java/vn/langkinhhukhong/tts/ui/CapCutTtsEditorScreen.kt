package vn.langkinhhukhong.tts.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
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
import vn.langkinhhukhong.tts.model.OfflineVoiceProfile
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.SubtitleOverlayStyle
import vn.langkinhhukhong.tts.model.TimelineSubtitleToken
import vn.langkinhhukhong.tts.ui.components.MultiTrackTimeline
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapCutTtsEditorScreen(
    viewModel: EditorViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var showTtsSheet by remember { mutableStateOf(false) }
    var showTextCreatorSheet by remember { mutableStateOf(false) }
    var showStickerCreatorSheet by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importShortVideo(uri)
        }
    }

    val stickerPngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importLogoPngOverlay(uri)
            showStickerCreatorSheet = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = Color(0xFF0B0D13)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. THANH HEADER: LKHK.TTS, Tối đa 1000 ký tự, Chọn MP4 9:16, Xuất File (MP3 / MP4)
            TopEditorHeader(
                videoTrack = state.videoTrack,
                maxChars = state.maxCharQuota,
                isExporting = state.isExportingVideo,
                onPickVideo = { videoPickerLauncher.launch("video/*") },
                onToggleMuteVideo = { viewModel.toggleMuteOriginalVideo() },
                onExport = { viewModel.requestExportFormatSelection() }
            )

            // 2. BANNER QUẢNG CÁO TREO TỰ ĐỘNG (AdMob Banner + Sponsor Banner)
            AutoBannerAdStrip()

            // 3. KHU VIEW 9:16 RỘNG RÃI (Cho phép kéo thả vị trí bất kỳ: Phụ đề, Tiêu đề, Sticker)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Vertical9By16PreviewStage(
                    videoTrack = state.videoTrack,
                    playheadMs = state.playheadMs,
                    isPlaying = state.isPlaying,
                    subtitleTokens = state.currentSubtitleTokens,
                    subtitleStyle = state.subtitleStyle,
                    visibleTextOverlays = state.visibleTextOverlaysAtPlayhead,
                    visibleImageOverlays = state.visibleImageOverlaysAtPlayhead,
                    selectedClipId = state.selectedClipId,
                    onSelectOverlay = { viewModel.selectClip(it) },
                    onDragSubtitle = { dx, dy -> viewModel.moveSubtitleOverlayOnCanvas(dx, dy) },
                    onDragTextOverlay = { id, dx, dy -> viewModel.moveTextOverlayOnCanvas(id, dx, dy) },
                    onDragImageOverlay = { id, dx, dy -> viewModel.moveImageOverlayOnCanvas(id, dx, dy) }
                )
            }

            // 4. THANH CHỈNH SIZE CHỮ PHỤ ĐỀ / SIZE TIÊU ĐỀ / LÀM MỜ STICKER
            val selectedText = state.selectedTextOverlay
            val selectedImage = state.selectedImageOverlay
            val selectedVoice = state.selectedVoiceClip
            if (selectedText != null) {
                SelectedTextBlurAndOpacityBar(
                    item = selectedText,
                    onUpdateOpacity = { viewModel.updateSelectedTextOverlay(opacity = it) },
                    onUpdateBlur = { viewModel.updateSelectedTextOverlay(blurRadiusDp = it) },
                    onUpdateFontSize = { viewModel.updateSelectedTextOverlay(fontSizeSp = it) }
                )
            } else if (selectedImage != null) {
                SelectedImageBlurAndOpacityBar(
                    item = selectedImage,
                    onUpdateOpacity = { viewModel.updateSelectedImageOverlay(opacity = it) },
                    onUpdateBlur = { viewModel.updateSelectedImageOverlay(blurRadiusDp = it) },
                    onUpdateSize = { viewModel.updateSelectedImageOverlay(sizeDp = it) }
                )
            } else if (selectedVoice != null || state.selectedClipId == "SUBTITLE_STYLE" || state.voiceClips.isNotEmpty()) {
                SubtitleFontSizeAndStyleBar(
                    subtitleStyle = state.subtitleStyle,
                    onUpdateFontSize = { viewModel.updateSubtitleStyle(fontSizeSp = it) },
                    onUpdateOpacity = { viewModel.updateSubtitleStyle(opacity = it) }
                )
            }

            // 5. THANH PLAY/PAUSE & THỜI GIAN PLAYHEAD (Gọn gàng, đã loại bỏ dòng chữ thừa bên phải)
            PlaybackTransportBar(
                playheadMs = state.playheadMs,
                totalDurationMs = state.totalTimelineDurationMs,
                isPlaying = state.isPlaying,
                statusMessage = state.statusMessage,
                onTogglePlay = { viewModel.togglePlayPause() }
            )

            // 6. THANH TIMELINE DẠNG PART (Video 9:16 + Voice&Sub + Tiêu đề & Sticker)
            MultiTrackTimeline(
                videoTrack = state.videoTrack,
                voiceClips = state.voiceClips,
                textOverlays = state.textOverlays,
                imageOverlays = state.imageOverlays,
                totalDurationMs = state.totalTimelineDurationMs,
                playheadMs = state.playheadMs,
                selectedClipId = state.selectedClipId,
                onSeek = { viewModel.seekTo(it) },
                onSelectClip = { viewModel.selectClip(it) },
                onToggleMuteVideo = { viewModel.toggleMuteOriginalVideo() },
                onDragVideoSegment = { segId, deltaMs ->
                    viewModel.moveVideoSegmentByDelta(segId, deltaMs)
                },
                onDragVoiceClip = { clipId, deltaMs ->
                    viewModel.moveVoiceClipByDelta(clipId, deltaMs)
                },
                onDragTextOverlay = { id, deltaMs ->
                    viewModel.moveTextOverlayOnTimeline(id, deltaMs)
                },
                onDragImageOverlay = { id, deltaMs ->
                    viewModel.moveImageOverlayOnTimeline(id, deltaMs)
                }
            )

            // 7. THANH CÔNG CỤ DƯỚI CÙNG (5 NÚT: Tạo Voice+Sub | Cắt | Tiêu đề | Sticker | Xóa)
            BottomCapCutToolbar(
                onOpenTtsCreator = { showTtsSheet = true },
                onSplitAtPlayhead = { viewModel.splitSelectedOrActiveClipAtPlayhead() },
                onOpenTextOverlaySheet = { showTextCreatorSheet = true },
                onOpenStickerSheet = { showStickerCreatorSheet = true },
                onDeleteSelected = { viewModel.deleteSelectedClip() }
            )
        }

        // BẢNG 1: CHUYỂN TEXT THÀNH VOICE + TỰ ĐỘNG TẠO SUB PHỤ ĐỀ KÈM THEO (TỐI ĐA 1000 KÝ TỰ)
        if (showTtsSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { if (!state.isSynthesizingTts) showTtsSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF161924)
            ) {
                OfflineTtsCreatorSheet(
                    installedVoices = state.installedVoices,
                    selectedVoice = state.selectedVoiceProfile,
                    inputText = state.ttsInputText,
                    speechRate = state.ttsSpeechRate,
                    subtitleFontSizeSp = state.subtitleStyle.fontSizeSp,
                    maxCharQuota = state.maxCharQuota,
                    isSynthesizing = state.isSynthesizingTts,
                    onSelectVoice = { viewModel.selectVoiceProfile(it) },
                    onPreviewVoice = { viewModel.previewVoiceSample(it) },
                    onTextChange = { viewModel.updateTtsInputText(it) },
                    onSpeechRateChange = { viewModel.updateTtsSpeechRate(it) },
                    onSubtitleFontSizeChange = { viewModel.updateSubtitleStyle(fontSizeSp = it) },
                    onRenderTts = {
                        viewModel.renderOfflineTtsAndAttachSubtitle(
                            activity = activity,
                            onComplete = { showTtsSheet = false }
                        )
                    }
                )
            }
        }

        // BẢNG 2: THÊM TIÊU ĐỀ + LÀM MỜ
        if (showTextCreatorSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showTextCreatorSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF161924)
            ) {
                AddTextOverlaySheet(
                    onAddText = { text, fontSize, colorHex, opacity, blurDp ->
                        viewModel.addTextOverlay(
                            text = text,
                            fontSizeSp = fontSize,
                            colorHex = colorHex,
                            opacity = opacity,
                            blurRadiusDp = blurDp
                        )
                        showTextCreatorSheet = false
                    }
                )
            }
        }

        // BẢNG 3: THÊM STICKER + LÀM MỜ
        if (showStickerCreatorSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showStickerCreatorSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF161924)
            ) {
                AddStickerOverlaySheet(
                    onPickPngFromDevice = {
                        stickerPngPickerLauncher.launch("image/*")
                    },
                    onCreateSampleBadgePng = { label ->
                        viewModel.addSampleLogoPngOverlay(label)
                        showStickerCreatorSheet = false
                    }
                )
            }
        }

        // HỘP THOẠI LỰA CHỌN ĐỊNH DẠNG XUẤT FILE: MP3 HOẶC MP4
        if (state.showExportFormatSheet) {
            ExportFormatSelectionDialog(
                hasVoiceClips = state.voiceClips.isNotEmpty(),
                hasVideoLoaded = state.videoTrack.videoUri.isNotBlank(),
                onDismiss = { viewModel.dismissExportFormatSelection() },
                onSelectFormat = { format ->
                    viewModel.startExportWithAd(format, activity)
                }
            )
        }

        // QUẢNG CÁO TỰ ĐỘNG TRƯỚC KHI XUẤT FILE (MP3 HOẶC MP4)
        if (state.showFallbackExportAdDialog) {
            AutoExportAdOverlayDialog(
                exportFormat = state.pendingExportFormat ?: ExportFormat.MP4,
                onAdFinished = {
                    viewModel.completeFallbackExportAd()
                }
            )
        }
    }
}

@Composable
private fun TopEditorHeader(
    videoTrack: ShortVideoTrack,
    maxChars: Int,
    isExporting: Boolean,
    onPickVideo: () -> Unit,
    onToggleMuteVideo: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141722))
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
                text = "9:16 (${EditorViewModel.formatMs(videoTrack.effectiveDurationMs)}) • Tối đa $maxChars ký tự",
                color = Color(0xFF80D8FF),
                fontSize = 10.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMuteVideo,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (videoTrack.muteOriginalAudio) Color(0xFFD32F2F) else Color(0xFF263238),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = if (videoTrack.muteOriginalAudio) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Tắt/Bật tiếng Video MP4",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            OutlinedButton(
                onClick = onPickVideo,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Chọn MP4 9:16", color = Color.White, fontSize = 11.sp)
            }

            Button(
                onClick = onExport,
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isExporting) "Đang xuất..." else "Xuất File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Thanh Banner Quảng Cáo Treo Tự Động:
 * - Tự động hiển thị AdMob Banner (`AdSize.BANNER`) và có Banner tài trợ dự phòng khi chưa nạp mạng.
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
                            text = "AD",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LKHK.TTS • Chuyển Text thành Voice AI & Sub (1000 ký tự)",
                        color = Color(0xFFE0E0E0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Quảng cáo",
                    color = Color(0xFF80D8FF),
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

/**
 * KHU VIEW XEM TRƯỚC VIDEO 9:16:
 * - Hệ tọa độ kéo thả đồng nhất, hiển thị rõ ràng các đoạn Video 9:16 đã cắt, Phụ đề Karaoke, Tiêu đề và Sticker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Vertical9By16PreviewStage(
    videoTrack: ShortVideoTrack,
    playheadMs: Long,
    isPlaying: Boolean,
    subtitleTokens: List<TimelineSubtitleToken>,
    subtitleStyle: SubtitleOverlayStyle,
    visibleTextOverlays: List<CanvasTextOverlayItem>,
    visibleImageOverlays: List<CanvasImageOverlayItem>,
    selectedClipId: String?,
    onSelectOverlay: (String?) -> Unit,
    onDragSubtitle: (deltaNormX: Float, deltaNormY: Float) -> Unit,
    onDragTextOverlay: (id: String, deltaNormX: Float, deltaNormY: Float) -> Unit,
    onDragImageOverlay: (id: String, deltaNormX: Float, deltaNormY: Float) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val activeVideoSegment = remember(videoTrack.segments, playheadMs, videoTrack.speedMultiplier) {
        val speed = videoTrack.speedMultiplier
        videoTrack.segments.lastOrNull { seg ->
            playheadMs in seg.timelineStartMs..seg.timelineEndAtSpeedMs(speed)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1F33), Color(0xFF0E121D), Color(0xFF1D152B))
                )
            )
            .border(1.5.dp, Color(0xFF343D56), RoundedCornerShape(12.dp))
    ) {
        val stageWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val stageHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

        // 1. LỚP VIDEO MP4 9:16
        if (videoTrack.videoUri.isNotBlank() && activeVideoSegment != null) {
            val exoPlayer = remember(videoTrack.videoUri) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(videoTrack.videoUri)))
                    prepare()
                }
            }

            LaunchedEffect(videoTrack.speedMultiplier) {
                exoPlayer.playbackParameters = PlaybackParameters(videoTrack.speedMultiplier)
            }

            LaunchedEffect(videoTrack.muteOriginalAudio) {
                exoPlayer.volume = if (videoTrack.muteOriginalAudio) 0f else 1f
            }

            LaunchedEffect(isPlaying, activeVideoSegment.id) {
                val relMs = (playheadMs - activeVideoSegment.timelineStartMs).coerceAtLeast(0L)
                val targetSourcePosMs = (activeVideoSegment.sourceStartMs + (relMs * videoTrack.speedMultiplier).toLong())
                    .coerceIn(activeVideoSegment.sourceStartMs, activeVideoSegment.sourceEndMs)
                if (isPlaying) {
                    exoPlayer.seekTo(targetSourcePosMs)
                    exoPlayer.play()
                } else {
                    exoPlayer.pause()
                    exoPlayer.seekTo(targetSourcePosMs)
                }
            }

            LaunchedEffect(playheadMs, activeVideoSegment) {
                val relMs = (playheadMs - activeVideoSegment.timelineStartMs).coerceAtLeast(0L)
                val sourceVideoPositionMs = (activeVideoSegment.sourceStartMs + (relMs * videoTrack.speedMultiplier).toLong())
                    .coerceIn(activeVideoSegment.sourceStartMs, activeVideoSegment.sourceEndMs)
                if (!isPlaying) {
                    exoPlayer.seekTo(sourceVideoPositionMs)
                } else if (abs(exoPlayer.currentPosition - sourceVideoPositionMs) > 280L) {
                    exoPlayer.seekTo(sourceVideoPositionMs)
                }
            }

            DisposableEffect(exoPlayer) {
                onDispose { exoPlayer.release() }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "KHU VIEW 9:16",
                    color = Color(0xFF64FFDA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Bấm 'Chọn MP4 9:16' để nạp Video (≤ 3p)\n• Bấm 'Tạo Voice+Sub' để chuyển Text thành Giọng đọc\n• Kéo thả Phụ đề, Tiêu đề & Sticker tại đây",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 2. CÁC LỚP STICKER (Kéo thả 2D + Làm mờ Opacity & Blur)
        visibleImageOverlays.forEach { imgItem ->
            val isSelected = imgItem.id == selectedClipId
            val currentOnDragImg by rememberUpdatedState(onDragImageOverlay)
            val bitmap = remember(imgItem.imageUri) {
                runCatching {
                    val uri = Uri.parse(imgItem.imageUri)
                    if (uri.scheme == "file" && uri.path != null) {
                        BitmapFactory.decodeFile(uri.path)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()
            }

            val offsetX = ((imgItem.normalizedX - 0.5f) * stageWidthPx).roundToInt()
            val offsetY = ((imgItem.normalizedY - 0.5f) * stageHeightPx).roundToInt()

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(imgItem.sizeDp.dp)
                    .alpha(imgItem.opacity.coerceIn(0.05f, 1.0f))
                    .let { mod ->
                        if (imgItem.blurRadiusDp > 0.5f) mod.blur(imgItem.blurRadiusDp.dp) else mod
                    }
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectOverlay(imgItem.id) }
                    .pointerInput(imgItem.id, stageWidthPx, stageHeightPx) {
                        detectDragGestures(
                            onDragStart = { onSelectOverlay(imgItem.id) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDragImg(
                                    imgItem.id,
                                    dragAmount.x / stageWidthPx,
                                    dragAmount.y / stageHeightPx
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = imgItem.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x8800E5FF), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "STICKER",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. CÁC LỚP TIÊU ĐỀ (Kéo thả 2D + Làm mờ Opacity & Blur)
        visibleTextOverlays.forEach { textItem ->
            val isSelected = textItem.id == selectedClipId
            val currentOnDragText by rememberUpdatedState(onDragTextOverlay)
            val offsetX = ((textItem.normalizedX - 0.5f) * stageWidthPx).roundToInt()
            val offsetY = ((textItem.normalizedY - 0.5f) * stageHeightPx).roundToInt()

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(offsetX, offsetY) }
                    .alpha(textItem.opacity.coerceIn(0.05f, 1.0f))
                    .let { mod ->
                        if (textItem.blurRadiusDp > 0.5f) mod.blur(textItem.blurRadiusDp.dp) else mod
                    }
                    .background(Color(textItem.backgroundColorHex), RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0x55FFFFFF),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectOverlay(textItem.id) }
                    .pointerInput(textItem.id, stageWidthPx, stageHeightPx) {
                        detectDragGestures(
                            onDragStart = { onSelectOverlay(textItem.id) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDragText(
                                    textItem.id,
                                    dragAmount.x / stageWidthPx,
                                    dragAmount.y / stageHeightPx
                                )
                            }
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = textItem.text,
                    color = Color(textItem.colorHex),
                    fontSize = textItem.fontSizeSp.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 4. KHUNG PHỤ ĐỀ VOICE AI KARAOKE (Chạm để chỉnh Size chữ Phụ đề + Kéo thả vị trí bất kỳ)
        if (subtitleTokens.isNotEmpty()) {
            val currentOnDragSub by rememberUpdatedState(onDragSubtitle)
            val subOffsetX = ((subtitleStyle.normalizedX - 0.5f) * stageWidthPx).roundToInt()
            val subOffsetY = ((subtitleStyle.normalizedY - 0.5f) * stageHeightPx).roundToInt()
            val isSubSelected = selectedClipId == "SUBTITLE_STYLE"

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(subOffsetX, subOffsetY) }
                    .padding(horizontal = 10.dp)
                    .alpha(subtitleStyle.opacity.coerceIn(0.1f, 1.0f))
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .border(
                        width = if (isSubSelected) 2.dp else 1.dp,
                        color = if (isSubSelected) Color(0xFF00E5FF) else Color(0x99FFD600),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectOverlay("SUBTITLE_STYLE") }
                    .pointerInput(stageWidthPx, stageHeightPx) {
                        detectDragGestures(
                            onDragStart = { onSelectOverlay("SUBTITLE_STYLE") },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDragSub(
                                    dragAmount.x / stageWidthPx,
                                    dragAmount.y / stageHeightPx
                                )
                            }
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    subtitleTokens.forEach { token ->
                        Text(
                            text = "${token.word} ",
                            color = if (token.isHighlighted) Color(0xFFFFEA00) else Color.White,
                            fontSize = subtitleStyle.fontSizeSp.sp,
                            lineHeight = (subtitleStyle.fontSizeSp * 1.25f).sp,
                            fontWeight = if (token.isHighlighted) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleFontSizeAndStyleBar(
    subtitleStyle: SubtitleOverlayStyle,
    onUpdateFontSize: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A2218))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "💬 Size Sub: ${subtitleStyle.fontSizeSp.toInt()}sp",
            color = Color(0xFFFFEA00),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(96.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2E3B2B))
                .clickable { onUpdateFontSize((subtitleStyle.fontSizeSp - 1f).coerceAtLeast(10f)) }
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text("-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = subtitleStyle.fontSizeSp,
            onValueChange = onUpdateFontSize,
            valueRange = 10f..36f,
            modifier = Modifier
                .weight(1.2f)
                .height(22.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2E3B2B))
                .clickable { onUpdateFontSize((subtitleStyle.fontSizeSp + 1f).coerceAtMost(36f)) }
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }

        Text(
            text = "Rõ ${(subtitleStyle.opacity * 100).toInt()}%",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.width(50.dp)
        )
        Slider(
            value = subtitleStyle.opacity,
            onValueChange = onUpdateOpacity,
            valueRange = 0.1f..1.0f,
            modifier = Modifier
                .weight(0.8f)
                .height(22.dp)
        )
    }
}

@Composable
private fun SelectedTextBlurAndOpacityBar(
    item: CanvasTextOverlayItem,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateBlur: (Float) -> Unit,
    onUpdateFontSize: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B192E))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "🔤 Size Tiêu đề: ${item.fontSizeSp.toInt()}sp",
                color = Color(0xFF80D8FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(114.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF312C51))
                    .clickable { onUpdateFontSize((item.fontSizeSp - 2f).coerceAtLeast(10f)) }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = item.fontSizeSp,
                onValueChange = onUpdateFontSize,
                valueRange = 10f..56f,
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF312C51))
                    .clickable { onUpdateFontSize((item.fontSizeSp + 2f).coerceAtMost(56f)) }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Độ rõ ${(item.opacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.width(64.dp)
            )
            Slider(
                value = item.opacity,
                onValueChange = onUpdateOpacity,
                valueRange = 0.1f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
            )
            Text(
                text = "Làm mờ ${item.blurRadiusDp.toInt()}dp",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = item.blurRadiusDp,
                onValueChange = onUpdateBlur,
                valueRange = 0f..20f,
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
            )
        }
    }
}

@Composable
private fun SelectedImageBlurAndOpacityBar(
    item: CanvasImageOverlayItem,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateBlur: (Float) -> Unit,
    onUpdateSize: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF142826))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Độ rõ ${(item.opacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.width(64.dp)
            )
            Slider(
                value = item.opacity,
                onValueChange = onUpdateOpacity,
                valueRange = 0.05f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
            )
            Text(
                text = "Làm mờ ${item.blurRadiusDp.toInt()}dp",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = item.blurRadiusDp,
                onValueChange = onUpdateBlur,
                valueRange = 0f..25f,
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
            )
            Text(
                text = "Size ${item.sizeDp.toInt()}",
                color = Color(0xFF80D8FF),
                fontSize = 10.sp,
                modifier = Modifier.width(44.dp)
            )
            Slider(
                value = item.sizeDp,
                onValueChange = onUpdateSize,
                valueRange = 32f..180f,
                modifier = Modifier
                    .weight(0.8f)
                    .height(22.dp)
            )
        }
    }
}

@Composable
private fun PlaybackTransportBar(
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    statusMessage: String,
    onTogglePlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141824))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                text = "${EditorViewModel.formatMs(playheadMs)} / ${EditorViewModel.formatMs(totalDurationMs)}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                color = Color(0xFF90CAF9),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Thanh công cụ dưới cùng:
 * 1. Tạo Voice+Sub
 * 2. Cắt (Dùng chung cho Cắt chia đoạn & Cắt Video/Âm: chọn vào đối tượng và vị trí bấm cắt)
 * 3. Tiêu đề
 * 4. Sticker
 * 5. Xóa
 */
@Composable
private fun BottomCapCutToolbar(
    onOpenTtsCreator: () -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onOpenTextOverlaySheet: () -> Unit,
    onOpenStickerSheet: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161925))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapCutToolItem(
            icon = Icons.Default.Mic,
            label = "Tạo Voice+Sub",
            accentColor = Color(0xFF00E676),
            onClick = onOpenTtsCreator
        )
        CapCutToolItem(
            icon = Icons.Default.ContentCut,
            label = "Cắt",
            accentColor = Color(0xFFFFD600),
            onClick = onSplitAtPlayhead
        )
        CapCutToolItem(
            icon = Icons.Default.TextFields,
            label = "Tiêu đề",
            accentColor = Color(0xFFCE93D8),
            onClick = onOpenTextOverlaySheet
        )
        CapCutToolItem(
            icon = Icons.Default.AddPhotoAlternate,
            label = "Sticker",
            accentColor = Color(0xFF80CBC4),
            onClick = onOpenStickerSheet
        )
        CapCutToolItem(
            icon = Icons.Default.Delete,
            label = "Xóa",
            accentColor = Color(0xFFFF5252),
            onClick = onDeleteSelected
        )
    }
}

@Composable
private fun CapCutToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
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
                    text = "LỰA CHỌN XUẤT FILE: MP3 HOẶC MP4",
                    color = Color(0xFF00E5FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Bấm chọn định dạng xuất file -> Xem xong quảng cáo hệ thống sẽ tự động xuất file về máy.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                            text = "Xuất File MP3 (Âm Thanh Voice AI)",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (hasVoiceClips) "Ghép các đoạn Voice trên Timeline" else "Cần tạo ít nhất 1 đoạn Voice+Sub",
                            color = Color(0xFF1B5E20),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                            text = if (hasVideoLoaded) "Video 9:16 + Phụ đề + Tiêu đề + Sticker" else "Cần chọn Video MP4 9:16 trước khi xuất",
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

@Composable
private fun AddTextOverlaySheet(
    onAddText: (text: String, fontSizeSp: Float, colorHex: Long, opacity: Float, blurDp: Float) -> Unit
) {
    var inputText by remember { mutableStateOf("Tiêu đề Video") }
    var fontSizeSp by remember { mutableFloatStateOf(20f) }
    var opacity by remember { mutableFloatStateOf(1.0f) }
    var blurDp by remember { mutableFloatStateOf(0f) }
    var selectedColorHex by remember { mutableLongStateOf(0xFFFFFFFF) }

    val colorPresets = listOf(
        "Trắng" to 0xFFFFFFFF,
        "Vàng" to 0xFFFFEA00,
        "Xanh Ngọc" to 0xFF00E5FF,
        "Hồng" to 0xFFFF80AB,
        "Xanh Lá" to 0xFF69F0AE
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "🔤 Thêm Tiêu đề & Làm Mờ",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Nội dung Tiêu đề hiển thị trên Video") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Màu chữ:", color = Color.White, fontSize = 12.sp)
            colorPresets.forEach { (label, hex) ->
                FilterChip(
                    selected = selectedColorHex == hex,
                    onClick = { selectedColorHex = hex },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Độ rõ (Opacity): ${(opacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(145.dp)
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = 0.1f..1.0f,
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Làm mờ nhòe (Blur): ${blurDp.toInt()} dp",
                color = Color(0xFFFFD54F),
                fontSize = 12.sp,
                modifier = Modifier.width(145.dp)
            )
            Slider(
                value = blurDp,
                onValueChange = { blurDp = it },
                valueRange = 0f..20f,
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Size Tiêu đề: ${fontSizeSp.toInt()} sp",
                color = Color(0xFF80D8FF),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.width(145.dp)
            )
            Slider(
                value = fontSizeSp,
                onValueChange = { fontSizeSp = it },
                valueRange = 10f..56f,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = { onAddText(inputText, fontSizeSp, selectedColorHex, opacity, blurDp) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Chèn Tiêu đề vào Khu View 9:16 (Kéo thả tự do)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun AddStickerOverlaySheet(
    onPickPngFromDevice: () -> Unit,
    onCreateSampleBadgePng: (String) -> Unit
) {
    var badgeLabel by remember { mutableStateOf("STICKER") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🖼️ Thêm Sticker (Làm Mờ & Kéo Thả Tự Do)",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = onPickPngFromDevice,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Chọn ảnh Sticker (PNG/JPG) từ Điện thoại",
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            text = "Hoặc tạo nhanh nhãn Sticker trong suốt để gắn lên Video:",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = badgeLabel,
                onValueChange = { badgeLabel = it },
                label = { Text("Nhãn Sticker") },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onCreateSampleBadgePng(badgeLabel.ifBlank { "STICKER" }) }
            ) {
                Text("+ Tạo Sticker", color = Color(0xFF80CBC4), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun OfflineTtsCreatorSheet(
    installedVoices: List<OfflineVoiceProfile>,
    selectedVoice: OfflineVoiceProfile,
    inputText: String,
    speechRate: Float,
    subtitleFontSizeSp: Float,
    maxCharQuota: Int,
    isSynthesizing: Boolean,
    onSelectVoice: (OfflineVoiceProfile) -> Unit,
    onPreviewVoice: (OfflineVoiceProfile) -> Unit,
    onTextChange: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSubtitleFontSizeChange: (Float) -> Unit,
    onRenderTts: () -> Unit
) {
    var localTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = inputText,
                selection = TextRange(inputText.length)
            )
        )
    }

    LaunchedEffect(inputText) {
        if (inputText != localTextFieldValue.text) {
            localTextFieldValue = localTextFieldValue.copy(
                text = inputText,
                selection = TextRange(inputText.length)
            )
        }
    }

    val currentLength = localTextFieldValue.text.length

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎙️ Chuyển Text thành Voice + Sub",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF153827))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$currentLength/$maxCharQuota ký tự",
                    color = Color(0xFF69F0AE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Dùng tự do tất cả 9 giọng đọc • Cho phép tối đa $maxCharQuota ký tự mỗi lần tạo",
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            installedVoices.forEach { profile ->
                val isSelected = profile.id == selectedVoice.id
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF1E3A5F) else Color(0xFF222634))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF394056),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectVoice(profile) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column {
                        Text(
                            text = profile.displayName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${profile.regionTag} • ${profile.styleTag}",
                            color = Color(0xFF80D8FF),
                            fontSize = 10.sp
                        )
                    }
                    IconButton(
                        onClick = { onPreviewVoice(profile) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Nghe thử",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = localTextFieldValue,
            onValueChange = { newValue ->
                val cappedText = if (newValue.text.length > maxCharQuota) {
                    newValue.text.take(maxCharQuota)
                } else {
                    newValue.text
                }
                localTextFieldValue = if (cappedText.length != newValue.text.length) {
                    newValue.copy(
                        text = cappedText,
                        selection = TextRange(cappedText.length.coerceAtMost(newValue.selection.end))
                    )
                } else {
                    newValue
                }
                onTextChange(cappedText)
            },
            label = {
                Text("Nhập văn bản (${localTextFieldValue.text.length}/$maxCharQuota ký tự • Tự động sinh Sub phụ đề)")
            },
            trailingIcon = {
                if (localTextFieldValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            localTextFieldValue = TextFieldValue("")
                            onTextChange("")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa trắng",
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tốc độ đọc: ${String.format("%.2fx", speechRate)}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(130.dp)
            )
            Slider(
                value = speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.7f..1.5f,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Size Text Phụ đề: ${subtitleFontSizeSp.toInt()}sp",
                color = Color(0xFFFFEA00),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(130.dp)
            )
            Slider(
                value = subtitleFontSizeSp,
                onValueChange = onSubtitleFontSizeChange,
                valueRange = 10f..36f,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = onRenderTts,
            enabled = !isSynthesizing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00C853)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isSynthesizing) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Đang kết xuất Voice ONNX + Sub...", fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = "🎙️ Chuyển Text thành Voice + Sub ($currentLength/$maxCharQuota ký tự)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Màn hình Quảng Cáo Tự Động Toàn Màn Hình khi bấm Xuất File (MP3 hoặc MP4):
 * Đếm ngược 5 giây và tự động tiến hành xuất file ngay sau khi quảng cáo kết thúc.
 */
@Composable
private fun AutoExportAdOverlayDialog(
    exportFormat: ExportFormat,
    onAdFinished: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(5) }
    val currentOnFinished by rememberUpdatedState(onAdFinished)

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        }
        delay(350L)
        currentOnFinished()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF090D16))
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF182032))
                    .border(1.5.dp, Color(0xFFFFD54F), RoundedCornerShape(18.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFD54F))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "QUẢNG CÁO TRƯỚC KHI XUẤT FILE ${exportFormat.name}",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = "LKHK.TTS Studio",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Đang chuyển quảng cáo... Hệ thống sẽ tự động xuất file ${exportFormat.name} ngay sau khi quảng cáo kết thúc.",
                    color = Color(0xFFCFD8DC),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                LinearProgressIndicator(
                    progress = { (5 - secondsLeft) / 5f },
                    color = Color(0xFF00E676),
                    trackColor = Color(0xFF2C3852),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                if (secondsLeft > 0) {
                    Text(
                        text = "⏳ Tự động xuất file ${exportFormat.name} sau ${secondsLeft}s...",
                        color = Color(0xFF80D8FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Button(
                        onClick = onAdFinished,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ Bắt Đầu Xuất File ${exportFormat.name} Ngay",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
