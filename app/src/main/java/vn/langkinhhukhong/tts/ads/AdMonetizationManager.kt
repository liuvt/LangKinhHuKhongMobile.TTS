package vn.langkinhhukhong.tts.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * Bộ quản lý Kiếm tiền từ Quảng Cáo (AdMob + Quảng cáo Nhà tài trợ riêng của bạn):
 *
 * 1. Đảm bảo người dùng phải BẬT MẠNG và XEM QUẢNG CÁO CỦA BẠN (Rewarded Video Ad)
 *    thì mới tải được giọng đọc mới từ Thư viện Online hoặc gia hạn thêm lượt dùng (+5 lượt/lần xem).
 * 2. Hiển thị Quảng cáo Toàn màn hình (Interstitial Ad) khi người dùng Xuất Video MP4.
 * 3. Lưu trữ số lượt dùng còn lại (Quota) của từng giọng đọc Online trong SharedPreferences.
 */
class AdMonetizationManager(
    private val context: Context,
    val config: MonetizationConfig = MonetizationConfig()
) {
    private val prefs = context.getSharedPreferences("lkhk_ad_quota_prefs", Context.MODE_PRIVATE)

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null

    init {
        runCatching {
            MobileAds.initialize(context) {}
            preloadRewardedAd()
            preloadInterstitialAd()
        }
    }

    /**
     * Kiểm tra điện thoại có đang kết nối Internet không (Bắt buộc có mạng để tải thư viện Online & hiển thị Quảng cáo).
     */
    fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Kiểm tra giọng đọc Online đã từng được mở khóa bằng Quảng cáo hay chưa.
     */
    fun isVoiceUnlocked(voiceId: String, isBuiltIn: Boolean = false): Boolean {
        if (isBuiltIn) return true
        return prefs.getBoolean("unlocked_$voiceId", false) || prefs.getInt("quota_$voiceId", 0) > 0
    }

    fun markVoiceUnlocked(voiceId: String, unlocked: Boolean = true) {
        prefs.edit().putBoolean("unlocked_$voiceId", unlocked).apply()
    }

    /**
     * Lấy số lượt tạo giọng đọc còn lại của một giọng trong Thư viện Online.
     */
    fun getRemainingUses(voiceId: String, isBuiltIn: Boolean): Int {
        if (isBuiltIn) return 999
        return prefs.getInt("quota_$voiceId", 0)
    }

    /**
     * Cộng thêm lượt dùng sau khi người dùng đã xem xong 1 Video Quảng Cáo của bạn.
     */
    fun addRewardedUses(voiceId: String, bonusUses: Int = config.rewardUsesPerAdWatch): Int {
        val current = prefs.getInt("quota_$voiceId", 0).coerceAtLeast(0)
        val updated = current + bonusUses
        prefs.edit()
            .putBoolean("unlocked_$voiceId", true)
            .putInt("quota_$voiceId", updated)
            .apply()
        incrementTotalAdsWatched()
        return updated
    }

    /**
     * Trừ 1 lượt dùng mỗi khi người dùng Render thành công 1 đoạn MP3 bằng giọng đọc Online.
     */
    fun consumeOneUse(voiceId: String, requiresAd: Boolean): Int {
        if (!requiresAd) return 999
        val current = prefs.getInt("quota_$voiceId", 0)
        val next = (current - 1).coerceAtLeast(0)
        prefs.edit().putInt("quota_$voiceId", next).apply()
        return next
    }

    fun getTotalAdsWatchedByUser(): Int = prefs.getInt("total_ads_watched", 0)

    private fun incrementTotalAdsWatched() {
        val total = getTotalAdsWatchedByUser() + 1
        prefs.edit().putInt("total_ads_watched", total).apply()
    }

    /**
     * Tải trước Quảng cáo Có Thưởng (Rewarded Video Ad) từ Google AdMob.
     */
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

    /**
     * Tải trước Quảng cáo Toàn màn hình (Interstitial Ad) khi Xuất Video.
     */
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
     * Hiển thị Quảng cáo Có Thưởng (Rewarded Ad) của AdMob nếu đã sẵn sàng trên Activity,
     * hoặc gọi [onRequireFallbackAdDialog] để hiển thị Video Quảng cáo Trực tiếp (Self-Hosted Sponsor Ad)
     * của chính bạn nhằm đảm bảo 100% lượt mở khóa đều hiển thị quảng cáo!
     */
    fun showRewardedAdOrSponsorFallback(
        activity: Activity?,
        onRewardEarned: () -> Unit,
        onRequireFallbackAdDialog: () -> Unit
    ) {
        val ad = rewardedAd
        if (activity != null && ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadRewardedAd()
                }
            }
            ad.show(activity) { _ ->
                onRewardEarned()
            }
        } else {
            // Khi AdMob đang chờ nạp hoặc chạy trên máy test, bật Quảng cáo Trực tiếp của bạn
            preloadRewardedAd()
            onRequireFallbackAdDialog()
        }
    }

    /**
     * Hiển thị Quảng cáo Toàn màn hình trước khi Xuất file MP4.
     */
    fun showInterstitialBeforeExport(
        activity: Activity?,
        onProceedExport: () -> Unit
    ) {
        val ad = interstitialAd
        if (activity != null && ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitialAd()
                    incrementTotalAdsWatched()
                    onProceedExport()
                }
            }
            ad.show(activity)
        } else {
            preloadInterstitialAd()
            onProceedExport()
        }
    }
}
