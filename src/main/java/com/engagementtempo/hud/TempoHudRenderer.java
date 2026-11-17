package com.engagementtempo.hud;

import com.engagementtempo.config.TempoConfig;
import com.engagementtempo.state.CombatStateManager;
import com.engagementtempo.state.CombatStateManager.Timer;
import com.engagementtempo.state.CombatStateManager.TotemPhase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Micro-HUD: draws NOTHING unless a timer is live. Max 2 rows, 12x12 icon + 2px bar / number.
 * All GuiGraphicsExtractor calls are isolated in the tiny "gfx" helpers at the bottom, so if a
 * method name differs in your exact 26.2 build you only have to fix it in one place.
 */
public final class TempoHudRenderer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("engagement_tempo", "tempo_hud");

    // Item stacks must NOT be built in a static initializer: this class is loaded during mod
    // init, before item components are bound ("Components not bound yet"). Build them lazily
    // on first render instead.
    private static ItemStack shieldStack;
    private static ItemStack pearlStack;
    private static ItemStack totemStack;

    private static ItemStack shieldIcon() {
        if (shieldStack == null) shieldStack = new ItemStack(Items.SHIELD);
        return shieldStack;
    }

    private static ItemStack pearlIcon() {
        if (pearlStack == null) pearlStack = new ItemStack(Items.ENDER_PEARL);
        return pearlStack;
    }

    private static ItemStack totemIcon() {
        if (totemStack == null) totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);
        return totemStack;
    }

    private static final int ICON = 12;
    private static final int ROW_GAP = 4;
    private static final int BAR_X = 16;
    private static final int BAR_W = 44;
    private static final int BAR_H = 2;

    private static final int SHIELD_RGB = 0x6EC8FF;
    private static final int PEARL_RGB = 0xB48CFF;
    private static final int RED_RGB = 0xFF6B6B;

    private static final long PREVIEW_MAX_NS = 60_000_000_000L;
    private static long previewStartNs = 0;

    private record Row(ItemStack icon, float fill, String text, int rgb, float alpha) {}

    private TempoHudRenderer() {}

    public static boolean togglePreview() {
        previewStartNs = previewStartNs == 0 ? System.nanoTime() : 0;
        return previewStartNs != 0;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.nanoTime();
        if (previewStartNs != 0 && now - previewStartNs > PREVIEW_MAX_NS) previewStartNs = 0;

        List<Row> rows = previewStartNs != 0 ? previewRows(now) : liveRows(now);
        if (rows.isEmpty()) return; // 100% invisible at rest
        if (rows.size() > 2) rows = rows.subList(0, 2);

        TempoConfig cfg = TempoConfig.get();
        float cx = gfxWidth(g) / 2f + cfg.offsetX;
        float cy = gfxHeight(g) / 2f + cfg.offsetY;

        gfxPush(g, cx, cy, cfg.scale);
        for (int i = 0; i < rows.size(); i++) {
            drawRow(g, mc.font, rows.get(i), i * (ICON + ROW_GAP));
        }
        gfxPop(g);
    }

    // ---------------- row building ----------------

    private static List<Row> liveRows(long now) {
        TempoConfig cfg = TempoConfig.get();
        CombatStateManager csm = CombatStateManager.INSTANCE;
        List<Row> rows = new ArrayList<>(3);

        Timer s = csm.shield();
        if (cfg.shield && s != null && !s.finished(now)) {
            rows.add(new Row(shieldIcon(), s.remaining(now), null, SHIELD_RGB, s.alpha(now)));
        }
        Timer p = csm.pearl();
        if (cfg.pearl && p != null && !p.finished(now)) {
            rows.add(new Row(pearlIcon(), p.remaining(now), null, PEARL_RGB, p.alpha(now)));
        }
        Timer t = csm.totemDisplay();
        if (cfg.totem && t != null && !t.finished(now)) {
            if (csm.totemPhase() == TotemPhase.RESULT) {
                String txt = String.format(Locale.ROOT, "%.2fs", csm.totemSeconds());
                rows.add(new Row(totemIcon(), -1f, txt, CombatStateManager.totemColor(csm.totemSeconds()), t.alpha(now)));
            } else if (csm.totemPhase() == TotemPhase.NO_TOTEM) {
                rows.add(new Row(totemIcon(), -1f, "NO TOTEM", RED_RGB, t.alpha(now)));
            }
        }
        return rows;
    }

    /** Looping dummy timers so offset/scale can be tuned without an opponent. */
    private static List<Row> previewRows(long now) {
        double t = (now - previewStartNs) / 1_000_000_000.0;
        List<Row> rows = new ArrayList<>(2);
        rows.add(new Row(shieldIcon(), (float) (1.0 - (t % 5.0) / 5.0), null, SHIELD_RGB, 1f));
        if (((long) (t / 3.0)) % 2 == 0) {
            rows.add(new Row(pearlIcon(), (float) (1.0 - (t % 1.0)), null, PEARL_RGB, 1f));
        } else {
            rows.add(new Row(totemIcon(), -1f, "0.40s", CombatStateManager.totemColor(0.40f), 1f));
        }
        return rows;
    }

    // ---------------- drawing ----------------

    private static void drawRow(GuiGraphicsExtractor g, Font font, Row r, int y) {
        if (r.alpha() <= 0.02f) return;
        // Item icons can't be alpha-blended through the extractor, so show them once mostly faded in.
        if (r.alpha() >= 0.35f) gfxItem(g, r.icon(), 0, y);

        int a = Math.max(5, Math.min(255, (int) (r.alpha() * 255f)));
        if (r.fill() >= 0f) {
            int by = y + (ICON - BAR_H) / 2;
            gfxFill(g, BAR_X, by, BAR_X + BAR_W, by + BAR_H, argb((int) (a * 0.30f), 0xFFFFFF));
            int w = Math.max(1, Math.round(BAR_W * r.fill()));
            if (r.fill() > 0f) gfxFill(g, BAR_X, by, BAR_X + w, by + BAR_H, argb(a, r.rgb()));
        } else if (r.text() != null) {
            gfxText(g, font, r.text(), BAR_X, y + 2, argb(a, r.rgb()));
        }
    }

    private static int argb(int a, int rgb) {
        return (Math.max(5, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    // ---------- gfx helpers: the ONLY place that touches GuiGraphicsExtractor ----------

    private static int gfxWidth(GuiGraphicsExtractor g) { return g.guiWidth(); }

    private static int gfxHeight(GuiGraphicsExtractor g) { return g.guiHeight(); }

    private static void gfxPush(GuiGraphicsExtractor g, float x, float y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
    }

    private static void gfxPop(GuiGraphicsExtractor g) { g.pose().popMatrix(); }

    private static void gfxFill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    /** Draws a 16px item scaled to 12x12 at (x, y). */
    private static void gfxItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(ICON / 16f, ICON / 16f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    private static void gfxText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int argb) {
        g.text(font, text, x, y, argb, true);
    }
}
