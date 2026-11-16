package com.engagementtempo.listener;

import com.engagementtempo.state.CombatStateManager;
import com.engagementtempo.tracker.TargetTracker;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;

/**
 * Passive observers. Everything here only READS data the vanilla client already received.
 * Nothing is ever sent to the server.
 */
public final class TempoEvents {
    // Vanilla entity-event ids
    private static final byte EVENT_USE_ITEM_FINISH = 9;
    private static final byte EVENT_SHIELD_BREAK = 30;
    private static final byte EVENT_TOTEM_ACTIVATE = 35;

    private TempoEvents() {}

    /** Fired by NetworkEventMixin for every ClientboundEntityEventPacket (main thread). */
    public static void onEntityEvent(Entity e, byte id) {
        if (!TargetTracker.INSTANCE.isTarget(e)) return;
        long now = System.nanoTime();
        CombatStateManager csm = CombatStateManager.INSTANCE;
        switch (id) {
            case EVENT_SHIELD_BREAK -> csm.onShieldDisabled(now);
            case EVENT_TOTEM_ACTIVATE -> {
                if (e instanceof LivingEntity le) csm.onTotemPop(now, le);
            }
            case EVENT_USE_ITEM_FINISH -> {
                // Called at packet HEAD, so the use-item is still visible.
                if (e instanceof LivingEntity le && le.isUsingItem()
                        && le.getUseItem().is(Items.CHORUS_FRUIT)) {
                    csm.onPearlOrChorusLockout(now);
                }
            }
            default -> { }
        }
    }

    /** Fired for every entity added to the client level (Fabric ClientEntityEvents.ENTITY_LOAD). */
    public static void onEntityLoad(Entity e) {
        if (e.getType() != EntityType.ENDER_PEARL) return;
        if (e.tickCount > 2) return; // ignore pearls that merely streamed in mid-flight
        AbstractClientPlayer target = TargetTracker.INSTANCE.getTarget();
        if (target == null) return;

        boolean ownedByTarget;
        Entity owner = e instanceof Projectile p ? p.getOwner() : null;
        if (owner != null) {
            ownedByTarget = owner.getId() == target.getId();
        } else {
            // Owner not resolved yet: fall back to "spawned right next to the target".
            ownedByTarget = e.distanceToSqr(target) < 9.0;
        }
        if (ownedByTarget) CombatStateManager.INSTANCE.onPearlOrChorusLockout(System.nanoTime());
    }
}
