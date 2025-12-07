# Todo List - Améliorations Futures (V3+)

Ce document recense les pistes d'amélioration identifiées à la suite du développement de la version V2.

---

## 1. Fonctionnalités Utilisateur & UX

- [ ] **Géocodage (Recherche d'Adresse)**
    - *Objectif* : Permettre de rechercher une adresse précise (ex: "123 rue Sherbrooke") dans la barre de recherche, pas seulement des lignes de bus.
    - *Moyen* : Intégrer l'API MapTiler Geocoding.

- [ ] **Filtrage des Arrêts par Direction**
    - *État Actuel* : Le bouton "Afficher les arrêts" affiche *tous* les arrêts de la ligne (Aller + Retour).
    - *Amélioration* : Ajouter un sélecteur (Chip ou Toggle) dans l'interface pour n'afficher que les arrêts de la direction "Nord" ou "Sud", en se basant sur les données `stm_parcours.json`.

- [ ] **Horaires Temps Réel dans la Bulle**
    - *Objectif* : Afficher le temps d'attente (ex: "Prochain passage : < 1 min") directement dans la bulle flottante du bus sélectionné.
    - *Moyen* : Croiser la position du bus avec les `TripUpdates` (si disponibles) ou l'horaire statique.

- [ ] **Animations de Transition**
    - Fluidifier l'apparition de la bulle et les transitions de caméra lors du changement de mode (Focus <-> Normal).

- [ ] **Mode Nuit Automatique**
    - Synchroniser le style de la carte (Dark/Light) avec le thème système Android. Actuellement manuel via un bouton.

## 2. Backend & Données

- [ ] **Injection Serveur des Headsigns**
    - *État Actuel* : L'app embarque un gros fichier `stm_trips.json` (~5-10 Mo) pour mapper les IDs de voyage aux noms de destination.
    - *Amélioration* : Modifier le script PHP/Python sur le serveur (`webllington.org`) pour qu'il injecte directement le champ `headsign` dans le flux Protobuf `stm_bus.pb`. Cela allégerait considérablement l'application.

- [ ] **Précision des Terminus**
    - *État Actuel* : Utilise le `headsign` fourni par la STM (parfois générique comme "Est" ou "Ouest").
    - *Amélioration* : Modifier `stm_convert.py` pour déduire le nom exact du dernier arrêt physique du trajet et l'utiliser comme destination.

## 3. Performance & Architecture

- [ ] **Base de Données Locale (Room)**
    - *Objectif* : Remplacer le chargement des gros JSON en mémoire par une base de données SQLite locale optimisée.
    - *Gain* : Démarrage plus rapide, moins de consommation RAM.

- [ ] **Compression des Données**
    - *Objectif* : Réduire la taille de l'APK en compressant les assets JSON (GZIP) ou en utilisant un format binaire (FlatBuffers).