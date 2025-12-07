# Changelog et Passation Technique - MyMapApp (V2)

**Date :** 04 Décembre 2025  
**Auteur :** Gemini CLI Agent

---

## 🚀 Nouvelles Fonctionnalités Implémentées

### 1. Affichage & Carte
*   **Clustering des Bus** : Intégration du clustering natif MapLibre pour regrouper les bus proches.
    *   Code couleur : Nuances de gris progressives selon la densité (Gris clair -> Noir).
    *   *Ajustement Final* : Rayon de clustering augmenté à 42px pour plus de clarté.
*   **Orientation des Bus** : Ajout d'une flèche orbitale autour de chaque bus indiquant son cap (bearing).
    *   La flèche hérite dynamiquement de la couleur de la ligne (SDF Icon).
*   **Tracés Complets (MultiLineString)** : Support des tracés multiples (Aller, Retour, Variantes) pour chaque ligne, au lieu d'un seul tracé simplifié.
    *   Opacité ajustée à 40%.
*   **TextureView** : Migration de `SurfaceView` vers `TextureView` (`maplibre_renderTextureMode`) pour résoudre les conflits de Z-index avec les overlays UI.
*   **Suppression de la Boussole** : Désactivation de l'icône boussole par défaut.

### 2. Interface Utilisateur (UI/UX)
*   **Barre de Recherche Flottante (Bottom Search)** : Remplacement du bouton filtre par une barre de recherche moderne (FAB "Loupe" -> Dialogue Modal).
    *   Dialogue `SearchDialogFragment` avec liste filtrable en temps réel.
*   **Info-Bulle Flottante (InfoBubble)** : Remplacement du Bottom Sheet pour les détails de bus par une bulle flottante ("Comic bubble") ancrée sur le bus.
    *   Suit les mouvements de la caméra et du bus (Follow Mode).
    *   Contient le nom de la ligne, la destination ("Vers ..."), le numéro de bus ("Bus n°...") et un bouton d'action.
*   **Bottom Sheet Adaptatif** : La hauteur du panneau s'adapte au contenu (wrap_content) avec une limite max de 66%.
*   **Réorganisation des FABs** : Alignement horizontal en bas à droite, animation synchronisée avec le Bottom Sheet.
*   **Bouton "Arrêts"** : Déplacé dans la barre de boutons flottants, visible uniquement lorsqu'une ligne est active. Change d'état visuel (couleur).

### 3. Navigation & Logique
*   **Follow Mode (Suivi Automatique)** : Lorsqu'un bus est sélectionné, la caméra le suit automatiquement à chaque mise à jour de position (toutes les 10s), tant que l'utilisateur ne touche pas la carte.
*   **Focus Ligne** : Zoom automatique et centrage intelligent (avec padding pour éviter les boutons) lors de la sélection d'une ligne.
    *   Le clustering est automatiquement désactivé en mode Focus Ligne pour voir tous les bus.
*   **Filtrage des Arrêts** : Bouton "Afficher/Masquer les arrêts" contextuel à la ligne sélectionnée.
    *   Utilise une nouvelle structure de données pour filtrer précisément les arrêts de la ligne.
*   **Reset d'État** : Cliquer sur la carte vide réinitialise proprement toute l'interface (filtres, bulles, tracés, suivi).

### 4. Données (Backend/Scripting)
*   **Amélioration GTFS** : Mise à jour du script `RSC/stm_convert.py` pour :
    *   Extraire la liste des `stop_ids` pour chaque `route_id`.
    *   Extraire **tous** les tracés (MultiLineString) au lieu d'un seul.
    *   Générer `stm_trips.json` pour mapper `trip_id` -> `trip_headsign`.
*   **Parsing Robuste** : Mise à jour de `StmRepository` et `Models` pour supporter la nouvelle structure JSON (`RouteParcoursData`).
*   **Polling Fréquent** : Fréquence de mise à jour des bus augmentée à 10 secondes.

---

## 🛠️ Défis Techniques Rencontrés

1.  **Clipping & Z-Index (InfoBubble)** :
    *   *Problème* : La bulle d'info passait "derrière" la carte ou disparaissait en haut de l'écran.
    *   *Cause* : La `MapView` par défaut utilise une `SurfaceView` qui a son propre contexte de rendu, ignorant souvent l'ordre des vues Android standards.
    *   *Solution* : Activation du mode `TextureView` via `app:maplibre_renderTextureMode="true"`, permettant un compositing correct avec les vues natives.

2.  **Données Manquantes (Arrêts par Ligne)** :
    *   *Problème* : Impossible de filtrer les arrêts d'une ligne car les données statiques ne faisaient pas le lien direct.
    *   *Solution* : Modification du script de pré-traitement Python pour générer ce lien en amont, évitant des calculs spatiaux lourds et imprécis sur le mobile.

3.  **Compilation & Syntaxe** :
    *   Plusieurs itérations ont été nécessaires pour stabiliser `MainActivity.kt` après des refactorings massifs (erreurs de binding, accolades manquantes, imports). Le fichier a été entièrement réécrit pour garantir sa cohérence.

4.  **Interaction Carte (Click)** :
    *   *Problème* : Impossible de cliquer sur un bus une fois le clustering désactivé (mode ligne).
    *   *Cause* : `queryRenderedFeatures` n'interrogeait que les layers clusterisés (masqués) et ignorait les nouveaux layers "plats".
    *   *Solution* : Ajout des layers "flat" dans la liste des cibles du clic.

---

## 🤝 Message de Passation (Pour les futurs développeurs)

Bonjour ! 👋

Cette base de code a été modernisée pour être plus robuste et agréable. Voici quelques points clés pour votre prise en main :

*   **MapLibre est le cœur** : Presque tout passe par `MapManager.kt`. Si vous devez changer le style des bus ou des arrêts, c'est là que ça se passe (Layers & Sources).
*   **Données Statiques** : Les fichiers JSON dans `assets/` (`stm_parcours.json`, `stm_stops.json`, `stm_trips.json`) ne sont pas magiques. Ils sont générés par le script Python dans `RSC/`.
    *   **IMPORTANT** : Si vous changez la structure des données, **n'oubliez pas de mettre à jour `StmRepository.kt` ET le script Python**. Les deux sont couplés.
*   **Architecture** : L'app suit une architecture MVVM simple.
    *   `MainViewModel` : Chef d'orchestre des données.
    *   `MainActivity` : Chef d'orchestre de l'UI et de la Carte.
    *   `MapManager` : Abstraction de la librairie de carte.
*   **InfoBubble** : C'est une simple `View` Android (`CardView`) qui est déplacée manuellement (`translationX/Y`) via `map.projection.toScreenLocation()`. C'est simple et performant. Ne cherchez pas à utiliser les "MarkerViews" dépréciés de Mapbox/MapLibre sauf nécessité absolue.

**Pistes d'amélioration future :**
*   **Géocodage** : Ajouter la recherche d'adresse (MapTiler API).
*   **Horaires Temps Réel** : Afficher le "prochain passage" directement dans l'InfoBubble du bus.

Bon code ! 🚀