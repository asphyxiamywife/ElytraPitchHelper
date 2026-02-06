package com.asphyxiamywife.elytrapitchhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    public float targetUpMinecraft = -40.0f;
    public float targetDownMinecraft = 40.0f;
    public float toleranceDegrees = 6.0f;
    public int maxOffsetPixels = 42;
    public float offsetPerDegree = 2.0f;
    public int lineColorRgb = 0xFFFFFF;
    public int centerTickColorRgb = 0xFF80FF;
    public boolean showOnlyWithFirework = false;

    private static final String FILE_NAME = "elytra-pitch-helper.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static Config load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Config cfg = GSON.fromJson(reader, Config.class);
                return cfg != null ? cfg : createAndSaveDefault();
            } catch (IOException e) {
                return createAndSaveDefault();
            }
        } else {
            return createAndSaveDefault();
        }
    }

    private static Config createAndSaveDefault() {
        Config cfg = new Config();
        cfg.save();
        return cfg;
    }

    public void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
