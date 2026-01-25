package com.example.dontmissmom

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    private var noInternetView: View? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Setup the Callback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { hideNoInternetScreen() }
            }

            override fun onLost(network: Network) {
                runOnUiThread { showNoInternetScreen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register callback when activity is active
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Initial Check
        if (!isNetworkAvailable()) {
            showNoInternetScreen()
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister to save battery
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternetScreen() {
        // Only add if not already added
        if (noInternetView == null) {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            noInternetView = LayoutInflater.from(this).inflate(R.layout.layout_no_internet, rootView, false)


            rootView.addView(noInternetView)
        }
    }

    private fun hideNoInternetScreen() {
        if (noInternetView != null) {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            rootView.removeView(noInternetView)
            noInternetView = null
        }
    }
}