package com.asphyxiamywife.elytrapitchhelper.clientprobe.mixin;
import com.asphyxiamywife.elytrapitchhelper.clientprobe.ClientProbe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiGraphicsExtractor.class)
abstract class GraphicsProbeMixin {
    @Inject(method = "fill(IIIII)V", at = @At("RETURN"))
    private void eph$fill(CallbackInfo ci) { if (ClientProbe.inHud) ClientProbe.fills++; }
}
