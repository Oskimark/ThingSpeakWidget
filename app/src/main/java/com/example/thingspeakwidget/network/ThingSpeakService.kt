package com.example.thingspeakwidget.network

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ThingSpeakService {
    @GET("channels/{channelId}/feeds.json")
    fun getLastEntry(
        @Path("channelId") channelId: String,
        @Query("api_key") apiKey: String?,
        @Query("results") results: Int = 1
    ): Call<ThingSpeakResponse>

    @GET("channels/{channelId}/fields/{fieldId}/last.json")
    fun getLastFieldEntry(
        @Path("channelId") channelId: String,
        @Path("fieldId") fieldId: Int,
        @Query("api_key") apiKey: String?
    ): Call<Feed>
}

data class ThingSpeakResponse(
    @SerializedName("channel") val channel: Channel,
    @SerializedName("feeds") val feeds: List<Feed>
)

data class Channel(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("field1") val field1Name: String?,
    @SerializedName("field2") val field2Name: String?,
    // Add more fields if needed
    @SerializedName("updated_at") val updatedAt: String
)

data class Feed(
    @SerializedName("entry_id") val entryId: Long,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("field1") val field1: String?,
    @SerializedName("field2") val field2: String?,
    @SerializedName("field3") val field3: String?,
    @SerializedName("field4") val field4: String?,
    @SerializedName("field5") val field5: String?,
    @SerializedName("field6") val field6: String?,
    @SerializedName("field7") val field7: String?,
    @SerializedName("field8") val field8: String?
) {
    fun getField(index: Int): String? {
        return when (index) {
            1 -> field1
            2 -> field2
            3 -> field3
            4 -> field4
            5 -> field5
            6 -> field6
            7 -> field7
            8 -> field8
            else -> null
        }
    }
}
