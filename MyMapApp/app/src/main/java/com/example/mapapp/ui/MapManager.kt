package com.example.mapapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.mapapp.R
import com.example.mapapp.data.model.BusRoute
import com.example.mapapp.data.model.BusStop
import com.google.transit.realtime.GtfsRealtime
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

class MapManager(private val context: Context, private val map: MapLibreMap) {

    companion object {
        const val BUS_SOURCE_ID = "bus-source"
        const val BUS_SOURCE_FLAT_ID = "bus-source-flat"
        
        const val BUS_LAYER_LABEL_ID = "bus-layer-label"
        const val BUS_LAYER_DOT_ID = "bus-layer-dot"
        const val BUS_BEARING_LAYER_ID = "bus-bearing-layer"
        
        const val BUS_FLAT_LAYER_LABEL_ID = "bus-flat-layer-label"
        const val BUS_FLAT_LAYER_DOT_ID = "bus-flat-layer-dot"
        const val BUS_FLAT_BEARING_LAYER_ID = "bus-flat-bearing-layer"
        
        const val BUS_BEARING_ICON_ID = "bus-bearing-icon"
        const val BUS_CLUSTER_LAYER_ID = "bus-cluster-layer"
        const val BUS_CLUSTER_COUNT_LAYER_ID = "bus-cluster-count-layer"
        const val BUS_ICON_ID = "bus-icon-v3"
        
        const val STOP_SOURCE_ID = "stop-source"
        const val STOP_LAYER_ID = "stop-layer"
        const val STOP_ICON_ID = "stop-icon"

        const val SELECTED_STOP_SOURCE_ID = "selected-stop-source"
        const val SELECTED_STOP_LAYER_ID = "selected-stop-layer"

        const val HIGHLIGHTED_ROUTE_SOURCE_ID = "highlighted-route-source"
        const val HIGHLIGHTED_ROUTE_LAYER_ID = "highlighted-route-layer"
    }

    private var busSource: GeoJsonSource? = null
    private var busSourceFlat: GeoJsonSource? = null
    private var stopSource: GeoJsonSource? = null
    private var selectedStopSource: GeoJsonSource? = null
    private var highlightedRouteSource: GeoJsonSource? = null
    private var routeData: Map<String, BusRoute> = emptyMap()

    // Colors from resources
    private val stopColor by lazy { ContextCompat.getColor(context, R.color.bus_stop_color) }
    private val strokeColor by lazy { ContextCompat.getColor(context, R.color.map_stroke_color) }
    private val textColor by lazy { ContextCompat.getColor(context, R.color.map_text_color) }

    fun initialize(style: Style, onReady: () -> Unit) {
        // Add Images
        val stopDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus_dot)
        if (stopDrawable != null) {
            style.addImage(STOP_ICON_ID, drawableToBitmap(stopDrawable), false)
        }

