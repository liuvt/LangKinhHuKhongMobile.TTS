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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieEdit
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import vn.langkinhhukhong.tts.model.CanvasImageOverlayItem
import vn.langkinhhukhong.tts.model.CanvasTextOverlayItem
import vn.langkinhhukhong.tts.model.EditorConstraints
import vn.langkinhhukhong.tts.model.OfflineVoiceProfile
import vn.langkinhhukhong.tts.model.ShortVideoTrack
import vn.langkinhhukhong.tts.model.SubtitleOverlayStyle
import vn.langkinhhukhong.tts.model.TimelineSubtitleToken
import vn.langkinhhukhong.tts.ui.components.MultiTrackTimeline
import vn.langkinhhukhong.tts.ui.components.RewardedSponsorAdDialog
import vn.langkinhhukhong.tts.ui.components.VoiceLibrarySheet
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
    var showVoiceLibrarySheet by remember { mutableStateOf(false) }
    var showTextCreatorSheet by remember { mutableStateOf(false) }
    var showLogoCreatorSheet by remember { mutableStateOf(false) }
    var showVideoEditBar by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importShortVideo(uri)
        }
    }

    val logoPngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importLogoPngOverlay(uri)
            showLogoCreatorSheet = false
        }
    }

    val secondaryMp3PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { viewModel.importSecondaryMp3(it) }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B0D13)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. THANH TIÊU ĐỀ TRÊN CÙNG (Nạp Video 9:16 <= 3p, Bật/Tắt tiếng Video, Xuất MP4)
            TopEditorHeader(
                videoTrack = state.videoTrack,
                totalAdsWatched = state.totalAdsWatched,
                isExporting = state.isExportingVideo,
                onPickVideo = { videoPickerLauncher.launch("video/*") },
                onToggleMuteVideo = { viewModel.toggleMuteOriginalVideo() },
                onExport = { viewModel.exportFinalVideo(activity) }
            )

            // 2. KHU VIEW XEM TRƯỚC VIDEO DỌC 9:16 + KÉO THẢ TỰ DO (PHỤ ĐỀ + CHỮ TEXT + ICON LOGO PNG)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
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

            // 3. THANH CHỈNH NHANH LÀM MỜ (OPACITY & BLUR) KHI ĐANG CHỌN TEXT HOẶC LOGO PNG
            val selectedText = state.selectedTextOverlay
            val selectedImage = state.selectedImageOverlay
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
            }

            // 4. THANH CẮT NGẮN VIDEO MP4 + BẬT/TẮT TIẾNG GỐC + TỐC ĐỘ VIDEO
            if (showVideoEditBar) {
                VideoTrimAndAudioControlBar(
                    videoTrack = state.videoTrack,
                    onToggleMute = { viewModel.toggleMuteOriginalVideo() },
                    onTrimStartAtPlayhead = { viewModel.trimVideoStartAtPlayhead() },
                    onTrimEndAtPlayhead = { viewModel.trimVideoEndAtPlayhead() },
                    onResetTrim = { viewModel.resetVideoTrim() },
                    onSelectSpeed = { viewModel.setVideoSpeed(it) }
                )
            }

            // 5. THANH TRẠNG THÁI & THỜI GIAN PLAYHEAD + ĐIỀU KHIỂN PHÁT
            PlaybackTransportBar(
                playheadMs = state.playheadMs,
                totalDurationMs = state.totalTimelineDurationMs,
                isPlaying = state.isPlaying,
                statusMessage = state.statusMessage,
                onTogglePlay = { viewModel.togglePlayPause() }
            )

            // 6. THANH TIMELINE ĐA TẦNG DẠNG PART (CAPCUT STYLE)
            MultiTrackTimeline(
                videoTrack = state.videoTrack,
                voiceClips = state.voiceClips,
                textOverlays = state.textOverlays,
                imageOverlays = state.imageOverlays,
                secondaryMp3Clips = state.secondaryMp3Clips,
                totalDurationMs = state.totalTimelineDurationMs,
                playheadMs = state.playheadMs,
                selectedClipId = state.selectedClipId,
                onSeek = { viewModel.seekTo(it) },
                onSelectClip = { viewModel.selectClip(it) },
                onToggleMuteVideo = { viewModel.toggleMuteOriginalVideo() },
                onTrimVideoStartAtPlayhead = { viewModel.trimVideoStartAtPlayhead() },
                onTrimVideoEndAtPlayhead = { viewModel.trimVideoEndAtPlayhead() },
                onResetVideoTrim = { viewModel.resetVideoTrim() },
                onDragVoiceClip = { clipId, deltaMs ->
                    viewModel.moveVoiceClipByDelta(clipId, deltaMs)
                },
                onDragTextOverlay = { id, deltaMs ->
                    viewModel.moveTextOverlayOnTimeline(id, deltaMs)
                },
                onDragImageOverlay = { id, deltaMs ->
                    viewModel.moveImageOverlayOnTimeline(id, deltaMs)
                },
                onDragSecondaryMp3 = { clipId, deltaMs ->
                    viewModel.moveSecondaryMp3ByDelta(clipId, deltaMs)
                }
            )

            // 7. THANH CÔNG CỤ CAPCUT DƯỚI CÙNG
            BottomCapCutToolbar(
                onOpenTtsCreator = { showTtsSheet = true },
                onOpenVoiceLibrary = { showVoiceLibrarySheet = true },
                onSplitAtPlayhead = { viewModel.splitSelectedOrActiveClipAtPlayhead() },
                onOpenTextOverlaySheet = { showTextCreatorSheet = true },
                onOpenLogoPngSheet = { showLogoCreatorSheet = true },
                onToggleVideoEditBar = { showVideoEditBar = !showVideoEditBar },
                onImportSecondaryMp3 = { secondaryMp3PickerLauncher.launch("audio/*") },
                onDeleteSelected = { viewModel.deleteSelectedClip() }
            )
        }

        // BẢNG 1: CHUYỂN TEXT THÀNH VOICE AI + TỰ ĐỘNG TẠO SUB PHỤ ĐỀ KÈM THEO
        if (showTtsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTtsSheet = false },
                containerColor = Color(0xFF161924)
            ) {
                OfflineTtsCreatorSheet(
                    installedVoices = state.installedVoices,
                    selectedVoice = state.selectedVoiceProfile,
                    inputText = state.ttsInputText,
                    speechRate = state.ttsSpeechRate,
                    isSynthesizing = state.isSynthesizingTts,
                    onSelectVoice = { viewModel.selectVoiceProfile(it) },
                    onOpenVoiceLibrary = {
                        showTtsSheet = false
                        showVoiceLibrarySheet = true
                    },
                    onWatchAdTopUp = { voice ->
                        viewModel.requestWatchAdForVoice(activity, voice)
                    },
                    onTextChange = { viewModel.updateTtsInputText(it) },
                    onSpeechRateChange = { viewModel.updateTtsSpeechRate(it) },
                    onRenderTts = {
                        viewModel.renderOfflineTtsAndAttachSubtitle(activity)
                        if (state.selectedVoiceProfile.isReadyToRender) {
                            showTtsSheet = false
                        }
                    }
                )
            }
        }

        // BẢNG 2: THƯ VIỆN GIỌNG ĐỌC ONLINE (Tích hợp Quảng cáo Banner & Rewarded Video Ad)
        if (showVoiceLibrarySheet) {
            ModalBottomSheet(
                onDismissRequest = { showVoiceLibrarySheet = false },
                containerColor = Color(0xFF141824)
            ) {
                VoiceLibrarySheet(
                    voiceCatalog = state.voiceCatalog,
                    selectedVoiceId = state.selectedVoiceProfile.id,
                    monetizationConfig = state.monetizationConfig,
                    totalAdsWatched = state.totalAdsWatched,
                    onSelectVoice = { profile ->
                        viewModel.selectVoiceProfile(profile)
                        showVoiceLibrarySheet = false
                        showTtsSheet = true
                    },
                    onWatchAdToDownloadOrTopUp = { profile ->
                        viewModel.requestWatchAdForVoice(activity, profile)
                    },
                    onPreviewVoice = { profile ->
                        viewModel.previewVoiceSample(profile)
                    },
                    onDeleteVoice = { profile ->
                        viewModel.deleteDownloadedVoice(profile)
                    },
                    onRefreshOnlineCatalog = {
                        viewModel.syncOnlineVoiceCatalog()
                    }
                )
            }
        }

        // BẢNG 3: THÊM CHỮ (TEXT OVERLAY) + LÀM MỜ OPACITY/BLUR
        if (showTextCreatorSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTextCreatorSheet = false },
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

        // BẢNG 4: THÊM ICON / LOGO PNG + LÀM MỜ OPACITY/BLUR
        if (showLogoCreatorSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLogoCreatorSheet = false },
                containerColor = Color(0xFF161924)
            ) {
                AddLogoPngOverlaySheet(
                    onPickPngFromDevice = {
                        logoPngPickerLauncher.launch("image/*")
                    },
                    onCreateSampleBadgePng = { label ->
                        viewModel.addSampleLogoPngOverlay(label)
                        showLogoCreatorSheet = false
                    }
                )
            }
        }

        // TRÌNH PHÁT VIDEO QUẢNG CÁO CÓ THƯỞNG
        val pendingVoice = state.pendingAdVoiceTarget
        if (pendingVoice != null) {
            RewardedSponsorAdDialog(
                voiceTarget = pendingVoice,
                monetizationConfig = state.monetizationConfig,
                onAdCompletedRewardGranted = {
                    viewModel.onUserCompletedWatchingAd(pendingVoice)
                },
                onCancelAd = {
                    viewModel.dismissSponsorAdDialog()
                }
            )
        }
    }
}

