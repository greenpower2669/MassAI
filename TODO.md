# MassAI — v0.9 en cours (branche de développement)

## Implémenté sur cette branche
- Sélection des vues multi-passages répartie d'abord par niveau, puis par angle, pour éviter qu'un niveau plus net évince tous les autres.
- Base v0.8.0 conservée : mesh triangulé rempli/filaire, OBJ, archives .massai et densité inchangés.

## À faire AVANT release v0.9
- Ajouter tests de sélection multi-niveaux et exécuter tests Android/Gradle ; aucune validation de build n'est encore acquise.
- Corriger le vote voxel : distinguer hors champ, masque incertain et rejet observé ; conserver une comparaison A/B avec v0.8.
- Diagnostiquer la disparition des jambes et pieds sur captures réelles ; ne pas promettre une réparation anatomique non observée.
- Introduire calibration caméra et géométrie 6 DoF vérifiable, sans inventer de distance/hauteur.
- Valider les volumes sur objets étalons et la répétabilité ; conserver les exports OBJ et .massai.
- Ne publier APK/AAB qu'après build et tests vérifiés.

---

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
