package com.traillink.app.net

import android.os.Handler
import android.os.Looper
import com.traillink.app.data.LocationPoint
import com.traillink.app.util.CodeGenerator
import com.traillink.app.util.Prefs
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks to the Firebase Realtime Database's plain REST interface
 * (https://<project>.firebaseio.com/<path>.json). No Firebase SDK, no
 * Google Play Services — just HttpURLConnection, which is already part
 * of Android. This is what keeps the whole app a few hundred KB instead
 * of tens of MB.
 */
object FirebaseRestClient {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val TIMEOUT_MS = 9000

    private fun urlFor(code: String): String {
        val base = Prefs.databaseUrl
        val safeCode = CodeGenerator.normalize(code)
        return "$base/locations/$safeCode.json"
    }

    fun putLocation(code: String, point: LocationPoint, onResult: (success: Boolean) -> Unit) {
        executor.execute {
            var success = false
            try {
                val conn = URL(urlFor(code)).openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(conn.outputStream).use { it.write(point.toJson().toString()) }
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (_: Exception) {
                success = false
            }
            mainHandler.post { onResult(success) }
        }
    }

    fun getLocation(code: String, onResult: (point: LocationPoint?, errorMessage: String?) -> Unit) {
        executor.execute {
            var result: LocationPoint? = null
            var error: String? = null
            try {
                val conn = URL(urlFor(code)).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                val code2 = conn.responseCode
                if (code2 in 200..299) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    if (body.isBlank() || body == "null") {
                        error = "not_found"
                    } else {
                        result = LocationPoint.fromJson(JSONObject(body))
                        if (result == null) error = "not_found"
                    }
                } else {
                    error = "http_$code2"
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = "network"
            }
            mainHandler.post { onResult(result, error) }
        }
    }

    fun deleteLocation(code: String, onResult: (success: Boolean) -> Unit) {
        executor.execute {
            var success = false
            try {
                val conn = URL(urlFor(code)).openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (_: Exception) {
                success = false
            }
            mainHandler.post { onResult(success) }
        }
    }
}
