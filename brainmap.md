# MassAI — cartographie technique vivante

## 1. Entrée et capture
- `MainActivity.kt` : choix humain/objet, hauteur/poids, profil 1–5 passages, caméra, Turbo, 25/40/60 vues/niveau, support 86/75 %, grille 48×96×48 ou 72×144×72 ; lance ScanActivity ou importe vidéo, extrait JPEG candidates, reconstruit et affiche métriques.
- `ScanActivity.kt` : CameraX Preview + Recorder FHD/HD/SD selon support matériel ; sélection caméra arrière/ultra si exposée (fallback normale), capture MP4 ; orientation TYPE_ROTATION_VECTOR horodatée, niveau courant et transition -1 ; export vidéo + métadonnées JSON.
- `SpiralGuideView.kt` : guide visuel d'angle/niveau et progression ; le guide ne mesure pas la position spatiale du téléphone.
- `CaptureOptions.kt` : profils et préférences caméra.
- `ScanMetadata.kt` : échantillons yaw/level, interpolation, couverture et caméra/focale ; **yaw téléphone ≠ pose orbitale caméra 6 DoF** : limitation géométrique persistante.

## 2. Images et choix des vues
- `MainActivity.extractCandidates` : échantillonnage temporel via MediaMetadataRetriever ; extraction initiale en résolution native potentiellement coûteuse, puis JPEG cache <= 960 px de grand côté ; efface les vieux fichiers candidats. Si exception, libère le bitmap.
- `FrameSelector.kt` : score de flou Laplacien, signature 8×8 ; si capteur disponible, équilibrage par niveau puis secteur angulaire ; sinon tranches temporelles avec angles estimés, pas des angles mesurés. Filtrage du flou et signatures par niveau ; risque restant : cases angulaires vides, aliasing temporel et yaw ≠ trajectoire.

## 3. Segmentation et volume
- `BodyReconstructor.kt` : décode candidates à <= 480 px humain / 640 px objet, ML Kit SelfieSegmenter/SubjectSegmenter, seuils 0.60/0.50, recadre chaque silhouette et la met à l'échelle avec hauteur saisie ; mesure maxSpan pour grille et projette les voxels dans les vues sous approximation orthographique. Attention : pas de 6 DoF, perspective/intrinsics ni correction distorsion ultra grand-angle. La segmentation objet peut télécharger un modèle externe ; le mode humain ne prouve pas une indépendance totale à l'initialisation.
- `VisualHullVoting.kt` : seuls les pixels projetés dans l'image votent ; min vues observantes max(4, ceil(N×0.20 classique / N×0.16 Turbo)), puis ceil(observé×ratio). Support 86/75 % indépendant du Turbo. Cela réduit les amputations « hors champ = négatif » mais ne reconstruit pas une partie absente de toutes les vues.
- `VoxelPostProcessor.kt` : composantes 6-connexes, garde les îlots volumineux, retire confettis. `BodyReconstructor.repair` : deux passes de voisinage 6, ajout si >=5 / suppression si <=1 ; risque d'effacer encore des extrémités fines -> tests réels requis.
- `VoxelMeshBuilder.kt` : faces externes voxel en triangles, pas de marching cubes ; détail élève la grille de 48×96×48 à 72×144×72 ; mémoire et CPU augmentent fortement. `BodyMath.kt` : volume et densité selon taille et poids saisis.

## 4. Affichage et fichiers
- `BodyModelView.kt` : modes brut, réparé, mesh rempli, filaire, corrections (rendu à vérifier sur appareil à grande maille).
- `ModelArchive.kt` : sauvegarde/rechargement .massai, export OBJ ; absence de vidéo complète dans l'archive héritée. Ne pas casser les anciens fichiers.
- `app/build.gradle.kts` : versionCode 12 / versionName 1.2.0-beta1, icône existante sous res/drawable et res/mipmap.
- `.github/workflows/validate-v1.2.yml` : build de validation APK/AAB **artefacts Actions seulement**, pas release ; conserver la branche et workflow v1.1 inchangés.

## 5. Tests et dépendances
- `VisualHullVotingTest.kt` : manque de vues ≠ négatif, min observations, seuils 75/86.
- `FrameSelectorTest.kt` : conserve les vues du niveau bas malgré flou/exposition et signature inter-niveau.
- Tests hérités : `BodyMathTest`, `ModelArchiveTest`, `OrientationMathTest`, `ScanGuideMathTest`, `VoxelMeshBuilderTest`, `VoxelPostProcessorTest`.
- AndroidX CameraX, Camera2 interop, ML Kit segmentation, Android SDK 35, Java 17 / Gradle 8.9 ; tests sensoriels et photogrammétriques sur téléphone non automatisés.

## 6. Chaîne des causes
Vue manquante/bas hors champ → en v1.1 support négatif « total vues » → voxels pieds/jambes éliminés → nettoyage/réparation peuvent amplifier → mesh tronqué. v1.2 compte uniquement les observations in-frame et exige un minimum. Autre chaîne distincte : yaw téléphone pris pour angle de trajectoire + perspective/distorsion non calibrées → projection fausse → volume et membre déformés malgré vote corrigé. **Ne pas confondre hypothèse et cause prouvée.**
