package fr.citizen.hub;

import fr.citizen.hub.network.HubNetwork;
import fr.citizen.hub.server.HubServerEvents;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Citizen Hub : la commande /citizen ouvre un parchemin qui regroupe
 * tous les menus et commandes des mods Citizen installés sur le serveur.
 */
@Mod(CitizenHub.MODID)
public class CitizenHub {
    public static final String MODID = "citizenhub";
    public static final Logger LOGGER = LoggerFactory.getLogger("CitizenHub");

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    /** Parchemin purement visuel : affiché dans les mains du joueur qui lit /citizen. Jamais donné en inventaire. */
    public static final DeferredItem<Item> PARCHEMIN =
            ITEMS.registerSimpleItem("parchemin_citizen", new Item.Properties().stacksTo(1));

    public CitizenHub(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener(HubNetwork::register);
        NeoForge.EVENT_BUS.register(HubServerEvents.class);
    }
}
