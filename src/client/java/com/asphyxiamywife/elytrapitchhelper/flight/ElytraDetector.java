package com.asphyxiamywife.elytrapitchhelper.flight;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ElytraDetector {
    public boolean hasUsableElytra(Player player) {
        return hasUsableElytra(player, false);
    }

    public boolean hasUsableElytra(Player player, boolean anyElytraGlide) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (isUsableElytra(chest)) {
            return true;
        }
        return anyElytraGlide && player.getFallFlyingTicks() > 0;
    }

    private static boolean isUsableElytra(ItemStack itemStack) {
        return itemStack.is(Items.ELYTRA) && itemStack.getDamageValue() < itemStack.getMaxDamage() - 1;
    }
}
