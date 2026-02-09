package com.example.thingspeakwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.thingspeakwidget.network.Feed
import com.example.thingspeakwidget.network.RetrofitClient
import com.example.thingspeakwidget.network.ThingSpeakResponse
import com.example.thingspeakwidget.util.NotificationHelper
import com.example.thingspeakwidget.util.PrefsManager
import com.example.thingspeakwidget.util.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ThingSpeakWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_AUTO_UPDATE = "com.example.thingspeakwidget.ACTION_AUTO_UPDATE"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_AUTO_UPDATE) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val pendingResult = goAsync()
                scope.launch(Dispatchers.IO) {
                    try {
                        updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                        scheduleNextUpdate(context, appWidgetId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Use goAsync to keep the broadcast receiver alive during network calls
        for (appWidgetId in appWidgetIds) {
             val pendingResult = goAsync()
             scope.launch(Dispatchers.IO) {
                 try {
                     updateAppWidget(context, appWidgetManager, appWidgetId)
                     scheduleNextUpdate(context, appWidgetId)
                 } catch (e: Exception) {
                     e.printStackTrace()
                 } finally {
                     pendingResult.finish()
                 }
             }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            PrefsManager.deleteConfig(context, appWidgetId)
            cancelUpdate(context, appWidgetId)
        }
        job.cancel()
    }

    override fun onEnabled(context: Context) {
        NotificationHelper.createNotificationChannel(context)
    }

    override fun onDisabled(context: Context) {
        job.cancel()
    }

    companion object {
        // Made suspendable to easier call from onUpdate's coroutine
        suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val config = PrefsManager.loadConfig(context, appWidgetId)
                
                if (config == null) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.tv_channel_name, context.getString(R.string.setup_required))
                    views.setTextViewText(R.id.tv_value_primary, "--")
                    
                    val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    }
                    val configPendingIntent = PendingIntent.getActivity(
                        context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent)
                    
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return
                }

                // Initial "Loading" state
                val loadingViews = RemoteViews(context.packageName, R.layout.widget_layout)
                loadingViews.setTextViewText(R.id.tv_channel_name, "${context.getString(R.string.loading)} ID: ${config.channelId}")
                // Keep the pending intent on root to allow re-config
                val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                val configPendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                loadingViews.setOnClickPendingIntent(R.id.widget_root, configPendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, loadingViews)

                // Fetch Data
                fetchData(context, appWidgetManager, appWidgetId, config)
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback view to allow user to recover (reconfigure or delete)
                val errorViews = RemoteViews(context.packageName, R.layout.widget_layout)
                errorViews.setTextViewText(R.id.tv_channel_name, "Error Loading")
                appWidgetManager.updateAppWidget(appWidgetId, errorViews)
            }
        }

        private suspend fun fetchData(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            config: WidgetConfig
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            
            // Re-apply common intents (refresh, config)
             val refreshIntent = Intent(context, ThingSpeakWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

             val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_settings, configPendingIntent)


            try {
                // Network call - Get last entry for the specific field to ensure we get a non-null value
                // Note: getLastFieldEntry returns a Feed object directly, not a ThingSpeakResponse wrapper
                val response = RetrofitClient.instance.getLastFieldEntry(config.channelId, config.selectedField, config.apiKey).execute()
                
                if (response.isSuccessful && response.body() != null) {
                    val feed = response.body()!!
                    // We need to fetch channel info separately or just reuse ID/Name if we stored it. 
                    // But wait, the previous code used channel name from response.
                    // getLastFieldEntry ONLY returns the feed, not channel info.
                    // To keep it simple and efficient, we will just use "Channel ID" or maybe we need two calls?
                    // actually, for the widget, just showing the value is most important.
                    // Let's stick to the feed. For channel name, we can try to keep it or fetch it if needed.
                    // Ideally we should cache channel name.
                    
                    // For now, let's just make a dummy Channel object or modify updateWithData to not need it
                    // Or, we can make a second call strictly for channel info only if we don't have it? 
                    // No, that's too slow.
                    // Let's just pass null for channel and handle it in updateWithData
                    
                    updateWithData(context, views, appWidgetManager, appWidgetId, config, feed)
                } else {
                    views.setTextViewText(R.id.tv_channel_name, "Error: ${response.code()}")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                views.setTextViewText(R.id.tv_channel_name, "Net Error")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun updateWithData(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            config: WidgetConfig,
            feed: Feed
        ) {
            // Since we don't have channel name from the single field endpoint, 
            // we'll just use the ID. 
            // Improvement: Store channel name in config when first configuring?
            val channelName = "Channel ${config.channelId}" 
            views.setTextViewText(R.id.tv_channel_name, "$channelName (F${config.selectedField})")

            var val1: String? = null
            
            val valueToDisplay = feed.getField(config.selectedField)
            if (valueToDisplay != null) {
                views.setTextViewText(R.id.tv_value_primary, valueToDisplay)
                val1 = valueToDisplay
            } else {
                views.setTextViewText(R.id.tv_value_primary, "Null (F${config.selectedField})")
            }

            // Format timestamp
            val formattedTime = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(feed.createdAt)
                val outputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                outputFormat.format(date!!)
            } catch (e: Exception) {
                feed.createdAt
            }
            
            views.setTextViewText(R.id.tv_last_updated, "${context.getString(R.string.updated)} $formattedTime")

            views.setViewVisibility(R.id.layout_alarms, if (config.showAlarms) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.layout_schedules, if (config.showSchedules) View.VISIBLE else View.GONE)

            checkAlarms(context, config, val1, views)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun checkAlarms(
            context: Context,
            config: WidgetConfig,
            valueStr: String?,
            views: RemoteViews
        ) {
            // Reset background safely
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background)
            views.setTextViewText(R.id.tv_alarm_status, "Normal")
            
            // Safe color setting
            val primaryColor = ContextCompat.getColor(context, R.color.text_primary)
            views.setTextColor(R.id.tv_value_primary, primaryColor)

            if (valueStr == null) return

            val value = valueStr.toFloatOrNull() ?: return
            
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

            val isDayActive = config.activeDays.contains(dayOfWeek)
            if (!isDayActive) {
                 views.setTextViewText(R.id.tv_alarm_status, "Alarm inactive today")
                 return
            }

            var alarmTriggered = false
            var alarmMsg = ""

            if (config.upperLimit != null && value >= config.upperLimit) {
                alarmTriggered = true
                alarmMsg = "High: $value > ${config.upperLimit}"
            } else if (config.lowerLimit != null && value <= config.lowerLimit) {
                alarmTriggered = true
                alarmMsg = "Low: $value < ${config.lowerLimit}"
            }

            if (alarmTriggered) {
                 // Use alarm background
                 views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_alarm)
                 
                 views.setTextViewText(R.id.tv_alarm_status, "ALARM!")
                 views.setTextColor(R.id.tv_value_primary, Color.RED)
                 
                 try {
                    NotificationHelper.sendAlarmNotification(context, "ThingSpeak Alarm", alarmMsg)
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
            }
        }

        private fun scheduleNextUpdate(context: Context, appWidgetId: Int) {
            val config = PrefsManager.loadConfig(context, appWidgetId) ?: return
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            
            val intent = Intent(context, ThingSpeakWidget::class.java).apply {
                action = ACTION_AUTO_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Need unique data uri to distinguish PendingIntents for different widgets
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) 
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                appWidgetId, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Cancel any existing logic
            alarmManager.cancel(pendingIntent)

            val intervalMillis = config.updateIntervalSeconds * 1000L
            val triggerTime = System.currentTimeMillis() + intervalMillis

            // Use setExactAndAllowWhileIdle for "seconds" precision even in Doze
            // Note: This is aggressive on battery.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }

        private fun cancelUpdate(context: Context, appWidgetId: Int) {
             val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
             val intent = Intent(context, ThingSpeakWidget::class.java).apply {
                action = ACTION_AUTO_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                appWidgetId, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
