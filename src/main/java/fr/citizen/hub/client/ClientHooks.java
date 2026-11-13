package fr.citizen.hub.client;

import fr.citizen.hub.network.OpenHubPayload;
import fr.citizen.hub.network.ReadingStatePayload;
import net.minecraft.client.Minecraft;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Réception des paquets côté client (jamais chargée sur le serveur dédié). */
public final class ClientHooks {
    private ClientHooks() {}

    /** IDs des joueurs (entités) qui lisent leur parchemin en ce moment. */
    static final Set<Integer> READING = ConcurrentHashMap.newKeySet();

    public static void openHub(OpenHubPayload payload) {
        Minecraft.getInstance().setScreen(new HubScreen(payload));
    }

    public static void readingState(ReadingStatePayload payload) {
        if (payload.reading()) READING.add(payload.entityId());
        else READING.remove(payload.entityId());
    }

    static boolean isReading(int entityId) {
        return READING.contains(entityId);
    }
}
