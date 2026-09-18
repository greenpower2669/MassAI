# MassAI

POC Android de reconstruction 3D corporelle / humanoïde à partir d'un smartphone.

## État actuel — v0.8.0

La v0.8 regroupe quatre chantiers demandés après les premiers tests terrain :

- vrai **mesh triangulé** issu du volume voxel réparé ;
- sauvegarde/rechargement des modèles ;
- choix du nombre de passages ;
- choix de caméra avec tentative d'ultra grand-angle lorsque CameraX l'expose.

## Capture configurable

Avant de filmer, l'utilisateur choisit :

- Simple : 1 passage ;
- Standard : 2 passages ;
- Précis : 3 passages ;
- Manuel : 1 à 5 passages.

Le guide hélicoïdal s'adapte automatiquement au nombre choisi.

Les transitions verticales restent exclues de la sélection des vues.

## Caméra

Trois choix sont proposés :

- Auto ;
- Caméra normale ;
- Ultra grand-angle.

Pour le mode ultra grand-angle, MassAI inspecte les caméras arrière exposées par CameraX et leurs focales Camera2. Si plusieurs caméras arrière utilisables sont visibles, la plus courte focale distincte est utilisée.

Si le constructeur n'expose pas l'ultra grand-angle comme caméra CameraX sélectionnable, MassAI retombe automatiquement sur la caméra arrière normale au lieu d'échouer.

La caméra réellement utilisée, son identifiant et sa focale disponible sont enregistrés dans les métadonnées du scan.

## Mesh 3D

La v0.8 produit un véritable maillage de triangles à partir des faces externes des voxels.

Deux rendus sont disponibles :

- MESH : surface triangulée remplie ;
- FIL : maillage filaire.

Les vues BRUT, POINTS et CORRECTIONS restent disponibles.

Le mesh v0.8 est volontairement un mesh voxel surfacique : il n'est pas encore lissé par marching cubes.

## Sauvegarde et export

Après une reconstruction :

- **SAUVER .MASSAI** crée une archive compressée avec la géométrie, les deux meshes et les métriques ;
- **OUVRIR** recharge ensuite ce modèle sans devoir refaire la vidéo ;
- **EXPORT OBJ** exporte le mesh réparé vers un format 3D standard.

Le fichier .massai v0.8 sauvegarde le modèle reconstruit, pas la vidéo source complète.

## Pipeline

Capture/import
→ angles + niveaux + caméra
→ sélection qualité
→ segmentation
→ visual hull
→ nettoyage voxel
→ réparation
→ mesh triangulé
→ volume
→ sauvegarde/export.

## Limites importantes

Il manque encore notamment :

- pose caméra 6 DoF et hauteur métrique réelle ;
- intrinsics/perspective utilisés directement par la reconstruction ;
- correction explicite de distorsion ultra grand-angle ;
- mesh lissé type marching cubes ;
- squelette anatomique et régions corporelles ;
- validation scientifique du volume ;
- modèle graisse/muscle calibré.

## Build

- Android natif Kotlin
- package : fr.massai.app
- versionCode : 8
- versionName : 0.8.0
- minSdk 26
- targetSdk 35
- compileSdk 35

La release produit :

- `MassAI-v0.8.0-release.apk`
- `MassAI-v0.8.0-release.aab`

La signature release utilise encore temporairement la clé debug Android pour les essais du POC.

Voir `TODO.md`.
