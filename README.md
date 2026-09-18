# MassAI

POC Android de reconstruction 3D corporelle / humanoïde à partir d'un smartphone.

## État actuel — v0.7.0

La v0.7 ajoute un **guide hélicoïdal multi-niveaux** pendant la capture native.

### Guide de scan

Le scan est découpé en trois niveaux :

1. anneau bas ;
2. anneau milieu ;
3. anneau haut.

Chaque anneau est divisé en 72 secteurs angulaires.

La ligne acquise reste ouverte pendant le tour. Lorsqu'au moins 66 secteurs sur 72 sont couverts, la boucle est validée et se ferme visuellement. MassAI affiche alors l'anneau supérieur et demande à l'utilisateur de monter le téléphone avant de continuer.

Les images acquises pendant la transition verticale sont marquées comme transition et ne sont pas utilisées pour la sélection des vues de reconstruction.

### Métadonnées v2

Chaque échantillon d'orientation contient désormais :

- temps ;
- yaw déroulé ;
- niveau de capture.

Le fichier de session enregistre également :

- nombre de niveaux demandés ;
- nombre de niveaux terminés ;
- couverture angulaire circulaire par niveau.

Les anciens fichiers restent lisibles : un échantillon sans niveau est interprété comme niveau 0.

### Sélection des vues

Pour un scan multi-niveaux :

- 72 images candidates sont extraites au lieu de 48 ;
- jusqu'à 24 vues sont retenues au lieu de 20 ;
- les transitions verticales sont ignorées ;
- les angles sont ramenés sur un tour complet ;
- la meilleure image de chaque secteur angulaire est sélectionnée, quel que soit le niveau où elle a été capturée.

Important : la v0.7 **ne mesure pas encore la hauteur réelle de la caméra**. Le guide vertical améliore l'acquisition et prépare les métadonnées, mais une exploitation géométrique métrique des différences de hauteur nécessitera une pose caméra 6 DoF / ARCore et une vraie projection perspective.

### Pipeline

Capture / import vidéo
→ angles et niveaux
→ sélection des vues
→ segmentation
→ visual hull
→ nettoyage des îlots
→ réparation voxel
→ modèle 3D
→ volume
→ densité si le poids est renseigné.

## Posture recommandée

- sujet immobile ;
- bras abaissés et légèrement écartés ;
- pieds séparés ;
- corps entier visible ;
- distance la plus régulière possible ;
- suivre successivement les trois anneaux.

## Limites

Il manque encore notamment :

- pose caméra 6 DoF et hauteur métrique ;
- intrinsics / perspective réelle ;
- squelette anatomique ;
- régions corporelles ;
- maillage triangulé ;
- plan du sol et traitement dédié des pieds ;
- validation scientifique du volume ;
- modèle graisse / muscle calibré.

## Build

- Android natif Kotlin
- package : fr.massai.app
- versionCode : 7
- versionName : 0.7.0
- minSdk 26
- targetSdk 35
- compileSdk 35

La release produit :

- `MassAI-v0.7.0-release.apk`
- `MassAI-v0.7.0-release.aab`

La signature release utilise encore temporairement la clé debug Android pour les essais du POC.

Voir `TODO.md`.
