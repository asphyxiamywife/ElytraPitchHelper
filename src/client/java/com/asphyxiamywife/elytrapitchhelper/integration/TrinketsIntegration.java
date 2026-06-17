package com.asphyxiamywife.elytrapitchhelper.integration;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Tuple;

public final class TrinketsIntegration {
    private TrinketsIntegration() {
    }

    public static ItemStack getElytraItem(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) {
            return ItemStack.EMPTY;
        }
        for (Tuple<TrinketSlotAccess, ItemStack> pair : attachment.getEquipped(Items.ELYTRA)) {
            return pair.getB();
        }
        return ItemStack.EMPTY;
    }
}
