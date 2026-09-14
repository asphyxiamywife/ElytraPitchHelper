package com.asphyxiamywife.elytrapitchhelper.clientprobe.neoforge;
import com.asphyxiamywife.elytrapitchhelper.clientprobe.ClientProbe;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
@Mixin(RegisterGuiLayersEvent.class)
abstract class HudRegistrationProbeMixin {
    @Inject(method = "registerAbove", at = @At("RETURN"))
    private void eph$attachment(Identifier target, Identifier id, GuiLayer element, CallbackInfo ci) {
        if (id.toString().equals("elytrapitchhelper:pitch_guides")) {
            if (!target.equals(VanillaGuiLayers.CROSSHAIR)) throw new IllegalStateException("Wrong product HUD attachment");
            ClientProbe.hudAttachments++;
        }
    }
}
