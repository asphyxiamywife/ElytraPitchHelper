package com.asphyxiamywife.elytrapitchhelper.probe;

import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("eph_port_probe")
public final class NeoForgeFlightProbe {
    public NeoForgeFlightProbe(IEventBus bus) {
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, "eph_port_probe");
        functions.register("contracts", () -> FlightProbe::run);
        functions.register(bus);
    }
}
