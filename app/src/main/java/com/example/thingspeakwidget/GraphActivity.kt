package com.example.thingspeakwidget

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thingspeakwidget.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GraphActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        val channelId = intent.getStringExtra("channelId") ?: ""
        val apiKey = intent.getStringExtra("apiKey")
        val fieldId = intent.getIntExtra("fieldId", 1)
        val results = intent.getIntExtra("results", 20)

        findViewById<TextView>(R.id.tv_graph_title).text = "Channel $channelId - Field $fieldId (Last $results)"

        findViewById<Button>(R.id.btn_close_graph).setOnClickListener {
            finish()
        }

        fetchData(channelId, apiKey, fieldId, results)
    }

    private fun fetchData(channelId: String, apiKey: String?, fieldId: Int, results: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.getLastEntry(channelId, apiKey, results).execute()
                if (response.isSuccessful && response.body() != null) {
                    val feeds = response.body()!!.feeds
                    val dataPoints = feeds.mapNotNull { it.getField(fieldId)?.toFloatOrNull() }.reversed()

                    withContext(Dispatchers.Main) {
                        findViewById<GraphView>(R.id.graph_view).setData(dataPoints)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@GraphActivity, "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GraphActivity, "Network Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