@Composable
private fun TopEditorHeader(
    videoTrack: ShortVideoTrack,
    totalAdsWatched: Int,
    isExporting: Boolean,
    onPickVideo: () -> Unit,
    onToggleMuteVideo: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141722))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Lăng Kính Hư Không • CapCut TTS Studio",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "9:16 (≤ 3p) • ${EditorViewModel.formatMs(videoTrack.effectiveDurationMs)} • ${videoTrack.speedMultiplier}x • QC: $totalAdsWatched",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút Tắt/Bật nhanh tiếng gốc của Video MP4
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
                    modifier = Modifier.size(17.dp)
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
                Text("Nạp MP4 9:16", color = Color.White, fontSize = 11.sp)
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
                Text(if (isExporting) "Đang xuất..." else "Xuất MP4", fontSize = 11.sp)
            }
        }
    }
}

/**
 * KHU VIEW XEM TRƯỚC VIDEO 9:16 CHUẨN CAPCUT:
 * - Phát Video đã cắt ngắn (trimStartMs..trimEndMs) + tắt/bật tiếng gốc MP4 (muteOriginalAudio).
 * - Cho phép KÉO THẢ VỊ TRÍ BẤT KỲ (2D Drag) trực tiếp đối với:
 *   1. Các lớp Icon / Logo PNG (kèm làm mờ Opacity & Blur)
 *   2. Các lớp Chữ Text Overlay (kèm làm mờ Opacity & Blur)
 *   3. Khung Phụ đề Karaoke đồng bộ theo từng đoạn MP3 Voice
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1F33), Color(0xFF0E121D), Color(0xFF1D152B))
                )
            )
            .border(1.dp, Color(0xFF2E354B), RoundedCornerShape(14.dp))
    ) {
        val stageWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val stageHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

        // 1. LỚP VIDEO MP4 9:16 (Áp dụng cắt ngắn trimStartMs..trimEndMs & tắt/bật tiếng gốc)
        if (videoTrack.videoUri.isNotBlank()) {
            val exoPlayer = remember(
                videoTrack.videoUri,
                videoTrack.trimStartMs,
                videoTrack.trimEndMs
            ) {
                ExoPlayer.Builder(context).build().apply {
                    val clippedItem = MediaItem.Builder()
                        .setUri(Uri.parse(videoTrack.videoUri))
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(videoTrack.trimStartMs)
                                .setEndPositionMs(videoTrack.trimEndMs)
                                .build()
                        )
                        .build()
                    setMediaItem(clippedItem)
                    prepare()
                }
            }

            LaunchedEffect(videoTrack.speedMultiplier) {
                exoPlayer.playbackParameters = PlaybackParameters(videoTrack.speedMultiplier)
            }

            LaunchedEffect(videoTrack.muteOriginalAudio) {
                exoPlayer.volume = if (videoTrack.muteOriginalAudio) 0f else 1f
            }

            LaunchedEffect(isPlaying) {
                if (isPlaying) exoPlayer.play() else exoPlayer.pause()
            }

            LaunchedEffect(playheadMs) {
                if (!isPlaying) {
                    val sourceVideoPositionMs = (playheadMs * videoTrack.speedMultiplier).toLong()
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
                    text = "KHU VIEW 9:16 (≤ 3 PHÚT)",
                    color = Color(0xFF64FFDA),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kéo thả Chữ (Text), Logo PNG & Phụ đề đến vị trí bất kỳ trên màn hình này",
                    color = Color(0xFF9AA5B8),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 2. CÁC LỚP ICON / LOGO PNG (Hỗ trợ làm mờ Opacity + Blur + Kéo thả vị trí bất kỳ)
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

            val sizePx = with(density) { imgItem.sizeDp.dp.toPx() }
            val offsetX = (imgItem.normalizedX * stageWidthPx - sizePx / 2f).roundToInt()
            val offsetY = (imgItem.normalizedY * stageHeightPx - sizePx / 2f).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(imgItem.sizeDp.dp)
                    .alpha(imgItem.opacity.coerceIn(0.05f, 1.0f))
                    .let { mod ->
                        if (imgItem.blurRadiusDp > 0.5f) mod.blur(imgItem.blurRadiusDp.dp) else mod
                    }
                    .then(
                        if (isSelected) {
                            Modifier.border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
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
                            text = "PNG",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. CÁC LỚP CHỮ TEXT OVERLAY (Hỗ trợ làm mờ Opacity + Blur + Kéo thả vị trí bất kỳ)
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

        // 4. KHUNG PHỤ ĐỀ VOICE AI KARAOKE (Kéo thả vị trí bất kỳ trên Khu View 9:16)
        if (subtitleTokens.isNotEmpty()) {
            val currentOnDragSub by rememberUpdatedState(onDragSubtitle)
            val subOffsetX = ((subtitleStyle.normalizedX - 0.5f) * stageWidthPx).roundToInt()
            val subOffsetY = ((subtitleStyle.normalizedY - 0.5f) * stageHeightPx).roundToInt()

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(subOffsetX, subOffsetY) }
                    .padding(horizontal = 10.dp)
                    .alpha(subtitleStyle.opacity.coerceIn(0.1f, 1.0f))
                    .let { mod ->
                        if (subtitleStyle.blurRadiusDp > 0.5f) mod.blur(subtitleStyle.blurRadiusDp.dp) else mod
                    }
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0x88FFD600), RoundedCornerShape(10.dp))
                    .pointerInput(stageWidthPx, stageHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            currentOnDragSub(
                                dragAmount.x / stageWidthPx,
                                dragAmount.y / stageHeightPx
                            )
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
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
                            fontWeight = if (token.isHighlighted) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thanh chỉnh nhanh Làm mờ (Opacity + Blur) và Cỡ chữ cho lớp Text đang chọn.
 */
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "🔤 Đang chỉnh Chữ: \"${item.text}\" (Kéo thả trên khung View để đổi vị trí)",
            color = Color(0xFFCE93D8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Độ rõ ${(item.opacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.width(68.dp)
            )
            Slider(
                value = item.opacity,
                onValueChange = onUpdateOpacity,
                valueRange = 0.1f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            )
            Text(
                text = "Làm nhòe ${item.blurRadiusDp.toInt()}dp",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                modifier = Modifier.width(78.dp)
            )
            Slider(
                value = item.blurRadiusDp,
                onValueChange = onUpdateBlur,
                valueRange = 0f..20f,
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            )
            Text(
                text = "Cỡ ${item.fontSizeSp.toInt()}",
                color = Color(0xFF80D8FF),
                fontSize = 10.sp,
                modifier = Modifier.width(40.dp)
            )
            Slider(
                value = item.fontSizeSp,
                onValueChange = onUpdateFontSize,
                valueRange = 12f..38f,
                modifier = Modifier
                    .weight(0.8f)
                    .height(24.dp)
            )
        }
    }
}

