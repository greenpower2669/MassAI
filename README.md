# MassAI

POC Android de photogrammétrie corporelle locale.

## État actuel — v0.2.0

- saisie taille et poids ;
- capture vidéo directement dans MassAI avec CameraX ;
- import d'une vidéo existante avec conservation de la permission URI lorsque possible ;
- extraction réelle de 18 frames candidates ;
- écran de suivi du pipeline ;
- zone réservée au modèle 3D brut/réparé et aux défauts du maillage.

La reconstruction du maillage 3D n'est pas encore implémentée : l'interface l'indique explicitement et ne fabrique pas de faux résultat.

## Pipeline cible

1. Saisie taille et poids.
2. Capture ou import vidéo.
3. Extraction de vues candidates.
4. Filtrage flou/redondance et couverture angulaire.
5. Segmentation du sujet.
6. Reconstruction géométrique / mesh 3D.
7. Conservation des meshes brut et corrigé.
8. Détection du plan du sol, coupe sous les pieds et fermeture séparée de chaque semelle.
9. Réparation des trous et contrôle watertight.
10. Visualisation 3D et validation utilisateur.
11. Mise à l'échelle par la taille connue.
12. Calcul du volume puis de la masse volumique (rho = masse / volume).
13. Rapport qualité/incertitude.

Les estimations futures de composition corporelle seront séparées de la mesure géométrique et accompagnées d'un indice d'incertitude.
