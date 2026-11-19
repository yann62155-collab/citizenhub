package fr.citizen.hub.client;

import fr.citizen.hub.CitizenHub;
import fr.citizen.hub.network.CloseHubPayload;
import fr.citizen.hub.network.OpenHubPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Tableau de bord graphique CITIZEN MENU. */
public final class DashboardScreen extends Screen {
    private static final int BACKGROUND = 0xFF080C13;
    private static final int PANEL = 0xFF111925;
    private static final int CARD = 0xFF182332;
    private static final int CARD_HOVER = 0xFF223247;
    private static final int GOLD = 0xFFE6BE78;
    private static final int GOLD_DARK = 0xFF927044;
    private static final int WHITE = 0xFFF4F0E8;
    private static final int MUTED = 0xFF9AA9BA;
    private static final int LINE = 0xFF354354;
    private static final int TEAL = 0xFF80B9B1;

    private int left, top, panelWidth, panelHeight, page, animationTick;
    private List<ModuleTile> tiles = List.of();
    private final OpenHubPayload payload;

    private enum Target { GUILDS, ECONOMY, COMMAND }
    private record ModuleTile(String title, String description, String command, Target target,
                              String emblem, int accent) {}

    public DashboardScreen(OpenHubPayload payload) {
        super(Component.literal("CITIZEN MENU"));
        this.payload = payload;
    }

