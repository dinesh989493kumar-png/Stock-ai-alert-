package com.example.stockaialert

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

object StockApi {

    private val client = OkHttpClient()

    data class StockData(
        val price: Double,
        val previousClose: Double,
        val changePercent: Double,
        val volume: Long
    )

    fun isMarketOpen(): Boolean {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(ist)
        val day = cal.get(Calendar.DAY_OF_WEEK)
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) return false

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val total = hour * 60 + minute

        return total >= (9 * 60 + 15) && total <= (15 * 60 + 30)
    }

    fun fetchStock(symbol: String): StockData? {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1m&range=1d"
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0").build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")

            val meta = json.getJSONObject("chart")
                .getJSONArray("result").getJSONObject(0)
                .getJSONObject("meta")

            val price = meta.getDouble("regularMarketPrice")
            val prevClose = meta.optDouble("chartPreviousClose", price)
            val changePercent = if (prevClose > 0) ((price - prevClose) / prevClose) * 100 else 0.0
            val volume = meta.optLong("regularMarketVolume", 0L)

            StockData(price, prevClose, changePercent, volume)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun aiAnalyze(symbol: String, data: StockData): String? {
        val absChange = Math.abs(data.changePercent)
        return when {
            absChange >= 5.0 -> if (data.changePercent > 0)
                "🚀 $symbol BIG SPIKE +${"%.2f".format(data.changePercent)}% - ₹${data.price}"
            else
                "🔻 $symbol BIG DROP ${"%.2f".format(data.changePercent)}% - ₹${data.price}"
            absChange >= 2.5 -> if (data.changePercent > 0)
                "📈 $symbol Rising +${"%.2f".format(data.changePercent)}%"
            else
                "📉 $symbol Falling ${"%.2f".format(data.changePercent)}%"
            else -> null
        }
    }
}
