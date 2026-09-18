# MassAI

POC Android de reconstruction corporelle locale à partir d'un smartphone.

## État actuel — v0.4.0

La v0.4 conserve la chaîne complète de la v0.3 et renforce l'entrée géométrique :

1. capture vidéo CameraX ou import vidéo ;
2. pendant un scan filmé dans MassAI, enregistrement de l'orientation réelle du téléphone via le capteur de rotation ;
3. suivi en direct de la couverture angulaire pendant le tour autour du sujet ;
4. extraction de 48 images candidates ;
5. score de netteté et détection simple de redondance ;
6. sélection d'environ 20 vues réparties par angle mesuré ;
7. segmentation corporelle locale avec ML Kit Selfie Segmentation ;
8. reconstruction 3D par visual hull voxelisé ;
9. réparation morphologique légère du volume ;
10. visualisation 3D interactive ;
11. mise à l'échelle par la taille renseignée ;
12. volume, densité et score de qualité technique.

Pour une vidéo importée ne possédant pas les métadonnées MassAI, la reconstruction conserve un mode de secours avec angles uniformément estimés.

## Contrôle de couverture

Lors d'un scan filmé depuis MassAI, l'écran caméra affiche la couverture mesurée en degrés.

Une reconstruction avec angles mesurés est refusée sous environ 300° de couverture afin d'éviter de fabriquer un volume artificiellement fermé à partir d'un tour incomplet.

## Important — limites actuelles

La reconstruction reste un **visual hull POC**, pas encore une photogrammétrie SfM/MVS métrique validée.

Améliorations apportées par la v0.4 :
- angles issus du téléphone au lieu d'une hypothèse uniforme pour les scans natifs ;
- sélection des vues par angle ;
- préférence pour les images les plus nettes ;
- élimination de vues extrêmement similaires ;
- couverture 360° intégrée au contrôle qualité.

Limites restantes :
- l'orientation du téléphone fournit une direction relative, pas encore une pose caméra 6 DoF ;
- la projection est encore approximée comme orthographique ;
- la distance caméra-sujet n'est pas encore reconstruite ;
- le plan du sol et la perspective caméra restent à intégrer ;
- le volume doit encore être validé expérimentalement.

## Build

- Android natif Kotlin
- package : fr.massai.app
- minSdk 26
- targetSdk 35
- compileSdk 35
- CameraX
- capteur Android TYPE_ROTATION_VECTOR
- ML Kit Selfie Segmentation

La release POC v0.4.0 produit :
- `MassAI-v0.4.0-release.apk`
- `MassAI-v0.4.0-release.aab`

La signature release utilise temporairement la clé debug Android pour permettre les essais du POC. Elle devra être remplacée avant toute publication Play Store.

Voir `TODO.md` pour la suite.