    @Override public void tick() { super.tick(); animationTick++; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        panelWidth = Math.min(760, width - 32);
        panelHeight = Math.min(500, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        tiles = collectTiles();
        int pages = Math.max(1, (tiles.size() + 3) / 4);
        page = Math.max(0, Math.min(page, pages - 1));
    }

    private List<ModuleTile> collectTiles() {
        List<ModuleTile> result = new ArrayList<>();
        for (OpenHubPayload.Section section : payload.sections()) {
            if (section.admin() || section.nom().equalsIgnoreCase("CitizenAura")) continue;
            String name = section.nom();
            String modId = payloadModId(name);
            if (!modId.isEmpty() && !ModList.get().isLoaded(modId)) continue;

            if (name.equalsIgnoreCase("Guildes") && ModList.get().isLoaded("citizenguilds")) {
                result.add(new ModuleTile(name, "Gérez votre guilde, vos membres et vos aventures.",
                        "", Target.GUILDS, "⚔", GOLD));
                continue;
            }
            if ((name.equalsIgnoreCase("Économie") || name.equalsIgnoreCase("Economie"))
                    && ModList.get().isLoaded("citizeneconomy")) {
                result.add(new ModuleTile(name, "Consultez vos finances et les services économiques du royaume.",
                        "ceconomy", Target.ECONOMY, "◈", TEAL));
                continue;
            }
            if (name.equalsIgnoreCase("Livre des recettes")) {
                boolean available = section.entrees().stream().anyMatch(entry ->
                        !entry.admin() && !entry.suggerer() && entry.commande().equalsIgnoreCase("craft"));
                if (available) result.add(new ModuleTile(name,
                        "Retrouvez vos recettes et vos possibilités de fabrication.",
                        "craft", Target.COMMAND, "⚒", GOLD));
            } else if (name.equalsIgnoreCase("Boutique") && ModList.get().isLoaded("citizenboutique")) {
                boolean available = section.entrees().stream().anyMatch(entry ->
                        !entry.admin() && !entry.suggerer() && entry.commande().equalsIgnoreCase("boutique"));
                if (available) result.add(new ModuleTile(name,
                        "Parcourez les articles et les étals disponibles.",
                        "boutique", Target.COMMAND, "✧", TEAL));
            }
        }
        return result;
    }

    private String payloadModId(String name) {
        return switch (name.toLowerCase()) {
            case "guildes" -> "citizenguilds";
            case "économie", "economie" -> "citizeneconomy";
            case "boutique" -> "citizenboutique";
            default -> "";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, left + panelWidth - 112, top + 22, 84, 26)) {
            onClose();
            return true;
        }

        int cardWidth = (panelWidth - 64) / 2;
        int first = page * 4;
        for (int slot = 0; slot < 4 && first + slot < tiles.size(); slot++) {
            int x = left + 24 + (slot % 2) * (cardWidth + 16);
            int y = top + 112 + (slot / 2) * 142;
            if (inside(mouseX, mouseY, x, y, cardWidth, 124)) {
                open(tiles.get(first + slot));
                return true;
            }
        }

        int pages = Math.max(1, (tiles.size() + 3) / 4);
        int center = left + panelWidth / 2;
        if (pages > 1 && inside(mouseX, mouseY, center - 142, top + panelHeight - 52, 118, 24)) {
            page = Math.max(0, page - 1);
            return true;
        }
        if (pages > 1 && inside(mouseX, mouseY, center + 24, top + panelHeight - 52, 118, 24)) {
            page = Math.min(pages - 1, page + 1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void open(ModuleTile tile) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        switch (tile.target()) {
            case GUILDS -> openGuilds(minecraft);
            case ECONOMY -> {
                onClose();
                minecraft.player.connection.sendCommand("ceconomy");
            }
            case COMMAND -> {
                onClose();
                minecraft.player.connection.sendCommand(tile.command().trim());
            }
        }
    }

    private void openGuilds(Minecraft minecraft) {
        try {
            Class<?> screenClass = Class.forName("fr.citizen.guilds.GuildManagementScreen");
            Object instance = screenClass.getConstructor().newInstance();
            if (!(instance instanceof Screen screen))
                throw new IllegalStateException("GuildManagementScreen n'est pas un écran Minecraft.");
            onClose();
            minecraft.setScreen(screen);
        } catch (ReflectiveOperationException | LinkageError exception) {
            CitizenHub.LOGGER.error("Impossible d'ouvrir l'écran natif Citizen Guilds.", exception);
            minecraft.player.displayClientMessage(
                    Component.literal("L'interface Guildes est indisponible. Vérifie Citizen Guilds.")
                            .withColor(0xFFFF7777), false);
        }
    }

    @Override
    public void removed() {
        if (Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(CloseHubPayload.INSTANCE);
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Fond opaque : aucune scène floutée ne transparaît derrière les textes.
        g.fill(0, 0, width, height, BACKGROUND);
        for (int i = 0; i < 34; i++) {
            int sx = Math.floorMod(i * 173 + 41, Math.max(1, width));
            int sy = Math.floorMod(i * 97 + 19, Math.max(1, height));
            g.fill(sx, sy, sx + 1, sy + 1, 0x553C5064);
        }

        g.fill(left + 5, top + 7, left + panelWidth + 5, top + panelHeight + 7, 0x66000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        g.renderOutline(left, top, panelWidth, panelHeight, LINE);
        g.fill(left + 1, top + 1, left + panelWidth - 1, top + 4, GOLD_DARK);
        int pulse = 105 + (int) (45 * (1 + Math.sin(animationTick * 0.07)));
        g.fill(left + 24, top + 1, left + 170, top + 4, (pulse << 24) | 0x00E6BE78);

        g.drawString(font, "C I T I Z E N", left + 28, top + 22, GOLD, false);
        g.drawString(font, "MENU", left + 28, top + 40, WHITE, false);
        g.drawString(font, "LE ROYAUME, À PORTÉE DE MAIN", left + 28, top + 70, MUTED, false);

        drawButton(g, left + panelWidth - 112, top + 22, 84, 26, "FERMER",
                inside(mouseX, mouseY, left + panelWidth - 112, top + 22, 84, 26), false);
        g.fill(left + 24, top + 91, left + panelWidth - 24, top + 92, LINE);

        int cardWidth = (panelWidth - 64) / 2;
        int first = page * 4;
        for (int slot = 0; slot < 4 && first + slot < tiles.size(); slot++) {
            ModuleTile tile = tiles.get(first + slot);
            int x = left + 24 + (slot % 2) * (cardWidth + 16);
            int y = top + 112 + (slot / 2) * 142;
            boolean hovered = inside(mouseX, mouseY, x, y, cardWidth, 124);
            drawCard(g, tile, x, y, cardWidth, hovered);
        }

        if (tiles.isEmpty()) {
            String message = "AUCUN MODULE DISPONIBLE";
            g.drawString(font, message, width / 2 - font.width(message) / 2,
                    top + panelHeight / 2 - 8, MUTED, false);
        }

        int pages = Math.max(1, (tiles.size() + 3) / 4);
        if (pages > 1) {
            int center = left + panelWidth / 2;
            drawButton(g, center - 142, top + panelHeight - 52, 118, 24, "← PRÉCÉDENT",
                    inside(mouseX, mouseY, center - 142, top + panelHeight - 52, 118, 24), page == 0);
            drawButton(g, center + 24, top + panelHeight - 52, 118, 24, "SUIVANT →",
                    inside(mouseX, mouseY, center + 24, top + panelHeight - 52, 118, 24), page >= pages - 1);
            String count = (page + 1) + " / " + pages;
            g.drawString(font, count, center - font.width(count) / 2, top + panelHeight - 43, MUTED, false);
        }
        g.drawString(font, "C  •  OUVRIR CITIZEN MENU", left + 28,
                top + panelHeight - 34, MUTED, false);
    }

    private void drawCard(GuiGraphics g, ModuleTile tile, int x, int y, int w, boolean hovered) {
        int fill = hovered ? CARD_HOVER : CARD;
        g.fill(x, y, x + w, y + 124, fill);
        g.renderOutline(x, y, w, 124, hovered ? tile.accent() : LINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, tile.accent());
        g.fill(x + 15, y + 16, x + 53, y + 54, 0xFF263448);
        g.renderOutline(x + 15, y + 16, 38, 38, tile.accent());
        int emblemWidth = font.width(tile.emblem());
        g.drawString(font, tile.emblem(), x + 34 - emblemWidth / 2, y + 29, tile.accent(), false);
        g.drawString(font, tile.title().toUpperCase(), x + 65, y + 19, WHITE, false);
        g.drawString(font, "MODULE DU ROYAUME", x + 65, y + 35, tile.accent(), false);

        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(tile.description() == null ? "" : tile.description()), w - 32);
        int lineY = y + 65;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), x + 16, lineY, MUTED, false);
            lineY += 12;
        }

        int ctaY = y + 94;
        g.fill(x + 14, ctaY, x + w - 14, ctaY + 20, hovered ? 0xFF3A4655 : 0xFF263342);
        g.renderOutline(x + 14, ctaY, w - 28, 20, hovered ? tile.accent() : LINE);
        String cta = hovered ? "OUVRIR LE MODULE  →" : "ACCÉDER AU MODULE";
        g.drawString(font, cta, x + (w - font.width(cta)) / 2, ctaY + 6,
                hovered ? GOLD : WHITE, false);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h,
                            String label, boolean hovered, boolean disabled) {
        int fill = disabled ? 0xFF171F2A : hovered ? 0xFF344253 : 0xFF222D3A;
        int border = hovered ? GOLD : LINE;
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, border);
        g.drawString(font, label, x + (w - font.width(label)) / 2,
                y + (h - 8) / 2, disabled ? MUTED : WHITE, false);
    }
}
