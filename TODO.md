# MassAI — TODO après v0.7.0

## Réalisé

- capture CameraX ;
- import vidéo ;
- mode Humain et mode Objet / humanoïde ;
- angles téléphone ;
- guide hélicoïdal 3 niveaux bas / milieu / haut ;
- couverture sectorielle 72 bins par niveau ;
- fermeture visuelle seulement après couverture suffisante ;
- exclusion des frames de transition verticale ;
- métadonnées de niveau version 2 ;
- sélection multi-niveaux par angle circulaire ;
- 72 candidates / 24 vues sur scans multi-niveaux ;
- visual hull voxelisé ;
- nettoyage conservateur des petits îlots ;
- réparation voxel ;
- affichage brut / réparé / corrections ;
- volume et densité.

## Prochain jalon prioritaire — v0.8

- Ajouter une vue de diagnostic des masques 2D.
- Ajouter le squelette humain 33 points sur le mode Humain.
- Utiliser le squelette comme contrôle anatomique.
- Ajouter l'affichage 3D Surface / Squelette / Régions / Corrections.
- Découper le corps en régions : tête, tronc, bassin, bras, avant-bras, cuisses, jambes, pieds.

## Acquisition / géométrie

- Intégrer une pose caméra 6 DoF lorsque disponible.
- Exploiter les intrinsics caméra et une vraie projection perspective.
- Transformer les trois niveaux visuels en hauteurs caméra métriques.
- Contrôler la distance caméra-sujet.
- Détecter le mouvement du sujet.
- Ajouter plan du sol et repérage tête-sol.
- Fermer indépendamment les deux semelles.
- Ajouter un vrai maillage triangulé.
- Quantifier les corrections par région.
- Ajouter un score de confiance spatial.
- Exporter GLB + JSON/CSV.

## Validation

- Tester le guide 1 niveau vs 3 niveaux sur la même figurine.
- Comparer le nombre d'artefacts, la répétabilité du volume et les détails des membres.
- Tester plusieurs fonds / contrastes.
- Tester des objets de volume connu.
- Répéter chaque scan pour mesurer biais et variance.
- Valider ensuite sur humain avec une référence adaptée.
- Rechercher dataset 3D + DXA avant toute estimation graisse / muscle.

## Android / distribution

- Remplacer la signature debug par une vraie clé de publication.
- Ajouter tests instrumentés caméra / capteurs / permissions.
- Ajouter retours vocaux et sonores pour changement de niveau.
- Tester plusieurs appareils Android.
