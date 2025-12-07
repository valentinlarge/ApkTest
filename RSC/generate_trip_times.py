import csv
import json
import os
from collections import defaultdict

# Configuration
GTFS_DIR = 'STM_GTFS_DATA'
OUTPUT_FILE = 'trip_times.json'

def generate_trip_times():
    print("Chargement des horaires (stop_times.txt) pour trouver les heures de début et de fin de voyage...")
    # Initialize with values that will be easily replaced by the first real times found.
    trip_times = defaultdict(lambda: {'start': '99:99:99', 'end': '00:00:00'})
    
    try:
        with open(os.path.join(GTFS_DIR, 'stop_times.txt'), 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            count = 0
            for row in reader:
                trip_id = row['trip_id']
                arrival_time = row['arrival_time']
                departure_time = row['departure_time']
                
                # Update start time with the earliest departure time
                if departure_time < trip_times[trip_id]['start']:
                    trip_times[trip_id]['start'] = departure_time
                
                # Update end time with the latest arrival time
                if arrival_time > trip_times[trip_id]['end']:
                    trip_times[trip_id]['end'] = arrival_time
                
                count += 1
                if count % 500000 == 0:
                    print(f"Traité {count} lignes...")

    except FileNotFoundError:
        print(f"Erreur: {os.path.join(GTFS_DIR, 'stop_times.txt')} introuvable.")
        return

    # Écrire le fichier JSON
    print(f"Génération du fichier {OUTPUT_FILE}...")
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        # Separators permet de minifier le JSON
        json.dump(trip_times, f, separators=(',', ':'))

    print("Terminé !")

if __name__ == "__main__":
    generate_trip_times()
