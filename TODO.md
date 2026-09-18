# MassAI — TODO après v0.4.0

La v0.4.0 fournit :
vidéo -> angles téléphone -> sélection netteté/angles -> silhouettes -> visual hull -> réparation -> modèle 3D -> volume -> densité.

## Priorité haute

- Ajouter la position caméra 6 DoF avec ARCore lorsque disponible.
- Exploiter les intrinsics caméra / focale pour remplacer la projection orthographique par une vraie projection perspective.
- Ajouter un contrôle de distance caméra-sujet et de corps entier visible pendant la capture.
- Ajouter une détection de mouvement du sujet indépendamment du mouvement du téléphone.
- Ajouter le plan du sol et le repérage tête-sol.
- Valider la répétabilité sur plusieurs scans consécutifs d'une même personne.
- Exporter session + modèle + métadonnées en GLB/JSON/CSV.

## Qualité image / acquisition

- Améliorer le score de flou avec une calibration par résolution et appareil.
- Ajouter une vraie mesure de parallaxe/redondance visuelle.
- Ajouter une aide vocale : trop vite, trop lent, revenez en arrière, tour complet atteint.
- Bloquer les zones angulaires insuffisamment échantillonnées.
- Ajouter une vérification des bras, jambes et pieds séparés.

## Géométrie

- Ajouter une surface triangulée via marching cubes ou équivalent.
- Intégrer les poses 6 DoF aux projections de silhouettes.
- Conserver reconstruction brute et corrigée.
- Quantifier les corrections par région corporelle.
- Ajouter un score de confiance spatial.
- Vérifier la sensibilité du volume à la résolution voxel.
- Traiter explicitement le sol et fermer séparément les deux semelles.

## Validation scientifique

- Tester d'abord sur objets de volume connu.
- Répéter chaque objet plusieurs fois pour estimer biais et variance.
- Tester ensuite sur humain avec une méthode de volume de référence si disponible.
- Ne pas inférer graisse/muscle/os avant validation de la répétabilité géométrique.
- Rechercher un dataset scientifique 3D + DXA pour la future partie composition corporelle.

## Android / distribution

- Remplacer la signature debug du POC par une vraie clé de publication.
- Ajouter tests instrumentés caméra/capteurs/permissions.
- Ajouter synthèse vocale et retours sonores.
- Tester plusieurs marques, focales, stabilisations et résolutions Android.
