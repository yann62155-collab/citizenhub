package fr.citizen.hub.server;

import fr.citizen.hub.network.ReadingStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HubServerEvents {
    private HubServerEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        HubConfig.load();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("citizen")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HubServer.open(player);
                    return 1;
                })
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            String err = HubConfig.load();
                            if (err == null) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Parchemin Citizen rechargé ("
                                        + HubConfig.get().sections.size() + " sections).").withStyle(ChatFormatting.GOLD), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("Erreur dans citizen_hub.json : " + err));
                            return 0;
                        })));
    }

    /** Un joueur qui arrive à portée voit directement si l'autre est en train de lire. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target
                && event.getEntity() instanceof ServerPlayer watcher
                && HubServer.isReading(target)) {
            PacketDistributor.sendToPlayer(watcher, new ReadingStatePayload(target.getId(), true));
        }
    }

    /** Message de bienvenue à la connexion, cliquable pour ouvrir directement le parchemin. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        HubConfig.Data cfg = HubConfig.get();
        if (!cfg.messageConnexionActif || cfg.messageConnexion.isBlank()) return;

        Component bouton = Component.literal("[Ouvrir le parchemin]").withStyle(style -> style
                .withColor(ChatFormatting.GOLD).withBold(true).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/citizen"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Clique pour ouvrir /citizen").withStyle(ChatFormatting.YELLOW))));

        sp.sendSystemMessage(Component.literal("✦ ━━━━━━━━ Royaume de Citizen ━━━━━━━━ ✦").withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.literal(cfg.messageConnexion).withStyle(ChatFormatting.YELLOW));
        sp.sendSystemMessage(Component.literal("➜ ").withStyle(ChatFormatting.DARK_RED).append(bouton));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            HubServer.stopReading(sp);
            HubServer.forget(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HubServer.stopReading(sp);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HubServer.stopReading(sp);
    }
}
