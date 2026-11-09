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
 * Quản lý Hạn mức tối đa 1000 Ký tự & Quảng Cáo Theo Lượt Xuất File (MP3/MP4) + Banner Tự Động:
 *
 * - Cho phép sử dụng TẤT CẢ 9 giọng đọc không khóa giọng nào.
 * - Giới hạn tối đa **1000 ký tự** ([MAX_CHARACTER_QUOTA]).
 * - Quảng cáo theo lượt xuất file: bấm xuất file thì chuyển quảng cáo xong mới cho xuất + treo banner quảng cáo tự động.
 */
class AdMonetizationManager(
    private val context: Context,
    val config: MonetizationConfig = MonetizationConfig()
) {
    companion object {
        const val MAX_CHARACTER_QUOTA = 1000
        const val ADMOB_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val KEY_REMAINING_CHARS = "remaining_char_quota_v3_1000"
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

    fun getRemainingCharacters(): Int {
        return prefs.getInt(KEY_REMAINING_CHARS, MAX_CHARACTER_QUOTA)
            .coerceIn(0, MAX_CHARACTER_QUOTA)
    }

    fun consumeCharacters(usedCount: Int): Int {
        val current = getRemainingCharacters()
        val next = (current - usedCount.coerceAtLeast(0)).coerceIn(0, MAX_CHARACTER_QUOTA)
        prefs.edit().putInt(KEY_REMAINING_CHARS, next).apply()
        return next
    }

    fun refillFullCharacterQuota(): Int {
        prefs.edit().putInt(KEY_REMAINING_CHARS, MAX_CHARACTER_QUOTA).apply()
        incrementTotalAdsWatched()
        return MAX_CHARACTER_QUOTA
    }

    // Giữ tương thích cho các lời gọi từ VoiceLibraryRepository (tất cả giọng đều mở sẵn)
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

    fun triggerAutoAdForQuotaRefill(
        activity: Activity?,
        onQuotaRefilled: (newQuota: Int) -> Unit,
        onShowFallbackAdOverlay: () -> Unit
    ) {
        triggerAdBeforeExport(
            activity = activity,
            onAdCompleted = { onQuotaRefilled(refillFullCharacterQuota()) },
            onShowFallbackAdOverlay = onShowFallbackAdOverlay
        )
    }

    /**
     * Quảng cáo theo lượt xuất file (MP3 hoặc MP4):
     * Bấm xuất file -> chuyển quảng cáo xong mới cho xuất file.
     */
    fun triggerAdBeforeExport(
        activity: Activity?,
        onAdCompleted: () -> Unit,
        onShowFallbackAdOverlay: () -> Unit
    ) {
        val iAd = interstitialAd
        if (activity != null && iAd != null) {
            iAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitialAd()
                    refillFullCharacterQuota()
                    onAdCompleted()
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

        val rAd = rewardedAd
        if (activity != null && rAd != null) {
            rAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadRewardedAd()
                    refillFullCharacterQuota()
                    onAdCompleted()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    preloadRewardedAd()
                    onShowFallbackAdOverlay()
                }
            }
            rAd.show(activity) {
                // Người dùng đã xem quảng cáo
            }
            return
        }

        preloadRewardedAd()
        preloadInterstitialAd()
        onShowFallbackAdOverlay()
    }
}
