package vn.langkinhhukhong.tts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import vn.langkinhhukhong.tts.model.MonetizationConfig
import vn.langkinhhukhong.tts.model.OfflineVoiceProfile
import vn.langkinhhukhong.tts.model.VoiceInstallState
import vn.langkinhhukhong.tts.model.VoiceStorageSource

/**
 * Giao diện Thư Viện Giọng Đọc Online (Online Voice Library + Tích hợp Quảng cáo Kiếm tiền):
 * - Hiển thị Quảng cáo Banner (AdMob Banner + Banner Nhà tài trợ của bạn) ngay đầu thư viện.
 * - Các giọng đọc VIP trên Cloud yêu cầu người dùng bấm "🎬 Xem Quảng Cáo để Tải & Nhận +5 Lượt".
 * - Khi dùng hết 5 lượt tạo giọng đọc -> Người dùng bấm "🎬 Xem QC (+5 lượt)" để tiếp tục dùng,
 *   đảm bảo tạo doanh thu quảng cáo định kỳ lâu dài cho bạn.
 */
@Composable
fun VoiceLibrarySheet(
    voiceCatalog: List<OfflineVoiceProfile>,
    selectedVoiceId: String,
    monetizationConfig: MonetizationConfig,
    totalAdsWatched: Int,
    onSelectVoice: (OfflineVoiceProfile) -> Unit,
    onWatchAdToDownloadOrTopUp: (OfflineVoiceProfile) -> Unit,
    onPreviewVoice: (OfflineVoiceProfile) -> Unit,
    onDeleteVoice: (OfflineVoiceProfile) -> Unit,
    onRefreshOnlineCatalog: () -> Unit
) {
    var activeFilter by remember { mutableStateOf("Tất cả") }
    val filters = listOf("Tất cả", "Đã mở khóa", "VPBank (5 giọng)", "Miền Bắc", "Đa vùng miền")

    val filteredVoices = remember(voiceCatalog, activeFilter) {
        when (activeFilter) {
            "Đã mở khóa" -> voiceCatalog.filter { it.installState == VoiceInstallState.INSTALLED }
            "VPBank (5 giọng)" -> voiceCatalog.filter { it.numSpeakers > 1 || it.id.contains("vpbank") }
            "Miền Bắc", "Đa vùng miền" -> voiceCatalog.filter { it.regionTag == activeFilter }
            else -> voiceCatalog
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. BANNER QUẢNG CÁO CỦA BẠN (AdMob + Sponsor Banner)
        OwnerAdBannerSlot(monetizationConfig = monetizationConfig)

        // 2. Tiêu đề + Nút Làm mới từ Server Online
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🌐 Thư Viện Giọng Đọc Online (Mở Khóa Bằng Quảng Cáo)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Xem 1 Quảng cáo nhận ngay +${monetizationConfig.rewardUsesPerAdWatch} lượt tạo TTS • Đã xem: $totalAdsWatched QC",
                    color = Color(0xFFFFD54F),
                    fontSize = 11.sp
                )
            }

            OutlinedButton(
                onClick = onRefreshOnlineCatalog,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Đồng bộ Online", color = Color.White, fontSize = 11.sp)
            }
        }

        // 3. Bộ lọc vùng miền
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val selected = activeFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { activeFilter = filter },
                    label = { Text(filter, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF),
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        // 4. Danh sách Giọng đọc từ Server Online
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredVoices, key = { it.id }) { voice ->
                OnlineVoicePackCard(
                    voice = voice,
                    isCurrentlySelected = voice.id == selectedVoiceId,
                    onSelect = { onSelectVoice(voice) },
                    onWatchAdToUnlock = { onWatchAdToDownloadOrTopUp(voice) },
                    onPreview = { onPreviewVoice(voice) },
                    onDelete = { onDeleteVoice(voice) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Khung Quảng Cáo Banner tích hợp cả Google AdMob BannerView và Banner Nhà Tài Trợ của bạn.
 */
@Composable
fun OwnerAdBannerSlot(
    monetizationConfig: MonetizationConfig,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2B1B3D), Color(0xFF162944))
                )
            )
            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFB300), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "QUẢNG CÁO",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column {
                    Text(
                        text = monetizationConfig.customSponsorTitle,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = monetizationConfig.customSponsorCtaText,
                        color = Color(0xFFFFE082),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Khung AdMob Banner Native (sẽ tự hiển thị khi có mạng & AdMob cấp phát)
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = monetizationConfig.admobBannerUnitId
                        runCatching { loadAd(AdRequest.Builder().build()) }
                    }
                },
                modifier = Modifier.height(38.dp)
            )
        }
    }
}

