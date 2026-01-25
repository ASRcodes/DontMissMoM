package com.example.dontmissmom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopSirenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val stopIntent = Intent(context, EmergencySoundService::class.java).apply {
            action = EmergencySoundService.ACTION_STOP
        }
        context.startService(stopIntent)
    }
}
