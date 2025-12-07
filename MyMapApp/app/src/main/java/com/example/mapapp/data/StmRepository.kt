package com.example.mapapp.data

import android.content.Context
import com.example.mapapp.data.model.BusRoute
import com.example.mapapp.data.model.BusStop
import com.example.mapapp.data.model.RouteMetadata
import com.example.mapapp.data.model.Stop
import com.example.mapapp.data.model.StopScheduleItem
import com.example.mapapp.data.model.TripTimes
import com.example.mapapp.utils.AppLogger
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class StmRepository(private val context: Context) {

    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
        coerceInputValues = true
    }
    private val client = OkHttpClient()
    private val baseUrl = "https://www.webllington.org/stm/"

    // Cache
    private var cachedRoutes: Map<String, BusRoute>? = null
    private var cachedStops: Map<String, BusStop>? = null
    private var cachedTripTimes: Map<String, TripTimes>? = null
    private var cachedTripHeadsigns: Map<String, String>? = null

    private fun fetchString(fileName: String): String {
        // Force local assets for modified files
        if (fileName == "stm_parcours.json" || fileName == "stm_stops.json") {
            AppLogger.log("StmRepository", "Forcing local asset for $fileName")
            return context.assets.open(fileName).bufferedReader().use { it.readText() }
        }

        try {
            AppLogger.log("StmRepository", "Attempting to fetch $fileName from network...")
            val request = Request.Builder().url(baseUrl + fileName).build()
            val response = client.newCall(request).execute()
            
            val result = response.use { 
                if (it.isSuccessful) {
                    it.body?.string()
                } else {
                    AppLogger.error("StmRepository", "Network fetch failed for $fileName: ${it.code}")
                    null
                }
            }

            if (!result.isNullOrBlank()) {
                AppLogger.log("StmRepository", "Successfully fetched $fileName from network. Size: ${result.length}")
                return result
            }
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Network exception for $fileName: ${e.message}")
        }

        AppLogger.log("StmRepository", "Falling back to assets for $fileName")
        try {
            return context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Asset fallback failed for $fileName", e)
            throw e
        }
    }

    suspend fun getTripHeadsigns(): Map<String, String> = withContext(Dispatchers.IO) {
        if (cachedTripHeadsigns != null) return@withContext cachedTripHeadsigns!!

        try {
            val jsonStr = fetchString("stm_trips.json")
            val serializer = MapSerializer(String.serializer(), String.serializer())
            val result: Map<String, String> = json.decodeFromString(serializer, jsonStr)
            
            cachedTripHeadsigns = result
            AppLogger.log("StmRepository", "Loaded ${result.size} trip headsigns.")
            return@withContext result
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Error loading trip headsigns", e)
            return@withContext emptyMap()
        }
    }

    suspend fun getRoutes(): Map<String, BusRoute> = withContext(Dispatchers.IO) {
        if (cachedRoutes != null) return@withContext cachedRoutes!!

        try {
            // 1. Load Metadata
            val completJson = fetchString("stm_complet.json")
            // Use reified serializer() function
            val metadataSerializer = MapSerializer(String.serializer(), serializer<RouteMetadata>())
            val metadataMap: Map<String, RouteMetadata> = json.decodeFromString(metadataSerializer, completJson)
            AppLogger.log("StmRepository", "Parsed metadata for ${metadataMap.size} routes")

            // 2. Load Geometry (Parcours) & Stops
            val parcoursJson = fetchString("stm_parcours.json")
            // Map<String, RouteParcoursData>
            val geometrySerializer = MapSerializer(String.serializer(), serializer<com.example.mapapp.data.model.RouteParcoursData>())
            val geometryMap: Map<String, com.example.mapapp.data.model.RouteParcoursData> = json.decodeFromString(geometrySerializer, parcoursJson)
            AppLogger.log("StmRepository", "Parsed geometry & stops for ${geometryMap.size} routes")

            // 3. Merge
            val result = mutableMapOf<String, BusRoute>()
            metadataMap.forEach { (id, metadata) ->
                val parcoursData = geometryMap[id]
                val rawShapes = parcoursData?.shapes ?: emptyList()
                val stopIds = parcoursData?.stops ?: emptyList()
                
                val geometry = rawShapes.map { path ->
                    path.map { point ->
                        if (point.size >= 2) Pair(point[0], point[1]) else Pair(0.0, 0.0)
                    }
                }

                val directions = metadata.directions.mapValues { entry -> entry.value.destination }

                result[id] = BusRoute(
                    id = id,
                    name = metadata.nom,
                    color = metadata.couleur,
                    directions = directions,
                    geometry = geometry,
                    stopIds = stopIds
                )
            }
            cachedRoutes = result
            AppLogger.log("StmRepository", "Fully loaded ${result.size} merged routes.")
            return@withContext result
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Error loading routes", e)
            return@withContext emptyMap()
        }
    }

    suspend fun getStops(): Map<String, BusStop> = withContext(Dispatchers.IO) {
        if (cachedStops != null) return@withContext cachedStops!!

        try {
            val stopsJson = fetchString("stm_stops.json")
            // Use reified serializer() function
            val stopsSerializer = MapSerializer(String.serializer(), serializer<Stop>())
            val rawStops: Map<String, Stop> = json.decodeFromString(stopsSerializer, stopsJson)

            val result = rawStops.mapValues { (id, stop) ->
                BusStop(id, stop.name, stop.lat, stop.lon)
            }
            cachedStops = result
            AppLogger.log("StmRepository", "Loaded ${result.size} stops.")
            return@withContext result
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Error loading stops", e)
            return@withContext emptyMap()
        }
    }

    suspend fun getBusPositions(): GtfsRealtime.FeedMessage? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + "stm_bus.pb")
            .addHeader("Accept", "application/x-protobuf")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.error("StmRepository", "Bus fetch failed: ${response.code}")
                    return@use null
                }
                return@use GtfsRealtime.FeedMessage.parseFrom(response.body?.byteStream())
            }
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Error fetching bus positions", e)
            return@withContext null
        }
    }

    suspend fun getStopSchedule(stopId: String): List<StopScheduleItem> = withContext(Dispatchers.IO) {
        try {
            val fileName = "stop_times/$stopId.json"
            val request = Request.Builder().url(baseUrl + fileName).build()
            
            val responseBody = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.error("StmRepository", "Schedule fetch failed for $stopId: ${response.code}")
                    return@withContext emptyList()
                }
                response.body?.string()
            }

            if (responseBody.isNullOrBlank()) return@withContext emptyList()

            val scheduleSerializer = ListSerializer(serializer<StopScheduleItem>())
            return@withContext json.decodeFromString(scheduleSerializer, responseBody)
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Error fetching schedule for $stopId", e)
            return@withContext emptyList()
        }
    }

    suspend fun getRelatedStops(stopName: String): List<BusStop> = withContext(Dispatchers.IO) {
        if (cachedStops == null) getStops()
        
        val normalizedTarget = stopName.lowercase()
        
        // Check for " / " pattern to handle swapped street names
        val parts = normalizedTarget.split(" / ")
        val swappedTarget = if (parts.size == 2) {
            "${parts[1]} / ${parts[0]}"
        } else {
            null
        }

        cachedStops?.values?.filter { 
            val name = it.name.lowercase()
            name == normalizedTarget || (swappedTarget != null && name == swappedTarget)
        }?.toList() ?: emptyList()
    }

    suspend fun getTripTimes(): Map<String, TripTimes> = withContext(Dispatchers.IO) {
        if (cachedTripTimes != null) return@withContext cachedTripTimes!!

        try {
            val tripTimesJson = fetchString("trip_times.json")
            val serializer = MapSerializer(String.serializer(), serializer<TripTimes>())
            val result: Map<String, TripTimes> = json.decodeFromString(serializer, tripTimesJson)
            
            cachedTripTimes = result
            AppLogger.log("StmRepository", "Loaded ${result.size} trip times.")
            return@withContext result
        } catch (e: Exception) {
            AppLogger.error("StmRepository", "Error loading trip times", e)
            return@withContext emptyMap()
        }
    }
}
