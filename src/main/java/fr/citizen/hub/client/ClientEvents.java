package fr.citizen.hub.client;

import fr.citizen.hub.CitizenHub;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Affichage du parchemin dans les mains des joueurs qui lisent /citizen.
 *
 * Aucune modification réelle de l'inventaire : juste avant de dessiner le joueur,
 * on remplace (côté client uniquement) l'objet tenu par le parchemin, puis on remet
 * le vrai objet juste après le rendu. Le serveur n'est jamais touché, rien ne peut être perdu.
 */
public final class ClientEvents {
    private ClientEvents() {}

    private record Saved(int slot, ItemStack main, ItemStack off) {}

    private static final Map<Player, Saved> SAVED = new IdentityHashMap<>();

    // ------------------------------------------------------------------ bus du jeu

    @EventBusSubscriber(modid = CitizenHub.MODID, value = Dist.CLIENT)
    public static final class Game {
        private Game() {}

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void beforeRender(RenderPlayerEvent.Pre event) {
            Player player = event.getEntity();
            if (!ClientHooks.isReading(player.getId()) || SAVED.containsKey(player)) return;
            Inventory inv = player.getInventory();
            int slot = inv.selected;
            if (slot < 0 || slot >= inv.items.size()) return;
            SAVED.put(player, new Saved(slot, inv.items.get(slot), inv.offhand.get(0)));
            inv.items.set(slot, new ItemStack(CitizenHub.PARCHEMIN.get()));
            inv.offhand.set(0, ItemStack.EMPTY);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public static void afterRender(RenderPlayerEvent.Post event) {
            restore(event.getEntity());
        }

        /** Filet de sécurité : si un autre mod a interrompu le rendu, on restaure quand même. */
        @SubscribeEvent
        public static void onTick(ClientTickEvent.Pre event) {
            restoreAll();
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            restoreAll();
            ClientHooks.READING.clear();
        }
    }

    private static void restore(Player player) {
        Saved s = SAVED.remove(player);
        if (s == null) return;
        Inventory inv = player.getInventory();
        if (s.slot() < inv.items.size()) inv.items.set(s.slot(), s.main());
        inv.offhand.set(0, s.off());
    }

    private static void restoreAll() {
        if (SAVED.isEmpty()) return;
        for (Player p : SAVED.keySet().toArray(new Player[0])) restore(p);
    }

    // ------------------------------------------------------------------ bus du mod

    @EventBusSubscriber(modid = CitizenHub.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                @Override
                public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                    // Les deux bras tendus devant soi : on tient le parchemin ouvert pour le lire.
                    return hand == InteractionHand.MAIN_HAND ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
                }
            }, CitizenHub.PARCHEMIN.get());
        }
    }
}
