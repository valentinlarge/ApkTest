package com.example.mapapp

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mapapp.data.model.BusRoute
import com.example.mapapp.databinding.ActivityMainBinding
import com.example.mapapp.ui.MapManager
import com.example.mapapp.ui.ScheduleAdapter
import com.example.mapapp.utils.AppLogger
import com.example.mapapp.viewmodel.MainViewModel
import com.example.mapapp.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.location.permissions.PermissionsListener
import org.maplibre.android.location.permissions.PermissionsManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapLibreMap
    private var mapManager: MapManager? = null
    private var permissionsManager: PermissionsManager? = null
    private var locationComponent: LocationComponent? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private val scheduleAdapter = ScheduleAdapter()

    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    // UI State
    private var isDarkTheme = false
    private var showStops = false
    private var currentSelectedRouteId: String? = null
    private var selectedBusPosition: LatLng? = null
    private var isFollowingBus = false
    private var selectedVehicleId: String? = null
    private val BUBBLE_OFFSET_Y = 40f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize MapLibre
        MapLibre.getInstance(this)

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2. View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. Setup MapView
        binding.map.onCreate(savedInstanceState)
        binding.map.getMapAsync(this)

        // 4. Setup UI
        setupWindowInsets()
        setupButtons()
        setupBottomSheet()
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.scheduleRecyclerView.adapter = scheduleAdapter
    }

    private fun onRouteSelected(route: BusRoute) {
        // Apply filter
        viewModel.updateFilter(setOf(route.id))
        
        // Highlight route
        mapManager?.highlightBusRoute(route.id)
        showBusRouteDetails(route.id)
        
        // Disable clustering for single route view
        mapManager?.setClusteringEnabled(false)

        // Zoom to Route Bounds
        if (route.geometry.isNotEmpty()) {
            val builder = org.maplibre.android.geometry.LatLngBounds.Builder()
            route.geometry.forEach { path ->
                path.forEach { (lat, lon) ->
                    builder.include(org.maplibre.android.geometry.LatLng(lat, lon))
                }
            }
            val bounds = builder.build()
            
            val density = resources.displayMetrics.density
            val paddingDefault = (32 * density).toInt()
            val paddingRight = (40 * density).toInt() // Reduced FAB compensation
            val bottomSheetHeight = (resources.displayMetrics.heightPixels * 0.15).toInt() // Reduced to 15%
            
            map.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
                    bounds, 
                    paddingDefault, // left
                    paddingDefault, // top
                    paddingRight,   // right
                    bottomSheetHeight // bottom
                ),
                1000
            )
        }

        Toast.makeText(this, "Filtre appliqué: ${route.name}", Toast.LENGTH_SHORT).show()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabContainer) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = insets.bottom + 32
            v.layoutParams = params
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupButtons() {
        binding.fabCenterLocation.setOnClickListener {
            locationComponent?.lastKnownLocation?.let { location ->
                map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude), 15.0))
                Toast.makeText(this, "Centré sur votre position", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "Localisation non trouvée, assurez-vous que les permissions sont activées.", Toast.LENGTH_SHORT).show()
        }

        // Long click to show logs
        binding.fabCenterLocation.setOnLongClickListener {
            showDebugLogs()
            true
        }

        binding.fabSearch.setOnClickListener {
            val allRoutes = viewModel.routes.value.values.toList()
            if (allRoutes.isEmpty()) {
                Toast.makeText(this, "Aucune ligne disponible", Toast.LENGTH_SHORT).show()
            } else {
                val dialog = com.example.mapapp.ui.SearchDialogFragment(allRoutes) { route ->
                    onRouteSelected(route)
                }
                dialog.show(supportFragmentManager, "SearchRoutes")
            }
        }

        binding.fabThemeSwitch.setOnClickListener {
            toggleMapTheme()
        }

        binding.fabToggleRouteStops.setOnClickListener {
            showStops = !showStops
            updateStopButtonState()
            
            if (showStops) {
                val routeId = currentSelectedRouteId
                val route = if (routeId != null) viewModel.routes.value[routeId] else null
                
                if (route != null && route.stopIds.isNotEmpty()) {
                    mapManager?.updateStops(viewModel.stops.value, route.stopIds.toSet())
                    mapManager?.setStopsVisibility(true)
                    Toast.makeText(this@MainActivity, "${route.stopIds.size} arrêts affichés", Toast.LENGTH_SHORT).show()
                } else {
                    // Fallback
                    mapManager?.updateStops(viewModel.stops.value, null)
                    mapManager?.setStopsVisibility(true)
                }
            } else {
                mapManager?.setStopsVisibility(false)
                mapManager?.updateStops(viewModel.stops.value, null) // Reset filter
            }
        }

        binding.directionChips.setOnCheckedStateChangeListener {
            group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedIds[0])
                viewModel.filterScheduleByDirection(chip.text.toString())
            } else {
                viewModel.filterScheduleByDirection(null)
            }
        }

        binding.relatedStopsChips.setOnCheckedStateChangeListener {
            group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedIds[0])
                val stop = chip.tag as? com.example.mapapp.data.model.BusStop
                if (stop != null) {
                    val currentId = binding.sheetDirection.text.toString().replace("Arrêt #", "")
                    val position = LatLng(stop.lat, stop.lon)
                    
                    // Always highlight when switching
                    mapManager?.highlightStop(position)
                    
                    if (stop.id != currentId) {
                        showStopDetails(stop.id, stop.name, position)
                    }
                }
            }
        }
    }

    private fun updateStopButtonState() {
        val color = if (showStops) Color.parseColor("#E0F2F1") else Color.WHITE
        binding.fabToggleRouteStops.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun showDebugLogs() {
        val logText = AppLogger.logs.value
        AlertDialog.Builder(this)
            .setTitle("Debug Logs")
            .setMessage(logText.take(4000)) // Limit size
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear") { _, _ -> AppLogger.clear() }
            .show()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        
        // Set max height to 66% of screen
        val displayMetrics = resources.displayMetrics
        val height = displayMetrics.heightPixels
        val maxHeight = (height * 0.66).toInt()
        
        bottomSheetBehavior.maxHeight = maxHeight
        bottomSheetBehavior.isFitToContents = true

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    binding.fabContainer.animate().translationY(0f).setDuration(200).start()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val parentHeight = (binding.root as View).height
                val sheetTop = bottomSheet.top
                val sheetVisibleHeight = parentHeight - sheetTop
                
                if (sheetVisibleHeight > 0) {
                    binding.fabContainer.translationY = -sheetVisibleHeight.toFloat()
                } else {
                    binding.fabContainer.translationY = 0f
                }
            }
        })
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        map.uiSettings.isCompassEnabled = false // Hide compass
        loadStyle()

        // Set Camera to Montreal
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(45.5017, -73.5673))
            .zoom(12.0)
            .build()
        
        binding.zoomLevelIndicator.text = "Zoom: 12.00"
        
        setupMapInteractions()
    }

    private fun loadStyle() {
        val apiKey = BuildConfig.MAPTILER_KEY 
        val styleUrl = if (isDarkTheme) {
            "https://api.maptiler.com/maps/darkmatter/style.json?key=$apiKey"
        } else {
            "https://api.maptiler.com/maps/positron/style.json?key=$apiKey"
        }

        map.setStyle(styleUrl) { style ->
            enableLocationComponent(style)
            
            // Initialize MapManager
            mapManager = MapManager(this, map)
            mapManager?.initialize(style) {
                // Once map is ready, start observing data
                observeViewModel()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Routes
                launch {
                    viewModel.routes.collect {
                        mapManager?.updateRoutes(it)
                        // Force refresh buses with new colors when routes are loaded
                        mapManager?.updateBuses(viewModel.busPositions.value, viewModel.filterRouteIds.value, viewModel.tripHeadsigns.value)
                    }
                }
                // Stops
                launch {
                    viewModel.stops.collect {
                        mapManager?.updateStops(it)
                    }
                }
                // Real-time Buses
                launch {
                    viewModel.busPositions.collect { feed ->
                        mapManager?.updateBuses(feed, viewModel.filterRouteIds.value, viewModel.tripHeadsigns.value)
                        
                        val vid = selectedVehicleId
                        if (feed != null && vid != null) {
                            val vehicle = feed.entityList.find { it.hasVehicle() && it.vehicle.vehicle.id == vid }?.vehicle
                            if (vehicle != null) {
                                val newPos = LatLng(vehicle.position.latitude.toDouble(), vehicle.position.longitude.toDouble())
                                AppLogger.log("FollowMode", "Update for bus $vid. Pos: $newPos. Following: $isFollowingBus")
                                
                                selectedBusPosition = newPos
                                
                                if (isFollowingBus) {
                                    map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(newPos, 17.0), 1000)
                                }
                                updateBubblePosition()
                            } else {
                                AppLogger.log("FollowMode", "Bus $vid not found in feed")
                            }
                        }
                    }
                }
                // Filter Changes
                launch {
                    viewModel.filterRouteIds.collect {
                        mapManager?.updateBuses(viewModel.busPositions.value, it, viewModel.tripHeadsigns.value)
                        mapManager?.setRouteVisibility(it)
                    }
                }
                // Trip Headsigns (Refresh buses when loaded)
                launch {
                    viewModel.tripHeadsigns.collect {
                        mapManager?.updateBuses(viewModel.busPositions.value, viewModel.filterRouteIds.value, it)
                    }
                }
                // Schedule
                launch {
                    viewModel.stopSchedule.collect {
                        scheduleAdapter.submitList(it)
                        if (it.isNotEmpty() && bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                             bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    }
                }
                // Directions (Chips)
                launch {
                    viewModel.availableDirections.collect {
                        binding.directionChips.removeAllViews()
                        it.forEach {
                            val chip = com.google.android.material.chip.Chip(this@MainActivity).apply {
                                text = it
                                isCheckable = true
                            }
                            binding.directionChips.addView(chip)
                        }
                    }
                }
                // Related Stops (Siblings)
                launch {
                    viewModel.relatedStops.collect {
                        binding.relatedStopsChips.removeAllViews()
                        val currentIdStr = binding.sheetDirection.text.toString().replace("Arrêt #", "")
                        
                        it.forEach {
                            val chip = com.google.android.material.chip.Chip(this@MainActivity).apply {
                                text = "${it.id}" 
                                isCheckable = true
                                tag = it 
                                isChecked = it.id == currentIdStr
                            }
                            binding.relatedStopsChips.addView(chip)
                        }
                    }
                }
            }
        }
    }

    private fun setupMapInteractions() {
        // Bubble Tracking & Zoom Indicator
        map.addOnCameraMoveListener { 
            if (binding.busInfoBubbleRoot.root.visibility == View.VISIBLE && selectedBusPosition != null) {
                updateBubblePosition()
            }
            binding.zoomLevelIndicator.text = String.format("Zoom: %.2f", map.cameraPosition.zoom)
        }

        // Stop following if user moves map manually
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == 1 /* REASON_GESTURE */) {
                if (isFollowingBus) {
                    isFollowingBus = false
                    Toast.makeText(this, "Suivi arrêté", Toast.LENGTH_SHORT).show()
                }
            }
        }

        map.addOnMapClickListener { point ->
            val screenPoint = map.projection.toScreenLocation(point)
            
            // 1. Check for Buses (Label or Dot, Clustered or Flat)
            val busLayers = arrayOf(
                MapManager.BUS_LAYER_LABEL_ID, 
                MapManager.BUS_FLAT_LAYER_LABEL_ID,
                MapManager.BUS_LAYER_DOT_ID, 
                MapManager.BUS_FLAT_LAYER_DOT_ID
            )
            var features = map.queryRenderedFeatures(screenPoint, *busLayers)

            if (features.isNotEmpty()) {
                val feature = features[0]
                val geometry = feature.geometry() as? org.maplibre.geojson.Point
                val latLng = geometry?.let { LatLng(it.latitude(), it.longitude()) }
                
                if (latLng != null) {
                    val routeId = feature.getStringProperty("routeId")
                    val vehicleId = feature.getStringProperty("vehicleId")
                    val directionId = if (feature.hasProperty("directionId")) feature.getNumberProperty("directionId") else null
                    val headsign = if (feature.hasProperty("headsign")) feature.getStringProperty("headsign") else null
                    
                    showBusInfoBubble(routeId, vehicleId, directionId, headsign, latLng)
                }
                return@addOnMapClickListener true
            }

            // 2. Check for Stops
            features = map.queryRenderedFeatures(screenPoint, MapManager.STOP_LAYER_ID)
            if (features.isNotEmpty()) {
                isFollowingBus = false // Stop following bus
                val feature = features[0]
                val stopId = feature.getStringProperty("stopId")
                val stopName = feature.getStringProperty("stopName")
                val geometry = feature.geometry() as? org.maplibre.geojson.Point
                val latLng = geometry?.let { LatLng(it.latitude(), it.longitude()) }

                if (latLng != null) {
                    showStopDetails(stopId, stopName, latLng)
                }
                return@addOnMapClickListener true
            }

            // 3. Check for Routes
            val routeLayerIds = mapManager?.getAllRouteLayerIds()?.toTypedArray()
            if (routeLayerIds != null && routeLayerIds.isNotEmpty()) {
                features = map.queryRenderedFeatures(screenPoint, *routeLayerIds)
                if (features.isNotEmpty()) {
                    isFollowingBus = false // Stop following bus
                    val feature = features[0]
                    val routeId = feature.getStringProperty("routeId")
                    
                    if (routeId != null) {
                        mapManager?.highlightBusRoute(routeId)
                        showBusRouteDetails(routeId)
                    }
                    return@addOnMapClickListener true
                }
            }

            // 4. Dismiss Everything
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }

            // Reset Padding
            val current = map.cameraPosition
            val reset = CameraPosition.Builder()
                .target(current.target)
                .zoom(current.zoom)
                .bearing(current.bearing)
                .tilt(current.tilt)
                .padding(0.0, 0.0, 0.0, 0.0)
                .build()
            map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(reset))
            
            // Hide Bubble
            binding.busInfoBubbleRoot.root.visibility = View.GONE
            selectedBusPosition = null
            isFollowingBus = false
            selectedVehicleId = null

            // Reset UI
            scheduleAdapter.submitList(emptyList())
            mapManager?.highlightStop(null)
            mapManager?.highlightBusRoute(null)
            
            viewModel.updateFilter(emptySet()) 
            
            showStops = false
            updateStopButtonState()
            binding.fabToggleRouteStops.visibility = View.GONE
            mapManager?.setStopsVisibility(false)
            mapManager?.updateStops(viewModel.stops.value, null)
            
            currentSelectedRouteId = null
            
            // Re-enable clustering
            mapManager?.setClusteringEnabled(true)
            
            false
        }
    }

    private fun showBusInfoBubble(routeId: String, vehicleId: String, directionId: Number?, headsign: String?, position: LatLng) {
        selectedBusPosition = position
        selectedVehicleId = vehicleId
        isFollowingBus = true
        
        val route = viewModel.routes.value[routeId]
        val routeName = route?.name ?: "Ligne $routeId"
        
        // Determine Destination
        var destText = "Destination inconnue"
        
        if (!headsign.isNullOrEmpty()) {
            destText = "Vers $headsign"
        } else if (route != null && route.directions.isNotEmpty()) {
            // Fallback to directionId
            val dests = route.directions.values.toList()
            if (directionId != null) {
                val idx = directionId.toInt()
                if (idx >= 0 && idx < dests.size) {
                    destText = "Vers ${dests[idx]}"
                } else {
                     destText = "Vers ${dests.joinToString(" / ")}"
                }
            } else {
                destText = "Vers ${dests.joinToString(" / ")}"
            }
        }
        
        // Populate Data using Binding
        binding.busInfoBubbleRoot.bubbleRouteName.text = routeName
        binding.busInfoBubbleRoot.bubbleDestination.text = destText
        binding.busInfoBubbleRoot.bubbleVehicleId.text = "Bus n°$vehicleId"
        
        binding.busInfoBubbleRoot.btnBubbleShowRoute.setOnClickListener {
            if (route != null) {
                onRouteSelected(route)
                // Hide bubble
                binding.busInfoBubbleRoot.root.visibility = View.GONE
                selectedBusPosition = null
                isFollowingBus = false
                selectedVehicleId = null
            } else {
                Toast.makeText(this, "Info route non disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // Show and Position
        binding.busInfoBubbleRoot.root.visibility = View.VISIBLE
        updateBubblePosition()
        
        // Optional: Center map on bus with RESET PADDING
        val cameraPosition = CameraPosition.Builder()
            .target(position)
            .zoom(17.0)
            .padding(0.0, 0.0, 0.0, 0.0)
            .build()
        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cameraPosition), 1000)
        
        Toast.makeText(this, "Suivi activé", Toast.LENGTH_SHORT).show()
    }

    private fun updateBubblePosition() {
        val pos = selectedBusPosition ?: return
        val screenPoint = map.projection.toScreenLocation(pos)
        val bubbleView = binding.busInfoBubbleRoot.root
        
        // Center horizontally
        bubbleView.translationX = screenPoint.x - (bubbleView.width / 2f)
        // Position above (subtract height and an additional offset)
        bubbleView.translationY = screenPoint.y - bubbleView.height.toFloat() - BUBBLE_OFFSET_Y
    }

    private fun showStopDetails(stopId: String, stopName: String, position: LatLng) {
        mapManager?.highlightStop(position) // Highlight this stop
        binding.sheetRouteTitle.text = stopName
        binding.sheetDirection.text = "Arrêt #$stopId"
        binding.sheetVehicleId.text = "Chargement des horaires..."
        
        // Hide Route Button
        binding.fabToggleRouteStops.visibility = View.GONE
        
        // Show stop-specific UI
        binding.relatedStopsChips.visibility = View.VISIBLE
        binding.directionChips.visibility = View.VISIBLE
        
        viewModel.loadStopSchedule(stopId)
        viewModel.updateFilter(emptySet()) // Clear route filter
        
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED // Use EXPANDED now that height is fixed
        
        // Center map on stop with adjusted padding for large sheet (35% height)
        val density = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels
        val bottomPadding = (screenHeight * 0.35).toInt() 
        val rightPadding = (40 * density).toInt() // Reduced FAB compensation
        
        val cameraPosition = map.cameraPosition
        val newPosition = CameraPosition.Builder()
            .target(position)
            .zoom(15.0)
            .padding(0.0, 0.0, rightPadding.toDouble(), bottomPadding.toDouble()) 
            .bearing(cameraPosition.bearing)
            .tilt(cameraPosition.tilt)
            .build()

        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(newPosition), 800)
    }

    private fun showBusRouteDetails(routeId: String) {
        currentSelectedRouteId = routeId
        mapManager?.highlightStop(null) // Clear stop highlight if any
        val route = viewModel.routes.value[routeId]
        binding.sheetRouteTitle.text = route?.name ?: "Ligne $routeId"

        // Setup Stop Button
        binding.fabToggleRouteStops.visibility = View.VISIBLE
        updateStopButtonState()

        // Hide stop-specific UI
        binding.relatedStopsChips.visibility = View.GONE
        binding.directionChips.visibility = View.GONE

        // Simplified direction logic
        val directions = route?.directions?.values?.joinToString(", ") ?: ""
        binding.sheetDirection.text = "Destinations: $directions"
        binding.sheetVehicleId.text = "" // Clear vehicle ID
        
        scheduleAdapter.submitList(emptyList()) // Clear any previous schedule

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun toggleMapTheme() {
        isDarkTheme = !isDarkTheme
        if (::map.isInitialized) {
            loadStyle() // Reloads style and re-initializes MapManager
            val mode = if (isDarkTheme) "Sombre" else "Clair"
            Toast.makeText(this, "Mode $mode", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationComponent = map.locationComponent
            val options = LocationComponentActivationOptions.builder(this, loadedMapStyle)
                .useDefaultLocationEngine(true)
                .build()
            
            locationComponent?.activateLocationComponent(options)
            locationComponent?.isLocationComponentEnabled = true
            locationComponent?.cameraMode = CameraMode.TRACKING
            locationComponent?.renderMode = RenderMode.COMPASS
            
            locationComponent?.lastKnownLocation?.let { location ->
                 map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude), 15.0))
            }
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager?.requestLocationPermissions(this)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(this, "Permission requise.", Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            map.getStyle { style -> enableLocationComponent(style) }
        } else {
            Toast.makeText(this, "Permissions refusées.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() { super.onStart(); binding.map.onStart() }
    override fun onResume() { super.onResume(); binding.map.onResume() }
    override fun onPause() { super.onPause(); binding.map.onPause() }
    override fun onStop() { super.onStop(); binding.map.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); binding.map.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); binding.map.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); binding.map.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}