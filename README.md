# MassAI

POC Android de reconstruction corporelle locale à partir d'un smartphone.

## État actuel — v0.3.0

La v0.3.0 introduit la première chaîne verticale complète réellement calculée :

1. capture vidéo CameraX ou import vidéo ;
2. extraction de vues réparties sur la vidéo ;
3. segmentation corporelle locale avec ML Kit Selfie Segmentation ;
4. reconstruction 3D par visual hull voxelisé à partir des silhouettes multi-vues ;
5. petite réparation morphologique du volume voxel ;
6. visualisation 3D interactive dans l'application ;
7. mise à l'échelle par la taille renseignée ;
8. calcul du volume ;
9. calcul de la masse volumique à partir du poids utilisateur ;
10. indice de qualité technique basé sur le nombre de vues valides et l'importance des corrections.

L'écran permet d'afficher :
- reconstruction brute ;
- reconstruction réparée ;
- voxels corrigés.

## Important — limites de la v0.3

La reconstruction v0.3 est un **visual hull POC**, pas encore une photogrammétrie SfM/MVS métrique validée.

Hypothèses actuelles :
- la vidéo couvre approximativement un tour complet de 360° ;
- les vues extraites sont supposées réparties régulièrement autour du sujet ;
- la projection est approximée comme orthographique ;
- la taille réelle sert à convertir les silhouettes en dimensions physiques.

Ces hypothèses devront être remplacées progressivement par :
- poses caméra issues des capteurs / ARCore ;
- contrôle de couverture angulaire ;
- calibration perspective ;
- sélection flou/redondance ;
- validation expérimentale de la répétabilité et du volume.

## Build

- Android natif Kotlin
- package : fr.massai.app
- minSdk 26
- targetSdk 35
- compileSdk 35
- CameraX
- ML Kit Selfie Segmentation

La release POC v0.3.0 produit :
- `MassAI-v0.3.0-release.apk`
- `MassAI-v0.3.0-release.aab`

La signature release est temporairement basée sur la clé debug Android afin de permettre les tests du POC. Elle devra être remplacée par une vraie signature de publication avant diffusion Play Store.

Voir `TODO.md` pour la suite.
