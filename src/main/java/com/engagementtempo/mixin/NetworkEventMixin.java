package com.engagementtempo.mixin;

import com.engagementtempo.listener.TempoEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only tap on the entity-event packet (shield break 30, totem 35, item-use finish 9). */
@Mixin(ClientPacketListener.class)
public abstract class NetworkEventMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void engagementTempo$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // The handler is first invoked on the netty thread, then re-dispatched to the main thread.
        if (!mc.isSameThread() || mc.level == null) return;
        try {
            Entity entity = packet.getEntity(mc.level);
            if (entity != null) TempoEvents.onEntityEvent(entity, packet.getEventId());
        } catch (Throwable ignored) {
            // A passive HUD must never be able to crash the game.
        }
    }
}
