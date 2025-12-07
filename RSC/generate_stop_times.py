import csv
import json
import os
from collections import defaultdict

# Configuration
GTFS_DIR = 'STM_GTFS_DATA'
OUTPUT_DIR = 'stop_times'

def generate_schedules():
    # 1. Charger les infos des voyages (Trips) pour avoir le lien Trip -> Route
    print("Chargement des voyages (trips.txt)...")
    trips_info = {} # trip_id -> { route_id, headsign, service_id }
    
    try:
        with open(os.path.join(GTFS_DIR, 'trips.txt'), 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                trips_info[row['trip_id']] = {
                    'route_id': row['route_id'],
                    'headsign': row['trip_headsign'],
                    'service_id': row['service_id']
                }
    except FileNotFoundError:
        print("Erreur: trips.txt introuvable.")
        return

    # 2. Charger les horaires (Stop Times) et grouper par Arrêt (Stop ID)
    print("Chargement des horaires (stop_times.txt)...")
    stops_data = defaultdict(list)
    
    try:
        with open(os.path.join(GTFS_DIR, 'stop_times.txt'), 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            count = 0
            for row in reader:
                stop_id = row['stop_id']
                trip_id = row['trip_id']
                arrival = row['arrival_time']
                
                # On enrichit avec les infos de la ligne
                trip = trips_info.get(trip_id)
                if trip:
                    stops_data[stop_id].append({
                        'r': trip['route_id'],      # r = route_id (minifié)
                        't': arrival,               # t = time
                        'h': trip['headsign'],      # h = headsign
                        's': trip['service_id']     # s = service_id (pour filtrer par jour)
                    })
                
                count += 1
                if count % 500000 == 0:
                    print(f"Traité {count} lignes...")

    except FileNotFoundError:
        print("Erreur: stop_times.txt introuvable.")
        return

    # 3. Écrire les fichiers JSON
    print(f"Génération de {len(stops_data)} fichiers JSON d'arrêts...")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    for stop_id, schedules in stops_data.items():
        # Trier par heure de passage pour faciliter l'affichage
        schedules.sort(key=lambda x: x['t'])
        
        output_path = os.path.join(OUTPUT_DIR, f"{stop_id}.json")
        with open(output_path, 'w', encoding='utf-8') as f:
            # Separators permet de minifier le JSON (enlever les espaces inutiles)
            json.dump(schedules, f, separators=(',', ':'))

    print("Terminé !")

if __name__ == "__main__":
    generate_schedules()
