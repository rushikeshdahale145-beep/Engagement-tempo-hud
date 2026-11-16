package com.engagementtempo;

import com.engagementtempo.command.TempoCommands;
import com.engagementtempo.config.TempoConfig;
import com.engagementtempo.hud.TempoHudRenderer;
import com.engagementtempo.listener.TempoEvents;
import com.engagementtempo.state.CombatStateManager;
import com.engagementtempo.tracker.TargetTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionResult;

/** Engagement Tempo HUD - 100% client-side, passive, server-safe. */
public final class EngagementTempoClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TempoConfig.load();

        // Primary target lock: the local player attacks another player.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() && entity instanceof AbstractClientPlayer victim
                    && victim != Minecraft.getInstance().player) {
                TargetTracker.INSTANCE.onLocalAttack(victim);
            }
            return InteractionResult.PASS; // never cancel or alter the attack
        });

        // Ender pearl spawn observer.
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            try {
                TempoEvents.onEntityLoad(entity);
            } catch (Throwable ignored) { }
        });

        // Main loop: target lock/decay, then timers + totem stopwatch.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            TargetTracker.INSTANCE.tick(mc);
            CombatStateManager.INSTANCE.tick(System.nanoTime(), TargetTracker.INSTANCE.getTarget());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            TargetTracker.INSTANCE.clear(System.nanoTime());
            CombatStateManager.INSTANCE.reset();
        });

        // HUD: drawn right after the crosshair.
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, TempoHudRenderer.ID,
                TempoHudRenderer::render);

        TempoCommands.register();
    }
}
