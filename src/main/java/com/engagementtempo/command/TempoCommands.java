package com.engagementtempo.command;

import com.engagementtempo.config.TempoConfig;
import com.engagementtempo.hud.TempoHudRenderer;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * /tempo toggle <shield|pearl|totem>
 * /tempo offset <x> <y>
 * /tempo scale <float>
 * /tempo preview
 * Pure Brigadier, client-side only (never sent to the server).
 */
public final class TempoCommands {
    private TempoCommands() {}

    private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(lit("tempo")
                        .then(lit("toggle")
                                .then(lit("shield").executes(c -> toggle(c, "shield")))
                                .then(lit("pearl").executes(c -> toggle(c, "pearl")))
                                .then(lit("totem").executes(c -> toggle(c, "totem"))))
                        .then(lit("offset")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("x", IntegerArgumentType.integer(-400, 400))
                                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("y", IntegerArgumentType.integer(-400, 400))
                                                .executes(c -> {
                                                    TempoConfig cfg = TempoConfig.get();
                                                    cfg.offsetX = IntegerArgumentType.getInteger(c, "x");
                                                    cfg.offsetY = IntegerArgumentType.getInteger(c, "y");
                                                    TempoConfig.save();
                                                    msg(c, "Offset set to " + cfg.offsetX + ", " + cfg.offsetY);
                                                    return 1;
                                                }))))
                        .then(lit("scale")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("scale", FloatArgumentType.floatArg(0.5f, 3.0f))
                                        .executes(c -> {
                                            TempoConfig.get().scale = FloatArgumentType.getFloat(c, "scale");
                                            TempoConfig.save();
                                            msg(c, "Scale set to " + TempoConfig.get().scale);
                                            return 1;
                                        })))
                        .then(lit("preview").executes(c -> {
                            boolean on = TempoHudRenderer.togglePreview();
                            msg(c, "Preview " + (on ? "ON (auto-off after 60s)" : "OFF"));
                            return 1;
                        }))));
    }

    private static int toggle(CommandContext<FabricClientCommandSource> c, String which) {
        TempoConfig cfg = TempoConfig.get();
        boolean now;
        switch (which) {
            case "shield" -> now = cfg.shield = !cfg.shield;
            case "pearl" -> now = cfg.pearl = !cfg.pearl;
            default -> now = cfg.totem = !cfg.totem;
        }
        TempoConfig.save();
        msg(c, which + " tracker " + (now ? "ENABLED" : "DISABLED"));
        return 1;
    }

    private static void msg(CommandContext<FabricClientCommandSource> c, String text) {
        c.getSource().sendFeedback(Component.literal("[Tempo] " + text));
    }
}
