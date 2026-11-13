package fr.citizen.hub.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import fr.citizen.hub.network.OpenHubPayload;
import fr.citizen.hub.network.ReadingStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
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

    public static void open(ServerPlayer player) {
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

        startReading(player);
        PacketDistributor.sendToPlayer(player, new OpenHubPayload(cfg.titre, sections));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 1.0f, 0.9f);
    }

    public static void startReading(ServerPlayer player) {
        if (READING.add(player.getUUID())) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ReadingStatePayload(player.getId(), true));
        }
    }

    public static void stopReading(ServerPlayer player) {
        if (READING.remove(player.getUUID())) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ReadingStatePayload(player.getId(), false));
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
