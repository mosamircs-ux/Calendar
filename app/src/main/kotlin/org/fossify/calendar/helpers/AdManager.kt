package org.fossify.calendar.helpers

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.fossify.calendar.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

object AdManager {
    private const val INTERSTITIAL_COOLDOWN_MS = 180_000L // 3 minutes between interstitials
    private const val ACTIONS_BEFORE_FIRST_INTERSTITIAL = 2

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var lastInterstitialShownAt = 0L
    private var qualifyingActionCount = 0

    fun isAdsEnabled(): Boolean =
        BuildConfig.ENABLE_ADS && BuildConfig.ADMOB_BANNER_ID.isNotBlank()

    fun requestConsentAndInitialize(activity: Activity, onReady: () -> Unit) {
        if (!isAdsEnabled()) return

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds(activity.applicationContext) {
                            onReady()
                        }
                    }
                }
            },
            {
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds(activity.applicationContext) {
                        onReady()
                    }
                }
            }
        )

        if (consentInformation.canRequestAds()) {
            initializeMobileAds(activity.applicationContext) {
                onReady()
            }
        }
    }

    private fun initializeMobileAds(appContext: Context, onInitialized: () -> Unit) {
        if (isMobileAdsInitialized.compareAndSet(false, true)) {
            MobileAds.initialize(appContext) {
                preloadInterstitial(appContext)
                onInitialized()
            }
        } else {
            onInitialized()
        }
    }

    fun isPrivacyOptionsRequired(context: Context): Boolean {
        if (!isAdsEnabled()) return false
        return UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            onDismissed(formError?.message)
        }
    }

    fun loadAdaptiveBanner(activity: Activity, container: FrameLayout): AdView? {
        if (!isAdsEnabled()) {
            container.visibility = View.GONE
            return null
        }

        val adView = AdView(activity)
        adView.adUnitId = BuildConfig.ADMOB_BANNER_ID
        adView.setAdSize(getAdaptiveBannerSize(activity, container))
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                container.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (container.childCount == 0) {
                    container.visibility = View.GONE
                }
            }
        }

        container.removeAllViews()
        container.addView(adView)
        container.visibility = View.VISIBLE
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }

    fun preloadInterstitial(context: Context) {
        if (!isAdsEnabled() || BuildConfig.ADMOB_INTERSTITIAL_ID.isBlank()) return
        if (interstitialAd != null || isLoadingInterstitial) return

        val consentInfo = UserMessagingPlatform.getConsentInformation(context)
        if (!consentInfo.canRequestAds()) return

        isLoadingInterstitial = true
        InterstitialAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    fun maybeShowInterstitial(activity: Activity, onComplete: () -> Unit = {}) {
        if (!isAdsEnabled() || activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }

        qualifyingActionCount++
        val now = System.currentTimeMillis()
        val cooledDown = now - lastInterstitialShownAt >= INTERSTITIAL_COOLDOWN_MS
        val ad = interstitialAd

        if (qualifyingActionCount < ACTIONS_BEFORE_FIRST_INTERSTITIAL || !cooledDown || ad == null) {
            if (ad == null) preloadInterstitial(activity)
            onComplete()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial(activity)
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preloadInterstitial(activity)
                onComplete()
            }

            override fun onAdShowedFullScreenContent() {
                lastInterstitialShownAt = System.currentTimeMillis()
            }
        }

        ad.show(activity)
    }

    private fun getAdaptiveBannerSize(activity: Activity, container: FrameLayout): AdSize {
        val density = activity.resources.displayMetrics.density
        var adWidthPixels = container.width.toFloat()
        if (adWidthPixels <= 0f) {
            adWidthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.windowManager.currentWindowMetrics.bounds.width().toFloat()
            } else {
                @Suppress("DEPRECATION")
                val outMetrics = DisplayMetrics().also {
                    activity.windowManager.defaultDisplay.getMetrics(it)
                }
                outMetrics.widthPixels.toFloat()
            }
        }
        val adWidthDp = (adWidthPixels / density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp)
    }
}
