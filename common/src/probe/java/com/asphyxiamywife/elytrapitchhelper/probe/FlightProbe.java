package com.asphyxiamywife.elytrapitchhelper.probe;

import com.asphyxiamywife.elytrapitchhelper.flight.FlightLevelBounds;
import com.asphyxiamywife.elytrapitchhelper.flight.PlayerFlightSeam;
import com.asphyxiamywife.elytrapitchhelper.flight.WorldFlightSeam;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

public final class FlightProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String CADENCE = "apply-input-server-level-entity-tick-capture";
    private final GameTestHelper helper;
    private final Path corpus = Path.of(System.getProperty("eph.probeCorpus"));
    private final Path report = Path.of(System.getProperty("eph.probeReport"));
    private final JsonArray ids;
    private int scenarioIndex;
    private long scheduledTick;

    private FlightProbe(GameTestHelper helper) throws Exception {
        this.helper = helper;
        helper.assertTrue(net.minecraft.SharedConstants.getCurrentVersion().name()
                .equals(System.getProperty("eph.minecraftVersion")), "Resolved Minecraft version differs from running engine");
        JsonObject policy = read(corpus.resolve("probe-policy.json"));
        ids = policy.getAsJsonArray("scenarios");
        helper.assertTrue(!ids.isEmpty() && ids.size() <= 32, "Probe scenario budget");
        helper.assertTrue(CADENCE.equals(policy.get("cadence").getAsString()), "Probe sampling policy drift");
        Files.createDirectories(report);
    }

    public static void run(GameTestHelper helper) {
        try {
            new FlightProbe(helper).nextScenario();
        } catch (Exception failure) {
            failure.printStackTrace();
            throw new IllegalStateException("Probe setup failed", failure);
        }
    }

    private void nextScenario() throws Exception {
        if (scenarioIndex == ids.size()) {
            JsonObject complete = new JsonObject();
            complete.addProperty("schema", 1);
            complete.addProperty("minecraftVersion", net.minecraft.SharedConstants.getCurrentVersion().name());
            complete.addProperty("policySha256", digest(corpus.resolve("probe-policy.json")));
            complete.add("scenarios", ids.deepCopy());
            Files.writeString(report.resolve("complete.json"), JSON.toJson(complete) + "\n");
            helper.succeed();
            return;
        }
        String id = ids.get(scenarioIndex++).getAsString();
        helper.assertTrue(id.matches("[a-z0-9_]+"), "Unsafe scenario id");
        Path path = corpus.resolve("scenarios/flight").resolve(id + ".json");
        JsonObject scenario = read(path);
        keys(scenario, "schema", "scenario", "cadence", "worldSeed", "worldFixture", "worldSha256",
                "minBuildY", "maxBuildY", "gameMode", "player", "clearBox", "blocks", "samples");
        helper.assertTrue(scenario.get("schema").getAsInt() == 1 && id.equals(scenario.get("scenario").getAsString()), "Scenario/schema mismatch");
        helper.assertTrue(CADENCE.equals(scenario.get("cadence").getAsString()), "Scenario cadence changed");
        helper.assertTrue("creative".equals(scenario.get("gameMode").getAsString()), "Probe requires creative mode");
        String fixture = scenario.get("worldFixture").getAsString();
        helper.assertTrue(fixture.equals("odd_overworld.json"), "Unknown world fixture");
        helper.assertTrue(digest(corpus.resolve("scenarios/world").resolve(fixture))
                .equals(scenario.get("worldSha256").getAsString()), "World fixture digest changed");
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getSeed() == scenario.get("worldSeed").getAsLong(), "World seed is not pinned");
        helper.assertTrue(level.getMinY() == scenario.get("minBuildY").getAsInt()
                && level.getMaxY() == scenario.get("maxBuildY").getAsInt(), "Pinned build-height fixture was not loaded");
        level.getGameRules().set(GameRules.SPAWN_MOBS, false, level.getServer());
        level.getGameRules().set(GameRules.SPAWN_PATROLS, false, level.getServer());
        level.getGameRules().set(GameRules.SPAWN_PHANTOMS, false, level.getServer());
        level.getGameRules().set(GameRules.SPAWN_WANDERING_TRADERS, false, level.getServer());
        level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, level.getServer());
        level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, level.getServer());
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
        JsonArray clear = scenario.getAsJsonArray("clearBox");
        helper.assertTrue(clear.size() == 6, "Clear box needs six coordinates");
        int[] bounds = new int[6];
        for (int i = 0; i < 6; i++) bounds[i] = clear.get(i).getAsInt();
        long volume = (long) (bounds[3] - bounds[0] + 1) * (bounds[4] - bounds[1] + 1) * (bounds[5] - bounds[2] + 1);
        helper.assertTrue(volume > 0 && volume <= 100_000, "Terrain placement budget");
        for (BlockPos pos : BlockPos.betweenClosed(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5])) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        place(level, scenario.getAsJsonArray("blocks"));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        GameType.CREATIVE.updatePlayerAbilities(player.getAbilities());
        player.getAbilities().flying = false;
        JsonObject start = scenario.getAsJsonObject("player");
        keys(start, "position", "velocity", "pitch", "yaw", "fallFlying", "noGravity", "unbreakableElytra");
        setPosition(player, start.getAsJsonArray("position"));
        setVelocity(player, start.getAsJsonArray("velocity"));
        player.setXRot(start.get("pitch").getAsFloat());
        player.setYRot(start.get("yaw").getAsFloat());
        player.setNoGravity(start.get("noGravity").getAsBoolean());
        player.setOnGround(false);
        ItemStack equipped = new ItemStack(Items.ELYTRA);
        if (start.get("unbreakableElytra").getAsBoolean()) {
            equipped.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        }
        player.setItemSlot(EquipmentSlot.CHEST, equipped);
        if (start.get("fallFlying").getAsBoolean()) player.startFallFlying();
        var playerSeam = new PlayerFlightSeam();
        var worldSeam = new WorldFlightSeam();
        JsonArray actions = scenario.getAsJsonArray("samples");
        helper.assertTrue(!actions.isEmpty() && actions.size() <= 256, "Probe sample budget");
        JsonArray observations = new JsonArray();
        for (int i = 0; i < actions.size(); i++) {
            int sample = i;
            long expectedTick = ++scheduledTick;
            helper.runAtTickTime(expectedTick, () -> {
                try {
                    helper.assertTrue(helper.getTick() == expectedTick, "GameTest sampling tick drifted");
                    JsonObject action = actions.get(sample).getAsJsonObject();
                    keys(action, "position", "velocity", "damageFromMax", "rocket", "blocks", "invalidate", "voidYOverride");
                    if (!action.get("position").isJsonNull()) setPosition(player, action.getAsJsonArray("position"));
                    if (!action.get("velocity").isJsonNull()) setVelocity(player, action.getAsJsonArray("velocity"));
                    if (!action.get("damageFromMax").isJsonNull()) {
                        ItemStack elytra = player.getItemBySlot(EquipmentSlot.CHEST);
                        elytra.setDamageValue(elytra.getMaxDamage() + action.get("damageFromMax").getAsInt());
                    }
                    if (!action.get("rocket").isJsonNull()) {
                        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                        switch (action.get("rocket").getAsString()) {
                            case "main" -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FIREWORK_ROCKET));
                            case "off" -> player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET));
                            case "none" -> { }
                            default -> throw new IllegalArgumentException("Unknown rocket input");
                        }
                    }
                    place(level, action.getAsJsonArray("blocks"));
                    switch (action.get("invalidate").getAsString()) {
                        case "none" -> { }
                        case "column" -> worldSeam.invalidateColumn(0, 0);
                        case "chunk" -> worldSeam.invalidateChunk(0, 0);
                        default -> throw new IllegalArgumentException("Unknown invalidation input");
                    }
                    int damageBefore = player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
                    level.tickNonPassenger(player);
                    var state = playerSeam.capturePlayer(player);
                    var world = worldSeam.captureWorld(player, state);
                    var box = state.boundingBox();
                    Integer override = action.get("voidYOverride").isJsonNull() ? null : action.get("voidYOverride").getAsInt();
                    int voidY = FlightLevelBounds.resolvedVoidY(override, world.minBuildY());
                    int bottom = FlightLevelBounds.terrainScanBottomY(world.minBuildY(), override);
                    int top = FlightLevelBounds.terrainScanTopY(world.maxBuildY(), state.y());
                    var support = world.terrain().detect(bottom, top,
                            (int) Math.floor(box.minX + 1e-7), (int) Math.floor(box.maxX - 1e-7),
                            (int) Math.floor(box.minZ + 1e-7), (int) Math.floor(box.maxZ - 1e-7), (sample + 1) * 50L);
                    helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == damageBefore,
                            "Creative probe unexpectedly drained elytra durability");
                    helper.assertTrue(state.horizontalSpeed() >= 0 && world.minBuildY() <= world.maxBuildY(), "Invalid normalized state");
                    if (id.equals("probe_sustained_glide")) {
                        helper.assertTrue(state.fallFlyingTicks() == sample + 1 && state.y() < start.getAsJsonArray("position").get(1).getAsDouble(),
                                "Real vanilla flight did not advance");
                    }
                    if (id.equals("ledge_to_void")) {
                        String[] expected = {"SUPPORTED", "UNSUPPORTED", "UNSUPPORTED", "UNSUPPORTED", "SUPPORTED", "UNSUPPORTED"};
                        helper.assertTrue(support.name().equals(expected[sample]), "Ledge/cache invalidation contract failed");
                    }
                    JsonObject out = new JsonObject();
                    out.addProperty("tick", sample + 1);
                    number(out, "pitch", state.pitch()); number(out, "y", state.y());
                    number(out, "verticalSpeed", state.verticalSpeed()); number(out, "horizontalSpeed", state.horizontalSpeed());
                    number(out, "minX", box.minX); number(out, "maxX", box.maxX);
                    number(out, "minY", box.minY); number(out, "maxY", box.maxY);
                    number(out, "minZ", box.minZ); number(out, "maxZ", box.maxZ);
                    out.addProperty("fallFlyingTicks", state.fallFlyingTicks());
                    out.addProperty("fallFlying", player.isFallFlying());
                    out.addProperty("usableElytra", state.equippedUsableElytra());
                    out.addProperty("rocket", state.hasFireworkRocket());
                    out.addProperty("elytraDamage", player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue());
                    out.addProperty("elytraMaxDamage", player.getItemBySlot(EquipmentSlot.CHEST).getMaxDamage());
                    out.addProperty("dimension", world.dimensionKey());
                    out.addProperty("minBuildY", world.minBuildY()); out.addProperty("maxBuildY", world.maxBuildY());
                    out.addProperty("voidY", voidY); out.addProperty("scanBottomY", bottom); out.addProperty("scanTopY", top);
                    out.addProperty("groundSupport", support.name());
                    observations.add(out);
                    if (sample + 1 == actions.size()) {
                        JsonObject result = new JsonObject();
                        result.addProperty("scenario", id); result.addProperty("scenarioSha256", digest(path));
                        result.addProperty("schema", 1); result.add("samples", observations);
                        Files.writeString(report.resolve(id + ".json"), JSON.toJson(result) + "\n");
                        nextScenario();
                    }
                } catch (Exception failure) {
                    failure.printStackTrace();
                    throw new IllegalStateException("Probe scenario " + id + " sample " + (sample + 1), failure);
                }
            });
        }
    }

    private void place(ServerLevel level, JsonArray blocks) {
        for (JsonElement element : blocks) {
            JsonArray block = element.getAsJsonArray();
            helper.assertTrue(block.size() == 4, "Block placement shape");
            var state = switch (block.get(3).getAsString()) {
                case "stone" -> Blocks.STONE.defaultBlockState();
                case "air" -> Blocks.AIR.defaultBlockState();
                default -> throw new IllegalArgumentException("Unknown block input");
            };
            level.setBlock(new BlockPos(block.get(0).getAsInt(), block.get(1).getAsInt(), block.get(2).getAsInt()), state, 3);
        }
    }

    private void keys(JsonObject value, String... fields) { helper.assertTrue(value.keySet().equals(Set.of(fields)), "Unknown/missing scenario fields"); }
    private static void setPosition(Player player, JsonArray v) { player.setPos(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()); }
    private static void setVelocity(Player player, JsonArray v) { player.setDeltaMovement(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()); }
    private void number(JsonObject out, String field, double value) {
        helper.assertTrue(Double.isFinite(value), "Non-finite " + field);
        out.addProperty(field, BigDecimal.valueOf(value).setScale(9, RoundingMode.HALF_EVEN).stripTrailingZeros());
    }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static String digest(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
}
