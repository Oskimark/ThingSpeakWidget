package com.example.thingspeakwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thingspeakwidget.util.PrefsManager
import com.example.thingspeakwidget.util.WidgetConfig
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Calendar

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    // UI References
    private lateinit var etChannelId: TextInputEditText
    private lateinit var etApiKey: TextInputEditText
    private lateinit var cbShowAlarms: CheckBox
    private lateinit var cbShowSchedules: CheckBox
    private lateinit var etUpperLimit: TextInputEditText
    private lateinit var etLowerLimit: TextInputEditText
    
    private val dayCheckboxes = mutableMapOf<Int, CheckBox>()

    private lateinit var spinnerField: android.widget.Spinner

    private var currentRingtone: android.media.Ringtone? = null

    override fun onDestroy() {
        super.onDestroy()
        currentRingtone?.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        setResult(RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        bindViews()
        setupSpinner()
        
        // Load existing config if available
        val existingConfig = PrefsManager.loadConfig(this, appWidgetId)
        if (existingConfig != null) {
            etChannelId.setText(existingConfig.channelId)
            etApiKey.setText(existingConfig.apiKey)
            cbShowAlarms.isChecked = existingConfig.showAlarms
            cbShowSchedules.isChecked = existingConfig.showSchedules
            existingConfig.upperLimit?.let { etUpperLimit.setText(it.toString()) }
            existingConfig.lowerLimit?.let { etLowerLimit.setText(it.toString()) }
            etUpdateInterval.setText(existingConfig.updateIntervalSeconds.toString())
            
            existingConfig.activeDays.forEach { day ->
                dayCheckboxes[day]?.isChecked = true
            }
            
            // Set spinner selection (index is 0-based, field is 1-based)
            spinnerField.setSelection(existingConfig.selectedField - 1)
        }

        findViewById<Button>(R.id.btn_save_config).setOnClickListener { saveContext() }
        
        findViewById<android.widget.Button>(R.id.btn_test_alarm).setOnClickListener {
            val btn = it as android.widget.Button
            
            if (currentRingtone?.isPlaying == true) {
                currentRingtone?.stop()
                currentRingtone = null
                btn.text = "Test Alarm Sound"
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                    return@setOnClickListener
                }
            }
            
            // Play direct sound for verification
            try {
                val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                val r = android.media.RingtoneManager.getRingtone(applicationContext, notification)
                currentRingtone = r
                r.play()
                btn.text = "Stop Alarm Sound"
                android.widget.Toast.makeText(this, "Playing Alarm...", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Also trigger notification
            com.example.thingspeakwidget.util.NotificationHelper.createNotificationChannel(this)
            com.example.thingspeakwidget.util.NotificationHelper.sendAlarmNotification(this, "Test Alarm", "This is a test alarm sound verification.")
        }
    }

    private fun bindViews() {
        etChannelId = findViewById(R.id.et_channel_id)
        etApiKey = findViewById(R.id.et_api_key)
        spinnerField = findViewById(R.id.spinner_field_selection)
        cbShowAlarms = findViewById(R.id.cb_show_alarms)
        cbShowSchedules = findViewById(R.id.cb_show_schedules)
        etUpperLimit = findViewById(R.id.et_upper_limit)
        etLowerLimit = findViewById(R.id.et_lower_limit)
        etUpdateInterval = findViewById(R.id.et_update_interval)
        
        dayCheckboxes[Calendar.MONDAY] = findViewById(R.id.cb_mon)
        dayCheckboxes[Calendar.TUESDAY] = findViewById(R.id.cb_tue)
        dayCheckboxes[Calendar.WEDNESDAY] = findViewById(R.id.cb_wed)
        dayCheckboxes[Calendar.THURSDAY] = findViewById(R.id.cb_thu)
        dayCheckboxes[Calendar.FRIDAY] = findViewById(R.id.cb_fri)
        dayCheckboxes[Calendar.SATURDAY] = findViewById(R.id.cb_sat)
        dayCheckboxes[Calendar.SUNDAY] = findViewById(R.id.cb_sun)
    }

    private fun setupSpinner() {
        val fields = (1..8).map { "Field $it" }
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, fields)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerField.adapter = adapter
    }

    private fun saveContext() {
        val channelIdStr = etChannelId.text.toString()
        
        if (channelIdStr.isEmpty()) {
            Toast.makeText(this, "Channel ID is required", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = etApiKey.text.toString().takeIf { it.isNotEmpty() }
        val upperLimit = etUpperLimit.text.toString().toFloatOrNull()
        val lowerLimit = etLowerLimit.text.toString().toFloatOrNull()
        val updateInterval = etUpdateInterval.text.toString().toIntOrNull() ?: 900
        
        val activeDays = mutableSetOf<Int>()
        dayCheckboxes.forEach { (day, cb) ->
            if (cb.isChecked) activeDays.add(day)
        }
        
        // Selected field index + 1
        val selectedField = spinnerField.selectedItemPosition + 1

        val config = WidgetConfig(
            channelId = channelIdStr,
            apiKey = apiKey,
            showAlarms = cbShowAlarms.isChecked,
            showSchedules = cbShowSchedules.isChecked,
            upperLimit = upperLimit,
            lowerLimit = lowerLimit,
            activeDays = activeDays,
            selectedField = selectedField,
            updateIntervalSeconds = updateInterval
        )

        PrefsManager.saveConfig(this, appWidgetId, config)

        // It is the responsibility of the configuration activity to request an update of the AppWidget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        
        // Use CoroutineScope to call suspend function
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
             try {
                ThingSpeakWidget.updateAppWidget(this@WidgetConfigActivity, appWidgetManager, appWidgetId)
             } catch (e: Exception) {
                 e.printStackTrace()
             }
             
             runOnUiThread {
                 val resultValue = Intent()
                 resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                 setResult(RESULT_OK, resultValue)
                 finish()
             }
        }
    }
}
