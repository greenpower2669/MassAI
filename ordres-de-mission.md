# Ordres de mission — MassAI

## IMMUTABLE / À PRÉSERVER
- Identité Android vérifiée : `fr.massai.app`, à ne pas renommer.
- `main`, `massai-v1.1-options` et `massai-v1.2-stabilisation` restent inchangées sans validation.
- Compatibilité avec les archives `.massai` antérieures et export OBJ.
- Pas de diagnostic corporel/médical non calibré ni de résultats scientifiquement validés revendiqués.

## MASSAI-FIX-20261025 — Audit et corrections profondes
- Commande de Fab, 25 octobre 2026 : « Ok go » suite à un audit ultra-profond demandant des corrections sur MassAI.
- Résultat voulu : reconstruire des objets et corps de façon plus fiable, supprimer les trous de rendu, réduire amputations pieds/jambes, stabiliser mémoire et gestion des vues, puis intégrer une vraie calibration caméra/6 DoF.
- Stratégie validée : repartir de v1.2, travailler en branche `feature/massai-reconstruction-audit-fixes-v1`, tests CI puis essais téléphoniques, sans merge main ni annonce d'exactitude anatomique avant validation.
- Statut : **en cours** ; lot initial programmé, géométrie métrique et validation réelle non terminées. Suivi dans `todo.md`.

### Point v1.2.2-preview2
- Deuxième lot : sécurité du cadrage des silhouettes et masque binaire compact ; pas de validation sur captures réelles, pose caméra 6 DoF toujours absente.

### Livraison de validation du 25 octobre 2026
- Version v1.2.2-preview2 publiée en préversion avec APK/AAB directs : https://github.com/greenpower2669/MassAI/releases/tag/v1.2.2-preview2
- CI vérifiée : https://github.com/greenpower2669/MassAI/actions/runs/40195915431
- Branche et PR #1 brouillon conservées, aucun merge. Mission **livrée partiellement, à valider** : calibration 6 DoF et essais métriques encore à produire.
