package com.example.ui

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.utils.UnityAdsManager
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

@Composable
fun UnityBannerAdView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp)
            .background(Color(0xFF04060C)), // Rich Space Dark Deep Blue matches the app's background
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            factory = { context ->
                val activity = context as? Activity
                if (activity != null) {
                    val bannerView = BannerView(
                        activity,
                        UnityAdsManager.BANNER_PLACEMENT,
                        UnityBannerSize(320, 50)
                    )
                    bannerView.listener = object : BannerView.IListener {
                        override fun onBannerLoaded(bannerAdView: BannerView?) {
                            Log.d("UnityBannerAdView", "Unity Banner ad loaded successfully.")
                        }

                        override fun onBannerClick(bannerAdView: BannerView?) {
                            Log.d("UnityBannerAdView", "Unity Banner ad clicked.")
                        }

                        override fun onBannerFailedToLoad(
                            bannerAdView: BannerView?,
                            errorInfo: BannerErrorInfo?
                        ) {
                            Log.e("UnityBannerAdView", "Unity Banner failed to load: [${errorInfo?.errorCode}] ${errorInfo?.errorMessage}")
                        }

                        override fun onBannerLeftApplication(bannerAdView: BannerView?) {}
                        override fun onBannerShown(bannerAdView: BannerView?) {
                            Log.d("UnityBannerAdView", "Unity Banner ad shown.")
                        }
                    }
                    bannerView.load()
                    bannerView
                } else {
                    android.view.View(context)
                }
            },
            update = { /* No updating state required */ }
        )
    }
}
