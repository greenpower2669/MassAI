# MassAI — contrat fonctionnel vivant

## Objet
POC Android Kotlin smartphone-only : filmer un sujet immobile en tournant autour, sélectionner des vues par passage/angle, segmenter, construire un volume voxel et un mesh triangulé, afficher et sauvegarder .massai / exporter OBJ. L'échelle repose actuellement sur la hauteur **saisie**, non sur une calibration 6 DoF. Le poids saisi permet une densité indicative, non validée médicalement.

## Demande de Fab pour la v1.2 expérimentale
Stabiliser capture et résolution 3D sans repartir de zéro, notamment réduire le risque de jambes/pieds manquants. Conserver caméra normale/ultra grand-angle si exposée, 1–5 passages, 25/40/60 vues par niveau lorsque Turbo actif, Turbo désactivable, grille standard/détaillée et seuil strict/tolérant, fichiers OBJ/.massai et icône Android.

## Décisions de cette intervention
- La grille détaillée doit être honorée indépendamment du Turbo.
- Le seuil de support doit être honoré en mode classique et Turbo ; hors champ = observation inconnue, pas vote négatif ; nombre minimal de vues observantes obligatoire.
- La qualité des vues et la suppression des doublons sont évaluées **par passage** afin de ne pas sacrifier le bas du sujet aux vues supérieures plus nettes.
- Les JPEG de travail sont limités à 960 px de grand côté, pas la vidéo enregistrée. La segmentation travaille déjà à 480 px humain / 640 px objet en entrée maximum.
- Tout modèle avec membres manquants reste un défaut à vérifier sur capture réelle : aucune « anatomie réparée » fictive n'est annoncée.

## Distribution
v1.2.0-beta1 = branche de validation, **pas** une release et pas une APK validée. Ne pas modifier la v1.1 ni main. Une éventuelle publication nécessite tests Gradle, vérification des sorties, essais téléphone, APK/AAB nommés et icône.
