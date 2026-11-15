package dev.projectvisual.client.gui;

import dev.projectvisual.client.hud.VisualHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClickGuiScreen extends Screen {
    private static final int BG = 0xF00D0F18, PANEL = 0xF0161925, CARD = 0xFF1D2130;
    private static final int HOVER = 0xFF282D40, ACCENT = 0xFF9B7BFF;
    private static final int TEXT = 0xFFF3F0FF, MUTED = 0xFFAAA6BA;
    private static final String[] CATS = {"HUD", "Combat", "Player", "World", "Animations", "Camera", "Interface", "Skins"};
    private static final String[][] ITEMS = {
        {"FPS Counter", "Ping", "CPS Counter", "Keystrokes", "Armor HUD", "Potion HUD", "Target HUD", "Array List"},
        {"Hit Particles", "Damage Tint", "Hit Color", "Crosshair", "Target Info", "Totem Counter"},
        {"China Hat", "Player Trails", "Name Tags", "Item Physics", "Hand Cosmetics", "Cape Effects"},
        {"Fullbright", "Weather", "Time Changer", "Ambience", "Block Overlay", "Breadcrumbs"},
        {"Swing Animation", "Old Animations", "Item Sway", "Eating Animation", "Rod Animation", "Shield Animation"},
        {"View Model", "Camera Clip", "Zoom", "Smooth Camera", "Perspective"},
        {"ClickGUI", "Notifications", "Watermark", "Module List", "Custom Font", "Color Theme"},
        {"Skin Manager", "Skin Preview", "Capes", "Outfits"}
    };
    private static final String[][] DESC = {
        {"Performance counter", "Connection latency", "Clicks per second", "Movement key display", "Equipment durability", "Active potion effects", "Target information", "Enabled module list"},
        {"Particles on hit", "Damage flash", "Custom hit feedback", "Crosshair styles", "Target details", "Totem pop tracking"},
        {"Hat above player", "Colored movement traces", "Custom name labels", "Dropped item rotation", "Hand appearance", "Cape visuals"},
        {"Brighter world", "Weather visuals", "Local world time", "World color mood", "Highlight selected blocks", "Movement path markers"},
        {"Custom hand swing", "Classic item animations", "Subtle item movement", "Food and drink motion", "Fishing rod animation", "Shield raise animation"},
        {"Hand position and scale", "Camera collision options", "Quick zoom control", "Smoother camera motion", "Third-person perspective"},
        {"Main module menu", "On-screen alerts", "Client branding", "Visible enabled modules", "Typography settings", "Accent colors"},
        {"Choose player skins", "Preview before applying", "Cape collection", "Saved appearance presets"}
    };
    private int category, scroll;

    public ClickGuiScreen() { super(Component.literal("PROJECT VISUAL")); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float tick) {
        g.fill(0, 0, width, height, 0x88000000);
        int pw = Math.min(760, width - 32), ph = Math.min(440, height - 32);
        int x = (width - pw) / 2, y = (height - ph) / 2, sw = 158;
        g.fill(x, y, x + pw, y + ph, BG);
        g.fill(x, y, x + pw, y + 3, ACCENT);
        g.fill(x, y, x + sw, y + ph, PANEL);
        g.fill(x + sw, y + 54, x + sw + 1, y + ph - 18, 0x553F3B50);
        g.drawString(font, "PROJECT VISUAL", x + 18, y + 17, TEXT, false);
        g.drawString(font, "VISUAL CLIENT  /  0.2", x + 18, y + 34, MUTED, false);
        g.drawString(font, "MODULES", x + 18, y + 67, ACCENT, false);

        for (int i = 0; i < CATS.length; i++) {
            int cy = y + 88 + i * 34;
            boolean active = i == category;
            if (active || (mx >= x + 10 && mx <= x + sw - 10 && my >= cy - 5 && my <= cy + 22))
                g.fill(x + 10, cy - 5, x + sw - 10, cy + 22, active ? 0x553F3568 : HOVER);
            if (active) g.fill(x + 10, cy - 5, x + 12, cy + 22, ACCENT);
            g.drawString(font, CATS[i], x + 22, cy + 3, active ? TEXT : MUTED, false);
        }

        int cx0 = x + sw + 22, cw = pw - sw - 42, cardW = (cw - 12) / 2;
        g.drawString(font, CATS[category], cx0, y + 20, TEXT, false);
        g.drawString(font, "Visual modules  /  Lightweight client", cx0, y + 37, MUTED, false);
        g.drawString(font, "Choose a module to configure it", cx0, y + 62, MUTED, false);

        String[] names = ITEMS[category], descriptions = DESC[category];
        int rows = Math.max(1, (ph - 126) / 58);
        int start = Math.min(scroll, Math.max(0, names.length - rows * 2));
        for (int n = start; n < Math.min(names.length, start + rows * 2); n++) {
            int col = (n - start) % 2, row = (n - start) / 2;
            int cx = cx0 + col * (cardW + 12), cy = y + 91 + row * 58;
            boolean live = category == 0 && n == 0;
            boolean hover = mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + 48;
            g.fill(cx, cy, cx + cardW, cy + 48, hover ? HOVER : CARD);
            g.fill(cx, cy, cx + 2, cy + 48, live ? ACCENT : 0xFF45495A);
            g.drawString(font, names[n], cx + 11, cy + 8, TEXT, false);
            g.drawString(font, descriptions[n], cx + 11, cy + 24, MUTED, false);
            String state = live ? (VisualHud.isEnabled() ? "ON" : "OFF") : "COMING SOON";
            int color = live && VisualHud.isEnabled() ? 0xFF83E0B0 : ACCENT;
            g.drawString(font, state, cx + cardW - font.width(state) - 9, cy + 8, color, false);
        }
        g.drawString(font, "RIGHT SHIFT  /  CLOSE", x + 18, y + ph - 24, MUTED, false);
        g.drawString(font, "More visuals are being added", cx0, y + ph - 24, ACCENT, false);
        super.render(g, mx, my, tick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int pw = Math.min(760, width - 32), ph = Math.min(440, height - 32);
        int x = (width - pw) / 2, y = (height - ph) / 2, sw = 158;
        for (int i = 0; i < CATS.length; i++) {
            int cy = y + 88 + i * 34;
            if (mx >= x + 10 && mx <= x + sw - 10 && my >= cy - 5 && my <= cy + 22) {
                category = i; scroll = 0; return true;
            }
        }
        if (category == 0) {
            int cx0 = x + sw + 22, cw = pw - sw - 42, cardW = (cw - 12) / 2;
            int cx = cx0, cy = y + 91;
            if (mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + 48) {
                VisualHud.setEnabled(!VisualHud.isEnabled()); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(8, scroll + (dy < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
