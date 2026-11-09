package vn.langkinhhukhong.tts.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quản lý giới hạn tối đa 1000 ký tự mỗi lần chuyển giọng đọc &
 * Quảng cáo theo lượt xuất file (MP3 / MP4) + Banner quảng cáo tự động:
 * - Cho phép dùng toàn bộ 9 giọng đọc (tối đa 1000 ký tự / lần tạo).
 * - Khi người dùng bấm Xuất File (MP3 hoặc MP4), hệ thống tự động hiển thị quảng cáo
 *   (AdMob Interstitial / Rewarded Ad hoặc màn hình quảng cáo đếm ngược 5 giây),
 *   chuyển quảng cáo xong mới tiến hành xuất file.
 */
class AdMonetizationManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AdMonetization"
        private const val PREFS_NAME = "lkhk_tts_quota_prefs"
        private const val KEY_EXPORT_COUNT = "export_count_total"

        // Tối đa 1000 ký tự cho mỗi lần nhập văn bản chuyển thành giọng đọc
        const val MAX_CHARACTER_QUOTA = 1000

        // AdMob Unit IDs (Test IDs chuẩn của Google AdMob cho Interstitial, Rewarded & Banner)
        const val ADMOB_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val ADMOB_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val ADMOB_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private val _exportCount = MutableStateFlow(loadSavedExportCount())
    val exportCount: StateFlow<Int> = _exportCount.asStateFlow()

    init {
        try {
            MobileAds.initialize(appContext) {
                preloadAds()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MobileAds init fallback: ${e.message}")
        }
    }

    private fun loadSavedExportCount(): Int {
        return prefs.getInt(KEY_EXPORT_COUNT, 0).coerceAtLeast(0)
    }

    fun recordExportSuccess() {
        val updated = _exportCount.value + 1
        _exportCount.value = updated
        prefs.edit().putInt(KEY_EXPORT_COUNT, updated).apply()
    }

    fun preloadAds() {
        loadInterstitialAd()
        loadRewardedAd()
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            appContext,
            ADMOB_INTERSTITIAL_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    updateAdLoadedFlag()
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    updateAdLoadedFlag()
                }
            }
        )
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            appContext,
            ADMOB_REWARDED_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    updateAdLoadedFlag()
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    updateAdLoadedFlag()
                }
            }
        )
    }

    private fun updateAdLoadedFlag() {
        _isAdLoaded.value = (interstitialAd != null || rewardedAd != null)
    }

    /**
     * Tự động nhảy quảng cáo khi người dùng bấm Xuất File (MP3 hoặc MP4).
     * Sau khi xem xong quảng cáo mới gọi [onAdCompleted] để tiến hành xuất file.
     */
    fun triggerAdBeforeExport(
        activity: Activity?,
        onAdCompleted: () -> Unit,
        onShowFallbackAdOverlay: () -> Unit
    ) {
        val currentInterstitial = interstitialAd
        if (activity != null && currentInterstitial != null) {
            currentInterstitial.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadAds()
                    onAdCompleted()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    preloadAds()
                    onShowFallbackAdOverlay()
                }
            }
            currentInterstitial.show(activity)
            return
        }

        val currentRewarded = rewardedAd
        if (activity != null && currentRewarded != null) {
            currentRewarded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadAds()
                    onAdCompleted()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    preloadAds()
                    onShowFallbackAdOverlay()
                }
            }
            currentRewarded.show(activity) { _ ->
                // Người dùng đã xem quảng cáo; chờ đóng quảng cáo hoặc xử lý tại onAdDismissedFullScreenContent
            }
            return
        }

        // Nếu AdMob đang nạp hoặc offline -> bật màn hình quảng cáo 5 giây trước khi xuất file
        preloadAds()
        onShowFallbackAdOverlay()
    }
}
