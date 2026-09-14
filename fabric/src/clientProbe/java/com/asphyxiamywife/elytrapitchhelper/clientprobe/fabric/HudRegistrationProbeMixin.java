package com.asphyxiamywife.elytrapitchhelper.clientprobe.fabric;
import com.asphyxiamywife.elytrapitchhelper.clientprobe.ClientProbe;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
@Mixin(HudElementRegistry.class)
interface HudRegistrationProbeMixin {
    @Inject(method = "attachElementAfter", at = @At("RETURN"))
    private static void eph$attachment(Identifier target, Identifier id, HudElement element, CallbackInfo ci) {
        if (id.toString().equals("elytrapitchhelper:pitch_guides")) {
            if (!target.equals(VanillaHudElements.CROSSHAIR)) throw new IllegalStateException("Wrong product HUD attachment");
            ClientProbe.hudAttachments++;
        }
    }
}
