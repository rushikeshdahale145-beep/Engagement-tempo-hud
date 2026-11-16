package com.engagementtempo.tracker;

import com.engagementtempo.state.CombatStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Smart target locking:
 *  1. Primary: the player you attack.
 *  2. Secondary: the player under your crosshair (<=12 blocks, line of sight) if you haven't hit anyone recently.
 *  3. Auto-decay: cleared after 8s without interaction, or on death / leaving render distance.
 */
public final class TargetTracker {
    public static final TargetTracker INSTANCE = new TargetTracker();

    private static final long DECAY_NS = 8_000_000_000L;
    private static final long HIT_PRIORITY_NS = 3_000_000_000L;
    private static final double CROSSHAIR_RANGE = 12.0;

    private int targetId = -1;
    private long lastInteractNs;
    private long lastHitNs;

    private TargetTracker() {}

    /** Called when the local player attacks another player (Fabric AttackEntityCallback). */
    public void onLocalAttack(AbstractClientPlayer victim) {
        long now = System.nanoTime();
        lock(victim, now);
        lastHitNs = now;
        lastInteractNs = now;
    }

    public AbstractClientPlayer getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (targetId == -1 || mc.level == null) return null;
        return mc.level.getEntity(targetId) instanceof AbstractClientPlayer p ? p : null;
    }

    public boolean isTarget(net.minecraft.world.entity.Entity e) {
        return e != null && targetId != -1 && e.getId() == targetId;
    }

    public void tick(Minecraft mc) {
        long now = System.nanoTime();
        ClientLevel level = mc.level;
        LocalPlayer me = mc.player;
        if (level == null || me == null) {
            clear(now);
            return;
        }

        if (targetId != -1) {
            AbstractClientPlayer cur = getTarget();
            if (cur == null || !valid(cur)) clear(now); // died / left render distance
        }

        AbstractClientPlayer looked = findCrosshairPlayer(level, me);
        if (looked != null) {
            if (targetId == -1) {
                lock(looked, now);
                lastInteractNs = now;
            } else if (looked.getId() == targetId) {
                lastInteractNs = now;
            } else if (now - lastHitNs > HIT_PRIORITY_NS && !CombatStateManager.INSTANCE.isBusy(now)) {
                lock(looked, now);
                lastInteractNs = now;
            }
        }

        if (targetId != -1 && now - lastInteractNs > DECAY_NS) clear(now);
    }

    private void lock(AbstractClientPlayer p, long now) {
        if (p.getId() != targetId) {
            CombatStateManager.INSTANCE.cancelAll(now);
            targetId = p.getId();
        }
    }

    public void clear(long now) {
        if (targetId != -1) {
            targetId = -1;
            CombatStateManager.INSTANCE.cancelAll(now);
        }
    }

    private static boolean valid(AbstractClientPlayer p) {
        return !p.isRemoved() && p.isAlive() && !p.isDeadOrDying() && !p.isSpectator();
    }

    private static AbstractClientPlayer findCrosshairPlayer(ClientLevel level, LocalPlayer me) {
        Vec3 eye = me.getEyePosition();
        Vec3 end = eye.add(me.getViewVector(1.0F).scale(CROSSHAIR_RANGE));
        AbstractClientPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (AbstractClientPlayer p : level.players()) {
            if (p == me || !valid(p)) continue;
            AABB box = p.getBoundingBox().inflate(0.15);
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d >= bestDist) continue;
            // No locking through walls.
            HitResult block = level.clip(new ClipContext(eye, hit.get(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
            if (block.getType() != HitResult.Type.MISS) continue;
            best = p;
            bestDist = d;
        }
        return best;
    }
}
