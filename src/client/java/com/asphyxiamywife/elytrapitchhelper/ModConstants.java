package com.asphyxiamywife.elytrapitchhelper;

import net.minecraft.resources.Identifier;

public final class ModConstants {
    public static final String MOD_ID = "elytrapitchhelper";

    private ModConstants() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
