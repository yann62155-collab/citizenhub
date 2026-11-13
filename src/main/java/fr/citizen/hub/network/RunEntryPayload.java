package fr.citizen.hub.network;

import fr.citizen.hub.CitizenHub;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> serveur : le joueur a cliqué sur une commande du parchemin. Le serveur revérifie tout. */
public record RunEntryPayload(int section, int entry) implements CustomPacketPayload {
    public static final Type<RunEntryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenHub.MODID, "run_entry"));

    public static final StreamCodec<FriendlyByteBuf, RunEntryPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.section); buf.writeVarInt(p.entry); },
            buf -> new RunEntryPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
