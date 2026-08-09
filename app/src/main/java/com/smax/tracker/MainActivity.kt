package com.smax.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.androidbrowserhelper.trusted.LauncherActivity
import android.webkit.JavascriptInterface

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
        try {
            startGpsService()
        } catch (e: Exception) {
        }
        try {
            attachFuelBridge()
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            injectAccumulatedTripIntoWebView()
        } catch (e: Exception) {
        }
        try {
            attachFuelBridge()
        } catch (e: Exception) {
        }
    }

    private fun attachFuelBridge() {
        val handler = android.os.Handler(mainLooper)
        var attempts = 0
        val tryAttach = object : Runnable {
            override fun run() {
                attempts++
                val root = window?.decorView?.rootView
                val webView = if (root != null) findWebView(root) else null
                if (webView != null && webView.tag != "fuel_bridge_attached") {
                    webView.addJavascriptInterface(FuelBridge(), "AndroidFuelBridge")
                    webView.tag = "fuel_bridge_attached"
                } else if (webView == null && attempts < 15) {
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(tryAttach)
    }

    inner class FuelBridge {
        @JavascriptInterface
        fun reportConsumption(litersPer100km: Double) {
            TrackingState.consoL100 = litersPer100km
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

    private fun startGpsService() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            val intent = Intent(this, GpsForegroundService::class.java)
            ContextCompat.startForegroundService(this, intent)
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
