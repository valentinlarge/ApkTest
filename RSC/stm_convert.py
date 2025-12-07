import pandas as pd
import zipfile
import requests
import io
import json

# 1. Télécharger le GTFS Static de la STM
url = "http://www.stm.info/sites/default/files/gtfs/gtfs_stm.zip"
print("Téléchargement du GTFS...")
r = requests.get(url)
z = zipfile.ZipFile(io.BytesIO(r.content))

# 2. Lire les fichiers nécessaires
print("Lecture des fichiers...")
trips = pd.read_csv(z.open('trips.txt'))
shapes = pd.read_csv(z.open('shapes.txt'))
stop_times = pd.read_csv(z.open('stop_times.txt'))

# 3. Optimisation : On garde tous les tracés valides pour chaque ligne
print("Traitement des tracés...")
# On filtre les shapes qui ne sont pas utilisés dans trips (optionnel mais propre)
used_shape_ids = trips['shape_id'].unique()
filtered_shapes = shapes[shapes['shape_id'].isin(used_shape_ids)]

# 3b. Extraction des arrêts par ligne
print("Traitement des arrêts par ligne...")
route_trips = trips[['route_id', 'trip_id']]
trip_stops = stop_times[['trip_id', 'stop_id']]
merged = pd.merge(route_trips, trip_stops, on='trip_id')
route_stops = merged.groupby('route_id')['stop_id'].unique().apply(list).to_dict()

# 4. Construction du JSON final
# Structure : { "15": { "shapes": [ [path1], [path2] ], "stops": ["123", "456"] } }
result = {}

# On groupe trips par route_id pour avoir tous les shape_id de la route
route_shapes_map = trips.groupby('route_id')['shape_id'].unique()

for route_id_int, shape_ids in route_shapes_map.items():
    route_id = str(route_id_int)
    
    paths_list = []
    for shape_id in shape_ids:
        # Récupérer les points pour ce shape
        points = filtered_shapes[filtered_shapes['shape_id'] == shape_id].sort_values('shape_pt_sequence')
        if not points.empty:
            path = points[['shape_pt_lat', 'shape_pt_lon']].values.tolist()
            paths_list.append(path)
    
    # Stops
    stops_list = [str(s) for s in route_stops.get(route_id_int, [])]
    
    result[route_id] = {
        "shapes": paths_list,
        "stops": stops_list
    }

# 5. Sauvegarde des tracés et arrêts liés
with open('stm_parcours.json', 'w') as f:
    json.dump(result, f)

print(f"Terminé ! Fichier stm_parcours.json généré avec {len(result)} lignes.")

# 6. Lire et traiter les arrêts (stops.txt)
print("Lecture et traitement des arrêts...")
stops = pd.read_csv(z.open('stops.txt'))

stops_result = {}
for index, row in stops.iterrows():
    stop_id = str(row['stop_id'])
    stop_name = row['stop_name']
    stop_lat = row['stop_lat']
    stop_lon = row['stop_lon']
    
    stops_result[stop_id] = {
        "name": stop_name,
        "lat": stop_lat,
        "lon": stop_lon
    }

# 7. Sauvegarde des arrêts
with open('stm_stops.json', 'w') as f:
    json.dump(stops_result, f)

print(f"Terminé ! Fichier stm_stops.json généré avec {len(stops_result)} arrêts.")

# 8. Génération du mapping Trip -> Headsign
print("Génération de stm_trips.json...")
trip_headsigns = {}
# trips est déjà chargé en haut (étape 2)
for index, row in trips.iterrows():
    trip_id = str(row['trip_id'])
    headsign = row['trip_headsign']
    trip_headsigns[trip_id] = headsign

with open('stm_trips.json', 'w') as f:
    json.dump(trip_headsigns, f)

print(f"Terminé ! Fichier stm_trips.json généré avec {len(trip_headsigns)} trips.")