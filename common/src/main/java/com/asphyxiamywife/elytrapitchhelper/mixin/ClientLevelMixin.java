package com.asphyxiamywife.elytrapitchhelper.mixin;

import com.asphyxiamywife.elytrapitchhelper.client.ClientBootstrap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void elytrapitchhelper$invalidateChangedColumn(BlockPos pos,
            BlockState oldState, BlockState newState, CallbackInfo callback) {
        ClientBootstrap.onClientBlockChanged(pos);
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void elytrapitchhelper$invalidateLoadedChunk(ChunkPos pos, CallbackInfo callback) {
        ClientBootstrap.onClientChunkChanged(pos);
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void elytrapitchhelper$invalidateUnloadedChunk(LevelChunk chunk, CallbackInfo callback) {
        ClientBootstrap.onClientChunkChanged(chunk.getPos());
    }
}
