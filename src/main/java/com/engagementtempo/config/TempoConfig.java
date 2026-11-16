package com.engagementtempo.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lightweight Gson config stored at config/engagement_tempo.json. */
public final class TempoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TempoConfig instance = new TempoConfig();

    public boolean shield = true;
    public boolean pearl = true;
    public boolean totem = true;
    /** Pixel offset from screen centre (before scale). */
    public int offsetX = 18;
    public int offsetY = 14;
    public float scale = 1.0f;

    public static TempoConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("engagement_tempo.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                TempoConfig loaded = GSON.fromJson(r, TempoConfig.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                System.err.println("[EngagementTempo] Failed to read config, using defaults: " + e);
            }
        }
        instance.clamp();
        save();
    }

    public static void save() {
        instance.clamp();
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            System.err.println("[EngagementTempo] Failed to write config: " + e);
        }
    }

    private void clamp() {
        scale = Math.max(0.5f, Math.min(3.0f, scale));
        offsetX = Math.max(-400, Math.min(400, offsetX));
        offsetY = Math.max(-400, Math.min(400, offsetY));
    }
}
