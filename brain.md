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

## MassAI v1.2.1-preview1 — corrections expérimentales (25 octobre 2026)
- Ne jamais afficher de demi-faces voxel par décimation individuelle des triangles ; le sélecteur de rendu travaille sur chaque paire du constructeur voxel. Export OBJ inchangé.
- Si les angles proviennent d'une supposition temporelle, ne pas prétendre mesurer 360° : couverture affichée = 0° mesuré et score technique réduit. L'algorithme de reconstruction reste une approximation orbitale, non une pose 6 DoF calibrée.
- Sélection angulaire multi-niveau : remplir chaque quota avec les vues disponibles si certains secteurs sont vides.
- Android 8.1+ : demander des images vidéo déjà réduites au décodeur (Android 8.0 conserve l'ancien chemin).
- Aucune exactitude anatomique ou volumétrique affirmée sans tests vidéo/objets étalons.

## Correctif préversion v1.2.2-preview2 — sécurité du cadrage
- Une silhouette détectée contre une marge verticale de l'image est ignorée avant mise à l'échelle (évite de confondre pied coupé et sol). Le nombre de vues rejetées est montré à l'écran.
- Les probabilités Float32 sont converties en masques binaires Byte après seuil, ce qui divise par quatre la mémoire persistante de ces masques sans changer le vote actuel ; probabilités de segmentation non conservées ensuite.
- Le volume calculé est annoncé comme indicatif et non calibré ; nécessité de calibrer et valider sur objets physiques.
