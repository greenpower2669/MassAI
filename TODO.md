# MassAI — TODO après v0.3.0

La v0.3.0 fournit la première chaîne verticale complète :
vidéo -> silhouettes -> visual hull voxel -> réparation -> modèle 3D -> volume -> densité.

## Priorité haute

- Remplacer l'hypothèse d'angles uniformes par des poses caméra réelles :
  capteurs du téléphone et ARCore lorsqu'il est disponible.
- Mesurer automatiquement le flou et la redondance avant segmentation.
- Détecter une couverture 360° incomplète et demander de recommencer.
- Ajouter une vraie calibration/perspective caméra au lieu de l'approximation orthographique.
- Séparer explicitement les composantes des pieds/bras et contrôler les occultations.
- Ajouter un contrôle du plan du sol et de la hauteur tête-sol.
- Mesurer la répétabilité scan-sur-scan sur une même personne.
- Exporter le modèle et les métadonnées en GLB + JSON/CSV.

## Géométrie

- Ajouter une extraction de surface triangulée (marching cubes ou équivalent).
- Conserver séparément reconstruction brute et reconstruction corrigée.
- Quantifier les corrections par zone et pas seulement globalement.
- Ajouter un vrai score de confiance spatial.
- Vérifier la sensibilité du volume à la résolution voxel.

## Validation scientifique

- Comparer le volume MassAI à une méthode de référence sur des objets de volume connu.
- Puis comparer sur humain à une mesure de référence lorsque disponible.
- Ne pas dériver graisse/muscle/os avant validation de la répétabilité géométrique.
- Rechercher un dataset 3D + DXA adapté avant toute partie IA de composition corporelle.

## Android / distribution

- Remplacer la signature debug utilisée pour le POC release par une vraie clé de publication.
- Ajouter les tests instrumentés caméra/permissions.
- Ajouter synthèse vocale et retours sonores/accessibilité renforcée.
- Tester plusieurs marques/résolutions Android.
