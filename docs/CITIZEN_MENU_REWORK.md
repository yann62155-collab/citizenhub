# CITIZEN MENU — Refonte et intégration

## Objectif

Faire de CitizenHub le tableau de bord central des modules Citizen pour Minecraft Java 1.21.1, NeoForge 21.1.215 et Java 21.

- Ouverture par **N**.
- **CitizenVoice reste strictement inchangé**, notamment sa touche M.
- Un clic sur une tuile lance directement le menu natif du module ou sa commande d'ouverture principale ; aucun écran intermédiaire de commandes.
- Les modules purement administratifs/techniques ne sont pas proposés aux joueurs.
- CitizenAura est exclu du tableau de bord joueur, même si une entrée est ajoutée par configuration.
- Les droits sont vérifiés côté serveur à l'ouverture et lors de l'exécution des commandes.

## Intégrations vérifiées

### Citizen Guilds

- Mod ID : `citizenguilds`.
- Écran natif : `fr.citizen.guilds.GuildManagementScreen`.
- Constructeur public sans argument.
- L'écran demande lui-même la synchronisation au serveur pendant son initialisation.
- L'intégration utilise une liaison réflexive pour ne pas rendre Citizen Guilds obligatoire à l'installation de CITIZEN MENU.

### Citizen Economy

- Mod ID : `citizeneconomy`.
- Point d'entrée du menu principal : `/ceconomy`.
- La commande appelle le service serveur qui envoie la vue native au client.
- Les autorisations des vues sensibles sont contrôlées par Citizen Economy.

### Livre des recettes / Citizen Craft

- Commande vérifiée : `/craft`.
- Le serveur envoie le paquet d'ouverture `OpenCraftPayload`.
- Le client ouvre directement `CraftScreen`.

### Citizen Boutique

- Mod ID : `citizenboutique`.
- Commande vérifiée : `/boutique`.
- Le serveur transmet les données de la boutique et ouvre `ShopScreen`.
- Une entrée Boutique est ajoutée automatiquement aux anciennes configurations si le mod est présent.

### Modules volontairement masqués

Quêtes, Réalisme et Éclipses ont des commandes d'information ou des interactions contextuelles, mais pas de menu principal joueur vérifié à ouvrir depuis le tableau de bord. Les afficher comme des raccourcis graphiques serait trompeur. Ils restent accessibles par leurs mécanismes habituels en jeu.

Le tableau de bord n'affiche donc que les intégrations graphiques vérifiées : Guildes, Économie, Livre des recettes et Boutique. CitizenVoice n'est pas modifié ni intégré à ce raccourci ; son menu et ses touches restent autonomes.

## Interface

- Présentation sombre, bordures fines, accents dorés et cartes de modules.
- Grille de quatre modules par page, avec navigation si nécessaire.
- Descriptions limitées à deux lignes pour conserver une lecture nette.
- Les modules absents ne sont pas proposés.
- Les cartes sont construites depuis les données filtrées par le serveur, et non depuis une liste de commandes codée en dur.

## Sécurité et compatibilité

- Le client ne reçoit que les sections et commandes que le serveur a autorisées pour le joueur.
- Les commandes sont exécutées comme le joueur ; les vérifications Brigadier et les permissions du mod cible restent actives.
- Les commandes marquées staff et les sections administratives sont ignorées par le tableau de bord.
- CitizenAura est explicitement exclu.
- Le raccourci N ne remplace aucun raccourci d'un autre mod.
- La fermeture du tableau de bord notifie le serveur afin d'arrêter l'état de lecture du parchemin.
- Le code client est isolé dans les classes client ; le serveur dédié ne doit pas charger les classes Minecraft client.

## Vérifications automatisées

Le workflow GitHub Actions compile le JAR et tente un démarrage de serveur dédié NeoForge sans client graphique. Le test vérifie que le serveur atteint son état « Done » avant l'arrêt contrôlé.

La compilation ne remplace pas un essai en jeu avec les vrais JARs des modules Citizen. Les tests suivants restent à faire sur le serveur CITIZEN :
1. Ouvrir/fermer le menu avec N et confirmer que M continue de gérer CitizenVoice.
2. Ouvrir Guildes et confirmer la synchronisation des données.
3. Ouvrir Économie et confirmer l'affichage de la vue principale.
4. Vérifier les modules présents/absents et les résolutions d'écran.
5. Vérifier un compte joueur standard et un compte opérateur.
6. Vérifier le démarrage/arrêt du serveur dédié avec la combinaison complète de mods.

## État

Cette branche contient la refonte du tableau de bord et les adaptateurs directs Guilds/Economy. Le statut de CI et les tests en jeu doivent être consultés avant toute publication finale. Une compilation réussie ne prouve pas à elle seule le bon fonctionnement de chaque écran en jeu.
