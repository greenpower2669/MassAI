# MassAI — TODO après v0.6.0

## Réalisé

- capture CameraX ;
- import vidéo ;
- angles téléphone et couverture ;
- sélection netteté / redondance ;
- mode Humain ;
- mode Objet / humanoïde ;
- visual hull voxelisé ;
- nettoyage conservateur des petits îlots ;
- réparation voxel ;
- affichage brut / réparé / corrections ;
- volume et densité ;
- diagnostics de composantes / artefacts.

## Prochain jalon prioritaire — v0.7

- Ajouter une vue de diagnostic des masques 2D.
- Ajouter le squelette humain 33 points sur le mode Humain.
- Utiliser le squelette comme contrôle anatomique.
- Ajouter l'affichage 3D Surface / Squelette / Régions / Corrections.
- Découper le corps en régions : tête, tronc, bassin, bras, avant-bras, cuisses, jambes, pieds.
- Ne pas produire de graisse/muscle avant calibration et validation.

## Géométrie

- Ajouter un vrai maillage triangulé.
- Intégrer ARCore / pose caméra 6 DoF lorsque disponible.
- Exploiter les intrinsics caméra et la perspective.
- Contrôler distance caméra-sujet.
- Ajouter plan du sol et repérage tête-sol.
- Fermer indépendamment les deux semelles.
- Quantifier les corrections par région.
- Ajouter un score de confiance spatial.
- Exporter GLB + JSON/CSV.

## Validation

- Tester sur objets de volume connu.
- Répéter chaque scan pour mesurer biais et variance.
- Comparer Humain vs Objet sur la même figurine.
- Tester plusieurs fonds / contrastes.
- Valider ensuite sur humain avec une référence adaptée.
- Rechercher dataset 3D + DXA pour la future composition corporelle.

## Android / distribution

- Remplacer la signature debug par une vraie clé de publication.
- Ajouter tests instrumentés caméra / capteurs / permissions.
- Ajouter retours vocaux et sonores.
- Tester plusieurs appareils Android.