@Composable
private fun OnlineVoicePackCard(
    voice: OfflineVoiceProfile,
    isCurrentlySelected: Boolean,
    onSelect: () -> Unit,
    onWatchAdToUnlock: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = when {
        isCurrentlySelected -> Color(0xFF00E5FF)
        voice.isReadyToRender -> Color(0xFF2E7D32)
        voice.needsAdTopUp -> Color(0xFFFF9100)
        else -> Color(0xFF343B4F)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1D2232))
            .border(
                width = if (isCurrentlySelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = voice.displayName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2C344B), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = voice.regionTag,
                            color = Color(0xFF80D8FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF332940), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = voice.styleTag,
                            color = Color(0xFFFFD180),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${voice.description} • ${voice.sizeMb} MB",
                    color = Color(0xFFA6B0C3),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onPreview,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Nghe thử",
                    tint = Color(0xFF00E5FF)
                )
            }
        }

        when (voice.installState) {
            VoiceInstallState.DOWNLOADING -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Đang tải giọng đọc từ Server Online (${voice.modelFileName})...",
                            color = Color(0xFF80D8FF),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${(voice.downloadProgress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { voice.downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF2B3245)
                    )
                }
            }

            VoiceInstallState.NOT_DOWNLOADED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🔒 Mở khóa bằng Quảng cáo",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = onWatchAdToUnlock,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xem QC để Tải (+${voice.adRewardUsesPerView} lượt)",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            VoiceInstallState.INSTALLED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (voice.needsAdTopUp) Color(0xFFFF9100) else Color(0xFF00E676),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                voice.storageSource == VoiceStorageSource.BUILT_IN_ASSET ->
                                    "Miễn phí mặc định (Không giới hạn)"
                                voice.needsAdTopUp ->
                                    "Hết lượt dùng! Xem QC để nạp +${voice.adRewardUsesPerView} lượt"
                                else ->
                                    "Còn ${voice.remainingQuotaUses} lượt tạo TTS"
                            },
                            color = if (voice.needsAdTopUp) Color(0xFFFFB74D) else Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (voice.storageSource != VoiceStorageSource.BUILT_IN_ASSET) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Xóa",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = onWatchAdToUnlock,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🎬 +${voice.adRewardUsesPerView} lượt",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!voice.needsAdTopUp) {
                            Button(
                                onClick = onSelect,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCurrentlySelected) Color(0xFF00E676) else Color(0xFF2B354F)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isCurrentlySelected) "✓ Đang chọn" else "Dùng giọng này",
                                    color = if (isCurrentlySelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Trình phát Video Quảng Cáo Có Thưởng (Rewarded Ad Player):
 * - Đảm bảo người dùng phải xem đủ thời gian đếm ngược của Quảng cáo thì mới nhận được
 *   quyền Tải giọng đọc hoặc Cộng thêm lượt tạo TTS (+5 lượt).
 */
@Composable
fun RewardedSponsorAdDialog(
    voiceTarget: OfflineVoiceProfile,
    monetizationConfig: MonetizationConfig,
    onAdCompletedRewardGranted: () -> Unit,
    onCancelAd: () -> Unit
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
            if (secondsLeft == 0) onCancelAd()
        },
        properties = DialogProperties(
            dismissOnBackPress = secondsLeft == 0,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121622))
                .border(2.dp, Color(0xFFFFB300), RoundedCornerShape(16.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFB300), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "QUẢNG CÁO NHẬN THƯỞNG",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = if (secondsLeft > 0) {
                        "Còn $secondsLeft giây để nhận thưởng..."
                    } else {
                        "✓ Đã đủ điều kiện nhận thưởng!"
                    },
                    color = if (secondsLeft > 0) Color(0xFFFFD54F) else Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Nội dung Video / Banner Quảng cáo của bạn
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1F2A48), Color(0xFF2B193E))
                        )
                    )
                    .border(1.dp, Color(0xFF455A64), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🎬 ${monetizationConfig.customSponsorTitle}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Đang tài trợ mở khóa giọng đọc:\n\"${voiceTarget.displayName}\"",
                        color = Color(0xFF80D8FF),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = monetizationConfig.customSponsorCtaText,
                        color = Color(0xFFFFE082),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelAd,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Bỏ qua (Không nhận)", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                }

                Button(
                    onClick = onAdCompletedRewardGranted,
                    enabled = secondsLeft == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        disabledContainerColor = Color(0xFF2E3546)
                    ),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text(
                        text = if (secondsLeft > 0) {
                            "Chờ ${secondsLeft}s..."
                        } else {
                            "Nhận Giọng +${voiceTarget.adRewardUsesPerView} Lượt!"
                        },
                        color = if (secondsLeft == 0) Color.Black else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
