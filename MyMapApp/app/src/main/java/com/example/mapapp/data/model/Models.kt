package com.example.mapapp.data.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// --- stm_complet.json ---
@Keep
@Serializable
data class RouteMetadata(
    val nom: String,
    val couleur: String,
    val directions: Map<String, DirectionMetadata>
)

@Keep
@Serializable
data class DirectionMetadata(
    val destination: String,
    // "trajet" exists in JSON but is empty/unused currently, so we can ignore or keep it
    val trajet: List<String> = emptyList() 
)

// --- stm_parcours.json (New Structure) ---
@Keep
@Serializable
data class RouteParcoursData(
    val shapes: List<List<List<Double>>>,
    val stops: List<String> = emptyList()
)

// --- stm_stops.json ---
@Keep
@Serializable
data class Stop(
    val name: String,
    val lat: Double,
    val lon: Double
)

// --- stop_times/{id}.json ---
@Keep
@Serializable
data class StopScheduleItem(
    @SerialName("r") val routeId: String,
    @SerialName("t") val time: String,
    @SerialName("h") val headsign: String,
    @SerialName("s") val serviceId: String
)

// --- trip_times.json ---
@Keep
@Serializable
data class TripTimes(
    val start: String,
    val end: String
)

// --- Internal App Models (Clean Architecture) ---
data class BusRoute(
    val id: String,
    val name: String,
    val color: String,
    val directions: Map<String, String>, // Direction ID -> Destination Name
    val geometry: List<List<Pair<Double, Double>>>, // MultiLineString (List of Paths)
    val stopIds: List<String>
)

data class BusStop(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double
)