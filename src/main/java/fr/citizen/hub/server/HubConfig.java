package fr.citizen.hub.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.citizen.hub.CitizenHub;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fichier config/citizen/citizen_hub.json (côté serveur).
 * Rechargeable à chaud avec /citizen reload.
 */
public final class HubConfig {
    private HubConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Data data = defaults();

    public static Data get() {
        return data;
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("citizen").resolve("citizen_hub.json");
    }

    /** @return null si OK, sinon le message d'erreur. */
    public static String load() {
        Path p = path();
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                    GSON.toJson(defaults(), w);
                }
                CitizenHub.LOGGER.info("[CitizenHub] Fichier créé : {}", p);
            }
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Data d = GSON.fromJson(r, Data.class);
                if (d == null) throw new IllegalStateException("fichier vide");
                d.sanitize();
                data = d;
            }
            CitizenHub.LOGGER.info("[CitizenHub] {} section(s) chargée(s)", data.sections.size());
            return null;
        } catch (Exception ex) {
            CitizenHub.LOGGER.error("[CitizenHub] Erreur de lecture de {} : {}", p, ex.toString());
            return ex.getMessage() == null ? ex.toString() : ex.getMessage();
        }
    }

    // ------------------------------------------------------------------ modèle JSON

    public static class Data {
        public String titre = "Registre du Royaume de Citizen";
        /** Niveau de permission vanilla requis pour les entrées marquées "admin" (2 = opérateur). */
        public int niveauAdmin = 2;
        /** Message envoyé à chaque connexion (cliquable : ouvre /citizen). */
        public boolean messageConnexionActif = true;
        public String messageConnexion = "Bienvenue dans le royaume de Citizen ! Banque, entreprises, courrier, recettes… tout se gère depuis ton parchemin : tape /citizen. Rappel : portes et clés sont l'affaire du menuisier.";
        public List<Section> sections = new ArrayList<>();

        void sanitize() {
            if (titre == null) titre = "Citizen";
            if (messageConnexion == null) messageConnexion = "";
            if (sections == null) sections = new ArrayList<>();
            for (Section s : sections) s.sanitize();
        }
    }

    public static class Section {
        public String nom = "";
        /** Objet affiché comme icône, ex. "minecraft:feather". */
        public String icone = "minecraft:paper";
        /** Mod ID : la section s'affiche si ce mod est installé (même sans commande). Laisser vide sinon. */
        public String mod = "";
        public String description = "";
        /** Section entière réservée au staff. */
        public boolean admin = false;
        public List<Entree> entrees = new ArrayList<>();

        void sanitize() {
            if (nom == null) nom = "";
            if (icone == null) icone = "minecraft:paper";
            if (mod == null) mod = "";
            if (description == null) description = "";
            if (entrees == null) entrees = new ArrayList<>();
            for (Entree e : entrees) e.sanitize();
        }

        Section(String nom, String icone, String mod, boolean admin, String description, Entree... entrees) {
            this.nom = nom; this.icone = icone; this.mod = mod; this.admin = admin; this.description = description;
            this.entrees = new ArrayList<>(List.of(entrees));
        }

        @SuppressWarnings("unused") Section() {}
    }

    public static class Entree {
        public String nom = "";
        public String description = "";
        /** Commande SANS le "/" (ex. "ceconomy bank"). */
        public String commande = "";
        /** "executer" = lancée au clic ; "suggerer" = pré-remplie dans le chat pour compléter les arguments. */
        public String mode = "executer";
        public boolean admin = false;

        void sanitize() {
            if (nom == null) nom = "";
            if (description == null) description = "";
            if (commande == null) commande = "";
            commande = commande.trim();
            if (commande.startsWith("/")) commande = commande.substring(1);
            if (mode == null) mode = "executer";
        }

        public boolean suggerer() {
            return mode.equalsIgnoreCase("suggerer") || mode.equalsIgnoreCase("suggérer");
        }

        Entree(String nom, String commande, String mode, boolean admin, String description) {
            this.nom = nom; this.commande = commande; this.mode = mode; this.admin = admin; this.description = description;
        }

        @SuppressWarnings("unused") Entree() {}
    }

    private static Entree exe(String nom, String cmd, String desc) { return new Entree(nom, cmd, "executer", false, desc); }
    private static Entree exeAdmin(String nom, String cmd, String desc) { return new Entree(nom, cmd, "executer", true, desc); }
    private static Entree sug(String nom, String cmd, String desc) { return new Entree(nom, cmd, "suggerer", false, desc); }
    private static Entree sugAdmin(String nom, String cmd, String desc) { return new Entree(nom, cmd, "suggerer", true, desc); }

    /**
     * Contenu par défaut. Une entrée dont la commande n'existe pas sur le serveur (ou que le joueur
     * n'a pas le droit d'utiliser) est masquée automatiquement : on peut donc laisser des entrées
     * pour des mods pas encore installés.
     */
    static Data defaults() {
        Data d = new Data();
        d.sections.add(new Section("Économie", "minecraft:gold_ingot", "citizeneconomy", false,
                "Banque du royaume, entreprises, annonces et factures. La monnaie est le PO (bronze 1, argent 10, or 100, platine 1000).",
                exe("Ma banque", "ceconomy bank", "Ouvrir ton compte personnel : dépôts, retraits, virements."),
                exe("Mon entreprise", "ceconomy company", "Gérer ton entreprise, tes employés et ton compte pro."),
                exe("Panel web de l'entreprise", "ceconomy web", "Reçois dans le chat un lien personnel (valable 30 min) vers le site de gestion de tes entreprises : employés, salaires, comptes, factures."),
                sug("Fonder une entreprise", "ceconomy create ", "Déposer une demande de création d'entreprise (validée par le staff)."),
                exe("Annonces", "ceconomy announcements", "Lire et publier les annonces du royaume."),
                exe("Aide", "ceconomy help", "Liste de toutes les commandes de l'économie."),
                exeAdmin("Trésorerie royale", "ceconomy treasury", "Consulter la trésorerie et les taxes perçues."),
                exeAdmin("Administration économie", "ceconomy admin", "Menu d'administration complet de l'économie.")));
        d.sections.add(new Section("Hibou postal", "minecraft:feather", "", false,
                "Envoie lettres et colis grâce à ton numéro de hibou. Le courrier arrive dans ta boîte aux lettres si tu es absent.",
                exe("Ouvrir le courrier", "hibou", "Écrire une lettre, envoyer un colis, retrouver ton numéro de hibou.")));
        d.sections.add(new Section("Livre des recettes", "minecraft:crafting_table", "", false,
                "Toutes les recettes du royaume. Rappel RP : artefacts, armures, filets, boîtes aux lettres, lettres, cartons et affiches se font chez le forgeron ; portes et clés uniquement chez le menuisier ; nourriture et bière à la taverne ou au restaurant ; la pêche est réservée aux pêcheurs.",
                exe("Ouvrir le livre des recettes", "craft", "Recherche, catégories et onglet Animaux.")));
        d.sections.add(new Section("Réalisme", "minecraft:leather_chestplate", "citizenrealisme", false,
                "Poids et encombrement (plus tu portes, plus tu ralentis), cases limitées et sacs, crafts avec durée, outils d'artisan et taux de réussite, enchantement par rituel avec catalyseurs.",
                exe("Mon équipement", "realisme", "Poids porté, sac équipé et nombre de cases utilisables."),
                exe("Comment fabriquer ?", "realisme craft", "Durée des crafts, taux de réussite et outils d'artisan (marteau, scie, aiguille)."),
                exe("Rituel d'enchantement", "realisme rituel", "Catalyseurs demandés, taux de réussite et risques d'échec."),
                exe("Retirer mon sac", "realisme sac retirer", "Retire le sac équipé (il doit être vide)."),
                exeAdmin("Recharger les réglages", "realisme admin recharger", "Relit realisme.json sans redémarrer."),
                sugAdmin("Donner un objet", "realisme admin donner ", "Objets : marteau, scie, aiguille, sacoche ou sacados, puis le joueur (facultatif).")));
        d.sections.add(new Section("Quêtes", "minecraft:writable_book", "citizenquetes", false,
                "Des messagers du château parcourent le royaume et proposent des quêtes. Ta quête en cours s'affiche à droite de l'écran ; la récompense est virée sur ton compte en banque.",
                exe("Ma quête", "quete", "Voir la quête en cours et son avancement."),
                sug("Abandonner ma quête", "quete abandonner", "Valide avec Entrée dans le chat pour confirmer l'abandon."),
                exeAdmin("Aide quêtes (staff)", "quetes", "Toutes les commandes d'administration des quêtes."),
                exeAdmin("Prochain messager", "quetes prochain", "Heure du prochain messager et messagers en route."),
                sugAdmin("Envoyer un messager", "quetes envoyer ", "Vers toi, ou ajoute le nom d'un joueur."),
                sugAdmin("Annuler la quête d'un joueur", "quetes annuler ", "Ajoute le nom du joueur."),
                sugAdmin("Définir le château", "quetes chateau", "Le château devient ta position actuelle (valide avec Entrée)."),
                sugAdmin("Ajouter un lieu de ronde", "quetes lieu ajouter ", "Lieu à ta position : ajoute son nom."),
                exeAdmin("Lieux de ronde", "quetes lieu liste", "Château et lieux de ronde enregistrés."),
                exeAdmin("Recharger les quêtes", "quetes recharger", "Relit config/citizenquetes/*.json.")));
        d.sections.add(new Section("Chasse & pêche", "minecraft:iron_sword", "citizenchasse", false,
                "Les bêtes tuées laissent une carcasse : transporte-la avec la touche Alt, puis vide-la et découpe-la sur une table de boucher avec un couteau. Plus la bête est lourde, plus elle donne de viande. Pêche au filet ou sur supports de canne, poissons à tailler en filets. Pièges à lapin et filets demandent des appâts."));
        d.sections.add(new Section("Menuiserie", "minecraft:oak_door", "citizenmenuiserie", false,
                "Portes à clé et clés : à confectionner UNIQUEMENT par le menuisier, sur l'établi de menuiserie. Chaque porte est livrée avec sa clé ; la machine à clés crée et duplique les clés (pour un employé par exemple). Il faut tenir la bonne clé en main pour ouvrir une porte à clé."));
        d.sections.add(new Section("Fonderie", "minecraft:blast_furnace", "citizenfonderie", false,
                "Fours de forgeron pour fondre les minerais : four simple (2 minerais), four d'apprenti (4), four de maître forgeron (8)."));
        d.sections.add(new Section("Artefacts", "minecraft:amethyst_shard", "citizenartefacts", false,
                "Un artefact agit tenu en main, en seconde main ou porté à la place du plastron. Vitae : cœurs doublés. Enec : vitesse 5 min (recharge 10 min). Suspendo : vol 1 min (recharge 3 min)."));
        d.sections.add(new Section("Affiches", "minecraft:painting", "citizenaffiches", false,
                "Pose une affiche sur un mur : regarde-la pour l'afficher en grand, recule pour la ranger."));
        d.sections.add(new Section("Éclipses", "minecraft:clock", "", false,
                "Au moins une éclipse par semaine : les créatures se multiplient. Ne regarde pas le soleil sans casque adapté !",
                exe("Prochaine éclipse", "eclipse", "Voir quand aura lieu la prochaine éclipse."),
                exeAdmin("Infos éclipse (staff)", "eclipse next", "Détail de la prochaine éclipse programmée."),
                exeAdmin("Déclencher une éclipse", "eclipse start", "Lancer une éclipse immédiatement.")));
        d.sections.add(new Section("Cinématiques", "minecraft:ender_eye", "", true,
                "Lancer une vidéo pour tous les joueurs connectés.",
                sugAdmin("Lancer une cinématique", "cinematique ", "Pré-remplit la commande : ajoute le lien ou le nom de la vidéo.")));
        d.sections.add(new Section("Horaires du serveur", "minecraft:bell", "", true,
                "Horaires d'ouverture, whitelist automatique et MOTD.",
                exeAdmin("Horaires", "horaires", "Voir et modifier les horaires d'ouverture.")));
        d.sections.add(new Section("Inventaires", "minecraft:chest", "", true,
                "Sauvegardes et restaurations des inventaires et coffres de l'Ender.",
                sugAdmin("Inventaires", "citizeninventaire ", "Pré-remplit la commande de gestion des sauvegardes.")));
        d.sections.add(new Section("Parchemin Citizen", "minecraft:writable_book", "", true,
                "Réglages de ce parchemin (fichier config/citizen/citizen_hub.json).",
                exeAdmin("Recharger le parchemin", "citizen reload", "Relire le fichier de configuration sans redémarrer.")));
        return d;
    }
}
