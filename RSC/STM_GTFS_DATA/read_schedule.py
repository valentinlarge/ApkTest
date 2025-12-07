import pandas as pd

# --- CONFIGURATION ---
TARGET_STOP_ID = '52684' # L'arrêt que nous testons
GTFS_FILE = 'stop_times.txt' # Assurez-vous que ce fichier est dans le même répertoire

def read_and_filter_schedule():
    try:
        # 1. Charger le fichier GTFS dans un DataFrame Pandas
        # Dtype = object est utilisé pour éviter les problèmes de formatage des IDs et des heures
        df_stop_times = pd.read_csv(GTFS_FILE, dtype=object)
        
        # 2. Filtrer le DataFrame pour ne garder que les entrées de l'arrêt cible
        # Note: L'ID d'arrêt est généralement un string dans le GTFS.
        schedule_filtered = df_stop_times[df_stop_times['stop_id'] == TARGET_STOP_ID]
        
        # 3. Afficher les colonnes pertinentes
        schedule_display = schedule_filtered[['trip_id', 'arrival_time', 'departure_time']]
        
        # 4. Afficher les 10 premiers horaires trouvés
        print(f"Horaires planifiés (GTFS Statique) pour l'arrêt {TARGET_STOP_ID} (10 premiers voyages) :")
        
        # Le .to_string() permet un affichage propre dans le terminal
        print(schedule_display.head(10).to_string(index=False)) 

        # Pour aller plus loin : pour lier le 'trip_id' à la ligne '361', 
        # vous devrez charger trips.txt et faire une jointure (merge) sur 'trip_id'.

    except FileNotFoundError:
        print(f"❌ ERREUR : Le fichier {GTFS_FILE} est introuvable. Avez-vous dézippé et placé le fichier dans ce dossier ?")
    except Exception as e:
        print(f"Une erreur inattendue s'est produite : {e}")

if __name__ == "__main__":
    read_and_filter_schedule()