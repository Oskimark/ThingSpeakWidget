package com.example.thingspeakwidget.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class DaySchedule(
    val startHour: Int = 8,
    val startMin: Int = 0,
    val endHour: Int = 18,
    val endMin: Int = 0
)

data class WidgetConfig(
    val channelId: String,
    val apiKey: String?,
    val showAlarms: Boolean,
    val showSchedules: Boolean,
    val upperLimit: Float?,
    val lowerLimit: Float?,
    val schedules: Map<Int, DaySchedule> = emptyMap(), // Key: Calendar.DAY_OF_WEEK (1-7)
    val selectedField: Int = 1,
    val updateIntervalSeconds: Int = 900, // Default 15 minutes
    val graphPointsCount: Int = 20
)

object PrefsManager {
    private const val PREFS_NAME = "com.example.thingspeakwidget.WidgetConfig"
    private const val PREFIX = "widget_"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConfig(context: Context, appWidgetId: Int, config: WidgetConfig) {
        val json = gson.toJson(config)
        getPrefs(context).edit().putString(PREFIX + appWidgetId, json).apply()
    }

    fun loadConfig(context: Context, appWidgetId: Int): WidgetConfig? {
        val json = getPrefs(context).getString(PREFIX + appWidgetId, null) ?: return null
        return try {
            val config: WidgetConfig = gson.fromJson(json, object : TypeToken<WidgetConfig>() {}.type)
            // Ensure schedules is not null
            if (config.schedules == null) {
                config.copy(schedules = emptyMap())
            } else {
                config
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteConfig(context: Context, appWidgetId: Int) {
        getPrefs(context).edit()
            .remove(PREFIX + appWidgetId)
            .remove("last_val_$appWidgetId")
            .apply()
    }

    fun saveLastValue(context: Context, appWidgetId: Int, value: Float) {
        getPrefs(context).edit().putFloat("last_val_$appWidgetId", value).apply()
    }

    fun loadLastValue(context: Context, appWidgetId: Int): Float? {
        val prefs = getPrefs(context)
        return if (prefs.contains("last_val_$appWidgetId")) {
            prefs.getFloat("last_val_$appWidgetId", 0f)
        } else null
    }
}
