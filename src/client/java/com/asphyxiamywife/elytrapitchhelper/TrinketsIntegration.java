package com.asphyxiamywife.elytrapitchhelper;

/**
 * Adapted from ElytraPitch (https://github.com/kennethsible/elytrapitch)
 * Original License: MIT
 */

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Pair;

import java.util.Optional;

public class TrinketsIntegration {

    public static ItemStack getElytraItem(PlayerEntity player) {
        Optional<TrinketComponent> component = TrinketsApi.getTrinketComponent(player);
        if (component.isPresent()) {
            for (Pair<SlotReference, ItemStack> pair : component.get().getEquipped(Items.ELYTRA)) {
                return pair.getRight();
            }
        }
        return ItemStack.EMPTY;
    }
}
