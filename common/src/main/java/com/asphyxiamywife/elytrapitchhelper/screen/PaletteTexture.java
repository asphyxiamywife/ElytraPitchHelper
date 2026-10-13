package com.asphyxiamywife.elytrapitchhelper.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.function.Supplier;

final class PaletteTexture implements AutoCloseable {
    private final NativeImage pixels;
    private final GpuTexture texture;
    private final GpuTextureView textureView;
    private boolean closed;

    PaletteTexture(Supplier<String> label, NativeImage pixels) {
        this.pixels = pixels;
        GpuTexture createdTexture = null;
        GpuTextureView createdView = null;
        try {
            var device = RenderSystem.getDevice();
            createdTexture = device.createTexture(label,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.RGBA8_UNORM, pixels.getWidth(), pixels.getHeight(), 1, 1);
            createdView = device.createTextureView(createdTexture);
            pixels.writeToGpuTexture(device.createCommandEncoder(), createdTexture);
        } catch (RuntimeException | Error failure) {
            if (createdView != null) createdView.close();
            if (createdTexture != null) createdTexture.close();
            pixels.close();
            throw failure;
        }
        texture = createdTexture;
        textureView = createdView;
    }

    NativeImage getPixels() {
        return pixels;
    }

    GpuTextureView getTextureView() {
        return textureView;
    }

    void upload() {
        pixels.writeToGpuTexture(RenderSystem.getDevice().createCommandEncoder(), texture);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        textureView.close();
        texture.close();
        pixels.close();
    }
}
