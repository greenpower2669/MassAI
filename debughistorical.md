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
