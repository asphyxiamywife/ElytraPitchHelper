package com.asphyxiamywife.elytrapitchhelper.flight;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class PlayerFlightSeam {
    private final ElytraDetector elytraDetector;

    public PlayerFlightSeam() {
        this(new ElytraDetector());
    }

    public PlayerFlightSeam(ElytraDetector elytraDetector) {
        this.elytraDetector = elytraDetector;
    }

    public PlayerFlightState capturePlayer(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        return new PlayerFlightState(player.getXRot(), player.getY(), velocity.y(),
                velocity.horizontalDistance(), player.getBoundingBox(), player.getFallFlyingTicks(),
                elytraDetector.hasUsableElytra(player, false),
                player.getMainHandItem().is(Items.FIREWORK_ROCKET)
                        || player.getItemInHand(InteractionHand.OFF_HAND).is(Items.FIREWORK_ROCKET));
    }
}
