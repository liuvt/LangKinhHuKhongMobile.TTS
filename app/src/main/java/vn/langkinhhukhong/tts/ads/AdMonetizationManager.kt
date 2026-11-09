package vn.langkinhhukhong.tts.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import vn.langkinhhukhong.tts.model.MonetizationConfig

/**
 * Quản lý Hạn mức 500 Ký tự & Tự động nhảy Quảng Cáo:
 *
 * - Cho phép sử dụng TẤT CẢ các giọng đọc (`Minh Khang`, `Ngọc Huyền`, `Lạc Phi`, `Minh Quang`, `VPBank` 5 giọng) không khóa giọng nào.
 * - Giới hạn hạn mức mỗi chu kỳ là **500 ký tự** ([MAX_CHARACTER_QUOTA]).
 * - Ngay khi dùng hết 500 ký tự (`remainingChars <= 0`) hoặc văn bản vượt quá số ký tự còn lại,
 *   hệ thống **tự động nhảy quảng cáo** (AdMob Rewarded / Interstitial + Fullscreen Sponsor Ad dự phòng)
 *   và cấp lại đầy **500 ký tự** sau khi quảng cáo kết thúc.
 */
class AdMonetizationManager(
    private val context: Context,
    val config: MonetizationConfig = MonetizationConfig()
) {
    companion object {
        const val MAX_CHARACTER_QUOTA = 500
        private const val KEY_REMAINING_CHARS = "remaining_char_quota_v2"
        private const val KEY_TOTAL_ADS_WATCHED = "total_ads_watched"
    }

    private val prefs = context.getSharedPreferences("lkhk_ad_quota_prefs", Context.MODE_PRIVATE)

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null

    init {
        if (!prefs.contains(KEY_REMAINING_CHARS)) {
            prefs.edit().putInt(KEY_REMAINING_CHARS, MAX_CHARACTER_QUOTA).apply()
        }
        runCatching {
            MobileAds.initialize(context) {
                preloadRewardedAd()
                preloadInterstitialAd()
            }
            preloadRewardedAd()
            preloadInterstitialAd()
        }
    }

    fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Số ký tự miễn phí còn lại trong chu kỳ 500 ký tự hiện tại (`0..500`).
     */
    fun getRemainingCharacters(): Int {
        return prefs.getInt(KEY_REMAINING_CHARS, MAX_CHARACTER_QUOTA)
            .coerceIn(0, MAX_CHARACTER_QUOTA)
    }

    /**
     * Trừ số ký tự vừa chuyển thành Voice. Trả về số ký tự còn lại (`0..500`).
     */
    fun consumeCharacters(usedCount: Int): Int {
        val current = getRemainingCharacters()
        val next = (current - usedCount.coerceAtLeast(0)).coerceIn(0, MAX_CHARACTER_QUOTA)
        prefs.edit().putInt(KEY_REMAINING_CHARS, next).apply()
        return next
    }

    /**
     * Khôi phục đầy 500 ký tự sau khi quảng cáo hoàn tất.
     */
    fun refillFullCharacterQuota(): Int {
        prefs.edit().putInt(KEY_REMAINING_CHARS, MAX_CHARACTER_QUOTA).apply()
        incrementTotalAdsWatched()
        return MAX_CHARACTER_QUOTA
    }

    // Giữ tương thích cho các lời gọi cũ từ VoiceLibraryRepository (tất cả giọng đều mở sẵn)
    fun isVoiceUnlocked(@Suppress("UNUSED_PARAMETER") voiceId: String, @Suppress("UNUSED_PARAMETER") isBuiltIn: Boolean = true): Boolean = true
    fun markVoiceUnlocked(@Suppress("UNUSED_PARAMETER") voiceId: String, @Suppress("UNUSED_PARAMETER") unlocked: Boolean = true) {}
    fun getRemainingUses(@Suppress("UNUSED_PARAMETER") voiceId: String, @Suppress("UNUSED_PARAMETER") isBuiltIn: Boolean): Int = 999
    fun addRewardedUses(@Suppress("UNUSED_PARAMETER") voiceId: String, @Suppress("UNUSED_PARAMETER") bonusUses: Int = 999): Int = 999

    fun getTotalAdsWatchedByUser(): Int = prefs.getInt(KEY_TOTAL_ADS_WATCHED, 0)

    private fun incrementTotalAdsWatched() {
        val total = getTotalAdsWatchedByUser() + 1
        prefs.edit().putInt(KEY_TOTAL_ADS_WATCHED, total).apply()
    }

    fun preloadRewardedAd() {
        runCatching {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                context,
                config.admobRewardedUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                    }
                }
            )
        }
    }

    fun preloadInterstitialAd() {
        runCatching {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                config.admobInterstitialUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                    }
                }
            )
        }
    }

    /**
     * Tự động hiển thị Quảng cáo ngay khi hết 500 ký tự:
     * 1. Ưu tiên AdMob Rewarded Video Ad.
     * 2. Nếu Rewarded chưa sẵn sàng, hiển thị AdMob Interstitial Ad.
     * 3. Nếu thiết bị chưa nạp kịp AdMob (máy ảo / mất mạng tạm / no-fill), tự động bật Fullscreen Ad Overlay đếm ngược 5 giây.
     */
    fun triggerAutoAdForQuotaRefill(
        activity: Activity?,
        onQuotaRefilled: (newQuota: Int) -> Unit,
        onShowFallbackAdOverlay: () -> Unit
    ) {
        val rAd = rewardedAd
        if (activity != null && rAd != null) {
            var earnedReward = false
            rAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadRewardedAd()
                    if (earnedReward) {
                        onQuotaRefilled(refillFullCharacterQuota())
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    preloadRewardedAd()
                    onShowFallbackAdOverlay()
                }
            }
            rAd.show(activity) {
                earnedReward = true
            }
            return
        }

        val iAd = interstitialAd
        if (activity != null && iAd != null) {
            iAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitialAd()
                    onQuotaRefilled(refillFullCharacterQuota())
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    preloadInterstitialAd()
                    onShowFallbackAdOverlay()
                }
            }
            iAd.show(activity)
            return
        }

        preloadRewardedAd()
        preloadInterstitialAd()
        onShowFallbackAdOverlay()
    }
}
