package com.smax.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.androidbrowserhelper.trusted.LauncherActivity

class MainActivity : LauncherActivity() {

    companion object {
        private const val REQ_PERMISSIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ensurePermissions()
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            injectAccumulatedTripIntoWebView()
        } catch (e: Exception) {
        }
    }

    private fun ensurePermissions() {
        val missing = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private fun injectAccumulatedTripIntoWebView() {
        if (TrackingState.distanceKm <= 0.0) return
        val root = window?.decorView?.rootView ?: return
        val webView = findWebView(root) ?: return

        val deltaKm = TrackingState.distanceKm

        val js = """
            if (window.__fuelOnTripDistanceKm) {
                window.__fuelOnTripDistanceKm($deltaKm);
            }
            if (window.__smaxOnBackgroundGpsPosition) {
                window.__smaxOnBackgroundGpsPosition(${TrackingState.lastLat}, ${TrackingState.lastLon}, ${TrackingState.speedKmh});
            }
        """.trimIndent()

        webView.evaluateJavascript(js, null)

        TrackingState.distanceKm = 0.0
    }

    private fun findWebView(view: android.view.View): WebView? {
        if (view is WebView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findWebView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
