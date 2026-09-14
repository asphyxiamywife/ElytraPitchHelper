package com.asphyxiamywife.elytrapitchhelper.clientprobe.mixin;
import com.asphyxiamywife.elytrapitchhelper.clientprobe.ClientProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
abstract class MinecraftProbeMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void eph$frame(CallbackInfo ci) { ClientProbe.frame(); }
}