        val bearingDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus_bearing)
        if (bearingDrawable != null) {
            style.addImage(BUS_BEARING_ICON_ID, drawableToBitmap(bearingDrawable), true)
        }

        // Sources
        val busSourceOptions = GeoJsonOptions()
            .withCluster(true)
            .withClusterRadius(42)
            .withClusterMaxZoom(14)

        busSource = GeoJsonSource(BUS_SOURCE_ID, busSourceOptions)
        style.addSource(busSource!!)
        
        // Flat Source (No Clustering)
        busSourceFlat = GeoJsonSource(BUS_SOURCE_FLAT_ID)
        style.addSource(busSourceFlat!!)
        
        stopSource = GeoJsonSource(STOP_SOURCE_ID)
        style.addSource(stopSource!!)

        selectedStopSource = GeoJsonSource(SELECTED_STOP_SOURCE_ID)
        style.addSource(selectedStopSource!!)

        highlightedRouteSource = GeoJsonSource(HIGHLIGHTED_ROUTE_SOURCE_ID)
        style.addSource(highlightedRouteSource!!)

        // Layers
        setupLayers(style)
        onReady()
    }

    private fun setupLayers(style: Style) {
        // 0. Selected Stop Highlight (Bottom)
        val highlightLayer = CircleLayer(SELECTED_STOP_LAYER_ID, SELECTED_STOP_SOURCE_ID)
        highlightLayer.setProperties(
            PropertyFactory.circleColor(Color.parseColor("#FFD700")),
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
        style.addLayer(highlightLayer)

        val highlightedRouteLayer = LineLayer(HIGHLIGHTED_ROUTE_LAYER_ID, HIGHLIGHTED_ROUTE_SOURCE_ID)
        highlightedRouteLayer.setProperties(
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineColor(Color.RED),
            PropertyFactory.lineOpacity(0.8f),
            PropertyFactory.visibility(Property.NONE)
        )
        style.addLayerBelow(highlightedRouteLayer, SELECTED_STOP_LAYER_ID)

        // --- CLUSTERING LAYERS (Default Visible) ---
        val clusterLayer = CircleLayer(BUS_CLUSTER_LAYER_ID, BUS_SOURCE_ID)
        clusterLayer.setFilter(Expression.has("point_count"))
        clusterLayer.setProperties(
            PropertyFactory.circleColor(
                Expression.step(Expression.get("point_count"),
                    Expression.color(Color.parseColor("#E0E0E0")),
                    Expression.stop(5, Expression.color(Color.parseColor("#C0C0C0"))),
                    Expression.stop(10, Expression.color(Color.parseColor("#A0A0A0"))),
                    Expression.stop(20, Expression.color(Color.parseColor("#707070"))),
                    Expression.stop(30, Expression.color(Color.parseColor("#404040"))),
                    Expression.stop(50, Expression.color(Color.parseColor("#101010")))
                )
            ),
            PropertyFactory.circleRadius(
                Expression.step(Expression.get("point_count"),
                    Expression.literal(18f),
                    Expression.stop(5, Expression.literal(21f)),
                    Expression.stop(10, Expression.literal(24f)),
                    Expression.stop(20, Expression.literal(27f)),
                    Expression.stop(30, Expression.literal(30f)),
                    Expression.stop(50, Expression.literal(34f))
                )
            ),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
        style.addLayer(clusterLayer)

        val clusterCountLayer = SymbolLayer(BUS_CLUSTER_COUNT_LAYER_ID, BUS_SOURCE_ID)
        clusterCountLayer.setFilter(Expression.has("point_count"))
        clusterCountLayer.setProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(Color.WHITE),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold", "Open Sans Bold", "Arial Unicode MS Bold"))
        )
        style.addLayer(clusterCountLayer)

        // --- INDIVIDUAL BUS LAYERS ---
        val dotLayer = CircleLayer(BUS_LAYER_DOT_ID, BUS_SOURCE_ID)
        dotLayer.setFilter(Expression.not(Expression.has("point_count")))
        dotLayer.setProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(14f),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
        style.addLayer(dotLayer)

        val bearingLayer = SymbolLayer(BUS_BEARING_LAYER_ID, BUS_SOURCE_ID)
        bearingLayer.setFilter(Expression.not(Expression.has("point_count")))
        bearingLayer.setProperties(
            PropertyFactory.iconImage(BUS_BEARING_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconColor(Expression.toColor(Expression.get("color")))
        )
        style.addLayer(bearingLayer)

        val labelLayer = SymbolLayer(BUS_LAYER_LABEL_ID, BUS_SOURCE_ID)
        labelLayer.setFilter(Expression.not(Expression.has("point_count")))
        labelLayer.setProperties(
            PropertyFactory.textField(Expression.get("routeId")),
            PropertyFactory.textColor(Color.WHITE),
            PropertyFactory.textSize(13f),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold", "Open Sans Bold", "Arial Unicode MS Bold")),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true)
        )
        style.addLayer(labelLayer)

        // --- FLAT LAYERS (No Clustering, Default Hidden) ---
        val flatDotLayer = CircleLayer(BUS_FLAT_LAYER_DOT_ID, BUS_SOURCE_FLAT_ID)
        flatDotLayer.setProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(14f),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.visibility(Property.NONE)
        )
        style.addLayer(flatDotLayer)

        val flatBearingLayer = SymbolLayer(BUS_FLAT_BEARING_LAYER_ID, BUS_SOURCE_FLAT_ID)
        flatBearingLayer.setProperties(
            PropertyFactory.iconImage(BUS_BEARING_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.visibility(Property.NONE)
        )
        style.addLayer(flatBearingLayer)

        val flatLabelLayer = SymbolLayer(BUS_FLAT_LAYER_LABEL_ID, BUS_SOURCE_FLAT_ID)
        flatLabelLayer.setProperties(
            PropertyFactory.textField(Expression.get("routeId")),
            PropertyFactory.textColor(Color.WHITE),
            PropertyFactory.textSize(13f),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold", "Open Sans Bold", "Arial Unicode MS Bold")),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.visibility(Property.NONE)
        )
        style.addLayer(flatLabelLayer)

        // 3. Stops
        val stopLayer = SymbolLayer(STOP_LAYER_ID, STOP_SOURCE_ID)
        stopLayer.setProperties(
            PropertyFactory.iconImage(STOP_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.7f),
            PropertyFactory.iconColor(stopColor),
            PropertyFactory.visibility(Property.NONE) 
        )
        style.addLayer(stopLayer)
    }

    fun highlightStop(position: org.maplibre.android.geometry.LatLng?) {
        if (position != null) {
            val point = Point.fromLngLat(position.longitude, position.latitude)
            selectedStopSource?.setGeoJson(Feature.fromGeometry(point))
        } else {
            selectedStopSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    fun highlightBusRoute(routeId: String?) {
        highlightStop(null) 

        map.style?.let { style ->
            if (routeId != null) {
                val route = routeData[routeId]
                if (route != null) {
                    val multiPoints = route.geometry.map { path ->
                        path.map { Point.fromLngLat(it.second, it.first) }
                    }
                    val multiLineString = MultiLineString.fromLngLats(multiPoints)
                    highlightedRouteSource?.setGeoJson(multiLineString)
                    style.getLayer(HIGHLIGHTED_ROUTE_LAYER_ID)?.setProperties(
                        PropertyFactory.lineColor(parseColor(getColorForRouteHex(routeId))),
                        PropertyFactory.visibility(Property.VISIBLE)
                    )
                } else {
                    highlightedRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                    style.getLayer(HIGHLIGHTED_ROUTE_LAYER_ID)?.setProperties(PropertyFactory.visibility(Property.NONE))
                }
            } else {
                highlightedRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                style.getLayer(HIGHLIGHTED_ROUTE_LAYER_ID)?.setProperties(PropertyFactory.visibility(Property.NONE))
            }
        }
    }

    fun updateRoutes(routes: Map<String, BusRoute>) {
        this.routeData = routes
        map.style?.let { style ->
            routes.forEach { (routeId, route) ->
                val sourceId = "route-$routeId-source"
                val layerId = "route-$routeId-layer"

                if (style.getSource(sourceId) == null) {
                    val multiPoints = route.geometry.map { path ->
                        path.map { Point.fromLngLat(it.second, it.first) }
                    }
                    val multiLineString = MultiLineString.fromLngLats(multiPoints)
                    style.addSource(GeoJsonSource(sourceId, multiLineString))

                    val lineLayer = LineLayer(layerId, sourceId)
                    val color = parseColor(getColorForRouteHex(routeId))
                    
                    lineLayer.setProperties(
                        PropertyFactory.lineWidth(3f),
                        PropertyFactory.lineColor(color),
                        PropertyFactory.lineOpacity(0.4f),
                        PropertyFactory.visibility(Property.NONE)
                    )
                    style.addLayerBelow(lineLayer, BUS_LAYER_DOT_ID)
                }
            }
        }
    }

    fun updateStops(stops: Map<String, BusStop>, filterStopIds: Set<String>? = null) {
        val filteredStops = if (filterStopIds != null) {
            stops.filterKeys { filterStopIds.contains(it) }
        } else {
            stops
        }

        val features = filteredStops.map { (id, stop) ->
            Feature.fromGeometry(Point.fromLngLat(stop.lon, stop.lat)).apply {
                addStringProperty("stopId", id)
                addStringProperty("stopName", stop.name)
            }
        }
        stopSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun updateBuses(feed: GtfsRealtime.FeedMessage?, filterRouteIds: Set<String>, tripHeadsigns: Map<String, String> = emptyMap()) {
        if (feed == null) return

        val features = ArrayList<Feature>()
        
        for (entity in feed.entityList) {
            if (entity.hasVehicle()) {
                val vehicle = entity.vehicle
                val routeId = vehicle.trip.routeId
                
                if (filterRouteIds.isNotEmpty() && !filterRouteIds.contains(routeId)) continue

                val point = Point.fromLngLat(vehicle.position.longitude.toDouble(), vehicle.position.latitude.toDouble())
                val feature = Feature.fromGeometry(point)
                
                feature.addStringProperty("vehicleId", vehicle.vehicle.id)
                feature.addStringProperty("tripId", vehicle.trip.tripId)
                feature.addStringProperty("routeId", routeId)
                
                val headsign = tripHeadsigns[vehicle.trip.tripId] ?: ""
                feature.addStringProperty("headsign", headsign)
                
                if (vehicle.trip.hasDirectionId()) {
                    feature.addNumberProperty("directionId", vehicle.trip.directionId)
                }
                
                val assignedColor = getColorForRouteHex(routeId)
                feature.addStringProperty("color", assignedColor)
                feature.addNumberProperty("bearing", vehicle.position.bearing)
                
                features.add(feature)
            }
        }
        
        val fc = FeatureCollection.fromFeatures(features)
        busSource?.setGeoJson(fc)
        busSourceFlat?.setGeoJson(fc)
    }

    fun setClusteringEnabled(enabled: Boolean) {
        map.style?.let { style ->
            val clusterVisibility = if (enabled) Property.VISIBLE else Property.NONE
            val flatVisibility = if (enabled) Property.NONE else Property.VISIBLE

            style.getLayer(BUS_CLUSTER_LAYER_ID)?.setProperties(PropertyFactory.visibility(clusterVisibility))
            style.getLayer(BUS_CLUSTER_COUNT_LAYER_ID)?.setProperties(PropertyFactory.visibility(clusterVisibility))
            style.getLayer(BUS_LAYER_DOT_ID)?.setProperties(PropertyFactory.visibility(clusterVisibility))
            style.getLayer(BUS_BEARING_LAYER_ID)?.setProperties(PropertyFactory.visibility(clusterVisibility))
            style.getLayer(BUS_LAYER_LABEL_ID)?.setProperties(PropertyFactory.visibility(clusterVisibility))

            style.getLayer(BUS_FLAT_LAYER_DOT_ID)?.setProperties(PropertyFactory.visibility(flatVisibility))
            style.getLayer(BUS_FLAT_BEARING_LAYER_ID)?.setProperties(PropertyFactory.visibility(flatVisibility))
            style.getLayer(BUS_FLAT_LAYER_LABEL_ID)?.setProperties(PropertyFactory.visibility(flatVisibility))
        }
    }

    fun setRouteVisibility(visibleRouteIds: Set<String>) {
        map.style?.let { style ->
            routeData.keys.forEach { routeId ->
                val layerId = "route-$routeId-layer"
                style.getLayer(layerId)?.setProperties(
                    PropertyFactory.visibility(
                        if (visibleRouteIds.contains(routeId)) Property.VISIBLE else Property.NONE
                    )
                )
            }
        }
    }

    fun setStopsVisibility(visible: Boolean) {
        map.style?.getLayer(STOP_LAYER_ID)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
        )
    }

    private fun parseColor(hex: String): Int {
        return try {
            var cleanHex = hex.trim()
            if (cleanHex.startsWith("#")) {
                cleanHex = cleanHex.substring(1)
            }
            if (cleanHex.matches(Regex("^[0-9a-fA-F]{6}$"))) {
                Color.parseColor("#$cleanHex")
            } else {
                Color.GRAY
            }
        } catch (e: Exception) {
            Color.GRAY
        }
    }

    private fun getColorForRouteHex(routeId: String): String {
        val cleanId = routeId.trim()
        val route = routeData[cleanId]
        
        if (route == null) {
            return "#00FF00"
        }

        val routeNum = cleanId.toIntOrNull()

        val customColor = when (routeNum) {
            in 10..249 -> "#0000FF"
            in 250..299 -> "#FFD700"
            in 300..399 -> "#000000"
            in 400..499 -> {
                when (routeNum) {
                    406, 439, 470 -> "#800080"
                    else -> "#00FF00"
                }
            }
            else -> {
                var rawColor = route.color.trim()
                if (rawColor.startsWith("#")) {
                    rawColor = rawColor.substring(1)
                }
                if (rawColor.length == 6 && rawColor.matches(Regex("^[0-9a-fA-F]+$"))) {
                    "#$rawColor"
                } else {
                    "#FF0000"
                }
            }
        }
        return customColor
    }
    fun getAllRouteLayerIds(): List<String> {
        return routeData.keys.map { routeId -> "route-$routeId-layer" }
    }
    
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}