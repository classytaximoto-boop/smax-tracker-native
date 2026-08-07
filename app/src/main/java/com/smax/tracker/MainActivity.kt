package com.smax.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.androidbrowserhelper.trusted.LauncherActivity

class MainActivity : LauncherActivity() {

    private val requestFineLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private val requestBackgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        injectAccumulatedTripIntoWebView()
    }

    private fun ensurePermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!bgGranted) requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
