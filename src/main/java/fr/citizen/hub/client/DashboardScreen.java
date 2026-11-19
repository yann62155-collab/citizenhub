package fr.citizen.hub.client;

import fr.citizen.hub.CitizenHub;
import fr.citizen.hub.network.CloseHubPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tableau de bord CITIZEN MENU.
 * Les cartes sont affichées uniquement si le module correspondant est présent côté client.
 * Chaque action ouvre le véritable écran du module cible.
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

    public DashboardScreen() {
        super(Component.literal("CITIZEN MENU"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(720, width - 32);
        panelHeight = Math.min(430, height - 32);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        int cardWidth = Math.min(290, (panelWidth - 72) / 2);
        int gap = 18;
        int firstX = left + (panelWidth - (cardWidth * 2 + gap)) / 2;
        int firstY = top + 112;

        boolean hasGuilds = ModList.get().isLoaded("citizenguilds");
        boolean hasEconomy = ModList.get().isLoaded("citizeneconomy");

        if (hasGuilds) {
            addRenderableWidget(Button.builder(Component.literal("OUVRIR LES GUILDES  →"),
                    button -> openGuilds())
                    .bounds(firstX, firstY + 78, cardWidth, 28).build());
        }
        if (hasEconomy) {
            int x = firstX + (hasGuilds ? cardWidth + gap : 0);
            addRenderableWidget(Button.builder(Component.literal("OUVRIR L'ÉCONOMIE  →"),
                    button -> openEconomy())
                    .bounds(x, firstY + 78, cardWidth, 28).build());
        }

        addRenderableWidget(Button.builder(Component.literal("FERMER"),
                button -> onClose())
                .bounds(left + panelWidth - 104, top + panelHeight - 42, 84, 24).build());
    }

    @Override
    public void removed() {
        // Le serveur suit l'état de lecture du parchemin pour les animations des joueurs proches.
        // On ferme proprement cet état même lorsqu'on ouvre directement un autre écran Citizen.
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(CloseHubPayload.INSTANCE);
        }
        super.removed();
    }

    private void openEconomy() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        onClose();
        // /ceconomy est le point d'entrée officiel qui ouvre la vue "main" native
        // de Citizen Economy. Les permissions restent vérifiées par ce mod côté serveur.
        minecraft.player.connection.sendCommand("ceconomy");
    }

    private void openGuilds() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            // Pas de dépendance de compilation : Citizen MENU reste installable seul.
            // Le constructeur public de GuildManagementScreen ouvre l'interface native
            // et demande immédiatement son snapshot au serveur.
            Class<?> screenClass = Class.forName("fr.citizen.guilds.GuildManagementScreen");
            Object instance = screenClass.getConstructor().newInstance();
            if (instance instanceof Screen screen) {
                minecraft.setScreen(screen);
            } else {
                throw new IllegalStateException("L'écran Guilds n'est pas un écran Minecraft.");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            CitizenHub.LOGGER.error("Impossible d'ouvrir l'écran natif Citizen Guilds.", exception);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("L'interface Guildes n'a pas pu être ouverte. Vérifie la version du mod.")
                                .withColor(0xFFFF7777), false);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA8000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, BACKGROUND);
        graphics.renderOutline(left, top, panelWidth, panelHeight, LINE);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 4, GOLD);

        graphics.drawString(font, "CITIZEN", left + 28, top + 22, GOLD, false);
        graphics.drawString(font, "MENU", left + 28, top + 40, WHITE, false);
        graphics.drawString(font, "LE ROYAUME, À PORTÉE DE MAIN", left + 28, top + 70, MUTED, false);
        graphics.fill(left + 28, top + 91, left + panelWidth - 28, top + 92, LINE);

        int cardWidth = Math.min(290, (panelWidth - 72) / 2);
        int gap = 18;
        boolean hasGuilds = ModList.get().isLoaded("citizenguilds");
        boolean hasEconomy = ModList.get().isLoaded("citizeneconomy");
        int visibleCount = (hasGuilds ? 1 : 0) + (hasEconomy ? 1 : 0);
        int cardsWidth = visibleCount * cardWidth + Math.max(0, visibleCount - 1) * gap;
        int firstX = left + (panelWidth - cardsWidth) / 2;
        int firstY = top + 112;

        int cardIndex = 0;
        if (hasGuilds) {
            drawCard(graphics, firstX + cardIndex++ * (cardWidth + gap), firstY, cardWidth, 116,
                    "GUILDES", "Gérez votre guilde, vos membres et vos donjons.",
                    "INTERFACE NATIF CITIZEN GUILDS", mouseX, mouseY);
        }
        if (hasEconomy) {
            drawCard(graphics, firstX + cardIndex * (cardWidth + gap), firstY, cardWidth, 116,
                    "ÉCONOMIE", "Banque, entreprises, factures et annonces.",
                    "INTERFACE NATIF CITIZEN ECONOMY", mouseX, mouseY);
        }

        if (visibleCount == 0) {
            String message = "Aucun module compatible n'est installé.";
            graphics.drawString(font, message, width / 2 - font.width(message) / 2,
                    top + panelHeight / 2, MUTED, false);
        }

        graphics.drawString(font, "N  •  OUVRIR CITIZEN MENU", left + 28,
                top + panelHeight - 34, MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int w, int h,
                          String title, String description, String footer,
                          int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hovered ? PANEL_HOVER : PANEL);
        graphics.renderOutline(x, y, w, h, hovered ? GOLD : LINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, GOLD);
        graphics.drawString(font, title, x + 16, y + 16, GOLD, false);

        int lineY = y + 39;
        for (var line : font.split(Component.literal(description), w - 32)) {
            graphics.drawString(font, line, x + 16, lineY, WHITE, false);
            lineY += 12;
        }
        graphics.drawString(font, footer, x + 16, y + h - 18, MUTED, false);
    }
}
