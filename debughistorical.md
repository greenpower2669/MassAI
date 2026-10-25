# MassAI — historique vivant des régressions et hypothèses

## Faits observés / rapportés
- Essais POC antérieurs rapportés par Fab : jambes tronquées en cône/pointe, résultat de densité « 1 », message dépendance API/Internet, 0.9 / 1.1 parfois sans artefact Actions visible. Aucune capture réelle reproductible ni cause scientifique n'est démontrée ici.
- Code v1.1 inspecté : `voxelDetail` n'agit que si `turbo && voxelDetail`, et Turbo n'est transmis à BodyReconstructor que pour un scan multi-niveaux ; choix détail inopérant dans les autres modes. **Cause logicielle prouvée par lecture du code.**
- Code v1.1 : `requiredSupport=ceil(N×0.86)` en mode classique malgré le choix 75 %, et les vues où la projection est hors champ sont implicitement négatives. **Cause prouvée d'incohérence de réglage** et mécanisme possible d'amputation, non preuve unique du symptôme réel.
- Code v1.1 : filtre de flou global et comparaison de signatures entre passages ; peut rejeter le bas moins net. **Mécanisme prouvé par lecture du code**, conséquences exactes dépendantes des vidéos.

## Correctifs v1.2-beta1
- Grille détaillée indépendante Turbo et choix Turbo actif même à un niveau ; ratio strict/tolérant passé à toutes les reconstructions ; vote hors champ ignoré et minimum de vues réellement observantes.
- Médiane de netteté et signatures par niveau ; cache JPEG candidates borné à 960 px, libération bitmap même en cas d'exception ; decodeScaled sans marge cachée ×2.
- Message de capture tête ET pieds et distance stable. 

## Hypothèses non tranchées / vérification requise
- Segmentation qui coupe les pieds/bras ; perspective, yaw non orbital, silhouette recalée sur hauteur sans plan au sol, distorsion ultra grand-angle ; réparateur supprimant voxels fins ; bins angulaires vides ; surcharge mémoire si 60×5 vues. Examiner masques par angle et niveau avant d'affirmer une cause.
- En présence de membres absents, ne **pas** imputer la correction au seul seuil ni prétendre que la forme anatomique est exacte.
- Build, tests unitaires et essai physique non encore confirmés lors de la rédaction initiale ; reporter les résultats avérés dans todo.md au prochain cycle.

## 25 octobre 2026 — remédiation initiale après audit
- **Prouvé (lecture du code)** : stride sur les triangles individuels dans `BodyModelView` pouvait laisser une moitié de chaque quad lors de décimation. Correctif : sélectionner les deux triangles ensemble. Les données OBJ ne sont pas modifiées.
- **Prouvé** : `BodyReconstructor` annonçait une couverture 360° fictive pour les angles issus du temps des vidéos sans métadonnées. Correctif : 0° mesuré, score technique prudent ; la pose caméra demeure non calibrée.
- **Prouvé** : secteurs sans candidats dans `FrameSelector` ne récupéraient pas les images d'autres secteurs ; correctif par complément des quotas.
- **Optimisation conditionnelle** : demande de frames réduites directement à MediaMetadataRetriever sur Android 8.1+ ; bénéfice mémoire/performance à mesurer sur téléphone.
- **À confirmer sur captures réelles** : jambes/pieds manquants, erreurs des masques, recentrage, consensus voxel, distorsion, géométrie 6 DoF ; ne pas annoncer de réparation anatomique avant reproduction.
