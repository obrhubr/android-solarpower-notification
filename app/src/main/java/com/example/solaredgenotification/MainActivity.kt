package com.example.solaredgenotification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Half.toFloat
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.fixedRateTimer

data class SolarEdgeSettings(var powerLimit: Int, var siteId: String, var apiKey: String)
data class SolarEdgeResults(var consumed: Int, var produced: Int, var error: Boolean)

class MainActivity : AppCompatActivity() {
    @RequiresApi(VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create channel for Notifications
        createNotificationChannel()
        // Load the previous powerLimit, apiKey and StateId and set the EditText fields
        loadAndSet()

        // Attach Listener to confirmButton
        val confirmButton = findViewById<Button>(R.id.btn_confirm)
        confirmButton.setOnClickListener(save)

        // Attach listener to manual check Button
        val testButton = findViewById<Button>(R.id.btn_test)
        testButton.setOnClickListener(checkApiAndSendNotificationOnClick)

        // Create timer to execute the check every 15 minutes(update frequency of the solaredge api)
        fixedRateTimer("timer", false, 0L, 15 * 60 * 1000) {
            checkApiAndSendNotification()
        }
    }

    private val save = View.OnClickListener {
        // Function to save the settings(powerLimit, apiKey, SiteId)

        // Get key value store
        val sharedPreferences = getPreferences(MODE_PRIVATE)
        val edit = sharedPreferences.edit()

        // Get the values
        val powerLimit = findViewById<EditText>(R.id.input_powerLimit).text.toString()
        val siteId = findViewById<EditText>(R.id.input_siteId).text.toString()
        val apiKey = findViewById<EditText>(R.id.input_apiKey).text.toString()

        // Set the values
        edit.putInt("powerLimit", Integer.parseInt(powerLimit))
        edit.putString("siteId", siteId)
        edit.putString("apiKey", apiKey)

        // Commit changes
        edit.commit()
    }

    private fun load(): SolarEdgeSettings {
        // Function to load the Settings from the key value store
        val sharedPreferences = getPreferences(MODE_PRIVATE)

        // Get Values
        val powerLimit = sharedPreferences.getInt("powerLimit", 0)
        val siteId = sharedPreferences.getString("siteId", "")
        val apiKey = sharedPreferences.getString("apiKey", "")

        // Return SolarEdgeSettings object
        return SolarEdgeSettings(powerLimit, siteId.toString(), apiKey.toString())
    }

    @RequiresApi(VERSION_CODES.O)
    private val checkApiAndSendNotificationOnClick = View.OnClickListener {
        // Function to manually trigger the check and notification

        // Set power state to make notification appear
        savePowerState(false)
        checkApiAndSendNotification()
    }

    @RequiresApi(VERSION_CODES.O)
    private fun savePowerState(state: Boolean) {
        // Function to save if there is currently enough kW to charge appliance or not
        // to limit redundant notifications

        val sharedPreferences = getPreferences(MODE_PRIVATE)
        val edit = sharedPreferences.edit()

        // Edit value in key value store
        edit.putString("lastAccessedPowerState", LocalDateTime.now().toString())
        edit.putBoolean("reached", state)

        edit.apply()
    }

    @RequiresApi(VERSION_CODES.O)
    private fun loadPowerState(): String {
        // Function to load the power state(if there is enough kW to charge)

        val sharedPreferences = getPreferences(MODE_PRIVATE)

        val lastAccessedPowerState = sharedPreferences.getString("lastAccessedPowerState", "")
        val lastPowerState = sharedPreferences.getBoolean("reached", false)

        if(LocalDateTime.parse(lastAccessedPowerState) < LocalDateTime.now().minusMinutes(15)) {
            return "Unknown"
        }

        return if (lastPowerState) {
            "True"
        } else {
            "False"
        }
    }

    @RequiresApi(VERSION_CODES.O)
    private fun savePower(results: SolarEdgeResults) {
        // Function to implement basic ArrayDeque saved to key value store
        // It is used to calculate average kW over 4 last states

        val sharedPreferences = getPreferences(MODE_PRIVATE)
        val edit = sharedPreferences.edit()

        // move second in queue to third
        edit.putInt("consumed_prev2", sharedPreferences.getInt("consumed_prev1", 0))
        edit.putInt("produced_prev2", sharedPreferences.getInt("produced_prev1", 0))

        // move first in queue to second
        edit.putInt("consumed_prev1", sharedPreferences.getInt("consumed", 0))
        edit.putInt("produced_prev1", sharedPreferences.getInt("produced", 0))

        edit.putInt("consumed", results.consumed)
        edit.putInt("produced", results.produced)

        edit.putString("lastAccessedPower", LocalDateTime.now().toString())

        edit.commit()
    }

