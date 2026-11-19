# CITIZEN MENU — Refonte de CitizenHub

## Objectif produit

Transformer CitizenHub en tableau de bord graphique central. La touche d'ouverture retenue est **N**. Depuis le tableau de bord, un clic sur une tuile doit ouvrir directement l'écran natif du mod ciblé : aucun écran de commandes ou sous-menu intermédiaire.

Le projet conserve l'identifiant technique `citizenhub` afin de préserver les configurations et installations existantes. Le nom affiché devient **CITIZEN MENU**.

## Règles non négociables

- Minecraft Java 1.21.1, NeoForge 21.1.215, Java 21.
- Interface moderne premium, sombre, lisible et adaptée aux résolutions.
- Une tuile = une entrée directe vers l'écran natif du module.
- Ne pas recopier ni réimplémenter les systèmes de Guilds, Economy, Quêtes, Boutique, etc.
- Les modules absents ou indisponibles ne sont pas affichés.
- Les actions et autorisations sont vérifiées côté serveur.
- Les modules purement administratifs/techniques sont exclus du menu joueur.
- **CitizenAura est réservé aux administrateurs** et ne doit jamais être visible par un joueur ordinaire.
- **Ne pas modifier CitizenVoice** : son raccourci M, ses réglages et tous ses autres raccourcis restent inchangés.
- Le raccourci CITIZEN MENU est N et ne doit pas entrer en conflit avec les commandes vocales existantes.

## Intégrations confirmées par audit des dépôts

### Citizen Economy

- Mod ID : `citizeneconomy`.
- Écran principal ouvert par `/ceconomy`.
- Raccourci natif actuel : K.
- Le mod expose une interface serveur pilotée par vues JSON et un écran client `ViewScreen`.
- Intégration prévue : déclencher l'ouverture du menu principal via le flux d'action existant, puis laisser Citizen Economy gérer son propre écran.

### Citizen Guilds

- Mod ID : `citizenguilds`.
- Raccourci natif documenté : G.
- Le mod possède déjà une interface graphique sombre fantasy avec synchronisation serveur.
- Les commandes documentées couvrent notamment création, invitations, membres, réserve, donjons et classements.
- Intégration prévue : ajouter un point d'entrée public côté Guilds pour ouvrir son écran natif depuis Citizen Menu. Ne pas simuler la touche G et ne pas remplacer l'écran Guilds.

### Citizen Voice

- Les raccourcis de Citizen Voice restent strictement inchangés.
- Le raccourci M de Citizen Voice est conservé ; CITIZEN MENU utilise N.

## État de développement

- Le raccourci client N est ajouté ; il ouvre le point d'entrée serveur existant `/citizen`.
- Le tableau de bord sombre CITIZEN MENU remplace maintenant l'ancien écran côté client.
- Les tuiles Guildes et Économie sont conditionnées à la présence du mod côté client **et** à sa disponibilité dans les modules annoncés par le serveur.
- Guildes ouvre directement `GuildManagementScreen`, sans dépendance de compilation entre les deux mods.
- Économie ouvre directement sa vue principale native via son point d'entrée officiel `/ceconomy`.
- La fermeture du tableau de bord notifie le serveur pour arrêter proprement l'état de lecture.
- CitizenVoice n'a reçu aucune modification.
- CitizenAura n'est pas proposé dans ce tableau de bord joueur.
- La compilation Gradle sur GitHub est activée pour les branches `feature/**` ; la publication d'une release reste limitée à `main`.

La compilation et les tests en jeu de la dernière révision doivent être confirmés avant de distribuer le JAR.

## Architecture cible

1. **Module Registry** : détecte les modules compatibles réellement présents.
2. **Client Integration Registry** : associe chaque tuile à une action d'ouverture d'écran native.
3. **Server Permission Gate** : confirme que le joueur peut utiliser le module.
4. **Dashboard Screen** : affiche uniquement les tuiles disponibles pour le joueur.
5. **Integration adapters** : petits adaptateurs par mod, sans dépendance obligatoire aux autres mods.
6. **Fallback** : si un module ne fournit pas encore son point d'entrée natif, la tuile est masquée plutôt que d'ouvrir une commande approximative.

## Première tranche de réalisation

- Renommer le nom affiché en CITIZEN MENU sans changer le mod ID.
- Ajouter le raccourci client N, qui réutilise la commande serveur existante `/citizen` et ses contrôles de permission.
- Conserver intégralement CitizenVoice et ses raccourcis.
- Remplacer progressivement l'ancienne présentation parchemin par un tableau de bord à tuiles.
- Ajouter les points d'entrée natifs pour Economy et Guilds.
- Ajouter les autres modules après audit de leurs écrans et API réels.

## Critères d'acceptation

- N ouvre le tableau de bord et ne déclenche pas une autre action involontaire.
- M continue de fonctionner exactement comme avant pour CitizenVoice.
- Un clic sur Guildes ouvre directement l'écran Guilds natif.
- Un clic sur Économie ouvre directement l'écran Economy natif.
- Aucun second clic ni commande à saisir n'est nécessaire.
- CitizenAura est invisible pour les non-administrateurs.
- L'absence d'un mod n'entraîne ni crash ni tuile morte.
- Le JAR compile sur NeoForge 21.1.215 et passe un test client/serveur.
