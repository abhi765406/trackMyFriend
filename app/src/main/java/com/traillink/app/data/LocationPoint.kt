package com.traillink.app.data

import org.json.JSONObject

data class LocationPoint(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val speedKmh: Float,
    val bearing: Float,
    val timestampMillis: Long,
    val batteryPercent: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", lat)
        put("lng", lng)
        put("accuracy", accuracyMeters.toDouble())
        put("speed", speedKmh.toDouble())
        put("bearing", bearing.toDouble())
        put("ts", timestampMillis)
        put("battery", batteryPercent)
    }

    companion object {
        fun fromJson(json: JSONObject): LocationPoint? {
            if (!json.has("lat") || !json.has("lng")) return null
            return LocationPoint(
                lat = json.optDouble("lat"),
                lng = json.optDouble("lng"),
                accuracyMeters = json.optDouble("accuracy", 0.0).toFloat(),
                speedKmh = json.optDouble("speed", 0.0).toFloat(),
                bearing = json.optDouble("bearing", 0.0).toFloat(),
                timestampMillis = json.optLong("ts", 0L),
                batteryPercent = json.optInt("battery", -1)
            )
        }
    }
}
