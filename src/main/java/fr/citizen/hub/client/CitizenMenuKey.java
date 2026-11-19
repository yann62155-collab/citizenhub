package fr.citizen.hub.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.citizen.hub.CitizenHub;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Raccourci principal de CITIZEN MENU.
 *
 * La touche N est volontairement distincte des raccourcis de CitizenVoice :
 * ce mod ne modifie et ne remplace aucun raccourci vocal.
 */
@EventBusSubscriber(modid = CitizenHub.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CitizenMenuKey {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.citizenhub.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.citizenhub"
    );

    private CitizenMenuKey() {}

    @SubscribeEvent
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    @EventBusSubscriber(modid = CitizenHub.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_MENU.consumeClick()) {
                if (minecraft.player == null || minecraft.screen != null) continue;
                // Réutilise le point d'entrée serveur existant : mêmes contrôles,
                // même filtrage des permissions et même compatibilité sans raccourci réseau dédié.
                minecraft.player.connection.sendCommand("citizen");
            }
        }
    }
}
