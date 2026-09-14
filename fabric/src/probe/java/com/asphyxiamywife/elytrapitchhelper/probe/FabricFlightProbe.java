package com.asphyxiamywife.elytrapitchhelper.probe;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class FabricFlightProbe {
    @GameTest(structure = "eph_port_probe:empty", maxTicks = 400)
    public void contracts(GameTestHelper helper) {
        FlightProbe.run(helper);
    }
}
