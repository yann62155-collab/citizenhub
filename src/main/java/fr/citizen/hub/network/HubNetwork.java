package fr.citizen.hub.network;

import fr.citizen.hub.server.HubServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class HubNetwork {
    private HubNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");

        // Les handlers client passent par des lambdas : la classe client n'est chargée
        // que si le paquet est reçu, donc jamais sur le serveur dédié.
        r.playToClient(OpenHubPayload.TYPE, OpenHubPayload.CODEC,
                (p, ctx) -> fr.citizen.hub.client.ClientHooks.openHub(p));
        r.playToClient(ReadingStatePayload.TYPE, ReadingStatePayload.CODEC,
                (p, ctx) -> fr.citizen.hub.client.ClientHooks.readingState(p));

        r.playToServer(RunEntryPayload.TYPE, RunEntryPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) HubServer.runEntry(sp, p.section(), p.entry());
        });
        r.playToServer(CloseHubPayload.TYPE, CloseHubPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) HubServer.stopReading(sp);
        });
    }
}