/**
 * Thanh chỉnh nhanh Làm mờ (Opacity + Blur) và Kích thước cho Icon / Logo PNG đang chọn.
 */
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "🖼️ Đang chỉnh Logo PNG: ${item.name} (Kéo thả trên khung View để đổi vị trí)",
            color = Color(0xFF80CBC4),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Độ rõ ${(item.opacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.width(68.dp)
            )
            Slider(
                value = item.opacity,
                onValueChange = onUpdateOpacity,
                valueRange = 0.05f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            )
            Text(
                text = "Làm mờ ${item.blurRadiusDp.toInt()}dp",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                modifier = Modifier.width(74.dp)
            )
            Slider(
                value = item.blurRadiusDp,
                onValueChange = onUpdateBlur,
                valueRange = 0f..25f,
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            )
            Text(
                text = "Size ${item.sizeDp.toInt()}",
                color = Color(0xFF80D8FF),
                fontSize = 10.sp,
                modifier = Modifier.width(46.dp)
            )
            Slider(
                value = item.sizeDp,
                onValueChange = onUpdateSize,
                valueRange = 32f..180f,
                modifier = Modifier
                    .weight(0.8f)
                    .height(24.dp)
            )
        }
    }
}

/**
 * Bảng công cụ Cắt ngắn Video MP4 + Tắt/Bật tiếng gốc của Video MP4 + Chỉnh tốc độ.
 */
