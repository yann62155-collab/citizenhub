package fr.citizen.hub.network;

import fr.citizen.hub.CitizenHub;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serveur -> clients proches : tel joueur lit (ou ne lit plus) son parchemin. */
public record ReadingStatePayload(int entityId, boolean reading) implements CustomPacketPayload {
    public static final Type<ReadingStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenHub.MODID, "reading_state"));

    public static final StreamCodec<FriendlyByteBuf, ReadingStatePayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.entityId); buf.writeBoolean(p.reading); },
            buf -> new ReadingStatePayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
