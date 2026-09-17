# MassAI

POC Android de photogrammétrie corporelle locale.

## Pipeline cible
1. Saisie taille et poids.
2. Capture ou import vidéo.
3. Extraction de vues utiles.
4. Reconstruction du mesh 3D.
5. Conservation des meshes brut et corrigé.
6. Détection du plan du sol, coupe sous les pieds et fermeture plane.
7. Réparation des trous et contrôle watertight.
8. Visualisation 3D et validation utilisateur.
9. Mise à l'échelle par la taille connue.
10. Calcul du volume puis de la masse volumique (rho = masse / volume).

La V0.1 pose le squelette APK et les entrées vidéo. Les estimations futures de composition corporelle seront séparées de cette mesure géométrique et accompagnées d'un indice d'incertitude.