    @RequiresApi(VERSION_CODES.O)
    private fun loadPower(): MutableList<SolarEdgeResults> {
        // Function to load ArrayDeque from Key value store to then calculate average over last 4 states
        val sharedPreferences = getPreferences(MODE_PRIVATE)
        val edit = sharedPreferences.edit()

        val lastAccessedPower = sharedPreferences.getString("lastAccessedPower", "2010-08-21T14:16:56.930")
        if(LocalDateTime.parse(lastAccessedPower) < LocalDateTime.now().minusMinutes(15)) {
            // move second in queue to third
            edit.putInt("consumed_prev2", sharedPreferences.getInt("consumed_prev1", -1))
            edit.putInt("produced_prev2", sharedPreferences.getInt("produced_prev1", -1))

            // move first in queue to second
            edit.putInt("consumed_prev1", sharedPreferences.getInt("consumed", -1))
            edit.putInt("produced_prev1", sharedPreferences.getInt("produced", -1))

            edit.putInt("consumed", -1)
            edit.putInt("produced", -1)

            edit.commit()
        }

        val cons1 = sharedPreferences.getInt("consumed", -1)
        val cons2 = sharedPreferences.getInt("consumed_prev1", -1)
        val cons3 = sharedPreferences.getInt("consumed_prev2", -1)

        val prod1 = sharedPreferences.getInt("produced", -1)
        val prod2 = sharedPreferences.getInt("produced_prev1", -1)
        val prod3 = sharedPreferences.getInt("produced_prev2", -1)

        // Add all values to MutableList<SolarEdgeResults> to then be looped through
        val prev_vals = mutableListOf<SolarEdgeResults>()
        prev_vals.add(SolarEdgeResults(cons1, prod1, false))
        prev_vals.add(SolarEdgeResults(cons2, prod2, false))
        prev_vals.add(SolarEdgeResults(cons3, prod3, false))

        return prev_vals
    }

    public fun avgPower(prev: MutableList<SolarEdgeResults>): Int {
        // Calculate average of MutableList<SolarEdgeResults> to then check if below threshold
        var avg = 0
        var i = 0

        prev.forEach { item ->
            if(item.produced != -1) {
                avg += item.produced
                i += 1
            }
        }

        return avg / i
    }

    private fun loadAndSet() {
        // Function to load settings(powerLimit, ApiKey and SiteId) from Key value store and set
        // EditText fields

        val settings = load()

        // Get EditText Fields
        val powerLimit = findViewById<EditText>(R.id.input_powerLimit)
        val siteId = findViewById<EditText>(R.id.input_siteId)
        val apiKey = findViewById<EditText>(R.id.input_apiKey)

        // Set Edit Text fields
        powerLimit.setText(settings.powerLimit.toString())
        siteId.setText(settings.siteId)
        apiKey.setText(settings.apiKey)
    }

    @RequiresApi(VERSION_CODES.O)
    public fun formatDate(tdBase: LocalDateTime): String {
        // Function to format date to correct format for the api
        var tD = ""

        // Create date part
        tD += tdBase.format(DateTimeFormatter.ofPattern("YYYY-MM-dd")).toString()
        // Add encoded space separator
        tD += "%20"
        // Create time part
        tD += tdBase.format(DateTimeFormatter.ofPattern("HH:mm:ss")).toString()

        return tD
    }

