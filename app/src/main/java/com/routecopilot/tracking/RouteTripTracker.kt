package com.routecopilot.tracking

import android.content.Context
import android.location.Location
import com.routecopilot.data.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

data class TripPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracyMeters: Float
)

object RouteTripTracker {

    private const val PREFS = "routecopilot_trip_v1"
    private const val KEY_ACTIVE = "active"
    private const val KEY_AT = "at"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_FINISHED_AT = "finished_at"
    private const val KEY_DISTANCE = "distance_m"
    private const val KEY_POINTS = "points_json"

    private const val MAX_ACCURACY_METERS = 80f
    private const val MAX_SPEED_METERS_PER_SECOND = 55.0
    private const val MIN_SEGMENT_METERS = 2.0
    private const val MAX_POINTS = 6000

    private var appContext: Context? = null

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _atId = MutableStateFlow<String?>(null)
    val atId: StateFlow<String?> = _atId.asStateFlow()

    private val _startedAt = MutableStateFlow(0L)
    val startedAt: StateFlow<Long> = _startedAt.asStateFlow()

    private val _finishedAt = MutableStateFlow(0L)
    val finishedAt: StateFlow<Long> = _finishedAt.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters.asStateFlow()

    private val _points = MutableStateFlow<List<TripPoint>>(emptyList())
    val points: StateFlow<List<TripPoint>> = _points.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _active.value = prefs.getBoolean(KEY_ACTIVE, false)
        _atId.value = prefs.getString(KEY_AT, null)
        _startedAt.value = prefs.getLong(KEY_STARTED_AT, 0L)
        _finishedAt.value = prefs.getLong(KEY_FINISHED_AT, 0L)
        _distanceMeters.value = prefs.getFloat(KEY_DISTANCE, 0f).toDouble()
        _points.value = parsePoints(prefs.getString(KEY_POINTS, null))

        _points.value.lastOrNull()?.let {
            _currentLocation.value = GeoPoint(it.latitude, it.longitude)
        }
    }

    fun startRoute(context: Context, atId: String) {
        initialize(context)

        val normalizedAt = atId.trim().uppercase()

        if (_active.value && _atId.value == normalizedAt) {
            return
        }

        _active.value = true
        _atId.value = normalizedAt
        _startedAt.value = System.currentTimeMillis()
        _finishedAt.value = 0L
        _distanceMeters.value = 0.0
        _points.value = emptyList()
        _currentLocation.value = null
        persist()
    }

    fun finishRoute(context: Context) {
        initialize(context)

        if (!_active.value) return

        _active.value = false
        _finishedAt.value = System.currentTimeMillis()
        persist()
    }

    fun recordLocation(context: Context, location: Location) {
        initialize(context)

        if (!_active.value) return

        if (
            location.hasAccuracy() &&
            location.accuracy > MAX_ACCURACY_METERS
        ) {
            return
        }

        val point = TripPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = if (location.time > 0L) location.time else System.currentTimeMillis(),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f
        )

        _currentLocation.value = GeoPoint(
            latitude = point.latitude,
            longitude = point.longitude
        )

        val previous = _points.value.lastOrNull()

        if (previous == null) {
            _points.value = listOf(point)
            persist()
            return
        }

        val distance = distanceMeters(previous, point)
        val elapsedSeconds = max(
            1.0,
            (point.timestamp - previous.timestamp).coerceAtLeast(0L) / 1000.0
        )

        if (distance < MIN_SEGMENT_METERS) {
            return
        }

        val speed = distance / elapsedSeconds

        if (speed > MAX_SPEED_METERS_PER_SECOND) {
            return
        }

        _distanceMeters.value += distance

        val next = ArrayList<TripPoint>(
            (_points.value.size + 1).coerceAtMost(MAX_POINTS)
        )

        if (_points.value.size >= MAX_POINTS) {
            next.addAll(_points.value.takeLast(MAX_POINTS - 1))
        } else {
            next.addAll(_points.value)
        }

        next.add(point)
        _points.value = next

        persist()
    }

    fun durationMillis(now: Long = System.currentTimeMillis()): Long {
        val start = _startedAt.value
        if (start <= 0L) return 0L

        val end = when {
            _finishedAt.value > 0L -> _finishedAt.value
            _active.value -> now
            else -> now
        }

        return (end - start).coerceAtLeast(0L)
    }

    private fun distanceMeters(
        a: TripPoint,
        b: TripPoint
    ): Double {
        val result = FloatArray(1)

        Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            result
        )

        return result[0].toDouble()
    }

    private fun persist() {
        val context = appContext ?: return

        val array = JSONArray()

        _points.value.forEach { point ->
            array.put(
                JSONObject().apply {
                    put("lat", point.latitude)
                    put("lon", point.longitude)
                    put("ts", point.timestamp)
                    put("acc", point.accuracyMeters.toDouble())
                }
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, _active.value)
            .putString(KEY_AT, _atId.value)
            .putLong(KEY_STARTED_AT, _startedAt.value)
            .putLong(KEY_FINISHED_AT, _finishedAt.value)
            .putFloat(KEY_DISTANCE, _distanceMeters.value.toFloat())
            .putString(KEY_POINTS, array.toString())
            .apply()
    }

    private fun parsePoints(raw: String?): List<TripPoint> {
        if (raw.isNullOrBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            val result = ArrayList<TripPoint>(array.length())

            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)

                result += TripPoint(
                    latitude = item.getDouble("lat"),
                    longitude = item.getDouble("lon"),
                    timestamp = item.optLong("ts", 0L),
                    accuracyMeters = item.optDouble("acc", 0.0).toFloat()
                )
            }

            result
        }.getOrDefault(emptyList())
    }
}
