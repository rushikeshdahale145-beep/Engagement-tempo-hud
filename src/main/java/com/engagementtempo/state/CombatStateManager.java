package com.engagementtempo.state;

import com.engagementtempo.config.TempoConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Holds every timer / state machine for the ACTIVE target. Client-thread only.
 * All times are System.nanoTime() based so countdowns are smooth (not tick-quantised).
 */
public final class CombatStateManager {
    public static final CombatStateManager INSTANCE = new CombatStateManager();

    private static final long MS = 1_000_000L;
    public static final long SHIELD_NS = 5_000 * MS;
    public static final long PEARL_NS = 1_000 * MS;
    public static final long TOTEM_RESULT_NS = 2_000 * MS;
    public static final long NO_TOTEM_AFTER_NS = 2_500 * MS;
    public static final long NO_TOTEM_SHOW_NS = 1_500 * MS;
    public static final long FADE_IN_NS = 100 * MS;
    public static final long FADE_OUT_NS = 200 * MS;

    /** A countdown with built-in fade-in (100ms) / fade-out (200ms). */
    public static final class Timer {
        private final long start;
        private final long duration;
        private long end;

        Timer(long start, long duration) {
            this.start = start;
            this.duration = duration;
            this.end = start + duration;
        }

        /** 1.0 at start -> 0.0 at end. */
        public float remaining(long now) {
            return clamp01((end - now) / (float) duration);
        }

        public float alpha(long now) {
            float in = clamp01((now - start) / (float) FADE_IN_NS);
            float out = now <= end ? 1f : clamp01(1f - (now - end) / (float) FADE_OUT_NS);
            return Math.min(in, out);
        }

        public boolean finished(long now) {
            return now > end + FADE_OUT_NS;
        }

        void cancel(long now) {
            if (now < end) end = now;
        }

        private static float clamp01(float v) {
            return v < 0f ? 0f : Math.min(v, 1f);
        }
    }

    public enum TotemPhase { IDLE, MEASURING, RESULT, NO_TOTEM }

    private Timer shield;
    private Timer pearl;
    private Timer totemDisplay;

    private TotemPhase totemPhase = TotemPhase.IDLE;
    private float totemSeconds;

    // Totem stopwatch internals
    private long popNs;
    private EquipmentSlot consumedSlot = EquipmentSlot.OFFHAND;
    private int consumedCountAtPop;
    private boolean armed;

    private CombatStateManager() {}

    // ---- accessors for the renderer ----
    public Timer shield() { return shield; }
    public Timer pearl() { return pearl; }
    public Timer totemDisplay() { return totemDisplay; }
    public TotemPhase totemPhase() { return totemPhase; }
    public float totemSeconds() { return totemSeconds; }

    /** True while something is running; used to stop crosshair-lock from stealing the target mid-window. */
    public boolean isBusy(long now) {
        return (shield != null && !shield.finished(now))
                || (pearl != null && !pearl.finished(now))
                || totemPhase == TotemPhase.MEASURING;
    }

    // ---- event entry points (called from TempoEvents) ----
    public void onShieldDisabled(long now) {
        if (!TempoConfig.get().shield) return;
        shield = new Timer(now, SHIELD_NS);
    }

    public void onPearlOrChorusLockout(long now) {
        if (!TempoConfig.get().pearl) return;
        pearl = new Timer(now, PEARL_NS);
    }

    public void onTotemPop(long now, LivingEntity target) {
        if (!TempoConfig.get().totem) return;
        ItemStack main = target.getMainHandItem();
        ItemStack off = target.getOffhandItem();
        // Vanilla consumes the main-hand totem first, then the off-hand one.
        if (main.is(Items.TOTEM_OF_UNDYING)) {
            consumedSlot = EquipmentSlot.MAINHAND;
            consumedCountAtPop = main.getCount();
        } else if (off.is(Items.TOTEM_OF_UNDYING)) {
            consumedSlot = EquipmentSlot.OFFHAND;
            consumedCountAtPop = off.getCount();
        } else {
            consumedCountAtPop = 0; // equipment already updated -> armed immediately
        }
        popNs = now; // stopwatch starts the exact tick the pop packet is processed
        armed = consumedCountAtPop == 0;
        totemPhase = TotemPhase.MEASURING;
        totemDisplay = null;
    }

    /** Per-tick maintenance. {@code target} may be null. */
    public void tick(long now, LivingEntity target) {
        if (shield != null && shield.finished(now)) shield = null;
        if (pearl != null && pearl.finished(now)) pearl = null;
        if (totemDisplay != null && totemDisplay.finished(now)) {
            totemDisplay = null;
            totemPhase = TotemPhase.IDLE;
        }

        if (totemPhase != TotemPhase.MEASURING) return;
        if (target == null) {
            totemPhase = TotemPhase.IDLE;
            return;
        }

        ItemStack main = target.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = target.getItemBySlot(EquipmentSlot.OFFHAND);
        boolean hasTotem = main.is(Items.TOTEM_OF_UNDYING) || off.is(Items.TOTEM_OF_UNDYING);

        if (!armed) {
            ItemStack consumed = target.getItemBySlot(consumedSlot);
            if (!consumed.is(Items.TOTEM_OF_UNDYING) || consumed.getCount() < consumedCountAtPop) {
                armed = true; // the popped totem is visibly gone from the hand
            }
        }

        if (armed && hasTotem) {
            totemSeconds = (now - popNs) / 1_000_000_000f;
            totemPhase = TotemPhase.RESULT;
            totemDisplay = new Timer(now, TOTEM_RESULT_NS);
        } else if (now - popNs >= NO_TOTEM_AFTER_NS) {
            totemPhase = TotemPhase.NO_TOTEM;
            totemDisplay = new Timer(now, NO_TOTEM_SHOW_NS);
        }
    }

    /** Cancels everything (target died / switched / left). Timers fade out over 200ms. */
    public void cancelAll(long now) {
        if (shield != null) shield.cancel(now);
        if (pearl != null) pearl.cancel(now);
        if (totemDisplay != null) totemDisplay.cancel(now);
        if (totemPhase == TotemPhase.MEASURING) totemPhase = TotemPhase.IDLE;
    }

    /** Hard reset (disconnect). */
    public void reset() {
        shield = null;
        pearl = null;
        totemDisplay = null;
        totemPhase = TotemPhase.IDLE;
    }

    /** Soft green/white = slow (>0.5s), amber = fast (0.25-0.5s), soft red = instant (<0.25s). */
    public static int totemColor(float seconds) {
        if (seconds < 0.25f) return 0xFF6B6B;
        if (seconds <= 0.5f) return 0xFFC247;
        return 0xBFF5C8;
    }
}
