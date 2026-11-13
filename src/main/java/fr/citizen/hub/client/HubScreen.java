package fr.citizen.hub.client;

import fr.citizen.hub.CitizenHub;
import fr.citizen.hub.network.CloseHubPayload;
import fr.citizen.hub.network.OpenHubPayload;
import fr.citizen.hub.network.RunEntryPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Le parchemin /citizen : sections à gauche, commandes à droite. */
public class HubScreen extends Screen {
    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(CitizenHub.MODID, "textures/gui/parchemin.png");

    // Palette Citizen : encre brune, rouge sombre, or ancien
    private static final int INK = 0xFF3B2A1A;
    private static final int INK_LIGHT = 0xFF6B5A44;
    private static final int RED = 0xFF7A1A12;
    private static final int GOLD = 0xFFB08D3C;
    private static final int HOVER = 0x307A1A12;
    private static final int SELECTED = 0x457A1A12;

    private static final int ROLL = 22;   // hauteur des rouleaux dans la texture (sur 256)
    private static final int BORDER_X = 24;  // bordure dorée intérieure dans la texture (x, sur 256)
    private static final int BORDER_Y = 10;  // idem, distance depuis le rouleau (y, dans le corps de 212 px)
    private static final int PAD = 6;        // marge de texte à l'intérieur de la bordure
    private static final int SCROLLBAR = 6;  // place réservée à la barre de défilement
    private static final int ROW_H = 20;

    private final OpenHubPayload data;
    private final List<ItemStack> icons = new ArrayList<>();
    private int selected = 0;
    private double scroll = 0;
    private int contentHeight = 0;

    // Géométrie calculée dans init()
    private int px, py, pw, ph, rollH;
    private int innerX, innerY, innerR, innerB;
    private int rightX, rightR, listTop, leftW;

    private record Hit(int x, int y, int w, int h, OpenHubPayload.Entry entry) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();

    public HubScreen(OpenHubPayload data) {
        super(Component.literal(data.titre()));
        this.data = data;
        for (OpenHubPayload.Section s : data.sections()) {
            ResourceLocation id = ResourceLocation.tryParse(s.icone());
            ItemStack stack = id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
            icons.add(stack.isEmpty() ? new ItemStack(Items.PAPER) : stack);
        }
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 420);
        ph = Math.min(height - 12, 260);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        rollH = Math.max(14, Math.round(ph * ROLL / 256f * 1.0f));
        rollH = Math.min(rollH, 22);

        // Les marges suivent la bordure dorée de la texture, quelle que soit la taille du parchemin.
        float sx = pw / 256f;
        float sy = (ph - 2 * rollH) / (float) (256 - 2 * ROLL);
        innerX = px + Math.round(BORDER_X * sx) + PAD;
        innerR = px + pw - Math.round(BORDER_X * sx) - PAD;
        innerY = py + rollH + Math.round(BORDER_Y * sy) + PAD;
        innerB = py + ph - rollH - Math.round(BORDER_Y * sy) - PAD;
        listTop = innerY + 18;
        leftW = Math.max(70, Math.min(112, (innerR - innerX) / 3));
        rightX = innerX + leftW + 10;
        rightR = innerR - SCROLLBAR;
        scroll = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // fond flouté vanilla
        drawParchment(g);

        // Titre + filet doré
        int cx = px + pw / 2;
        String titleTxt = fit(data.titre(), innerR - innerX, true);
        Component title = Component.literal(titleTxt).withStyle(ChatFormatting.BOLD);
        int tw = font.width(title);
        g.drawString(font, title, cx - tw / 2, innerY, RED, false);
        g.fill(Math.max(innerX, cx - tw / 2 - 18), innerY + 11, Math.min(innerR, cx + tw / 2 + 18), innerY + 12, GOLD);
        g.fill(cx - 2, innerY + 10, cx + 2, innerY + 13, RED);

        if (data.sections().isEmpty()) {
            int ny = py + ph / 2 - 5;
            for (FormattedCharSequence line : font.split(
                    Component.literal("Aucun module Citizen n'est disponible pour toi."), innerR - innerX)) {
                g.drawString(font, line, cx - font.width(line) / 2, ny, INK_LIGHT, false);
                ny += 10;
            }
            drawFooter(g);
            return;
        }

