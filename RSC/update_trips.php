<?php
// Configuration
$gtfs_url = "http://www.stm.info/sites/default/files/gtfs/gtfs_stm.zip";
$json_file = __DIR__ . '/stm_trips.json';
$zip_file = __DIR__ . '/gtfs_stm.zip';
$extract_path = __DIR__ . '/gtfs_temp/';

// 1. Télécharger le GTFS
echo "Téléchargement du GTFS...\n";
file_put_contents($zip_file, fopen($gtfs_url, 'r'));

// 2. Décompresser trips.txt
$zip = new ZipArchive;
if ($zip->open($zip_file) === TRUE) {
    if (!is_dir($extract_path)) mkdir($extract_path);
    $zip->extractTo($extract_path, ['trips.txt']);
    $zip->close();
    echo "Extraction réussie.\n";
} else {
    die("Erreur lors de l'ouverture du ZIP.\n");
}

// 3. Parser trips.txt et générer le JSON
echo "Génération du JSON...\n";
$trips_map = [];
if (($handle = fopen($extract_path . 'trips.txt', "r")) !== FALSE) {
    // Lire l'en-tête pour trouver les indices
    $header = fgetcsv($handle);
    $idx_id = array_search('trip_id', $header);
    $idx_headsign = array_search('trip_headsign', $header);

    if ($idx_id === FALSE || $idx_headsign === FALSE) {
        die("Colonnes introuvables dans trips.txt\n");
    }

    while (($data = fgetcsv($handle, 0, ',', '"', '\\')) !== FALSE) {
        $trip_id = $data[$idx_id];
        $headsign = $data[$idx_headsign];
        $trips_map[$trip_id] = $headsign;
    }
    fclose($handle);
}

// 4. Sauvegarder le JSON
file_put_contents($json_file, json_encode($trips_map));
echo "Fichier stm_trips.json mis à jour (" . count($trips_map) . " entrées).\n";

// Nettoyage
unlink($zip_file);
array_map('unlink', glob("$extract_path/*.*\n"));
rmdir($extract_path);
?>