    public fun parseDataFromApi(response: String, volleyResponse: VolleyStringResponse) {
        var produced = 0
        var consumed = 0
        var error = false

        var foundProd = false
        var foundCons = false

        val jsonObj = JSONObject(response)
        val metersJsonObj = jsonObj.getJSONObject("powerDetails")
        val jsonArray: JSONArray = metersJsonObj.getJSONArray("meters")

        if(jsonArray.length() == 0) volleyResponse.onError(SolarEdgeResults(0, 0, true))

        for (i in 0 until jsonArray.length()) {
            val innerObject = jsonArray.getJSONObject(i)
            if(innerObject == null) volleyResponse.onError(SolarEdgeResults(0, 0, true))

            val type = innerObject.getString("type")
            val innerJsonArray = innerObject.getJSONArray("values")
            if(jsonArray.length() == 0)  volleyResponse.onError(SolarEdgeResults(0, 0, true))

            val meter = innerJsonArray.getJSONObject(0).getDouble("value")

            if (type == "Production".toString()) {
                foundProd = true
                produced += meter.toInt()
            }
            if (type == "Consumption".toString()) {
                foundCons = true
                consumed += meter.toInt()
            }
        }

        return if(foundCons && foundProd) volleyResponse.onSuccess(SolarEdgeResults(consumed, produced, error))
        else volleyResponse.onError(SolarEdgeResults(consumed, produced, true))
    }