        drawSections(g, mouseX, mouseY);
        g.fill(rightX - 6, listTop, rightX - 5, innerB - 12, GOLD); // séparateur
        drawEntries(g, mouseX, mouseY);
        drawFooter(g);
    }

    private void drawParchment(GuiGraphics g) {
        // Rouleau haut, corps étiré, rouleau bas : les rouleaux gardent leur forme quelle que soit la taille.
        int bodyTop = py + rollH;
        int bodyBottom = py + ph - rollH;
        g.blit(BG, px, py, pw, rollH, 0f, 0f, 256, ROLL, 256, 256);
        g.blit(BG, px, bodyTop, pw, bodyBottom - bodyTop, 0f, (float) ROLL, 256, 256 - 2 * ROLL, 256, 256);
        g.blit(BG, px, bodyBottom, pw, rollH, 0f, (float) (256 - ROLL), 256, ROLL, 256, 256);
    }

    private void drawSections(GuiGraphics g, int mouseX, int mouseY) {
        int y = listTop;
        for (int i = 0; i < data.sections().size(); i++) {
            if (y + ROW_H > innerB - 12) break;
            OpenHubPayload.Section s = data.sections().get(i);
            boolean hover = mouseX >= innerX && mouseX < innerX + leftW && mouseY >= y && mouseY < y + ROW_H;
            if (i == selected) g.fill(innerX - 2, y, innerX + leftW, y + ROW_H, SELECTED);
            else if (hover) g.fill(innerX - 2, y, innerX + leftW, y + ROW_H, HOVER);

            g.renderItem(icons.get(i), innerX, y + 2);
            String name = fit(s.nom(), leftW - 22, false);
            g.drawString(font, name, innerX + 19, y + 6, i == selected ? RED : INK, false);
            y += ROW_H;
        }
    }

    private void drawEntries(GuiGraphics g, int mouseX, int mouseY) {
        hits.clear();
        if (selected >= data.sections().size()) return;
        OpenHubPayload.Section s = data.sections().get(selected);
        int w = rightR - rightX;
        int top = listTop;
        int bottom = innerB - 12;

        g.enableScissor(rightX - 3, top, rightR, bottom);
        int y = top - (int) scroll;

        // En-tête de section
        int staffW = s.admin() ? font.width("[Staff]") + 6 : 0;
        List<FormattedCharSequence> headLines = font.split(
                Component.literal(s.nom()).withStyle(ChatFormatting.BOLD), w - staffW);
        for (int i = 0; i < headLines.size(); i++) {
            g.drawString(font, headLines.get(i), rightX, y, RED, false);
            if (i == 0 && s.admin()) g.drawString(font, "[Staff]", rightR - staffW + 6, y, GOLD, false);
            y += 10;
        }
        y += 2;
        if (!s.description().isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.literal(s.description()), w)) {
                g.drawString(font, line, rightX, y, INK_LIGHT, false);
                y += 10;
            }
        }
        y += 4;

        if (s.entrees().isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.literal(
                    "Ce module n'a pas de commande : il se découvre en jeu.").withStyle(ChatFormatting.ITALIC), w)) {
                g.drawString(font, line, rightX, y, INK_LIGHT, false);
                y += 10;
            }
            y += 2;
        }

        for (OpenHubPayload.Entry e : s.entrees()) {
            // Étiquettes à droite du nom ([Admin], ✎) : on réserve leur place sur la première ligne.
            int tagsW = (e.admin() ? font.width("[Admin]") + 4 : 0) + (e.suggerer() ? font.width("✎") + 4 : 0);
            List<FormattedCharSequence> nameLines = font.split(
                    Component.literal("❖ " + e.nom()).withStyle(ChatFormatting.BOLD), Math.max(20, w - tagsW - 2));
            List<FormattedCharSequence> desc = e.description().isEmpty()
                    ? List.of() : font.split(Component.literal(e.description()), w - 10);
            int nameH = nameLines.size() * 10;
            int h = nameH + 12 + desc.size() * 10 + 2;
            boolean visible = y + h > top && y < bottom;
            boolean hover = visible && mouseX >= rightX - 3 && mouseX < rightR && mouseY >= Math.max(y, top)
                    && mouseY < Math.min(y + h, bottom);

            if (hover) g.fill(rightX - 3, y - 2, rightR, y + h - 2, HOVER);

            int ny = y;
            for (FormattedCharSequence line : nameLines) {
                g.drawString(font, line, rightX, ny, hover ? RED : INK, false);
                ny += 10;
            }
            int tagX = rightX + font.width(nameLines.get(0)) + 4;
            if (e.admin()) {
                g.drawString(font, "[Admin]", tagX, y, RED, false);
                tagX += font.width("[Admin]") + 4;
            }
            if (e.suggerer()) {
                g.drawString(font, "✎", tagX, y, GOLD, false);
            }
            g.drawString(font, fit("/" + e.commande().trim(), w - 10, false), rightX + 10, y + nameH + 1, GOLD, false);
            int dy = y + nameH + 12;
            for (FormattedCharSequence line : desc) {
                g.drawString(font, line, rightX + 10, dy, INK_LIGHT, false);
                dy += 10;
            }
            if (visible) hits.add(new Hit(rightX - 3, Math.max(y - 2, top), rightR - rightX + 3,
                    Math.min(y + h - 2, bottom) - Math.max(y - 2, top), e));
            y += h + 4;
        }
        g.disableScissor();

        contentHeight = (int) (y + scroll) - top;
        int max = Math.max(0, contentHeight - (bottom - top));
        if (max > 0) { // petite barre de défilement dorée
            int barH = Math.max(12, (bottom - top) * (bottom - top) / contentHeight);
            int barY = top + (int) ((bottom - top - barH) * (scroll / max));
            g.fill(innerR - 2, top, innerR - 1, bottom, 0x40B08D3C);
            g.fill(innerR - 3, barY, innerR, barY + barH, GOLD);
        }
    }

    private void drawFooter(GuiGraphics g) {
        Component hint = Component.literal("Clique sur une commande • ✎ = à compléter dans le chat • Échap pour ranger")
                .withStyle(ChatFormatting.ITALIC);
        String txt = fit(hint.getString(), innerR - innerX, false);
        g.drawString(font, Component.literal(txt).withStyle(ChatFormatting.ITALIC),
                px + pw / 2 - font.width(txt) / 2, innerB - 8, INK_LIGHT, false);
    }

    /** Coupe un texte trop long avec « … » pour qu'il ne sorte jamais du parchemin. */
    private String fit(String text, int maxWidth, boolean bold) {
        Component c = bold ? Component.literal(text).withStyle(ChatFormatting.BOLD) : Component.literal(text);
        if (font.width(c) <= maxWidth) return text;
        String cut = text;
        while (!cut.isEmpty()) {
            cut = cut.substring(0, cut.length() - 1);
            Component t = Component.literal(cut + "…");
            if (bold) t = Component.literal(cut + "…").withStyle(ChatFormatting.BOLD);
            if (font.width(t) <= maxWidth) return cut + "…";
        }
        return "";
    }

    // ------------------------------------------------------------------ interactions

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = listTop;
            for (int i = 0; i < data.sections().size(); i++) {
                if (y + ROW_H > innerB - 12) break;
                if (mouseX >= innerX && mouseX < innerX + leftW && mouseY >= y && mouseY < y + ROW_H) {
                    if (i != selected) {
                        selected = i;
                        scroll = 0;
                        playPage();
                    }
                    return true;
                }
                y += ROW_H;
            }
            for (Hit h : hits) {
                if (h.contains(mouseX, mouseY)) {
                    use(h.entry());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void use(OpenHubPayload.Entry e) {
        playPage();
        if (e.suggerer()) {
            // Le joueur complète les arguments lui-même dans le chat.
            String cmd = e.commande();
            if (!cmd.endsWith(" ")) cmd = cmd + " ";
            minecraft.setScreen(new ChatScreen("/" + cmd));
        } else {
            PacketDistributor.sendToServer(new RunEntryPayload(e.s(), e.e()));
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = (innerB - 12) - listTop;
        int max = Math.max(0, contentHeight - visible);
        scroll = Mth.clamp(scroll - scrollY * 14, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playPage() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
    }

    /** Appelé dès que l'écran disparaît (Échap, autre menu, chat…) : on range le parchemin. */
    @Override
    public void removed() {
        super.removed();
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(CloseHubPayload.INSTANCE);
        }
    }
}
