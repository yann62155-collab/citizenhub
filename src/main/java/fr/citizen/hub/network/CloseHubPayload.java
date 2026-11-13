package fr.citizen.hub.network;

import fr.citizen.hub.CitizenHub;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> serveur : le joueur a rangé son parchemin. */
public record CloseHubPayload() implements CustomPacketPayload {
    public static final CloseHubPayload INSTANCE = new CloseHubPayload();
    public static final Type<CloseHubPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenHub.MODID, "close_hub"));
    public static final StreamCodec<FriendlyByteBuf, CloseHubPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
