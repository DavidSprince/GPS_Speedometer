package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class DRouteApplication : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private lateinit var appOpenAdManager: AppOpenAdManager
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()
        registerActivityLifecycleCallbacks(this)
        
        // Initialize the Mobile Ads SDK
        MobileAds.initialize(this) { initializationStatus ->
            Log.d("DRouteApp", "Mobile Ads SDK Initialized: $initializationStatus")
        }
        
        appOpenAdManager = AppOpenAdManager()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // DefaultLifecycleObserver method when app comes to foreground or cold starts
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        currentActivity?.let { activity ->
            Log.d("DRouteApp", "App foregrounded - attempting to show app open ad.")
            appOpenAdManager.showAdIfAvailable(activity)
        }
    }

    // Lifecycle callbacks to track current activity
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }

    /** Inner class to handle loading/showing App Open Ads */
    inner class AppOpenAdManager {
        private var appOpenAd: AppOpenAd? = null
        private var isLoadingAd = false
        private var isShowingAd = false
        private var loadTime: Long = 0

        private val adUnitId = "ca-app-pub-5457728037768344/8625927649"

        /** Request an ad */
        fun loadAd(context: Context) {
            if (isLoadingAd || isAdAvailable()) {
                return
            }

            isLoadingAd = true
            val request = AdRequest.Builder().build()
            Log.d("DRouteApp", "Loading App Open Ad with Unit ID: $adUnitId")
            
            AppOpenAd.load(
                context,
                adUnitId,
                request,
                AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        Log.d("DRouteApp", "App Open Ad loaded successfully.")
                        appOpenAd = ad
                        isLoadingAd = false
                        loadTime = Date().time
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e("DRouteApp", "App Open Ad failed to load: ${loadAdError.message}")
                        isLoadingAd = false
                    }
                }
            )
        }

        /** Check if ad exists and has not expired (must be within 4 hours) */
        private fun isAdAvailable(): Boolean {
            val numCachedHours = 4
            val wasLoadedWithinCachedHours = (Date().time - loadTime) < (numCachedHours * 3600000)
            return appOpenAd != null && wasLoadedWithinCachedHours
        }

        /** Show the ad if available */
        fun showAdIfAvailable(activity: Activity) {
            if (isShowingAd) {
                Log.d("DRouteApp", "App Open Ad is already showing.")
                return
            }

            if (!isAdAvailable()) {
                Log.d("DRouteApp", "Ad not available. Requesting a new one.")
                loadAd(activity)
                return
            }

            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("DRouteApp", "Ad dismissed full screen content.")
                    appOpenAd = null
                    isShowingAd = false
                    loadAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e("DRouteApp", "Ad failed to show: ${adError.message}")
                    appOpenAd = null
                    isShowingAd = false
                    loadAd(activity)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("DRouteApp", "Ad showed full screen content.")
                    isShowingAd = true
                }
            }

            appOpenAd?.show(activity)
        }
    }
}
