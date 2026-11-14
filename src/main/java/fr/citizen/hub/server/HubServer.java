package fr.citizen.hub.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import fr.citizen.hub.network.OpenHubPayload;
import fr.citizen.hub.network.ReadingStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HubServer {
    private HubServer() {}

    /** Joueurs qui ont actuellement le parchemin ouvert. */
    private static final Set<UUID> READING = ConcurrentHashMap.newKeySet();

    public static boolean isReading(ServerPlayer player) {
        return READING.contains(player.getUUID());
    }

    // ------------------------------------------------------------------ ouverture / fermeture

    /** Sections et entrées visibles pour ce joueur (commandes existantes + droits vérifiés). */
    static List<OpenHubPayload.Section> visibleSections(ServerPlayer player) {
        HubConfig.Data cfg = HubConfig.get();
        CommandSourceStack src = player.createCommandSourceStack();
        boolean isAdmin = src.hasPermission(cfg.niveauAdmin);

        List<OpenHubPayload.Section> sections = new ArrayList<>();
        for (int si = 0; si < cfg.sections.size(); si++) {
            HubConfig.Section s = cfg.sections.get(si);
            if (s.admin && !isAdmin) continue;

            List<OpenHubPayload.Entry> entries = new ArrayList<>();
            for (int ei = 0; ei < s.entrees.size(); ei++) {
                HubConfig.Entree e = s.entrees.get(ei);
                if (!canUse(src, e, isAdmin)) continue;
                entries.add(new OpenHubPayload.Entry(si, ei, e.nom, e.description, e.commande, e.suggerer(), e.admin));
            }

            boolean modPresent = !s.mod.isBlank() && ModList.get().isLoaded(s.mod);
            if (entries.isEmpty() && !modPresent) continue; // rien d'utile à montrer
            sections.add(new OpenHubPayload.Section(s.nom, s.icone, s.description, s.admin, entries));
        }
        return sections;
    }

    /** Vrai si le joueur a Citizen Hub sur son PC (sinon : console via Citizen Bedrock ou Java vanilla). */
    static boolean modded(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(OpenHubPayload.TYPE);
    }

    public static void open(ServerPlayer player) {
        HubConfig.Data cfg = HubConfig.get();
        List<OpenHubPayload.Section> sections = visibleSections(player);

        // Joueur console : menu Bedrock natif
        if (Bedrock.isBedrock(player) && openBedrock(player, cfg.titre, sections)) return;

        // Joueur PC sans le mod : liste cliquable dans le chat
        if (!modded(player)) {
            openChat(player, cfg.titre, sections);
            return;
        }

        startReading(player);
        PacketDistributor.sendToPlayer(player, new OpenHubPayload(cfg.titre, sections));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 1.0f, 0.9f);
    }

    // ------------------------------------------------------------------ console (Citizen Bedrock)

    private static boolean openBedrock(ServerPlayer player, String titre, List<OpenHubPayload.Section> sections) {
        List<String> buttons = new ArrayList<>();
        for (OpenHubPayload.Section s : sections) buttons.add(s.admin() ? s.nom() + " [Staff]" : s.nom());
        buttons.add("Fermer");
        String content = sections.isEmpty()
                ? "Aucun module Citizen n'est disponible pour toi."
                : "Choisis un registre du royaume.";
        return Bedrock.menu(player, titre, content, buttons, index -> {
            if (index >= 0 && index < sections.size()) openBedrockSection(player, titre, sections, sections.get(index));
        });
    }

    private static void openBedrockSection(ServerPlayer player, String titre, List<OpenHubPayload.Section> sections,
                                           OpenHubPayload.Section section) {
        StringBuilder content = new StringBuilder(section.description());
        if (section.entrees().isEmpty()) {
            if (content.length() > 0) content.append("\n\n");
            content.append("§7Ce module n'a pas de commande : il se découvre en jeu.");
        }
        List<String> buttons = new ArrayList<>();
        for (OpenHubPayload.Entry e : section.entrees()) buttons.add(e.admin() ? e.nom() + " [Admin]" : e.nom());
        buttons.add("Retour");
        Bedrock.menu(player, section.nom(), content.toString(), buttons, index -> {
            if (index >= 0 && index < section.entrees().size()) {
                OpenHubPayload.Entry e = section.entrees().get(index);
                if (e.suggerer()) {
                    // Pas de chat pré-rempli sur console : on indique la commande à taper.
                    player.sendSystemMessage(Component.literal("Tape dans le chat : ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("/" + e.commande().trim() + " …").withStyle(ChatFormatting.YELLOW)));
                    if (!e.description().isEmpty()) {
                        player.sendSystemMessage(Component.literal(e.description()).withStyle(ChatFormatting.GRAY));
                    }
                } else {
                    runEntry(player, e.s(), e.e());
                }
            } else if (index == section.entrees().size()) {
                openBedrock(player, titre, sections); // Retour
            }
        });
    }

    // ------------------------------------------------------------------ PC sans le mod : chat cliquable

    private static void openChat(ServerPlayer player, String titre, List<OpenHubPayload.Section> sections) {
        player.sendSystemMessage(Component.literal("✦ " + titre + " ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (sections.isEmpty()) {
            player.sendSystemMessage(Component.literal("Aucun module Citizen n'est disponible pour toi.").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (OpenHubPayload.Section s : sections) {
            if (s.entrees().isEmpty()) continue;
            MutableComponent line = Component.literal(s.nom() + " : ").withStyle(ChatFormatting.DARK_RED);
            for (OpenHubPayload.Entry e : s.entrees()) {
                String cmd = "/" + e.commande().trim();
                ClickEvent click = e.suggerer()
                        ? new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd + " ")
                        : new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd);
                Component hover = Component.literal(cmd + (e.description().isEmpty() ? "" : "\n" + e.description()));
                line.append(Component.literal("[" + e.nom() + "]").withStyle(st -> st
                        .withColor(e.admin() ? ChatFormatting.RED : ChatFormatting.YELLOW)
                        .withClickEvent(click)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))));
                line.append(Component.literal(" "));
            }
            player.sendSystemMessage(line);
        }
    }

    public static void startReading(ServerPlayer player) {
        if (READING.add(player.getUUID())) broadcast(player, true);
    }

    public static void stopReading(ServerPlayer player) {
        if (READING.remove(player.getUUID())) broadcast(player, false);
    }

    /** Prévient les joueurs proches (qui ont le mod) qu'un joueur lit ou range son parchemin. */
    private static void broadcast(ServerPlayer player, boolean reading) {
        ReadingStatePayload payload = new ReadingStatePayload(player.getId(), reading);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.distanceToSqr(player) > 160 * 160) continue;
            if (modded(other)) PacketDistributor.sendToPlayer(other, payload);
        }
    }

    public static void forget(UUID id) {
        READING.remove(id);
    }

    // ------------------------------------------------------------------ clic sur une entrée

    public static void runEntry(ServerPlayer player, int section, int entry) {
        HubConfig.Data cfg = HubConfig.get();
        if (section < 0 || section >= cfg.sections.size()) return;
        HubConfig.Section s = cfg.sections.get(section);
        if (entry < 0 || entry >= s.entrees.size()) return;
        HubConfig.Entree e = s.entrees.get(entry);
        if (e.suggerer()) return; // les suggestions sont gérées côté client (chat pré-rempli)

        CommandSourceStack src = player.createCommandSourceStack();
        boolean isAdmin = src.hasPermission(cfg.niveauAdmin);
        if ((s.admin && !isAdmin) || !canUse(src, e, isAdmin)) {
            player.sendSystemMessage(Component.literal("Ce feuillet du parchemin t'est interdit.")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }
        // La commande est exécutée AVEC LES DROITS DU JOUEUR, exactement comme s'il l'avait tapée.
        player.server.getCommands().performPrefixedCommand(src, e.commande);
    }

    /**
     * Une entrée n'est affichée que si sa commande existe vraiment sur ce serveur
     * et que le joueur a le droit de l'utiliser (on interroge Brigadier directement).
     */
    static boolean canUse(CommandSourceStack src, HubConfig.Entree e, boolean isAdmin) {
        if (e.admin && !isAdmin) return false;
        String cmd = e.commande.trim();
        if (cmd.isEmpty()) return false;
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = src.getServer().getCommands().getDispatcher();
            ParseResults<CommandSourceStack> parse = dispatcher.parse(cmd, src);
            if (parse.getReader().canRead()) return false;             // partie non reconnue / interdite
            CommandContextBuilder<CommandSourceStack> ctx = parse.getContext();
            if (ctx.getNodes().isEmpty()) return false;
            if (e.suggerer()) return true;                              // le joueur complétera les arguments
            return ctx.getLastChild().getCommand() != null;              // commande exécutable telle quelle
        } catch (Exception ex) {
            return false;
        }
    }
}
