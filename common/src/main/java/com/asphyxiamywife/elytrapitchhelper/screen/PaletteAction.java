package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

interface PaletteAction {
    Identifier id();

    Component title();

    Component category();

    List<String> keywords();

    int priority();

    boolean available(PaletteState state);

    void execute(Minecraft client, PaletteState state);
}