    private fun getDataFromApi(volleyResponse: VolleyStringResponse) {
        // Function to load date from solaredge api
        val settings = load()
        if(settings.apiKey == "" || settings.siteId == "") return

        println("TESTTEST")

        // Set individual url parameters
        val queue = Volley.newRequestQueue(this)
        val url = "https://monitoringapi.solaredge.com/site/"
        val paramPowerDetails = "/powerDetails?"
        val paramStartTime = "startTime="
        val paramEndTime = "&endTime="
        val paramApiKey = "&api_key="

        val formattedStartTime = if (VERSION.SDK_INT >= VERSION_CODES.O) {
            formatDate(LocalDateTime.now())
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val formattedEndTime = if (VERSION.SDK_INT >= VERSION_CODES.O) {
            formatDate(LocalDateTime.now().plusMinutes(15))
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        // Compose url from parts
        var composedUrl = url +
                settings.siteId.toString() +
                paramPowerDetails +
                paramStartTime +
                formattedStartTime +
                paramEndTime +
                formattedEndTime +
                paramApiKey +
                settings.apiKey

        // Create request
        val stringRequest = StringRequest(
            Request.Method.GET, composedUrl,
            { response ->
                var produced = 0
                var consumed = 0
                var error = false

                // Parse the json to find necessary values
                val stringResponse = response.toString()

                // Send to parser with stringResponse and Callback
                parseDataFromApi(stringResponse, volleyResponse);
            },
            Response.ErrorListener {
                // Send callback with special parameters, to then trigger notification about failure
                volleyResponse.onError(SolarEdgeResults(0, 0, true))
            })

        queue.add(stringRequest)
    }

    //Interface for Api request callbacks
    public interface VolleyStringResponse {
        fun onSuccess(response: SolarEdgeResults?)
        fun onError(error: SolarEdgeResults?)
    }

    private fun sendNotification(title: String, text: String, bigText: String, state: Boolean) {
        // Function to send Notification based on values provided

        // Set icon depending on state(powerLimit Reached or Missed)
        val icon = if(state) {
            R.drawable.ic_stat_reached
        } else {
            R.drawable.ic_stat_missed
        }

        // Build notification
        var builder = NotificationCompat.Builder(this, "solar_edge_notification_id")
            .setSmallIcon(icon)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(), icon))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle()
            .bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Send notification
        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(1, builder.build())
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library

        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            val name = "channelname"
            val descriptionText = "descriptiontext"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("solar_edge_notification_id", name, importance).apply {
                description = descriptionText
            }

            // Set notification to vibrate and enableLights
            channel.enableVibration(true)
            channel.enableLights(true)
            channel.lightColor = Color.GREEN

            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendFormattedNotificationReached(results: SolarEdgeResults, settings: SolarEdgeSettings, avgProd: Int) {
        // Creates title, text and BigText from values, if powerLimit was reached
        val title = "Your power limit of " + "%.2f".format(settings.powerLimit.toFloat() / 1000) + "kW was reached and is now stable"
        val text = "Power limit: " +
                "%.2f".format(settings.powerLimit.toFloat() / 1000) +
                "kW, Free Power:  " +
                "%.2f".format((results.produced - results.consumed).toFloat() / 1000) +
                "kW)"
        val bigText = "The power limit of " +
                "%.2f".format(settings.powerLimit.toFloat() / 1000) +
                "kW you set was reached and is now stable. The current power production and consumption is: " +
                "%.2f".format(results.produced.toFloat() / 1000) +
                "kW and " +
                "%.2f".format(results.consumed.toFloat() / 1000) +
                "kW. " +
                "Your appliance is ready to charge now without incurring significant cost! " +
                "(Avg: " +
                "%.2f".format(avgProd.toFloat() / 1000) +
                "kW)"

        sendNotification(title, text, bigText, true)
    }

    private fun sendFormattedNotificationUnreached(results: SolarEdgeResults, settings: SolarEdgeSettings, avgProd: Int) {
        // Creates title, text and BigText from values, if powerLimit was missed

        val title = "Your power limit of " + "%.2f".format(settings.powerLimit.toFloat() / 1000) + "kW was missed thrice!"
        val text = "Power limit: " +
                "%.2f".format(settings.powerLimit.toFloat() / 1000) +
                "kW, Power Bought:  " +
                "%.2f".format((results.consumed - results.produced).toFloat() / 1000) +
                "kW)"
        val bigText = "The power limit of " +
                "%.2f".format(settings.powerLimit.toFloat() / 1000) +
                "kW you set was missed. The current power production and consumption is: " +
                "%.2f".format(results.produced.toFloat() / 1000) +
                "kW and " +
                "%.2f".format(results.consumed.toFloat() / 1000) +
                "kW. " +
                "Your appliance should be disconnected unless you want to incur significant costs!" +
                "(Avg: " +
                "%.2f".format(avgProd.toFloat() / 1000) +
                "kW)"
        sendNotification(title, text, bigText, false)
    }

    public fun calculateMissedOrReached(currentPower: SolarEdgeResults, previousPower: MutableList<SolarEdgeResults>, powerLimit: Int, powerState: String): Pair<String, Int> {
        // Function to calculate if powerLimit has been reached or not and if to send notification
        // Extracted from checkApiSendNotification Function to be able to test it

        // Get average power
        previousPower.add(currentPower)
        val avgProd = avgPower(previousPower)

        // Add +10 and subtract -100 from powerLimits to give margin for small fluctuations in power,
        // As not to trigger notification every time power misses Limit
        if(avgProd > (powerLimit + 10)) {
            if(powerState == "False" || powerState == "Unknown") {
                return Pair("SendTrue", avgProd)
            }
        }
        if(avgProd < (powerLimit - 100)) {
            if(powerState == "True" || powerState == "Unknown") {
                return Pair("SendFalse", avgProd)
            }
        }

        return Pair("NotSend", avgProd)
    }

    public fun checkApiAndSendNotification() {
        // Function to send api request, check results and avg against PowerLimit and then send notification
        val settings = load()

        val volleyRequest = getDataFromApi(object: VolleyStringResponse {
            @RequiresApi(VERSION_CODES.O)
            override fun onSuccess(response: SolarEdgeResults?) {
                if(response != null) {
                    // Set indicators
                    findViewById<TextView>(R.id.tv_prod).setText(response.produced.toString() + " W produced")
                    findViewById<TextView>(R.id.tv_cons).setText(response.consumed.toString() + " W consumed")

                    // Get Missed or Reached Value
                    var powerState: String = loadPowerState()
                    val (missedOrReached, avgProd) = calculateMissedOrReached(response, loadPower(), settings.powerLimit, powerState)

                    if(missedOrReached == "SendTrue") {
                        savePowerState(true)
                        sendFormattedNotificationReached(response, settings, avgProd)
                    } else if(missedOrReached == "SendFalse") {
                        savePowerState(false)
                        sendFormattedNotificationUnreached(response, settings, avgProd)
                    }

                    // Set image
                    powerState = loadPowerState()
                    if(powerState == "True") {
                        findViewById<ImageView>(R.id.iv_status).setImageDrawable(getDrawable(R.drawable.ic_reached))
                    } else if(powerState == "False") {
                        findViewById<ImageView>(R.id.iv_status).setImageDrawable(getDrawable(R.drawable.ic_missed))
                    }

                    savePower(response)
                }
            }
            override fun onError(error: SolarEdgeResults?) {
                if(error != null) {
                    // On error send notification to inform user of api failure and set image
                    findViewById<ImageView>(R.id.iv_status).setImageDrawable(getDrawable(R.drawable.ic_unknown))
                    sendNotification(
                        "Error while fetching data from SolarEdge API",
                        "The Api key or site id might be wrong or expired",
                        "The Api key or site id might be wrong or expired",
                        false
                    )
                }
            }
        })
    }
}