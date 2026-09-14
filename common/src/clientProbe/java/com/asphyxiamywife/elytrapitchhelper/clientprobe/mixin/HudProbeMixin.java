package com.asphyxiamywife.elytrapitchhelper.clientprobe.mixin;
import com.asphyxiamywife.elytrapitchhelper.clientprobe.ClientProbe;
import com.asphyxiamywife.elytrapitchhelper.hud.PitchGuideHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PitchGuideHud.class)
abstract class HudProbeMixin {
    @Inject(method = "invalidateGroundColumn", at = @At("RETURN"))
    private void eph$block(CallbackInfo ci) { ClientProbe.blockCallbacks++; }
    @Inject(method = "invalidateGroundChunk", at = @At("RETURN"))
    private void eph$chunk(CallbackInfo ci) { ClientProbe.chunkCallbacks++; }
    @Inject(method = "render", at = @At("HEAD"))
    private void eph$begin(CallbackInfo ci) { ClientProbe.inHud = true; }
    @Inject(method = "render", at = @At("RETURN"))
    private void eph$end(CallbackInfo ci) {
        ClientProbe.inHud = false;
        if (ClientProbe.fills > 0) ClientProbe.renderedFrames++;
    }
}
