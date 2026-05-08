package com.asphyxiamywife.elytrapitchhelper;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Tuple;

public class TrinketsIntegration {

    public static ItemStack getElytraItem(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        for (Tuple<TrinketSlotAccess, ItemStack> pair : attachment.getEquipped(Items.ELYTRA)) {
            return pair.getB();
        }
        return ItemStack.EMPTY;
    }
}
