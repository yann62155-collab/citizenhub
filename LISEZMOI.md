# Citizen Hub — commande /citizen

NeoForge 21.1.215 · Minecraft 1.21.1 · Java 21 · mod ID `citizenhub`

## Compiler
GitHub compile tout seul à chaque envoi : le .jar est dans **Releases** du dépôt.
Sur PC : `gradlew build`.
JAR : `build/libs/citizenhub-1.0.0.jar` → à mettre côté serveur ET joueurs (via Citizen Updater).

## Ce que fait le mod
- `/citizen` ouvre un parchemin : sections à gauche (Économie, Hibou postal, Recettes, Chasse…), commandes à droite.
- Clic sur une commande = elle est lancée avec les droits du joueur (comme s'il l'avait tapée).
  Les commandes marquées ✎ sont pré-remplies dans le chat pour compléter les arguments.
- Pendant la lecture, le joueur tient un parchemin à deux mains, visible par les autres joueurs (et par lui en F5).
  C'est purement visuel : l'inventaire n'est jamais modifié, rien ne peut être perdu.
- Le parchemin est rangé dès que le menu se ferme (Échap, E, autre menu, déconnexion, mort, changement de dimension).
- Message de bienvenue à chaque connexion avec un bouton cliquable [Ouvrir le parchemin].
- `/citizen reload` (op) : recharge la config sans redémarrer.
- Joueurs console (Citizen Bedrock) : `/citizen` s'ouvre en menu Xbox natif (sections → commandes).
- Joueurs PC sans le mod : `/citizen` affiche une liste cliquable dans le chat. Le mod n'empêche personne de se connecter.

## Configuration : `config/citizen/citizen_hub.json`
Créé au premier démarrage du serveur.
- `titre`, `niveauAdmin` (2 = op), `messageConnexionActif`, `messageConnexion`
- `sections[]` : `nom`, `icone` (ex. `minecraft:feather`), `mod` (mod ID : la section s'affiche si le mod est installé),
  `description`, `admin`, `entrees[]`
- `entrees[]` : `nom`, `commande` (sans `/`), `mode` (`executer` ou `suggerer`), `admin`, `description`

**Détection automatique** : une entrée n'apparaît que si sa commande existe réellement sur le serveur ET que le
joueur a le droit de l'utiliser. Les entrées des mods pas installés ou des commandes mal nommées sont donc
simplement masquées — corrige le nom dans le JSON puis `/citizen reload`.

## Réglages visuels
Position du parchemin en main : bloc `display` de
`assets/citizenhub/models/item/parchemin_citizen.json` (rechargeable en jeu avec F3+T).
