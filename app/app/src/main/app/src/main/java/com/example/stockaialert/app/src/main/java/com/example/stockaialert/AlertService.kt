package com.example.stockaialert

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject

class AlertService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHANNEL_ID = "stock_service"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildServiceNotification())
        startChecking()
    }

    private fun startChecking() {
        scope.launch {
            while (isActive) {
                if (StockApi.isMarketOpen()) {
                    checkAllAlerts()
                }
                delay(60_000L)
            }
        }
    }

    private fun checkAllAlerts() {
        val prefs = getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val symbols = prefs.getStringSet("symbols", emptySet()) ?: emptySet()

        symbols.forEach { symbol ->
            val data = StockApi.fetchStock(symbol) ?: return@forEach

            val alertJson = prefs.getString("alert_$symbol", null)
            if (alertJson != null) {
                val obj = JSONObject(alertJson)
                val target = obj.getDouble("target")
                val type = obj.getString("type")
                val triggered = (type == "ABOVE" && data.price >= target) ||
                                (type == "BELOW" && data.price <= target)
                if (triggered) {
                    notify("🎯 $symbol Target Hit!",
                        "Price ₹${data.price} (Target: ₹$target $type)")
                }
            }

            StockApi.aiAnalyze(symbol, data)?.let { aiMsg ->
                notify("🧠 AI Alert", aiMsg)
            }
        }
    }

    private fun notify(title: String, msg: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, "stock_alert")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stock AI Alert Running")
            .setContentText("Monitoring your stocks")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(
                "stock_alert", "Stock Alerts", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