@Composable
private fun VideoTrimAndAudioControlBar(
    videoTrack: ShortVideoTrack,
    onToggleMute: () -> Unit,
    onTrimStartAtPlayhead: () -> Unit,
    onTrimEndAtPlayhead: () -> Unit,
    onResetTrim: () -> Unit,
    onSelectSpeed: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2030))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleMute,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (videoTrack.muteOriginalAudio) Color(0xFFD32F2F) else Color(0xFF00C853)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (videoTrack.muteOriginalAudio) "🔇 Tiếng gốc MP4: Đang TẮT" else "🔊 Tiếng gốc MP4: Đang BẬT",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onTrimStartAtPlayhead,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("✂ Cắt bỏ Đầu Video tại Playhead", color = Color(0xFF80D8FF), fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onTrimEndAtPlayhead,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Cắt bỏ Đuôi Video tại Playhead ✂", color = Color(0xFF80D8FF), fontSize = 11.sp)
            }

            if (videoTrack.isTrimmed) {
                OutlinedButton(
                    onClick = onResetTrim,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("↺ Khôi phục gốc", color = Color(0xFFFFD54F), fontSize = 11.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Tốc độ Video:",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            EditorConstraints.SPEED_PRESETS.forEach { preset ->
                val selected = videoTrack.speedMultiplier == preset
                FilterChip(
                    selected = selected,
                    onClick = { onSelectSpeed(preset) },
                    label = { Text("${preset}x", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF),
                        selectedLabelColor = Color.Black
                    )
                )
            }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141824))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFF00E5FF), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${EditorViewModel.formatMs(playheadMs)} / ${EditorViewModel.formatMs(totalDurationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = statusMessage,
                color = Color(0xFF90CAF9),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun BottomCapCutToolbar(
    onOpenTtsCreator: () -> Unit,
    onOpenVoiceLibrary: () -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onOpenTextOverlaySheet: () -> Unit,
    onOpenLogoPngSheet: () -> Unit,
    onToggleVideoEditBar: () -> Unit,
    onImportSecondaryMp3: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161925))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapCutToolItem(
            icon = Icons.Default.Mic,
            label = "Text→Voice+Sub",
            accentColor = Color(0xFF00E676),
            onClick = onOpenTtsCreator
        )
        CapCutToolItem(
            icon = Icons.Default.ContentCut,
            label = "Cắt Chia Đoạn",
            accentColor = Color(0xFFFFD600),
            onClick = onSplitAtPlayhead
        )
        CapCutToolItem(
            icon = Icons.Default.MovieEdit,
            label = "Cắt Video/Âm",
            accentColor = Color(0xFFFF9100),
            onClick = onToggleVideoEditBar
        )
        CapCutToolItem(
            icon = Icons.Default.TextFields,
            label = "+Chữ (Text)",
            accentColor = Color(0xFFCE93D8),
            onClick = onOpenTextOverlaySheet
        )
        CapCutToolItem(
            icon = Icons.Default.AddPhotoAlternate,
            label = "+Logo PNG",
            accentColor = Color(0xFF80CBC4),
            onClick = onOpenLogoPngSheet
        )
        CapCutToolItem(
            icon = Icons.Default.CloudDownload,
            label = "Kho Giọng VIP",
            accentColor = Color(0xFFFFB300),
            onClick = onOpenVoiceLibrary
        )
        CapCutToolItem(
            icon = Icons.Default.LibraryMusic,
            label = "+MP3 Phụ",
            accentColor = Color(0xFF00E5FF),
            onClick = onImportSecondaryMp3
        )
        CapCutToolItem(
            icon = Icons.Default.Delete,
            label = "Xóa đoạn",
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
            .padding(horizontal = 7.dp, vertical = 4.dp),
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
private fun AddTextOverlaySheet(
    onAddText: (text: String, fontSizeSp: Float, colorHex: Long, opacity: Float, blurDp: Float) -> Unit
) {
    var inputText by remember { mutableStateOf("Tiêu đề Video Short") }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "🔤 Thêm Chữ (Text Overlay) & Làm Mờ",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Nội dung chữ hiển thị trên Video") },
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
                text = "Cỡ chữ: ${fontSizeSp.toInt()} sp",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(145.dp)
            )
            Slider(
                value = fontSizeSp,
                onValueChange = { fontSizeSp = it },
                valueRange = 12f..40f,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = { onAddText(inputText, fontSizeSp, selectedColorHex, opacity, blurDp) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Chèn Chữ vào Khu View 9:16 (Kéo thả tự do)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AddLogoPngOverlaySheet(
    onPickPngFromDevice: () -> Unit,
    onCreateSampleBadgePng: (String) -> Unit
) {
    var badgeLabel by remember { mutableStateOf("LKHK VIP") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🖼️ Chèn Icon / Logo PNG (Hỗ trợ Làm Mờ & Kéo Thả Tự Do)",
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
                text = "Chọn file Icon / Logo PNG từ Điện thoại",
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            text = "Hoặc tạo nhanh Icon/Logo PNG trong suốt để thử nghiệm ngay:",
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
                label = { Text("Nhãn Logo PNG") },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onCreateSampleBadgePng(badgeLabel.ifBlank { "LOGO" }) }
            ) {
                Text("+ Tạo PNG", color = Color(0xFF80CBC4), fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = "💡 Sau khi chèn, chạm vào Logo PNG trên khu View 9:16 để kéo đến vị trí bất kỳ và kéo thanh trượt Làm mờ (Opacity / Blur).",
            color = Color(0xFFFFD54F),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun OfflineTtsCreatorSheet(
    installedVoices: List<OfflineVoiceProfile>,
    selectedVoice: OfflineVoiceProfile,
    inputText: String,
    speechRate: Float,
    isSynthesizing: Boolean,
    onSelectVoice: (OfflineVoiceProfile) -> Unit,
    onOpenVoiceLibrary: () -> Unit,
    onWatchAdTopUp: (OfflineVoiceProfile) -> Unit,
    onTextChange: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onRenderTts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🎙️ Chuyển Text thành Voice + Sub Phụ Đề",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                onClick = onOpenVoiceLibrary,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kho Giọng VIP (Xem QC)", color = Color(0xFFFFD54F), fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            installedVoices.forEach { profile ->
                val isSelected = profile.id == selectedVoice.id
                val quotaText = if (profile.requiresAdToUnlock) {
                    "Còn ${profile.remainingQuotaUses} lượt"
                } else {
                    "Miễn phí"
                }
                Column(
                    modifier = Modifier
                        .width(205.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF1E3A5F) else Color(0xFF222634))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF394056),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectVoice(profile) }
                        .padding(10.dp)
                ) {
                    Text(
                        text = profile.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${profile.regionTag} • $quotaText",
                        color = if (profile.needsAdTopUp) Color(0xFFFFB74D) else Color(0xFF80D8FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChange,
            label = { Text("Nhập văn bản cần chuyển thành Voice (Tự động sinh Sub phụ đề kèm theo)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tốc độ đọc: ${String.format("%.2fx", speechRate)}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(120.dp)
            )
            Slider(
                value = speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.7f..1.5f,
                modifier = Modifier.weight(1f)
            )
        }

        if (selectedVoice.needsAdTopUp) {
            Button(
                onClick = { onWatchAdTopUp(selectedVoice) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.OndemandVideo,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Giọng VIP đã hết lượt • Xem 1 QC để nhận +${selectedVoice.adRewardUsesPerView} lượt!",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else {
            Button(
                onClick = onRenderTts,
                enabled = !isSynthesizing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                modifier = Modifier.fillMaxWidth()
            ) {
                val quotaSuffix = if (selectedVoice.requiresAdToUnlock) {
                    " (-1 lượt, còn ${selectedVoice.remainingQuotaUses})"
                } else {
                    ""
                }
                Text(
                    text = if (isSynthesizing) {
                        "Đang chuyển Text thành Voice + Sub..."
                    } else {
                        "Chuyển Text thành Voice + Tạo Sub Phụ Đề$quotaSuffix"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
