package com.example.stockaialert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etSymbol = findViewById<EditText>(R.id.etSymbol)
        val etTarget = findViewById<EditText>(R.id.etTarget)
        val spinner = findViewById<Spinner>(R.id.spinnerType)
        val btnSet = findViewById<Button>(R.id.btnSetAlert)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val tvList = findViewById<TextView>(R.id.tvList)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Above", "Below"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        btnSet.setOnClickListener {
            val symbol = etSymbol.text.toString().uppercase().trim()
            val target = etTarget.text.toString().toDoubleOrNull()
            if (symbol.isEmpty() || target == null) {
                Toast.makeText(this, "Symbol & target enter pannu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val type = if (spinner.selectedItem == "Above") "ABOVE" else "BELOW"

            val prefs = getSharedPreferences("alerts", MODE_PRIVATE)
            val symbols = prefs.getStringSet("symbols", mutableSetOf())!!.toMutableSet()
            symbols.add(symbol)
            prefs.edit().putStringSet("symbols", symbols).apply()

            val alertObj = JSONObject().apply {
                put("target", target)
                put("type", type)
            }
            prefs.edit().putString("alert_$symbol", alertObj.toString()).apply()

            refreshList(tvList, prefs)
            Toast.makeText(this, "Alert set: $symbol $type ₹$target", Toast.LENGTH_LONG).show()
        }

        btnStart.setOnClickListener {
            val intent = Intent(this, AlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            Toast.makeText(this, "Monitoring started ✅", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, AlertService::class.java))
            Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
        }

        refreshList(tvList, getSharedPreferences("alerts", MODE_PRIVATE))
    }

    private fun refreshList(tv: TextView, prefs: android.content.SharedPreferences) {
        val symbols = prefs.getStringSet("symbols", emptySet()) ?: emptySet()
        if (symbols.isEmpty()) { tv.text = "No alerts set"; return }

        val sb = StringBuilder("📋 Your Alerts:\n\n")
        symbols.forEach { s ->
            val a = prefs.getString("alert_$s", null)
            if (a != null) {
                val o = JSONObject(a)
                sb.append("• $s — ${o.getString("type")} ₹${o.getDouble("target")}\n")
            }
        }
        tv.text = sb.toString()
    }
}
