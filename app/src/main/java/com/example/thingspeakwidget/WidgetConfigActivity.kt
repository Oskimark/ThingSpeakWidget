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
import com.example.thingspeakwidget.util.DaySchedule
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
    private lateinit var etUpdateInterval: TextInputEditText
    private lateinit var etGraphPoints: TextInputEditText
    
    private val dayCheckboxes = mutableMapOf<Int, CheckBox>()
    private val daySchedules = mutableMapOf<Int, DaySchedule>()
    // Helper to store button references for updating text
    private val dayButtons = mutableMapOf<Int, Pair<Button, Button>>()

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
            etGraphPoints.setText(existingConfig.graphPointsCount.toString())
            
            existingConfig.schedules.forEach { (day, schedule) ->
                dayCheckboxes[day]?.isChecked = true
                daySchedules[day] = schedule
                updateTimeButtons(day)
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
        etGraphPoints = findViewById(R.id.et_graph_points)
        
        setupDayRow(Calendar.MONDAY, R.id.cb_mon, R.id.btn_time_mon_start, R.id.btn_time_mon_end)
        setupDayRow(Calendar.TUESDAY, R.id.cb_tue, R.id.btn_time_tue_start, R.id.btn_time_tue_end)
        setupDayRow(Calendar.WEDNESDAY, R.id.cb_wed, R.id.btn_time_wed_start, R.id.btn_time_wed_end)
        setupDayRow(Calendar.THURSDAY, R.id.cb_thu, R.id.btn_time_thu_start, R.id.btn_time_thu_end)
        setupDayRow(Calendar.FRIDAY, R.id.cb_fri, R.id.btn_time_fri_start, R.id.btn_time_fri_end)
        setupDayRow(Calendar.SATURDAY, R.id.cb_sat, R.id.btn_time_sat_start, R.id.btn_time_sat_end)
        setupDayRow(Calendar.SUNDAY, R.id.cb_sun, R.id.btn_time_sun_start, R.id.btn_time_sun_end)
    }

    private fun setupDayRow(day: Int, cbId: Int, startBtnId: Int, endBtnId: Int) {
        val cb = findViewById<CheckBox>(cbId)
        val startBtn = findViewById<Button>(startBtnId)
        val endBtn = findViewById<Button>(endBtnId)
        
        dayCheckboxes[day] = cb
        dayButtons[day] = Pair(startBtn, endBtn)
        
        // Default schedule if not loaded
        if (!daySchedules.containsKey(day)) {
            daySchedules[day] = DaySchedule()
        }

        // Initial update to show default/loaded values
        updateTimeButtons(day)
        
        startBtn.setOnClickListener {
            val current = daySchedules[day]!!
            android.app.TimePickerDialog(this, { _, hour, min ->
                val latest = daySchedules[day]!!
                daySchedules[day] = latest.copy(startHour = hour, startMin = min)
                updateTimeButtons(day)
            }, current.startHour, current.startMin, true).show()
        }
        
        endBtn.setOnClickListener {
            val current = daySchedules[day]!!
            android.app.TimePickerDialog(this, { _, hour, min ->
                val latest = daySchedules[day]!!
                daySchedules[day] = latest.copy(endHour = hour, endMin = min)
                updateTimeButtons(day)
            }, current.endHour, current.endMin, true).show()
        }
    }

    private fun updateTimeButtons(day: Int) {
        val sched = daySchedules[day] ?: return
        val buttons = dayButtons[day] ?: return
        buttons.first.text = String.format("%02d:%02d", sched.startHour, sched.startMin)
        buttons.second.text = String.format("%02d:%02d", sched.endHour, sched.endMin)
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
        // Use etUpdateInterval that is declared above
        val updateInterval = etUpdateInterval.text.toString().toIntOrNull() ?: 900
        val graphPoints = etGraphPoints.text.toString().toIntOrNull() ?: 20
        
        val schedules = mutableMapOf<Int, DaySchedule>()
        dayCheckboxes.forEach { (day, cb) ->
            if (cb.isChecked) {
                schedules[day] = daySchedules[day] ?: DaySchedule()
            }
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
            schedules = schedules,
            selectedField = selectedField,
            updateIntervalSeconds = updateInterval,
            graphPointsCount = graphPoints
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
