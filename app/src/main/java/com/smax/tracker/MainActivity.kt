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

class MainActivity : LauncherActivity() {

    companion object {
        private const val REQ_FINE_LOCATION = 1001
        private const val REQ_BACKGROUND_LOCATION = 1002
        private const val REQ_NOTIF = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        injectAccumulatedTripIntoWebView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            if (requestCode == REQ_FINE_LOCATION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationIfNeeded()
            }
        } catch (e: Exception) {
        }
    }

    

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val bgGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!bgGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQ_BACKGROUND_LOCATION
            )
        }
    }
    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val bgGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!bgGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQ_BACKGROUND_LOCATION
            )
        }
    }

    fun startBackgroundTracking() {
        val intent = Intent(this, GpsForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    fun stopBackgroundTracking() {
        val intent = Intent(this, GpsForegroundService::class.java).apply {
            action = GpsForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    private fun injectAccumulatedTripIntoWebView() {
        if (TrackingState.distanceKm <= 0.0) return
        val webView = findWebView(window.decorView.rootView) ?: return

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
