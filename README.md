# MassAI

POC Android de reconstruction 3D corporelle / humanoïde à partir d'un smartphone.

## État actuel — v0.6.0

La v0.6 conserve les deux modes de segmentation de la v0.5 :

- **Humain** : segmentation personne.
- **Objet / humanoïde** : segmentation générique du sujet.

Elle ajoute surtout un nettoyage géométrique conservateur avant le calcul du volume.

### Pipeline actuel

1. capture CameraX ou import vidéo ;
2. angles téléphone mesurés pour les scans filmés dans MassAI ;
3. extraction de 48 images candidates ;
4. tri netteté / redondance / couverture ;
5. sélection d'environ 20 vues ;
6. segmentation selon le mode choisi ;
7. visual hull voxelisé ;
8. **suppression des petits îlots voxelisés déconnectés** ;
9. réparation morphologique légère ;
10. deuxième passe de nettoyage ;
11. visualisation 3D brut / réparé / corrections ;
12. mise à l'échelle par la hauteur ;
13. volume ;
14. densité si un poids est renseigné.

## Nettoyage v0.6

Le nettoyage ne force pas le sujet à devenir une seule composante.

C'est volontaire : un bras, une jambe ou un pied peut être temporairement séparé à cause d'une mauvaise reconstruction. MassAI conserve donc les composantes significatives et retire seulement les petits amas parasites.

L'écran de résultat affiche maintenant :
- voxels parasites supprimés ;
- nombre de composantes avant nettoyage ;
- nombre de composantes après nettoyage.

La vue **BRUT** conserve la reconstruction originale pour comparaison.
La vue **RÉPARÉ** montre la reconstruction après nettoyage/réparation.

## Posture recommandée

Pour le mode humain :
- sujet immobile ;
- bras abaissés et légèrement écartés du torse ;
- pieds séparés ;
- corps entier visible ;
- tour régulier autour du sujet.

## Limites actuelles

La reconstruction reste un **visual hull POC** et non une photogrammétrie SfM/MVS métrique validée.

Il manque encore :
- squelette anatomique ;
- régions corporelles ;
- maillage triangulé ;
- perspective caméra métrique / pose 6 DoF ;
- plan du sol et traitement dédié des deux pieds ;
- validation expérimentale du volume ;
- modèle graisse / muscle calibré.

## Build

- Android natif Kotlin
- package : fr.massai.app
- versionCode : 6
- versionName : 0.6.0
- minSdk 26
- targetSdk 35
- compileSdk 35

La release produit :
- `MassAI-v0.6.0-release.apk`
- `MassAI-v0.6.0-release.aab`

La signature release utilise encore temporairement la clé debug Android pour les essais du POC.

Voir `TODO.md`.
