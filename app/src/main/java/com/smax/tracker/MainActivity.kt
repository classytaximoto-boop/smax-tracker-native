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
        private const val REQ_PERMISSIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.widget.Toast.makeText(this, "onCreate début", android.widget.Toast.LENGTH_SHORT).show()
        try {
            ensurePermissions()
            android.widget.Toast.makeText(this, "permissions OK", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "crash permissions: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        try {
            startGpsService()
            android.widget.Toast.makeText(this, "service OK", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "crash service: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        android.widget.Toast.makeText(this, "onResume début", android.widget.Toast.LENGTH_SHORT).show()
        try {
            injectAccumulatedTripIntoWebView()
            android.widget.Toast.makeText(this, "inject OK", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "crash inject: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
