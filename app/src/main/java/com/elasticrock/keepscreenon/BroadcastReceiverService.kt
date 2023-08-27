package com.elasticrock.keepscreenon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BATTERY_LOW
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BroadcastReceiverService : LifecycleService() {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screen_timeout")

    private val batteryLowReceiver = BatteryLowReceiver()
    private val screenOffReceiver = ScreenOffReceiver()

    private val tag = "BroadcastReceiverService"

    override fun onCreate() {
        super.onCreate()
        Log.d(tag,"onCreate")
        ContextCompat.registerReceiver(this, batteryLowReceiver, IntentFilter(ACTION_BATTERY_LOW), ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, screenOffReceiver, IntentFilter(ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.foreground_service)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel("foreground_service", name, importance)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)

            val notification: Notification = Notification.Builder(this, "foreground_service")
                .setContentTitle(getString(R.string.listening_for_battery_low_action))
                .setSmallIcon(R.drawable.outline_lock_clock_qs)
                .build()
            startForeground(1, notification)
        } else {
            @Suppress("DEPRECATION") val notification: Notification = Notification.Builder(this)
                .setContentTitle(getString(R.string.listening_for_battery_low_action))
                .setSmallIcon(R.drawable.outline_lock_clock_qs)
                .build()
            startForeground(1, notification)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private inner class BatteryLowReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_BATTERY_LOW) {
                Log.d(tag,"ACTION_BATTERY_LOW")
                runBlocking { restoreScreenTimeout() }
                stopForegroundService()
            }
        }
    }

    private inner class ScreenOffReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SCREEN_OFF) {
                Log.d(tag,"ACTION_SCREEN_OFF")
                runBlocking { restoreScreenTimeout() }
                stopForegroundService()
            }
        }
    }

    private suspend fun restoreScreenTimeout() {
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, UserPreferencesRepository(dataStore).readScreenTimeout.first())
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag,"onDestroy")
        unregisterReceiver(batteryLowReceiver)
        unregisterReceiver(screenOffReceiver)
    }
}