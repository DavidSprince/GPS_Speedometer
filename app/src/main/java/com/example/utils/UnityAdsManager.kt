package com.example.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

object UnityAdsManager {
    private const val TAG = "UnityAdsManager"
    const val GAME_ID = "800003175"
    const val TEST_MODE = true // Highly recommended for builds in AI Studio Sandbox and Emulator

    const val REWARDED_PLACEMENT = "Rewarded_Android"
    const val BANNER_PLACEMENT = "Banner_Android"

    var isInitialized = false
        private set

    var isRewardedAdLoaded = false
        private set

    fun initialize(context: Context) {
        if (isInitialized) return

        Log.d(TAG, "Initializing Unity Ads with Game ID: $GAME_ID")
        UnityAds.initialize(context.applicationContext, GAME_ID, TEST_MODE, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                isInitialized = true
                Log.d(TAG, "Unity Ads Initialization Complete.")
                // Preload rewarded ad once initialized
                loadRewardedAd(context.applicationContext)
            }

            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                isInitialized = false
                Log.e(TAG, "Unity Ads Initialization Failed: [$error] $message")
            }
        })
    }

    fun loadRewardedAd(context: Context) {
        if (!isInitialized) {
            Log.w(TAG, "Cannot load rewarded ad: Unity Ads is not initialized.")
            return
        }

        Log.d(TAG, "Loading Rewarded Ad: $REWARDED_PLACEMENT")
        UnityAds.load(REWARDED_PLACEMENT, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                Log.d(TAG, "Rewarded Ad Loaded Successfully: $placementId")
                isRewardedAdLoaded = true
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                Log.e(TAG, "Rewarded Ad Failed to Load: $placementId, error: $error, msg: $message")
                isRewardedAdLoaded = false
            }
        })
    }

    fun showRewardedAd(activity: Activity, onComplete: () -> Unit = {}, onFailure: () -> Unit = {}) {
        if (!isRewardedAdLoaded) {
            Log.w(TAG, "Rewarded ad is not loaded yet. Attempting to reload and falling back.")
            loadRewardedAd(activity.applicationContext)
            onFailure()
            return
        }

        Log.d(TAG, "Showing Rewarded Ad: $REWARDED_PLACEMENT")
        UnityAds.show(activity, REWARDED_PLACEMENT, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                Log.e(TAG, "Rewarded Ad failed to show: $placementId, error: $error, msg: $message")
                isRewardedAdLoaded = false
                loadRewardedAd(activity.applicationContext)
                onFailure()
            }

            override fun onUnityAdsShowStart(placementId: String?) {
                Log.d(TAG, "Rewarded Ad Show Started: $placementId")
            }

            override fun onUnityAdsShowClick(placementId: String?) {
                Log.d(TAG, "Rewarded Ad Clicked: $placementId")
            }

            override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                Log.d(TAG, "Rewarded Ad Show Completed: $placementId, state: $state")
                isRewardedAdLoaded = false
                // Load the next ad for future use
                loadRewardedAd(activity.applicationContext)
                onComplete()
            }
        })
    }
}
