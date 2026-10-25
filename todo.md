# MassAI — état vivant v1.2.0-beta1

## Fait dans le commit de stabilisation
- [x] Inspection branche `massai-v1.1-options` et lecture de FAB Copilot.
- [x] Création branche v1.2 indépendante ; correction grille et options support/Turbo.
- [x] Vote « hors champ = inconnu » + minimum observé ; sélection nette par niveau ; cache JPEG borné ; guide de cadrage plus explicite.
- [x] Tests unitaires ajoutés (vote + conservation vues basses), documentation des quatre mémoires synchronisée avec le code.

## Vérification
- [ ] CI Gradle `testDebugUnitTest :app:assembleRelease :app:bundleRelease` : **en attente de résultat vérifié** ; ne pas présenter comme passée.
- [ ] Vérifier nom/version des APK et AAB, intégrité et icône ; artefacts Actions temporaires ≠ GitHub Release.
- [ ] Tester téléphone avec 1/2/3 passages, pieds visibles sur chaque boucle, mode classique/Turbo, 25/40/60 vues, deux grilles, seuil 86/75 ; comparer v1.1 et v1.2 sur **la même** vidéo.
- [ ] Vérifier consommation mémoire/temps pour 60×5 ; mesurer visuellement les masques et formes des pieds et jambes ; corriger les erreurs restantes selon preuves.
- [ ] Calibrer 6 DoF, intrinsics, hauteur par passage et distorsion grand-angle ; valider volumes sur étalons et répétabilité avant usage scientifique/médical.

## Publication
- [ ] Pas de release tant que build et tests téléphone ne sont pas vérifiés ; ensuite APK/AAB versionnés dans GitHub Releases et liens directs, sans ZIP substitut.
- Branche d'origine v1.1 et `main` conservées ; suivi historique dans `TODO.md`.

## MASSAI-FIX-20261025 — nouvelle branche expérimentale
### Changements de code faits (validation CI à vérifier)
- [x] Rendu des faces voxel par paires de triangles, test dédié.
- [x] Complément des quotas de vues par niveau si secteurs vides, test dédié.
- [x] Mesures angulaires non inventées dans le score et l'affichage pour les vidéos importées.
- [x] Décodage vidéo directement réduit sur Android 8.1+ ; repli préservé.
- [x] Version 1.2.1-preview1 ; workflow de test et préversion APK/AAB nommés.
### Non encore fait : prochains tests et correction de fond
- [ ] CI GitHub verte, APK/AAB intègres, liens directs et signature vérifiés ; ne pas déclarer de release prématurément.
- [ ] Comparer la même vidéo sous 1.1, 1.2 et préversion (y compris silhouette 2D, rendu et export OBJ).
- [ ] Instrumentation par masque/angle/étage, normalisation sol et hauteur ; discriminer erreurs de segmentation et erreurs de projection.
- [ ] Géométrie caméra 6 DoF, intrinsics, distorsion et calibration métrique avec objets étalons.
- [ ] Mesurer CPU/RAM 25/40/60 vues par niveau × 1–5, caméra normale/ultra, compatibilité Samsung Android 16.
- [ ] Étudier l'érosion des membres par consensus voxel et nettoyage A/B ; apporter preuve sur vrai scan.
- [ ] Ne pas merger main, ni présenter volumes/densité comme validés scientifiquement.
