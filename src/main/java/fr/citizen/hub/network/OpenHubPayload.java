package fr.citizen.hub.network;

import fr.citizen.hub.CitizenHub;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Serveur -> client : contenu du parchemin, déjà filtré selon les droits du joueur. */
public record OpenHubPayload(String titre, List<Section> sections) implements CustomPacketPayload {

    public record Section(String nom, String icone, String description, boolean admin, List<Entry> entrees) {}

    /** s / e = index dans la config serveur, renvoyés tels quels quand le joueur clique. */
    public record Entry(int s, int e, String nom, String description, String commande,
                        boolean suggerer, boolean admin) {}

    public static final Type<OpenHubPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenHub.MODID, "open_hub"));

    public static final StreamCodec<FriendlyByteBuf, OpenHubPayload> CODEC =
            StreamCodec.of(OpenHubPayload::write, OpenHubPayload::read);

    private static void write(FriendlyByteBuf buf, OpenHubPayload p) {
        buf.writeUtf(p.titre);
        buf.writeVarInt(p.sections.size());
        for (Section s : p.sections) {
            buf.writeUtf(s.nom);
            buf.writeUtf(s.icone);
            buf.writeUtf(s.description);
            buf.writeBoolean(s.admin);
            buf.writeVarInt(s.entrees.size());
            for (Entry e : s.entrees) {
                buf.writeVarInt(e.s);
                buf.writeVarInt(e.e);
                buf.writeUtf(e.nom);
                buf.writeUtf(e.description);
                buf.writeUtf(e.commande);
                buf.writeBoolean(e.suggerer);
                buf.writeBoolean(e.admin);
            }
        }
    }

    private static OpenHubPayload read(FriendlyByteBuf buf) {
        String titre = buf.readUtf();
        int n = buf.readVarInt();
        List<Section> sections = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String nom = buf.readUtf();
            String icone = buf.readUtf();
            String desc = buf.readUtf();
            boolean admin = buf.readBoolean();
            int m = buf.readVarInt();
            List<Entry> entrees = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                entrees.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
            }
            sections.add(new Section(nom, icone, desc, admin, entrees));
        }
        return new OpenHubPayload(titre, sections);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
