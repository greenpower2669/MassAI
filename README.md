# MassAI

POC Android de reconstruction corporelle locale à partir d'un smartphone.

## État actuel — v0.5.0

La v0.5 ajoute un test A/B de segmentation sans casser le pipeline humain :

- **Mode Humain** : conserve ML Kit Selfie Segmentation, destiné au corps humain et à la future intégration du squelette anatomique.
- **Mode Objet / humanoïde** : utilise ML Kit Subject Segmentation pour extraire le sujet générique du fond, sans supposer qu'il s'agit d'un humain.

Les deux modes rejoignent ensuite le même pipeline :

1. capture CameraX ou import vidéo ;
2. angles téléphone mesurés lorsque le scan est filmé dans MassAI ;
3. extraction de 48 candidates ;
4. tri netteté / redondance / couverture ;
5. sélection d'environ 20 vues ;
6. segmentation selon le mode choisi ;
7. visual hull voxelisé ;
8. réparation légère ;
9. modèle 3D interactif ;
10. mise à l'échelle par la hauteur ;
11. volume ;
12. densité si un poids est renseigné.

## Mode Objet / humanoïde

Ce mode sert principalement à tester et valider la géométrie sur des figurines ou objets de forme connue.

- hauteur acceptée : 5 à 250 cm ;
- poids facultatif ;
- la densité n'est calculée que si le poids est renseigné ;
- le modèle Subject Segmentation est fourni dynamiquement par Google Play Services et peut nécessiter un téléchargement lors du premier usage.

Ce mode permet de comparer directement la reconstruction d'un humanoïde artificiel avec la segmentation humaine précédente.

## Limites

La reconstruction reste un **visual hull POC**, pas encore une photogrammétrie SfM/MVS métrique validée.

La v0.5 ne fournit pas encore :
- squelette anatomique ;
- régions corporelles ;
- composition graisse / muscle ;
- perspective caméra métrique ;
- pose caméra 6 DoF ;
- plan du sol et fermeture spécifique des pieds.

Ces briques restent séparées afin de ne pas présenter une estimation physiologique comme une mesure.

## Build

- Android natif Kotlin
- package : fr.massai.app
- minSdk 26
- targetSdk 35
- compileSdk 35
- CameraX
- ML Kit Selfie Segmentation
- ML Kit Subject Segmentation

La release v0.5.0 produit :
- `MassAI-v0.5.0-release.apk`
- `MassAI-v0.5.0-release.aab`

La signature release utilise encore temporairement la clé debug Android pour les essais du POC.

Voir `TODO.md`.
