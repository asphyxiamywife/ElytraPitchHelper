package com.asphyxiamywife.elytrapitchhelper.clientprobe;

import com.asphyxiamywife.elytrapitchhelper.client.ClientBootstrap;
import com.asphyxiamywife.elytrapitchhelper.flight.PlayerFlightSeam;
import com.asphyxiamywife.elytrapitchhelper.flight.WorldFlightSeam;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class ClientProbe {
    private static final long START = System.nanoTime();
    private static boolean opening, prepared, complete;
    private static long lastProgress;
    public static boolean inHud;
    public static int hudAttachments;
    public static int fills, renderedFrames, blockCallbacks, chunkCallbacks, ticks;

    public static void frame() {
        if (complete) return;
        Minecraft client = Minecraft.getInstance();
        try {
            long now = System.nanoTime();
            if (now - lastProgress > 10_000_000_000L) {
                lastProgress = now;
                System.out.println("EPH client probe: screen=" + (client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName())
                        + ", prepared=" + prepared + ", ticks=" + ticks + ", fills=" + fills
                        + ", flightTicks=" + (client.player == null ? -1 : client.player.getFallFlyingTicks()));
            }
            require((System.nanoTime() - START) / 1_000_000_000L < 150, "Client bootstrap timed out");
            if (!opening) {
                if (client.gui.overlay() != null) return;
                if (client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
                    client.options.onboardingAccessibilityFinished();
                    client.gui.setScreen(new TitleScreen());
                }
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                opening = true;
                client.options.pauseOnLostFocus = false;
                if (client.gui.hud.isHidden()) client.gui.hud.toggle();
                client.options.renderDistance().set(2);
                client.options.simulationDistance().set(5);
                client.options.framerateLimit().set(60);
                require(SharedConstants.getCurrentVersion().name().equals(System.getProperty("eph.minecraftVersion")), "Wrong client engine version");
                Set<String> keys = Arrays.stream(client.options.keyMappings).map(k -> k.getName()).collect(Collectors.toSet());
                require(keys.containsAll(Set.of("key.elytrapitchhelper.toggle", "key.elytrapitchhelper.open_config",
                        "key.elytrapitchhelper.open_profiles", "key.elytrapitchhelper.open_palette_modifier",
                        "key.elytrapitchhelper.open_palette")), "Product key mappings were not registered");
                MixinEnvironment.getCurrentEnvironment().audit();
                client.createWorldOpenFlows().createFreshLevel("eph-client-contract",
                        new LevelSettings("EPH client contract", GameType.CREATIVE,
                                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                                true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(0L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
                return;
            }
            if (client.player == null || client.level == null || client.gui.screen() != null) return;
            var player = client.player;
            if (!prepared) {
                prepared = true;
                player.setPos(player.getX(), player.getY() + 16, player.getZ());
                player.setXRot(20); player.setYRot(0);
                player.setDeltaMovement(0, -0.1, 1.5);
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FIREWORK_ROCKET));
                var state = new PlayerFlightSeam().capturePlayer(player);
                var world = new WorldFlightSeam().captureWorld(player, state);
                require(state.pitch() == 20 && state.y() == player.getY(), "LocalPlayer pose capture differs");
                require(Math.abs(state.verticalSpeed() + 0.1) < 1e-9 && Math.abs(state.horizontalSpeed() - 1.5) < 1e-9,
                        "LocalPlayer velocity capture differs");
                require(state.equippedUsableElytra() && state.hasFireworkRocket(), "LocalPlayer equipment capture differs");
                require(state.boundingBox().minY == player.getY() && state.boundingBox().maxY > state.boundingBox().minY,
                        "LocalPlayer footprint incoherent");
                require(world.minBuildY() == client.level.getMinY() && world.maxBuildY() == client.level.getMaxY()
                        && world.dimensionKey().equals(client.level.dimension().identifier().toString()), "LocalPlayer world capture differs");
                require(client.font.width("EPH") > 0 && client.getWindow().getGuiScaledWidth() > 0, "Invalid client text/window metrics");
                int blocksBefore = blockCallbacks, chunksBefore = chunkCallbacks;
                BlockPos position = player.blockPosition().below();
                client.level.setBlocksDirty(position, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState());
                client.level.onChunkLoaded(new ChunkPos(position.getX() >> 4, position.getZ() >> 4));
                require(blockCallbacks > blocksBefore && chunkCallbacks > chunksBefore, "Product terrain mixins did not invoke callbacks");
            }
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FIREWORK_ROCKET));
            player.getAbilities().flying = false;
            player.setOnGround(false);
            player.startFallFlying();
            player.setXRot(ClientBootstrap.config().activeProfile().pitch().targetDownMinecraft());
            if (renderedFrames > 0 && fills > 0 && ticks > 0) {
                require(hudAttachments == 1, "Expected one product HUD layer after crosshair");
                JsonObject result = new JsonObject();
                result.addProperty("schema", 1);
                result.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());
                result.addProperty("status", "PASS");
                for (String claim : List.of("entrypoint", "hudAttachment", "keys", "mixinAudit", "localPlayer", "hudRender", "blockCallback", "chunkCallback")) result.addProperty(claim, true);
                Path report = Path.of(System.getProperty("eph.clientReport"));
                Files.createDirectories(report.getParent());
                Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
                complete = true;
                client.stop();
            }
        } catch (Throwable failure) {
            complete = true;
            failure.printStackTrace();
            client.stop();
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
