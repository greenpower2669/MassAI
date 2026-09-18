# MassAI — TODO après v0.8.0

## Réalisé

- capture CameraX ;
- import vidéo ;
- mode Humain et Objet / humanoïde ;
- choix Simple / Standard / Précis / Manuel ;
- 1 à 5 passages ;
- guide hélicoïdal dynamique ;
- exclusion des transitions verticales ;
- caméra Auto / normale / ultra grand-angle si exposée ;
- fallback caméra arrière normale ;
- enregistrement id caméra + focale ;
- visual hull ;
- nettoyage des îlots ;
- réparation voxel ;
- mesh triangulé réel ;
- rendu mesh rempli et filaire ;
- sauvegarde/rechargement .massai ;
- export OBJ ;
- volume et densité.

## Prochain jalon géométrique

- Ajouter pose caméra 6 DoF.
- Exploiter réellement la hauteur des différents passages.
- Lire et sauvegarder les intrinsics complets.
- Corriger la distorsion de l'ultra grand-angle avant projection.
- Remplacer/compléter le mesh voxel par marching cubes ou équivalent lissé.
- Ajouter un vrai z-buffer/OpenGL si le mesh devient trop dense.
- Ajouter plan du sol et fermeture dédiée des pieds.

## Anatomie

- Ajouter diagnostic des masques 2D.
- Ajouter squelette humain 33 points.
- Contrôle anatomique bras/jambes/tête/pieds.
- Découpage en régions.
- Volumes régionaux.
- Pas de graisse/muscle chiffré avant calibration et validation.

## Sauvegarde

- Étendre .massai à une session complète optionnelle avec vidéo ou frames retenues.
- Export GLB en plus de OBJ.
- Versionner les futurs formats de modèle.
- Ajouter nom/date/commentaire au modèle.

## Validation

- Comparer 1 / 2 / 3 passages sur le même sujet.
- Comparer caméra normale et ultra grand-angle sur le même objet.
- Vérifier biais de volume induit par la distorsion grand-angle.
- Tester objets de volume connu.
- Mesurer répétabilité scan-sur-scan.
- Tester plusieurs appareils Android.
- Rechercher dataset 3D + DXA avant composition corporelle.

## Android / distribution

- Remplacer la signature debug par une vraie clé de publication.
- Ajouter tests instrumentés caméra/capteurs/permissions.
- Ajouter retours vocaux et sonores.
