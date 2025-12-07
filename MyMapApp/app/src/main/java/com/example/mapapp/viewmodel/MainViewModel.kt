package com.example.mapapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mapapp.data.StmRepository
import com.example.mapapp.data.model.BusRoute
import com.example.mapapp.data.model.BusStop
import com.example.mapapp.data.model.StopScheduleItem
import com.example.mapapp.data.model.TripTimes
import com.example.mapapp.utils.AppLogger
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StmRepository(application)

    // State Flows
    private val _routes = MutableStateFlow<Map<String, BusRoute>>(emptyMap())
    val routes: StateFlow<Map<String, BusRoute>> = _routes.asStateFlow()

    private val _stops = MutableStateFlow<Map<String, BusStop>>(emptyMap())
    val stops: StateFlow<Map<String, BusStop>> = _stops.asStateFlow()

    private val _busPositions = MutableStateFlow<GtfsRealtime.FeedMessage?>(null)
    val busPositions: StateFlow<GtfsRealtime.FeedMessage?> = _busPositions.asStateFlow()

    private val _filterRouteIds = MutableStateFlow<Set<String>>(emptySet())
    val filterRouteIds: StateFlow<Set<String>> = _filterRouteIds.asStateFlow()

    private val _stopSchedule = MutableStateFlow<List<StopScheduleItem>>(emptyList())
    val stopSchedule: StateFlow<List<StopScheduleItem>> = _stopSchedule.asStateFlow()

    private val _availableDirections = MutableStateFlow<List<String>>(emptyList())
    val availableDirections: StateFlow<List<String>> = _availableDirections.asStateFlow()

    private val _relatedStops = MutableStateFlow<List<BusStop>>(emptyList())
    val relatedStops: StateFlow<List<BusStop>> = _relatedStops.asStateFlow()

    private val _tripHeadsigns = MutableStateFlow<Map<String, String>>(emptyMap())
    val tripHeadsigns: StateFlow<Map<String, String>> = _tripHeadsigns.asStateFlow()

    private var fullScheduleCache: List<StopScheduleItem> = emptyList()
    private var selectedDirection: String? = null
    private var tripTimes: Map<String, TripTimes> = emptyMap()

    private var isPolling = false

    init {
        loadStaticData()
        startBusPolling()
    }

    private fun loadStaticData() {
        viewModelScope.launch {
            AppLogger.log("MainViewModel", "Loading static data...")
            
            // Parallel loading
            launch {
                val routes = repository.getRoutes()
                _routes.value = routes
                AppLogger.log("MainViewModel", "Routes loaded: ${routes.size}")
            }

            launch {
                val stops = repository.getStops()
                _stops.value = stops
                AppLogger.log("MainViewModel", "Stops loaded: ${stops.size}")
            }

            launch {
                tripTimes = repository.getTripTimes()
                AppLogger.log("MainViewModel", "Trip times loaded: ${tripTimes.size}")
            }
            
            launch {
                val headsigns = repository.getTripHeadsigns()
                _tripHeadsigns.value = headsigns
                AppLogger.log("MainViewModel", "Trip headsigns loaded: ${headsigns.size}")
            }
        }
    }

    private fun gtfsTimeToSeconds(time: String): Int {
        return try {
            val parts = time.split(':')
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val s = parts[2].toInt()
            h * 3600 + m * 60 + s
        } catch (e: Exception) {
            -1 // Invalid format
        }
    }

    private fun startBusPolling() {
        if (isPolling) return
        isPolling = true
        viewModelScope.launch {
            val yyyymmddFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val serviceDayCutoffHour = 4
            val tripStartBufferSeconds = 10 * 60 // 10 minutes

            while (true) {
                AppLogger.log("MainViewModel", "Polling bus positions...")
                val feed = repository.getBusPositions()
                if (feed != null) {
                    val now = LocalTime.now()
                    
                    // Determine the current service date based on the cutoff time.
                    val today = LocalDate.now()
                    val currentServiceDate = if (now.hour < serviceDayCutoffHour) today.minusDays(1) else today

                    val filteredEntities = feed.entityList.filter { entity ->
                        if (!entity.hasVehicle() || !entity.vehicle.hasTrip()) return@filter false

                        val trip = entity.vehicle.trip
                        
                        // --- 1. DATE CHECK ---
                        if (trip.hasStartDate()) {
                            val tripStartDate = try { LocalDate.parse(trip.startDate, yyyymmddFormatter) } catch (e: Exception) { null }
                            if (tripStartDate != null && tripStartDate != currentServiceDate) {
                                return@filter false // Hide if trip is not for the current service day
                            }
                        }

                        // --- 2. TIME CHECK ---
                        val schedule = tripTimes[trip.tripId]
                        if (schedule == null) return@filter true // Keep if we have no schedule info, to be safe

                        // Convert GTFS times to seconds from midnight
                        val startTimeInSeconds = gtfsTimeToSeconds(schedule.start)
                        val endTimeInSeconds = gtfsTimeToSeconds(schedule.end)
                        if (startTimeInSeconds < 0 || endTimeInSeconds < 0) return@filter true // Keep if format is bad

                        // Adjust current time for service day logic
                        var nowInSeconds = now.toSecondOfDay()
                        if (now.hour < serviceDayCutoffHour) {
                            nowInSeconds += 24 * 3600 // Add 24 hours if we're in the early morning
                        }
                        
                        // A trip is active if:
                        // - The current time is after its start time (with a buffer)
                        // - AND the current time is before its end time.
                        val isStarted = nowInSeconds >= (startTimeInSeconds - tripStartBufferSeconds)
                        val isNotEnded = nowInSeconds < endTimeInSeconds
                        
                        return@filter isStarted && isNotEnded
                    }
                    val filteredFeed = feed.toBuilder().clearEntity().addAllEntity(filteredEntities).build()
                    _busPositions.value = filteredFeed
                    AppLogger.log("MainViewModel", "Bus feed updated. Original: ${feed.entityList.size}, Filtered: ${filteredFeed.entityCount}")
                } else {
                    AppLogger.error("MainViewModel", "Bus feed fetch failed (null response)")
                }
                delay(10000) // Poll every 10 seconds
            }
        }
    }

    fun updateFilter(routeIds: Set<String>) {
        AppLogger.log("MainViewModel", "Filter updated: ${routeIds.size} routes selected")
        _filterRouteIds.value = routeIds
    }

    fun loadStopSchedule(stopId: String) {
        viewModelScope.launch {
            // Reset state
            _stopSchedule.value = emptyList()
            _availableDirections.value = emptyList()
            _relatedStops.value = emptyList()
            selectedDirection = null
            
            // 1. Find Related Stops (Siblings by name)
            val currentStop = _stops.value[stopId]
            if (currentStop != null) {
                val siblings = repository.getRelatedStops(currentStop.name)
                _relatedStops.value = siblings.sortedBy { it.id }
            }

            // 2. Fetch Schedule
            AppLogger.log("MainViewModel", "Fetching schedule for stop $stopId")
            val fullSchedule = repository.getStopSchedule(stopId)
            
            val now = LocalTime.now()
            val nowStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

            // Filter
            fullScheduleCache = fullSchedule
                .distinctBy { "${it.routeId}-${it.time}-${it.headsign}" }
                .filter { scheduleItem ->
                    val time = scheduleItem.time
                    
                    val isLateNow = now.hour >= 20
                    val isEarlySchedule = time.startsWith("00:") || time.startsWith("01:") || time.startsWith("02:") || time.startsWith("03:")

                    if (isLateNow && isEarlySchedule) {
                        true // Show next-day's early buses when it's late now.
                    } else {
                        // Otherwise, rely on string comparison. It's not perfect for all edge cases
                        // but covers same-day times and GTFS times > 24h correctly when `now` is not late.
                        time >= nowStr
                    }
                }
            
            // Extract unique directions
            val directions = fullScheduleCache.map { it.headsign }.distinct().sorted()
            _availableDirections.value = directions
            
            applyScheduleFilter()
            AppLogger.log("MainViewModel", "Schedule loaded: ${fullScheduleCache.size} upcoming trips")
        }
    }

    fun filterScheduleByDirection(direction: String?) {
        selectedDirection = direction
        applyScheduleFilter()
    }

    private fun applyScheduleFilter() {
        val filtered = if (selectedDirection != null) {
            fullScheduleCache.filter { it.headsign == selectedDirection }
        } else {
            fullScheduleCache
        }
        _stopSchedule.value = filtered.take(20)
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}