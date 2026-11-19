package fr.citizen.hub.client;

import fr.citizen.hub.CitizenHub;
import fr.citizen.hub.network.CloseHubPayload;
import fr.citizen.hub.network.OpenHubPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Tableau de bord CITIZEN MENU.
 *
 * Les modules sont construits depuis la liste déjà filtrée par le serveur.
 * Les intégrations qui possèdent un écran natif l'ouvrent directement ; les
 * autres modules utilisent leur première commande d'ouverture exécutable,
 * sans écran intermédiaire de commandes.
 */
public final class DashboardScreen extends Screen {
    private static final int BACKGROUND = 0xF0090D16;
    private static final int PANEL = 0xFF151C28;
    private static final int PANEL_HOVER = 0xFF202B3B;
    private static final int GOLD = 0xFFE3BD73;
    private static final int MUTED = 0xFF9AA8BC;
    private static final int WHITE = 0xFFF4F1E9;
    private static final int LINE = 0xFF344052;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private int animationTick;
    private List<ModuleTile> tiles = List.of();
    private final OpenHubPayload payload;

    private enum Target { GUILDS, ECONOMY, COMMAND }
    private record ModuleTile(String title, String description, String command, Target target) {}

    public DashboardScreen(OpenHubPayload payload) {
        super(Component.literal("CITIZEN MENU"));
        this.payload = payload;
    }

