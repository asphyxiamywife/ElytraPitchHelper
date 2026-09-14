package com.asphyxiamywife.elytrapitchhelper.clientprobe.mixin;
import com.asphyxiamywife.elytrapitchhelper.clientprobe.ClientProbe;
import com.asphyxiamywife.elytrapitchhelper.client.ClientBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientBootstrap.class)
abstract class BootstrapProbeMixin {
    @Inject(method = "onClientTick", at = @At("RETURN"))
    private void eph$tick(CallbackInfo ci) { ClientProbe.ticks++; }
}
