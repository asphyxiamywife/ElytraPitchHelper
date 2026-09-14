package com.asphyxiamywife.elytrapitchhelper.mixin;

import com.asphyxiamywife.elytrapitchhelper.client.KeyBindings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {
    @Inject(method = "click", at = @At("TAIL"))
    private static void elytrapitchhelper$capturePaletteClick(InputConstants.Key key, CallbackInfo callback) {
        KeyBindings.onKeyMappingClick(key);
    }
}
