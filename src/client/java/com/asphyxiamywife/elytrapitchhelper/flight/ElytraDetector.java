package com.asphyxiamywife.elytrapitchhelper.flight;

import com.asphyxiamywife.elytrapitchhelper.integration.TrinketsIntegration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ElytraDetector {
    private final boolean trinketsLoaded = FabricLoader.getInstance().isModLoaded("trinkets_updated");

    public boolean hasUsableElytra(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (isUsableElytra(chest)) {
            return true;
        }
        if (!trinketsLoaded) {
            return false;
        }
        return isUsableElytra(TrinketsIntegration.getElytraItem(player));
    }

    private static boolean isUsableElytra(ItemStack itemStack) {
        return itemStack.is(Items.ELYTRA) && itemStack.getDamageValue() < itemStack.getMaxDamage() - 1;
    }
}
