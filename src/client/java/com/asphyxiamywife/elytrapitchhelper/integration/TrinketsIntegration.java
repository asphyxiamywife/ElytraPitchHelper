package com.asphyxiamywife.elytrapitchhelper.integration;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TrinketsIntegration {
    private TrinketsIntegration() {
    }

    public static ItemStack getElytraItem(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        ItemStack[] found = { ItemStack.EMPTY };
                attachment.forEachWhileTrue((slot, stack) -> {
                    if (stack.is(Items.ELYTRA)) {
                        found[0] = stack;
                        return false;
                    }
                    return true;
                });
                return found[0];
    }
}
