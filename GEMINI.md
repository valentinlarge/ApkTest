# Project Overview - MyMapApp (V2)

MyMapApp is an advanced Android application for tracking public transit (buses) in Montreal (STM) in real-time. It visualizes bus positions, routes, and stops on an interactive map using MapLibre GL.

## 🏗️ Architecture

The project is divided into two main components:

1.  **MyMapApp (Android Client)**: The mobile application written in Kotlin.
2.  **RSC (Resource/Script)**: Python scripts for processing GTFS static data into optimized JSON files used by the app.

---

## 📱 MyMapApp (Android Application)

### Technologies
*   **Language**: Kotlin.
*   **Map Engine**: MapLibre GL Native Android (v11+).
*   **Rendering**: `TextureView` (via `maplibre_renderTextureMode`) for seamless UI overlay integration.
*   **Architecture**: MVVM (Model-View-ViewModel) with `StateFlow` and Coroutines.
*   **UI Toolkit**: ViewBinding, Material Components (Bottom Sheet, FABs, Dialogs).
*   **Data Formats**: Protocol Buffers (GTFS-RT), GeoJSON (Map Data), JSON (Static Data).

### Key Features
*   **Real-time Tracking**: Displays buses moving in real-time (polling every 10s) with interpolated animations.
*   **Follow Mode**: Automatically keeps the camera focused on a selected bus as it moves.
*   **Smart Clustering**: Groups nearby buses using dynamic radii and color-coded clusters (Gray scale) to reduce clutter.
*   **Visual Orientation**: Buses have orbiting arrows indicating their bearing/heading, colored to match the bus line.
*   **Route Focus**: Selecting a bus or route isolates that line, shows its full path (including variations), and hides others.
*   **Info Bubble**: Custom floating UI (Comic bubble style) anchored to the bus, displaying Route, Destination, and Bus ID.
*   **Stop Filtering**: Button to toggle visibility of stops specific to the selected route.
*   **Search**: Floating search dialog to quickly find and focus on specific bus lines.

### Data Flow
1.  **Static Data**: Loaded from `assets/` at startup (`stm_routes.json`, `stm_stops.json`, `stm_parcours.json`, `stm_trips.json`).
2.  **Real-time Data**: Polled from a proxy server (`webllington.org/stm/stm_bus.pb`) which mirrors the STM GTFS-RT feed.
3.  **Rendering**: Data is converted to GeoJSON `FeatureCollection` and rendered via MapLibre Sources and Layers (`CircleLayer`, `SymbolLayer`, `LineLayer`).

---

## 🐍 RSC (Data Processing Scripts)

### `stm_convert.py`
This Python script is the backbone of the data preparation. It downloads the raw GTFS Static zip from STM and generates optimized JSON files for the Android app.

**Outputs:**
1.  **`stm_parcours.json`**: Maps `route_id` to:
    *   `shapes`: A list of **all** physical paths (MultiLineString) for the route (Aller, Retour, variants).
    *   `stops`: A list of all `stop_id`s served by this route.
2.  **`stm_stops.json`**: Maps `stop_id` to name, lat, lon.
3.  **`stm_trips.json`**: Maps `trip_id` to `trip_headsign` (Destination name) to allow the app to display readable destinations instead of IDs.

### Usage
```bash
cd RSC
python3 stm_convert.py
# Then copy the generated *.json files to MyMapApp/app/src/main/assets/
```

---

## 📂 Project Structure Highlights

*   `ui/MapManager.kt`: Core logic for MapLibre interaction. Manages Sources (Clustered vs Flat), Layers, and GeoJSON updates.
*   `MainActivity.kt`: UI orchestration, ViewModel observation, and interaction logic (Clicks, BottomSheet, Search).
*   `viewmodel/MainViewModel.kt`: Data fetching (Coroutine polling), parsing, and state management.
*   `data/StmRepository.kt`: Handles network requests and caching of static/real-time data.
*   `ui/SearchDialogFragment.kt`: Handles the route search UI.

## 🔧 Setup & Build

**Prerequisites:**
*   Android SDK (API 34)
*   `local.properties` with `MAPTILER_KEY`.

**Build Commands:**
```bash
./gradlew assembleDebug   # Dev build
./gradlew assembleRelease # Final build (Signed)
```