    @Override
    public void tick() {
        super.tick();
        animationTick++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(760, width - 32);
        panelHeight = Math.min(500, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        tiles = collectTiles();
        int pageCount = Math.max(1, (tiles.size() + 3) / 4);
        page = Math.max(0, Math.min(page, pageCount - 1));

        addRenderableWidget(Button.builder(Component.literal("FERMER"), button -> onClose())
                .bounds(left + panelWidth - 112, top + 22, 84, 24).build());

        int first = page * 4;
        int cardWidth = Math.max(100, (panelWidth - 64) / 2);
        for (int slot = 0; slot < 4 && first + slot < tiles.size(); slot++) {
            ModuleTile tile = tiles.get(first + slot);
            int column = slot % 2;
            int row = slot / 2;
            int x = left + 24 + column * (cardWidth + 16);
            int y = top + 112 + row * 142;
            addRenderableWidget(Button.builder(Component.literal("OUVRIR  →"), button -> open(tile))
                    .bounds(x + 14, y + 82, cardWidth - 28, 26).build());
        }

        if (pageCount > 1) {
            int center = left + panelWidth / 2;
            addRenderableWidget(Button.builder(Component.literal("← PRÉCÉDENT"), button -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(center - 142, top + panelHeight - 52, 118, 24).build());
            addRenderableWidget(Button.builder(Component.literal("SUIVANT →"), button -> {
                page = Math.min(pageCount - 1, page + 1);
                rebuildWidgets();
            }).bounds(center + 24, top + panelHeight - 52, 118, 24).build());
        }
    }

    private List<ModuleTile> collectTiles() {
        List<ModuleTile> result = new ArrayList<>();
        for (OpenHubPayload.Section section : payload.sections()) {
            if (section.admin() || section.nom().equalsIgnoreCase("CitizenAura")) continue;

            String name = section.nom();
            String modId = payloadModId(name);
            if (!modId.isEmpty() && !ModList.get().isLoaded(modId)) continue;

            if (name.equalsIgnoreCase("Guildes") && ModList.get().isLoaded("citizenguilds")) {
                result.add(new ModuleTile(name, section.description(),
                        "", Target.GUILDS));
                continue;
            }
            if ((name.equalsIgnoreCase("Économie") || name.equalsIgnoreCase("Economie"))
                    && ModList.get().isLoaded("citizeneconomy")) {
                result.add(new ModuleTile(name, section.description(),
                        "ceconomy", Target.ECONOMY));
                continue;
            }

            // Liste blanche des écrans réellement vérifiés dans les dépôts Citizen.
            // Une commande d'information (Quêtes, Réalisme, Éclipse, etc.) ne devient
            // jamais une fausse tuile de menu.
            if (name.equalsIgnoreCase("Livre des recettes")) {
                OpenHubPayload.Entry craft = section.entrees().stream()
                        .filter(candidate -> !candidate.admin() && !candidate.suggerer()
                                && candidate.commande().equalsIgnoreCase("craft"))
                        .findFirst().orElse(null);
                if (craft != null) {
                    result.add(new ModuleTile(name, section.description(), "craft", Target.COMMAND));
                }
            } else if (name.equalsIgnoreCase("Boutique") && ModList.get().isLoaded("citizenboutique")) {
                OpenHubPayload.Entry shop = section.entrees().stream()
                        .filter(candidate -> !candidate.admin() && !candidate.suggerer()
                                && candidate.commande().equalsIgnoreCase("boutique"))
                        .findFirst().orElse(null);
                if (shop != null) {
                    result.add(new ModuleTile(name, section.description(), "boutique", Target.COMMAND));
                }
            }
        }
        return result;
    }

    /**
     * Le modId est transmis indirectement par le serveur dans la section historique.
     * Les intégrations natives connues sont contrôlées explicitement ci-dessus ; les
     * modules à commande restent utilisables même si leur code client est facultatif.
     */
    private String payloadModId(String name) {
        return switch (name.toLowerCase()) {
            case "guildes" -> "citizenguilds";
            case "économie", "economie" -> "citizeneconomy";
            case "boutique" -> "citizenboutique";
            default -> "";
        };
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
            // Classe et constructeur vérifiés dans le dépôt Citizen Guilds :
            // fr.citizen.guilds.GuildManagementScreen() est public et sans argument.
            Class<?> screenClass = Class.forName("fr.citizen.guilds.GuildManagementScreen");
            Object instance = screenClass.getConstructor().newInstance();
            if (!(instance instanceof Screen screen)) {
                throw new IllegalStateException("GuildManagementScreen n'est pas un écran Minecraft.");
            }
            onClose();
            minecraft.setScreen(screen);
        } catch (ReflectiveOperationException | LinkageError exception) {
            CitizenHub.LOGGER.error("Impossible d'ouvrir l'écran natif Citizen Guilds.", exception);
            minecraft.player.displayClientMessage(
                    Component.literal("L'interface Guildes est indisponible. Vérifie que Citizen Guilds est installé sur le client.")
                            .withColor(0xFFFF7777), false);
        }
    }

    @Override
    public void removed() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(CloseHubPayload.INSTANCE);
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA8000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, BACKGROUND);
        graphics.renderOutline(left, top, panelWidth, panelHeight, LINE);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 4, GOLD);
        int glowAlpha = 90 + (int) (45.0 * (1.0 + Math.sin(animationTick * 0.08)));
        graphics.fill(left + panelWidth - 132, top + 1, left + panelWidth - 28, top + 4,
                (glowAlpha << 24) | 0x00E3BD73);

        graphics.drawString(font, "CITIZEN", left + 28, top + 22, GOLD, false);
        graphics.drawString(font, "MENU", left + 28, top + 40, WHITE, false);
        graphics.drawString(font, "LE ROYAUME, À PORTÉE DE MAIN", left + 28, top + 70, MUTED, false);
        graphics.fill(left + 24, top + 91, left + panelWidth - 24, top + 92, LINE);

        int cardWidth = Math.max(100, (panelWidth - 64) / 2);
        int first = page * 4;
        for (int slot = 0; slot < 4 && first + slot < tiles.size(); slot++) {
            ModuleTile tile = tiles.get(first + slot);
            int column = slot % 2;
            int row = slot / 2;
            int x = left + 24 + column * (cardWidth + 16);
            int y = top + 112 + row * 142;
            boolean hovered = mouseX >= x && mouseX < x + cardWidth
                    && mouseY >= y && mouseY < y + 116;
            drawCard(graphics, tile, x, y, cardWidth, hovered);
        }

        if (tiles.isEmpty()) {
            String message = "Aucun module joueur avec une interface disponible.";
            graphics.drawString(font, message, width / 2 - font.width(message) / 2,
                    top + panelHeight / 2 - 8, MUTED, false);
            String hint = "Les modules sans point d'ouverture natif restent masqués.";
            graphics.drawString(font, hint, width / 2 - font.width(hint) / 2,
                    top + panelHeight / 2 + 10, MUTED, false);
        }

        int pageCount = Math.max(1, (tiles.size() + 3) / 4);
        if (pageCount > 1) {
            String pageText = (page + 1) + " / " + pageCount;
            graphics.drawString(font, pageText, left + panelWidth / 2 - font.width(pageText) / 2,
                    top + panelHeight - 43, MUTED, false);
        }
        graphics.drawString(font, "N  •  OUVRIR CITIZEN MENU", left + 28,
                top + panelHeight - 34, MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics graphics, ModuleTile tile, int x, int y, int w, boolean hovered) {
        int cardColor = hovered ? PANEL_HOVER : PANEL;
        graphics.fill(x, y, x + w, y + 116, cardColor);
        graphics.renderOutline(x, y, w, 116, hovered ? GOLD : LINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, GOLD);
        graphics.drawString(font, tile.title().toUpperCase(), x + 16, y + 16, GOLD, false);

        int lineY = y + 40;
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(tile.description() == null ? "" : tile.description()), w - 32);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x + 16, lineY, WHITE, false);
            lineY += 13;
        }
        graphics.drawString(font, "MODULE CITIZEN  •  ACCÈS DIRECT", x + 16, y + 68, MUTED, false);
    }
}
