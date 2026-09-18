# MassAI — TODO après v0.5.0

La v0.5 permet désormais de comparer :
- Humain : segmentation personne ;
- Objet / humanoïde : segmentation générique du sujet.

## Prochain jalon prioritaire

- Ajouter le squelette humain 33 points sur le mode Humain.
- Utiliser le squelette comme contrôle anatomique et pour découper le volume :
  tête / tronc / bassin / bras / avant-bras / cuisses / jambes / pieds.
- Ajouter l'affichage 3D Surface / Squelette / Régions / Corrections.
- Mesurer les volumes régionaux et sections principales.
- Ne pas calculer graisse/muscle avant validation et calibration sur données de référence.

## Acquisition / géométrie

- Ajouter ARCore / pose caméra 6 DoF lorsque disponible.
- Exploiter les intrinsics et la focale pour la perspective réelle.
- Mesurer la distance caméra-sujet.
- Détecter le mouvement du sujet.
- Contrôler corps entier visible, bras/jambes séparés et couverture angulaire.
- Ajouter plan du sol, tête-sol et fermeture indépendante des deux pieds.
- Trianguler la surface via marching cubes ou équivalent.
- Exporter GLB + JSON/CSV.

## Mode Objet / validation

- Comparer Humain vs Objet sur la même figurine.
- Tester plusieurs fonds et niveaux de contraste.
- Tester des objets de volume connu.
- Répéter chaque scan pour estimer biais et variance.
- Vérifier l'effet de la couleur du sujet et du fond sur le masque.
- Ajouter éventuellement une visualisation 2D du masque sélectionné pour diagnostic.

## Validation scientifique humaine

- Répétabilité scan-sur-scan d'une même personne.
- Comparaison du volume à une méthode de référence.
- Recherche dataset scan 3D + DXA.
- Composition graisse / masse maigre seulement après calibration et validation.

## Android / distribution

- Remplacer la signature debug par une vraie clé de publication.
- Ajouter tests instrumentés caméra/capteurs/permissions.
- Ajouter synthèse vocale et retours sonores.
- Tester plusieurs appareils Android.
